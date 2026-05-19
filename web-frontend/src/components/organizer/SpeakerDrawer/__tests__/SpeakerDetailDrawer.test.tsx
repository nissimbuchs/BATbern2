/**
 * SpeakerDetailDrawer Tests — Story 11.D.4 (AC10 cases 31-38).
 *
 * Covers the redesigned 2-tab drawer (Details / History — Overview + Activity tabs
 * deleted per AC7.5) and the review-patch convergence onto rich-modal callbacks
 * (Resolved Q#1).
 *
 *   31. Primary-action surface renders at top of drawer body for ACCEPTED speakers.
 *   32. Secondary actions list (Decline / Reassign organizer / Edit details /
 *       Override state) for CONTACTED speakers.
 *   33. Content sub-tab chip ONLY for READY / ACCEPTED / CONTENT_SUBMITTED /
 *       QUALITY_REVIEWED.
 *   34. 2-tab layout (Details + History) with Overview + Activity tabs absent.
 *   35. Clicking "Decline with reason" opens the StatusChangeDialog.
 *   36. Override-state popover lists legal targets — without DECLINED (DECLINED has
 *       its own dedicated decline row).
 *   37. Override ACCEPTED → CONTENT_SUBMITTED dispatches to ContentSubmissionSubView.
 *   38. Override IDENTIFIED → CONTACTED dispatches to the parent's `onLogOutreach`
 *       callback (Resolved Q#1 review-patch convergence).
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerDetailDrawer } from '../SpeakerDetailDrawer';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

// i18n mock — passthrough that strips namespace, returns deterministic English labels
// for the keys the drawer + its children actually render. Mirrors the pattern in
// SpeakerStatusLanes.test.tsx (namespace-stripped behavior).
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, paramsOrDefault?: unknown) => {
      const labels: Record<string, string> = {
        // Speaker status labels (used by header chip + override popover menu items)
        'organizer:speakerStatus.IDENTIFIED': 'Identified',
        'organizer:speakerStatus.CONTACTED': 'Contacted',
        'organizer:speakerStatus.READY': 'Ready',
        'organizer:speakerStatus.INVITED': 'Invited',
        'organizer:speakerStatus.ACCEPTED': 'Accepted',
        'organizer:speakerStatus.CONTENT_SUBMITTED': 'Content submitted',
        'organizer:speakerStatus.QUALITY_REVIEWED': 'Quality reviewed',
        'organizer:speakerStatus.DECLINED': 'Declined',
        // Secondary actions
        'organizer:speakerDrawer.secondaryActions.decline': 'Decline with reason',
        'organizer:speakerDrawer.secondaryActions.reassignOrganizer': 'Reassign organizer',
        'organizer:speakerDrawer.secondaryActions.editDetails': 'Edit details',
        'organizer:speakerDrawer.secondaryActions.overrideState': 'Override state',
        // Tabs + sub-tabs
        'organizer:speakerDrawer.tabs.details': 'Details',
        'organizer:speakerDrawer.tabs.history': 'History',
        'organizer:speakerDrawer.subTabs.content': 'Content',
        // History panel
        'organizer:speakerDrawer.history.noEntries': 'No history yet.',
        'organizer:speakerDrawer.history.loadError': 'Failed to load history.',
        // Primary-action button labels (from getPrimaryAction)
        'organizer:speakerCard.primaryAction.logOutreach': 'Log outreach',
        'organizer:speakerCard.primaryAction.promoteToSpeaker': 'Promote to speaker',
        'organizer:speakerCard.primaryAction.sendInvitation': 'Send invitation',
        'organizer:speakerCard.primaryAction.viewResponseStatus': 'View response status',
        'organizer:speakerCard.primaryAction.enterContent': 'Enter content',
        'organizer:speakerCard.primaryAction.reviewContent': 'Review content',
        'organizer:speakerCard.primaryAction.assignSessionSlot': 'Assign session slot',
        'organizer:speakerCard.primaryAction.viewDetails': 'View details',
        'organizer:speakerCard.publishable': 'Publishable',
      };
      if (labels[key]) return labels[key];
      // i18next-style default-value support — `t(key, { defaultValue })` or
      // `t(key, fallbackString)`.
      if (typeof paramsOrDefault === 'string') return paramsOrDefault;
      if (
        paramsOrDefault &&
        typeof paramsOrDefault === 'object' &&
        'defaultValue' in paramsOrDefault &&
        typeof (paramsOrDefault as { defaultValue?: unknown }).defaultValue === 'string'
      ) {
        return (paramsOrDefault as { defaultValue: string }).defaultValue;
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
}));

// Service / hook mocks — keep the drawer pure for unit-test purposes.
vi.mock('@/services/speakerStatusService', () => ({
  speakerStatusService: {
    updateStatus: vi.fn(() => Promise.resolve({})),
    getStatusHistory: vi.fn(() => Promise.resolve([])),
    getStatusSummary: vi.fn(() => Promise.resolve({})),
  },
}));

vi.mock('@/hooks/useSpeakerOutreach', () => ({
  useSpeakerOutreachHistory: () => ({
    data: [],
    isLoading: false,
    isError: false,
  }),
}));

// AssignedOrganizerField pulls in OrganizerSelect → fetch organizers. Stub the
// field-level component used by DetailsTabPanel.
vi.mock('@/components/organizer/SpeakerDrawer/AssignedOrganizerField', () => ({
  AssignedOrganizerField: () => <div data-testid="assigned-organizer-field-stub" />,
}));

// ContentSubmissionSubView is rendered when drawerView === 'content-submission'.
// Stub it so the drawer test doesn't have to mount the user-autocomplete + upload
// machinery. Surfaces a data-testid the AC10#37 assertion targets.
vi.mock('../ContentSubmissionSubView', () => ({
  ContentSubmissionSubView: ({ speaker }: { speaker: SpeakerPoolEntry }) => (
    <div data-testid="content-submission-subview-stub" data-speaker-id={speaker.id}>
      ContentSubmissionSubView stub
    </div>
  ),
}));

vi.mock('../QualityReviewSubView', () => ({
  QualityReviewSubView: ({ speaker }: { speaker: SpeakerPoolEntry }) => (
    <div data-testid="quality-review-subview-stub" data-speaker-id={speaker.id}>
      QualityReviewSubView stub
    </div>
  ),
}));

// The history panel uses the mocked outreach hook + speakerStatusService — those
// already return empty arrays above so it renders the "no entries" empty state.

describe('SpeakerDetailDrawer — Story 11.D.4 AC10 cases 31-38', () => {
  let queryClient: QueryClient;
  const eventCode = 'BATbern56';

  const makeSpeaker = (
    status: SpeakerWorkflowState,
    overrides: Partial<SpeakerPoolEntry> = {}
  ): SpeakerPoolEntry => ({
    id: `speaker-${status.toLowerCase()}`,
    eventId: 'event-1',
    speakerName: `${status} Speaker`,
    status,
    createdAt: '2026-05-01T00:00:00Z',
    updatedAt: '2026-05-15T00:00:00Z',
    ...overrides,
  });

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  const renderDrawer = (
    overrides: Partial<React.ComponentProps<typeof SpeakerDetailDrawer>> = {}
  ) => {
    const props: React.ComponentProps<typeof SpeakerDetailDrawer> = {
      open: true,
      onClose: vi.fn(),
      speaker: overrides.speaker ?? makeSpeaker('ACCEPTED'),
      eventCode,
      ...overrides,
    };
    return render(
      <QueryClientProvider client={queryClient}>
        <SpeakerDetailDrawer {...props} />
      </QueryClientProvider>
    );
  };

  // Case 31 — Primary action button is rendered (its presence at the top of the body
  // is structural; the rendering of `drawer-primary-action-button-{id}` testid is the
  // assertion target).
  it('should_renderPrimaryActionSurface_atTopOfDrawerBody_forAcceptedSpeaker', () => {
    const speaker = makeSpeaker('ACCEPTED');
    renderDrawer({ speaker });

    const primaryBtn = screen.getByTestId(`drawer-primary-action-button-${speaker.id}`);
    expect(primaryBtn).toBeInTheDocument();
    expect(primaryBtn).toHaveTextContent('Enter content');
    expect(primaryBtn).toHaveAttribute('data-action', 'enter-content');

    // Structural assertion — the primary-action surface appears before the secondary
    // actions list in the drawer's DOM order (top-of-body invariant).
    const secondaryList = screen.getByTestId('drawer-secondary-actions');
    expect(
      primaryBtn.compareDocumentPosition(secondaryList) & Node.DOCUMENT_POSITION_FOLLOWING
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  // Case 32 — Secondary actions list renders Decline + Reassign + Edit + Override
  // for a CONTACTED speaker (every legal CONTACTED transition is non-empty so all
  // four rows are present).
  it('should_renderSecondaryActionsList_withDeclineEditReassignOverride_forContactedSpeaker', () => {
    const speaker = makeSpeaker('CONTACTED');
    renderDrawer({ speaker });

    const list = screen.getByTestId('drawer-secondary-actions');
    expect(within(list).getByTestId('drawer-action-decline')).toBeInTheDocument();
    expect(within(list).getByTestId('drawer-action-reassign-organizer')).toBeInTheDocument();
    expect(within(list).getByTestId('drawer-action-edit-details')).toBeInTheDocument();
    expect(within(list).getByTestId('drawer-action-override-state')).toBeInTheDocument();

    expect(within(list).getByText('Decline with reason')).toBeInTheDocument();
    expect(within(list).getByText('Reassign organizer')).toBeInTheDocument();
    expect(within(list).getByText('Edit details')).toBeInTheDocument();
    expect(within(list).getByText('Override state')).toBeInTheDocument();
  });

  // Case 33 (Epic 11 bug fix 2026-05-18) — Content TAB renders for READY / ACCEPTED /
  // CONTENT_SUBMITTED / QUALITY_REVIEWED, and only those four states. Replaces the
  // prior Chip-based variant.
  it('should_renderContentTab_only_forContentTabStates', () => {
    const tabStates: SpeakerWorkflowState[] = [
      'READY',
      'ACCEPTED',
      'CONTENT_SUBMITTED',
      'QUALITY_REVIEWED',
    ];
    const noTabStates: SpeakerWorkflowState[] = ['IDENTIFIED', 'CONTACTED', 'INVITED', 'DECLINED'];

    for (const status of tabStates) {
      const { unmount } = renderDrawer({ speaker: makeSpeaker(status) });
      expect(screen.getByTestId('drawer-tab-content')).toBeInTheDocument();
      unmount();
    }

    for (const status of noTabStates) {
      const { unmount } = renderDrawer({ speaker: makeSpeaker(status) });
      expect(screen.queryByTestId('drawer-tab-content')).not.toBeInTheDocument();
      unmount();
    }
  });

  // Case 34 — Tab layout. Details + History always render; Content tab is conditional
  // (only for READY+). The deleted Overview + Activity tabs must NOT render.
  it('should_renderTabLayout_DetailsAndHistory_andNotOverviewOrActivity', () => {
    renderDrawer({ speaker: makeSpeaker('ACCEPTED') });

    expect(screen.getByTestId('drawer-tab-details')).toBeInTheDocument();
    expect(screen.getByTestId('drawer-tab-content')).toBeInTheDocument();
    expect(screen.getByTestId('drawer-tab-history')).toBeInTheDocument();

    // Regression guard — the deleted OverviewTabPanel / ActivityTabPanel tabs must
    // not surface in the new drawer.
    expect(screen.queryByTestId('drawer-tab-overview')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drawer-tab-activity')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /overview/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /activity/i })).not.toBeInTheDocument();
  });

  // Case 35 — Clicking "Decline with reason" opens the StatusChangeDialog.
  it('should_openStatusChangeDialog_when_DeclineWithReasonClicked', async () => {
    const user = userEvent.setup();
    renderDrawer({ speaker: makeSpeaker('ACCEPTED') });

    // Dialog should not be visible before clicking Decline (always-mounted but
    // closed; querying for the dialog testid returns null when `open === false`).
    expect(screen.queryByTestId('status-change-dialog')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('drawer-action-decline'));

    expect(screen.getByTestId('status-change-dialog')).toBeInTheDocument();
  });

  // Case 36 — Override-state popover lists every legal target EXCEPT DECLINED.
  it('should_listLegalTargets_inOverridePopover_perAllowedTransitions', async () => {
    const user = userEvent.setup();
    // CONTACTED's legal targets: READY + DECLINED. The popover should list READY
    // only (DECLINED has its own dedicated row in the secondary actions list).
    renderDrawer({ speaker: makeSpeaker('CONTACTED') });

    await user.click(screen.getByTestId('drawer-action-override-state'));

    const menu = screen.getByTestId('drawer-override-state-menu');
    expect(within(menu).getByTestId('drawer-override-target-ready')).toBeInTheDocument();
    // DECLINED has its own decline row — popover must NOT duplicate it.
    expect(within(menu).queryByTestId('drawer-override-target-declined')).not.toBeInTheDocument();
  });

  // Case 37 (Epic 11 bug fix 2026-05-18) — Override ACCEPTED → CONTENT_SUBMITTED now
  // switches the drawer to the Content TAB (intent `legal-input` with modal
  // `content-form`). Previously this opened a takeover sub-view; the tab refactor
  // converges on the in-drawer Content tab body.
  it('should_switchToContentTab_when_OverrideACCEPTEDtoCONTENT_SUBMITTED', async () => {
    const user = userEvent.setup();
    const speaker = makeSpeaker('ACCEPTED');
    renderDrawer({ speaker });

    // Default tab for ACCEPTED is Details — the Content tab is mounted but not selected.
    expect(screen.queryByTestId('content-submission-subview-stub')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('drawer-action-override-state'));
    await user.click(screen.getByTestId('drawer-override-target-content_submitted'));

    // The override switches the active tab to 'content' — the stub mounts in-place.
    const sub = screen.getByTestId('content-submission-subview-stub');
    expect(sub).toBeInTheDocument();
    expect(sub).toHaveAttribute('data-speaker-id', speaker.id);
  });

  // Case 38 — Override IDENTIFIED → CONTACTED dispatches to the parent's
  // `onLogOutreach` callback (Resolved Q#1 review-patch convergence — replaces the
  // prior generic-dialog fallback).
  it('should_dispatchToParentOnLogOutreach_when_OverrideIDENTIFIEDtoCONTACTED', async () => {
    const user = userEvent.setup();
    const onLogOutreach = vi.fn();
    const speaker = makeSpeaker('IDENTIFIED');
    renderDrawer({ speaker, onLogOutreach });

    await user.click(screen.getByTestId('drawer-action-override-state'));
    await user.click(screen.getByTestId('drawer-override-target-contacted'));

    expect(onLogOutreach).toHaveBeenCalledTimes(1);
    expect(onLogOutreach).toHaveBeenCalledWith(expect.objectContaining({ id: speaker.id }));
    // Should NOT have surfaced the fallback StatusChangeDialog.
    expect(screen.queryByTestId('status-change-dialog')).not.toBeInTheDocument();
  });
});
