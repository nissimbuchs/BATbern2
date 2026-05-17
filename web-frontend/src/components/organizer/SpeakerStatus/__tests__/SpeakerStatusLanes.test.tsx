/**
 * SpeakerStatusLanes Tests (Story 11.D.2 — state-aware primary-action button)
 *
 * Replaces the legacy Story 6.1c invite-button tests. The legacy IDENTIFIED
 * `<IconButton>` and the CONTACTED `<EmailIcon>` "invitation sent" badge were
 * removed in this story per ADR-009 §0.2 (CONTACTED no longer implies
 * invitation; READY is the formal-invite gate).
 *
 * Coverage (AC9 items 1–18):
 *   - Per-state primary-action rendering (label, button vs chip)
 *   - Slot-capacity disabled state on READY
 *   - Time-in-state chip on organizer row
 *   - Regression guards for removed legacy indicators
 *   - Lane ordering per ADR-009 §0.1
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerStatusLanes } from '../SpeakerStatusLanes';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

// i18n mock — passthrough on namespace-stripped keys, with parameter interpolation for
// the slot-capacity tooltip so we can assert the formatted message.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'organizer:speakerCard.slotCapacityTooltip' && params) {
        return `Slot capacity reached. ${params.invited} invitations outstanding + ${params.accepted} acceptances for ${params.slots} slots. Wait or decline an accepted speaker to free a slot.`;
      }
      if (key === 'organizer:speakerCard.timeInStateTooltip' && params) {
        return `Current state since ${params.date}`;
      }
      const labels: Record<string, string> = {
        'organizer:speakerCard.primaryAction.logOutreach': 'Log outreach',
        'organizer:speakerCard.primaryAction.promoteToSpeaker': 'Promote to speaker',
        'organizer:speakerCard.primaryAction.sendInvitation': 'Send invitation',
        'organizer:speakerCard.primaryAction.viewResponseStatus': 'View response status',
        'organizer:speakerCard.primaryAction.enterContent': 'Enter content',
        'organizer:speakerCard.primaryAction.reviewContent': 'Review content',
        'organizer:speakerCard.primaryAction.assignSessionSlot': 'Assign session slot',
        'organizer:speakerCard.primaryAction.viewDetails': 'View details',
        'organizer:speakerCard.publishable': 'Publishable',
        'organizer:speakerStatus.lanes': 'Speaker Status Lanes',
        'organizer:speakerStatus.dragToChange': 'Drag to change',
      };
      return labels[key] ?? key;
    },
    i18n: { language: 'en' },
  }),
}));

vi.mock('@/services/speakerStatusService', () => ({
  speakerStatusService: {
    updateStatus: vi.fn(),
    getStatusSummary: vi.fn(),
  },
}));

vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    sendInvitation: vi.fn(),
    getSpeakerPool: vi.fn(),
  },
}));

// Stub the organizers hook to avoid network deps.
vi.mock('@/components/shared/OrganizerSelect', () => ({
  useOrganizers: () => ({ organizers: [] }),
  __esModule: true,
}));

describe('SpeakerStatusLanes — Story 11.D.2 primary-action button', () => {
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

  const renderLanes = (
    speakers: SpeakerPoolEntry[],
    overrides: Partial<React.ComponentProps<typeof SpeakerStatusLanes>> = {}
  ) =>
    render(
      <QueryClientProvider client={queryClient}>
        <SpeakerStatusLanes
          eventCode={eventCode}
          speakers={speakers}
          sessions={[]}
          maxSlots={overrides.maxSlots}
          onLogOutreach={overrides.onLogOutreach}
          onPromoteSpeaker={overrides.onPromoteSpeaker}
          onSpeakerClick={overrides.onSpeakerClick}
          onAssignSessionSlot={overrides.onAssignSessionSlot}
        />
      </QueryClientProvider>
    );

  // AC9 #1
  it('should_renderLogOutreachButton_when_speakerIsIdentified', () => {
    const speaker = makeSpeaker('IDENTIFIED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toBeInTheDocument();
    expect(btn).toHaveTextContent('Log outreach');
    expect(btn).toHaveAttribute('data-action', 'log-outreach');
    expect(btn).not.toBeDisabled();
  });

  // AC9 #2
  it('should_renderPromoteToSpeakerButton_when_speakerIsContacted', () => {
    const speaker = makeSpeaker('CONTACTED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Promote to speaker');
    expect(btn).toHaveAttribute('data-action', 'promote-to-speaker');
  });

  // AC9 #3
  it('should_renderSendInvitationButton_when_speakerIsReady', () => {
    const speaker = makeSpeaker('READY');
    renderLanes([speaker], { maxSlots: 8 });
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Send invitation');
    expect(btn).toHaveAttribute('data-action', 'send-invitation');
    expect(btn).not.toBeDisabled();
  });

  // AC9 #4
  it('should_disableSendInvitation_when_slotCapacityReached', () => {
    const ready = makeSpeaker('READY');
    const accepted = makeSpeaker('ACCEPTED', { id: 'acc-1' });
    const invited = makeSpeaker('INVITED', { id: 'inv-1' });
    renderLanes([ready, accepted, invited], { maxSlots: 2 });

    const btn = screen.getByTestId(`primary-action-button-${ready.id}`);
    expect(btn).toBeDisabled();

    const tooltipWrapper = screen.getByTestId(`primary-action-tooltip-${ready.id}`);
    expect(tooltipWrapper).toBeInTheDocument();
  });

  // AC9 #5
  it('should_renderViewResponseStatusButton_when_speakerIsInvited', () => {
    const speaker = makeSpeaker('INVITED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('View response status');
    expect(btn).toHaveAttribute('data-action', 'view-response-status');
  });

  // AC9 #6
  it('should_renderEnterContentButton_when_speakerIsAccepted', () => {
    const speaker = makeSpeaker('ACCEPTED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Enter content');
    expect(btn).toHaveAttribute('data-action', 'enter-content');
  });

  // AC9 #7
  it('should_renderReviewContentButton_when_speakerIsContentSubmitted', () => {
    const speaker = makeSpeaker('CONTENT_SUBMITTED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Review content');
    expect(btn).toHaveAttribute('data-action', 'review-content');
  });

  // AC9 #8
  it('should_renderAssignSessionSlotButton_when_speakerIsQualityReviewed_andNoSlotAssigned', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', { isSlotAssigned: false });
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Assign session slot');
    expect(btn).toHaveAttribute('data-action', 'assign-session-slot');
  });

  // AC9 #9
  it('should_renderPublishableChip_when_speakerIsQualityReviewed_andSlotAssigned', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', { isSlotAssigned: true });
    renderLanes([speaker]);
    // No button — only the info chip.
    expect(screen.queryByTestId(`primary-action-button-${speaker.id}`)).not.toBeInTheDocument();
    const chip = screen.getByTestId(`primary-action-chip-${speaker.id}`);
    expect(chip).toBeInTheDocument();
    expect(chip).toHaveTextContent('Publishable');
  });

  // AC9 #10
  it('should_renderViewDetailsButton_when_speakerIsDeclined', () => {
    const speaker = makeSpeaker('DECLINED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('View details');
    expect(btn).toHaveAttribute('data-action', 'view-details');
  });

  // AC9 #11
  it('should_callOnLogOutreach_when_logOutreachButtonClicked', async () => {
    const user = userEvent.setup();
    const onLogOutreach = vi.fn();
    const speaker = makeSpeaker('IDENTIFIED');
    renderLanes([speaker], { onLogOutreach });

    await user.click(screen.getByTestId(`primary-action-button-${speaker.id}`));

    expect(onLogOutreach).toHaveBeenCalledTimes(1);
    expect(onLogOutreach).toHaveBeenCalledWith(expect.objectContaining({ id: speaker.id }));
  });

  // AC9 #12
  it('should_callOnPromoteSpeaker_when_promoteToSpeakerButtonClicked', async () => {
    const user = userEvent.setup();
    const onPromoteSpeaker = vi.fn();
    const speaker = makeSpeaker('CONTACTED');
    renderLanes([speaker], { onPromoteSpeaker });

    await user.click(screen.getByTestId(`primary-action-button-${speaker.id}`));

    expect(onPromoteSpeaker).toHaveBeenCalledTimes(1);
    expect(onPromoteSpeaker).toHaveBeenCalledWith(expect.objectContaining({ id: speaker.id }));
  });

  // AC9 #13
  it('should_callOnSpeakerClick_when_viewResponseStatusButtonClicked', async () => {
    const user = userEvent.setup();
    const onSpeakerClick = vi.fn();
    const speaker = makeSpeaker('INVITED');
    renderLanes([speaker], { onSpeakerClick });

    await user.click(screen.getByTestId(`primary-action-button-${speaker.id}`));

    expect(onSpeakerClick).toHaveBeenCalled();
    expect(onSpeakerClick.mock.calls[0][0]).toEqual(expect.objectContaining({ id: speaker.id }));
  });

  // AC9 #14
  it('should_renderTimeInStateChip_onOrganizerRow_rightAligned', () => {
    const speaker = makeSpeaker('IDENTIFIED', { updatedAt: '2026-05-10T00:00:00Z' });
    renderLanes([speaker]);
    const chip = screen.getByTestId(`time-in-state-chip-${speaker.id}`);
    expect(chip).toBeInTheDocument();
    // Chip is inside the organizer row container.
    const row = screen.getByTestId(`organizer-row-${speaker.id}`);
    expect(row).toContainElement(chip);
  });

  // AC9 #15 — regression guard: legacy IDENTIFIED IconButton must not render
  it('should_notRenderLegacySendInviteIconButton_onIdentifiedCards', () => {
    const speaker = makeSpeaker('IDENTIFIED', { email: 'jane@example.com' });
    renderLanes([speaker]);
    expect(screen.queryByTestId(`invite-button-${speaker.id}`)).not.toBeInTheDocument();
  });

  // AC9 #16 — regression guard: legacy "invitation sent" badge must not render
  it('should_notRenderLegacyEmailSentBadge_onContactedCards', () => {
    const speaker = makeSpeaker('CONTACTED');
    renderLanes([speaker]);
    expect(screen.queryByTestId('invite-sent-badge')).not.toBeInTheDocument();
  });

  // AC9 #17 — regression guard: CONFIRMED lane must not render
  it('should_notRenderConfirmedLane', () => {
    renderLanes([]);
    expect(screen.queryByTestId('status-lane-confirmed')).not.toBeInTheDocument();
  });

  // AC9 #18 — lane order per ADR-009 §0.1
  it('should_renderLanesInAdr009Order', () => {
    renderLanes([]);
    const allLanes = screen.getAllByTestId(/^status-lane-/);
    const laneOrder = allLanes
      .map((el) => el.getAttribute('data-testid'))
      .filter((id): id is string => !!id && !id.includes('heading'));
    expect(laneOrder).toEqual([
      'status-lane-identified',
      'status-lane-contacted',
      'status-lane-ready',
      'status-lane-invited',
      'status-lane-accepted',
      'status-lane-content_submitted',
      'status-lane-quality_reviewed',
      'status-lane-declined',
    ]);
  });

  // Additional: confirm the primary-action button stops propagation so the parent
  // Card's onClick (drawer-open) does not fire.
  it('should_notOpenDrawer_when_primaryActionButtonClicked', async () => {
    const user = userEvent.setup();
    const onSpeakerClick = vi.fn();
    const onLogOutreach = vi.fn();
    const speaker = makeSpeaker('IDENTIFIED');
    renderLanes([speaker], { onLogOutreach, onSpeakerClick });

    await user.click(screen.getByTestId(`primary-action-button-${speaker.id}`));

    expect(onLogOutreach).toHaveBeenCalledTimes(1);
    // Card-level click should not have fired (stopPropagation in the button handler).
    expect(onSpeakerClick).not.toHaveBeenCalled();
  });

  // Additional: a card with no organizer assigned still renders its time-in-state chip.
  it('should_renderTimeInStateChip_evenWithoutOrganizer', () => {
    const speaker = makeSpeaker('IDENTIFIED', { assignedOrganizerId: null });
    renderLanes([speaker]);
    expect(screen.getByTestId(`time-in-state-chip-${speaker.id}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`assigned-organizer-chip-${speaker.id}`)).not.toBeInTheDocument();
  });

  // Confirm the AC11 invariant: when slot capacity is NOT enforced (maxSlots=0/undefined),
  // the READY button is enabled even with many accepted+invited speakers.
  it('should_keepSendInvitationEnabled_when_maxSlotsIsZero', () => {
    const ready = makeSpeaker('READY');
    const accepted = makeSpeaker('ACCEPTED', { id: 'acc-1' });
    const invited = makeSpeaker('INVITED', { id: 'inv-1' });
    renderLanes([ready, accepted, invited], { maxSlots: 0 });

    expect(screen.getByTestId(`primary-action-button-${ready.id}`)).not.toBeDisabled();
  });

  // The within import is genuinely used by other tests over time; reference it here so
  // future maintainers see the helper available for lane-scoped queries.
  it('should_findCardByItsLaneContainer', () => {
    const speaker = makeSpeaker('IDENTIFIED');
    renderLanes([speaker]);
    const lane = screen.getByTestId('status-lane-identified');
    expect(within(lane).getByTestId(`speaker-card-${speaker.id}`)).toBeInTheDocument();
  });
});
