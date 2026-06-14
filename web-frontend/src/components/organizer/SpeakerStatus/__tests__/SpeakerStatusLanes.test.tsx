/**
 * SpeakerStatusLanes Tests — 4-phase kanban (Epic 14 · Story 14.C.2 / 14.C.3).
 *
 * Supersedes the Story 11.D.2/11.D.3 8-lane tests: the board is now 4 phase columns
 * (Sourcing · Inviting · Content · Confirmed) with a collapsible Declined strip. The
 * per-card primary-action contract, the workflow-safe `classifyDrop` dispatch, and the
 * time-in-state chip carry over unchanged and are re-asserted here against the new
 * column geometry.
 *
 * Coverage:
 *   - 4 phase columns; each card keeps its exact 8-state chip (FR15)
 *   - your-move (accent border, top) vs waiting-on-speaker (below divider) sort (FR16)
 *   - Confirmed-column slot tie-in: "needs a slot" link / ✓+time (FR17)
 *   - collapsible Declined strip, collapsed by default (FR18)
 *   - workflow-safe cross-column drag: one transition, opens modal, snap-back (FR19/FR20, NFR1)
 *   - keyboard + ARIA on columns / declined toggle / divider (NFR7)
 *   - per-state primary-action button (carry-over from 11.D.2)
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerStatusLanes } from '../SpeakerStatusLanes';
import { DEFAULT_KANBAN_THRESHOLDS } from '../kanbanThresholds';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

if (!DEFAULT_KANBAN_THRESHOLDS.CONTACTED) {
  throw new Error('kanbanThresholds CONTACTED default missing — test setup invariant violated');
}

vi.mock('date-fns', async () => {
  const actual = await vi.importActual<typeof import('date-fns')>('date-fns');
  return {
    ...actual,
    formatDistanceToNow: vi.fn(() => '2 days'),
  };
});

// i18n mock — passthrough on namespace-stripped keys + interpolation for the messages
// the tests assert against.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'organizer:speakerCard.slotCapacityTooltip' && params) {
        return `Slot capacity reached. ${params.invited} invitations outstanding + ${params.accepted} acceptances for ${params.slots} slots.`;
      }
      if (key === 'organizer:kanbanDrag.rejection.template' && params) {
        return `${params.from} → ${params.to} not allowed — ${params.explanation}`;
      }
      if (key === 'organizer:speakerStatus.declinedStrip' && params) {
        return `▸ Declined (${params.count})`;
      }
      if (key === 'organizer:speakerCard.slotAssignedAt' && params) {
        return `✓ ${params.time}`;
      }
      if (key === 'organizer:speakerCard.timeInStateTooltip' && params) {
        return `Current state since ${params.date}`;
      }
      const labels: Record<string, string> = {
        'organizer:speakerStatus.phaseColumns.sourcing': 'Sourcing',
        'organizer:speakerStatus.phaseColumns.inviting': 'Inviting',
        'organizer:speakerStatus.phaseColumns.content': 'Content',
        'organizer:speakerStatus.phaseColumns.confirmed': 'Confirmed',
        'organizer:speakerStatus.waitingOnSpeakerDivider': 'Waiting on speaker',
        'organizer:speakerCard.needsASlot': '⚠ Needs a slot →',
        'organizer:speakerCard.slotAssigned': 'Slot assigned',
        'organizer:speakerCard.primaryAction.logOutreach': 'Log outreach',
        'organizer:speakerCard.primaryAction.promoteToSpeaker': 'Promote to speaker',
        'organizer:speakerCard.primaryAction.sendInvitation': 'Send invitation',
        'organizer:speakerCard.primaryAction.viewResponseStatus': 'View response status',
        'organizer:speakerCard.primaryAction.enterContent': 'Enter content',
        'organizer:speakerCard.primaryAction.reviewContent': 'Review content',
        'organizer:speakerCard.primaryAction.assignSessionSlot': 'Assign session slot',
        'organizer:speakerCard.primaryAction.viewDetails': 'View details',
        'organizer:speakerCard.publishable': 'Publishable',
        'organizer:kanbanDrag.invalidColumnTooltip': 'Not a valid forward step.',
        'organizer:speakerStatus.lanes': 'Speaker Status',
        'organizer:speakerStatus.dragToChange': 'Drag to change',
        'organizer:speakerStatus.IDENTIFIED': 'Identified',
        'organizer:speakerStatus.CONTACTED': 'Contacted',
        'organizer:speakerStatus.READY': 'Ready',
        'organizer:speakerStatus.INVITED': 'Invited',
        'organizer:speakerStatus.ACCEPTED': 'Accepted',
        'organizer:speakerStatus.CONTENT_SUBMITTED': 'Content submitted',
        'organizer:speakerStatus.QUALITY_REVIEWED': 'Quality reviewed',
        'organizer:speakerStatus.DECLINED': 'Declined',
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

const dndKitTestHandle: {
  onDragStart?: (event: { active: { id: string } }) => void;
  onDragEnd?: (event: { active: { id: string }; over: { id: string } | null }) => void;
  useDraggableCalls: Array<{ id: string; disabled?: boolean }>;
} = { useDraggableCalls: [] };

vi.mock('@dnd-kit/core', () => {
  return {
    DndContext: ({
      children,
      onDragStart,
      onDragEnd,
    }: {
      children: React.ReactNode;
      onDragStart?: (event: { active: { id: string } }) => void;
      onDragEnd?: (event: { active: { id: string }; over: { id: string } | null }) => void;
    }) => {
      dndKitTestHandle.onDragStart = onDragStart;
      dndKitTestHandle.onDragEnd = onDragEnd;
      return <>{children}</>;
    },
    DragOverlay: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    closestCenter: vi.fn(),
    useSensor: vi.fn(),
    useSensors: vi.fn(() => []),
    PointerSensor: vi.fn(),
    useDraggable: ({ id, disabled }: { id: string; disabled?: boolean }) => {
      dndKitTestHandle.useDraggableCalls.push({ id, disabled });
      return { attributes: {}, listeners: {}, setNodeRef: () => undefined, transform: null };
    },
    useDroppable: () => ({ setNodeRef: () => undefined, isOver: false }),
  };
});

vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: { sendInvitation: vi.fn(), getSpeakerPool: vi.fn() },
}));

vi.mock('@/components/shared/OrganizerSelect', () => ({
  useOrganizers: () => ({ organizers: [] }),
  __esModule: true,
}));

describe('SpeakerStatusLanes — 4-phase kanban (Story 14.C.2)', () => {
  let queryClient: QueryClient;
  const eventCode = 'BATbern56';
  const NOW = new Date('2026-05-16T12:00:00Z');

  const makeSpeaker = (
    status: SpeakerWorkflowState,
    overrides: Partial<SpeakerPoolEntry> = {}
  ): SpeakerPoolEntry => ({
    id: `speaker-${status.toLowerCase()}`,
    eventId: 'event-1',
    speakerName: `${status} Speaker`,
    status,
    createdAt: '2026-05-14T00:00:00Z',
    updatedAt: '2026-05-14T00:00:00Z',
    ...overrides,
  });

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    vi.clearAllMocks();
    dndKitTestHandle.onDragStart = undefined;
    dndKitTestHandle.onDragEnd = undefined;
    dndKitTestHandle.useDraggableCalls = [];
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
          sessions={overrides.sessions ?? []}
          maxSlots={overrides.maxSlots}
          eventDate={overrides.eventDate}
          now={overrides.now ?? NOW}
          onLogOutreach={overrides.onLogOutreach}
          onPromoteSpeaker={overrides.onPromoteSpeaker}
          onSendInvitation={overrides.onSendInvitation}
          onEnterContent={overrides.onEnterContent}
          onReviewContent={overrides.onReviewContent}
          onSpeakerClick={overrides.onSpeakerClick}
          onAssignSessionSlot={overrides.onAssignSessionSlot}
        />
      </QueryClientProvider>
    );

  it('should_renderFourPhaseColumns_inFunnelOrder', () => {
    renderLanes([]);
    const columns = screen.getAllByTestId(/^phase-column-(sourcing|inviting|content|confirmed)$/);
    const order = columns.map((el) => el.getAttribute('data-testid'));
    expect(order).toEqual([
      'phase-column-sourcing',
      'phase-column-inviting',
      'phase-column-content',
      'phase-column-confirmed',
    ]);
  });

  it('should_notRenderEightPerStateLanes', () => {
    renderLanes([]);
    // The 8-lane testids are gone.
    expect(screen.queryByTestId('status-lane-identified')).not.toBeInTheDocument();
    expect(screen.queryByTestId('status-lane-quality_reviewed')).not.toBeInTheDocument();
  });

  it('should_placeIdentifiedAndContactedCards_inSourcingColumn', () => {
    const identified = makeSpeaker('IDENTIFIED', { id: 's-id' });
    const contacted = makeSpeaker('CONTACTED', { id: 's-co' });
    renderLanes([identified, contacted]);
    const sourcing = screen.getByTestId('phase-column-sourcing');
    expect(within(sourcing).getByTestId(`speaker-card-${identified.id}`)).toBeInTheDocument();
    expect(within(sourcing).getByTestId(`speaker-card-${contacted.id}`)).toBeInTheDocument();
  });

  it('should_placeReadyAndInvited_inInvitingColumn_andQualityReviewed_inConfirmed', () => {
    const ready = makeSpeaker('READY', { id: 's-rd' });
    const invited = makeSpeaker('INVITED', { id: 's-in' });
    const quality = makeSpeaker('QUALITY_REVIEWED', { id: 's-qr', isSlotAssigned: true });
    renderLanes([ready, invited, quality], { maxSlots: 8 });
    const inviting = screen.getByTestId('phase-column-inviting');
    const confirmed = screen.getByTestId('phase-column-confirmed');
    expect(within(inviting).getByTestId(`speaker-card-${ready.id}`)).toBeInTheDocument();
    expect(within(inviting).getByTestId(`speaker-card-${invited.id}`)).toBeInTheDocument();
    expect(within(confirmed).getByTestId(`speaker-card-${quality.id}`)).toBeInTheDocument();
  });

  it('should_renderExactEightStateChip_onEachCard', () => {
    const accepted = makeSpeaker('ACCEPTED', { id: 's-ac' });
    renderLanes([accepted]);
    const chip = screen.getByTestId(`state-chip-${accepted.id}`);
    expect(chip).toHaveAttribute('data-state', 'ACCEPTED');
    expect(chip).toHaveTextContent('Accepted');
  });

  // FR16 — your-move sort + accent border.
  it('should_markStaleContactedCard_asYourMove_withAccentBorder', () => {
    // CONTACTED stale > errorDays (14) ⇒ error severity ⇒ your move.
    const stale = makeSpeaker('CONTACTED', {
      id: 's-stale',
      updatedAt: new Date(NOW.getTime() - 20 * 86_400_000).toISOString(),
    });
    renderLanes([stale]);
    const card = screen.getByTestId(`speaker-card-${stale.id}`);
    expect(card).toHaveAttribute('data-your-move', 'true');
  });

  it('should_markFreshAcceptedCard_asWaitingOnSpeaker', () => {
    // ACCEPTED fresh (< warningDays 14) ⇒ normal severity ⇒ waiting on speaker.
    const fresh = makeSpeaker('ACCEPTED', {
      id: 's-fresh',
      acceptedAt: new Date(NOW.getTime() - 1 * 86_400_000).toISOString(),
    });
    renderLanes([fresh]);
    const card = screen.getByTestId(`speaker-card-${fresh.id}`);
    expect(card).toHaveAttribute('data-your-move', 'false');
  });

  it('should_renderDivider_when_columnHasBothYourMoveAndWaitingCards', () => {
    const stale = makeSpeaker('ACCEPTED', {
      id: 's-stale-acc',
      acceptedAt: new Date(NOW.getTime() - 30 * 86_400_000).toISOString(),
    });
    const fresh = makeSpeaker('ACCEPTED', {
      id: 's-fresh-acc',
      acceptedAt: new Date(NOW.getTime() - 1 * 86_400_000).toISOString(),
    });
    renderLanes([stale, fresh]);
    expect(screen.getByTestId('phase-column-divider-content')).toBeInTheDocument();
  });

  it('should_notRenderDivider_when_columnHasOnlyOneBand', () => {
    const fresh = makeSpeaker('ACCEPTED', {
      id: 's-only-fresh',
      acceptedAt: new Date(NOW.getTime() - 1 * 86_400_000).toISOString(),
    });
    renderLanes([fresh]);
    expect(screen.queryByTestId('phase-column-divider-content')).not.toBeInTheDocument();
  });

  // FR17 — Confirmed-column slot tie-in.
  it('should_showNeedsASlotLink_forQualityReviewedWithoutSlot', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', { id: 's-noslot', isSlotAssigned: false });
    renderLanes([speaker]);
    const link = screen.getByTestId(`needs-slot-link-${speaker.id}`);
    expect(link).toBeInTheDocument();
    expect(link).toHaveTextContent('Needs a slot');
  });

  it('should_invokeOnAssignSessionSlot_when_needsASlotLinkClicked', async () => {
    const user = userEvent.setup();
    const onAssignSessionSlot = vi.fn();
    const speaker = makeSpeaker('QUALITY_REVIEWED', { id: 's-noslot2', isSlotAssigned: false });
    renderLanes([speaker], { onAssignSessionSlot });
    await user.click(screen.getByTestId(`needs-slot-link-${speaker.id}`));
    expect(onAssignSessionSlot).toHaveBeenCalledTimes(1);
    expect(onAssignSessionSlot).toHaveBeenCalledWith(expect.objectContaining({ id: speaker.id }));
  });

  it('should_showSlotAssignedTime_forQualityReviewedWithSlot', () => {
    const session = {
      id: 'sess-1',
      title: 'A talk',
      startTime: '2026-06-01T13:30:00Z',
      speakers: [],
    } as never;
    const speaker = makeSpeaker('QUALITY_REVIEWED', {
      id: 's-slotted',
      isSlotAssigned: true,
      sessionId: 'sess-1',
    });
    renderLanes([speaker], { sessions: [session] });
    expect(screen.getByTestId(`slot-assigned-${speaker.id}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`needs-slot-link-${speaker.id}`)).not.toBeInTheDocument();
  });

  // FR18 — collapsible Declined strip.
  it('should_renderDeclinedStrip_collapsedByDefault', () => {
    const declined = makeSpeaker('DECLINED', { id: 's-dec' });
    renderLanes([declined]);
    expect(screen.getByTestId('declined-strip')).toBeInTheDocument();
    const toggle = screen.getByTestId('declined-strip-toggle');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(toggle).toHaveTextContent('▸ Declined (1)');
    // Collapsed → the declined card is not in the DOM (unmountOnExit).
    expect(screen.queryByTestId(`speaker-card-${declined.id}`)).not.toBeInTheDocument();
  });

  it('should_expandDeclinedStrip_when_toggleClicked', async () => {
    const user = userEvent.setup();
    const declined = makeSpeaker('DECLINED', { id: 's-dec2' });
    renderLanes([declined]);
    await user.click(screen.getByTestId('declined-strip-toggle'));
    expect(screen.getByTestId('declined-strip-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(await screen.findByTestId(`speaker-card-${declined.id}`)).toBeInTheDocument();
  });

  it('should_notRenderDeclinedStrip_when_noDeclinedSpeakers', () => {
    renderLanes([makeSpeaker('IDENTIFIED', { id: 's-only-id' })]);
    expect(screen.queryByTestId('declined-strip')).not.toBeInTheDocument();
  });

  it('should_notPlaceDeclinedCard_inAnyPhaseColumn', () => {
    const declined = makeSpeaker('DECLINED', { id: 's-dec3' });
    renderLanes([declined]);
    for (const key of ['sourcing', 'inviting', 'content', 'confirmed']) {
      const col = screen.getByTestId(`phase-column-${key}`);
      expect(within(col).queryByTestId(`speaker-card-${declined.id}`)).not.toBeInTheDocument();
    }
  });

  // FR21 — clicking a card opens the drawer.
  it('should_callOnSpeakerClick_when_cardBodyClicked', async () => {
    const user = userEvent.setup();
    const onSpeakerClick = vi.fn();
    const speaker = makeSpeaker('INVITED', { id: 's-click' });
    renderLanes([speaker], { onSpeakerClick });
    await user.click(screen.getByTestId(`speaker-card-${speaker.id}`));
    expect(onSpeakerClick).toHaveBeenCalledWith(expect.objectContaining({ id: speaker.id }));
  });

  // NFR7 — a11y: columns + declined toggle + divider carry roles/labels.
  it('should_labelEachPhaseColumn_asAriaGroup', () => {
    renderLanes([]);
    expect(screen.getByTestId('phase-column-sourcing')).toHaveAttribute('role', 'group');
    expect(screen.getByTestId('phase-column-sourcing')).toHaveAttribute('aria-label', 'Sourcing');
  });

  it('should_toggleDeclinedStrip_viaKeyboard', async () => {
    const user = userEvent.setup();
    const declined = makeSpeaker('DECLINED', { id: 's-dec-kbd' });
    renderLanes([declined]);
    const toggle = screen.getByTestId('declined-strip-toggle');
    toggle.focus();
    expect(toggle).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });

  it('should_labelDivider_asSeparator', () => {
    const stale = makeSpeaker('ACCEPTED', {
      id: 's-stale-d',
      acceptedAt: new Date(NOW.getTime() - 30 * 86_400_000).toISOString(),
    });
    const fresh = makeSpeaker('ACCEPTED', {
      id: 's-fresh-d',
      acceptedAt: new Date(NOW.getTime() - 1 * 86_400_000).toISOString(),
    });
    renderLanes([stale, fresh]);
    const divider = screen.getByTestId('phase-column-divider-content');
    expect(divider).toHaveAttribute('role', 'separator');
    expect(divider).toHaveAttribute('aria-label', 'Waiting on speaker');
  });

  // Per-state primary-action buttons (carry-over contract from 11.D.2).
  it('should_renderLogOutreachButton_when_speakerIsIdentified', () => {
    const speaker = makeSpeaker('IDENTIFIED');
    renderLanes([speaker]);
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Log outreach');
    expect(btn).toHaveAttribute('data-action', 'log-outreach');
  });

  it('should_renderSendInvitationButton_when_speakerIsReady', () => {
    const speaker = makeSpeaker('READY');
    renderLanes([speaker], { maxSlots: 8 });
    const btn = screen.getByTestId(`primary-action-button-${speaker.id}`);
    expect(btn).toHaveTextContent('Send invitation');
  });

  it('should_disableSendInvitation_when_slotCapacityReached', () => {
    const ready = makeSpeaker('READY');
    const accepted = makeSpeaker('ACCEPTED', { id: 'acc-1' });
    const invited = makeSpeaker('INVITED', { id: 'inv-1' });
    renderLanes([ready, accepted, invited], { maxSlots: 2 });
    expect(screen.getByTestId(`primary-action-button-${ready.id}`)).toBeDisabled();
  });

  it('should_renderTimeInStateChip_onCard', () => {
    const speaker = makeSpeaker('IDENTIFIED', { updatedAt: '2026-05-10T00:00:00Z' });
    renderLanes([speaker]);
    expect(screen.getByTestId(`time-in-state-chip-${speaker.id}`)).toHaveTextContent('2 days');
  });

  it('should_notRenderPrimaryActionButton_forQualityReviewed_slotTieInReplacesIt', () => {
    const speaker = makeSpeaker('QUALITY_REVIEWED', { id: 's-qr-noslot', isSlotAssigned: false });
    renderLanes([speaker]);
    // The QUALITY_REVIEWED card uses the slot tie-in, not the assign-slot primary button.
    expect(screen.queryByTestId(`primary-action-button-${speaker.id}`)).not.toBeInTheDocument();
    expect(screen.getByTestId(`needs-slot-link-${speaker.id}`)).toBeInTheDocument();
  });
});

describe('SpeakerStatusLanes — workflow-safe cross-column drag (Story 14.C.3)', () => {
  let queryClient: QueryClient;
  const eventCode = 'BATbern56';
  const NOW = new Date('2026-05-16T12:00:00Z');

  const makeSpeaker = (
    status: SpeakerWorkflowState,
    overrides: Partial<SpeakerPoolEntry> = {}
  ): SpeakerPoolEntry => ({
    id: `speaker-${status.toLowerCase()}`,
    eventId: 'event-1',
    speakerName: `${status} Speaker`,
    status,
    createdAt: '2026-05-14T00:00:00Z',
    updatedAt: '2026-05-14T00:00:00Z',
    ...overrides,
  });

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    vi.clearAllMocks();
    dndKitTestHandle.onDragStart = undefined;
    dndKitTestHandle.onDragEnd = undefined;
    dndKitTestHandle.useDraggableCalls = [];
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
          now={NOW}
          onLogOutreach={overrides.onLogOutreach}
          onPromoteSpeaker={overrides.onPromoteSpeaker}
          onSendInvitation={overrides.onSendInvitation}
          onEnterContent={overrides.onEnterContent}
          onReviewContent={overrides.onReviewContent}
          onSpeakerClick={overrides.onSpeakerClick}
          onAssignSessionSlot={overrides.onAssignSessionSlot}
        />
      </QueryClientProvider>
    );

  // Drop onto a PHASE COLUMN (over.id = phase key), not a per-state lane.
  const dropOnColumn = (fromId: string, phase: string) => {
    if (!dndKitTestHandle.onDragEnd) {
      throw new Error('DndContext onDragEnd was not captured — was renderLanes() called first?');
    }
    act(() => {
      dndKitTestHandle.onDragEnd!({ active: { id: fromId }, over: { id: phase } });
    });
  };

  // FR19 — forward cross-column drag invokes the same modal handler as the card button,
  // advancing exactly ONE transition. Dropping IDENTIFIED on Content fires only
  // IDENTIFIED→CONTACTED (the log-outreach modal), never three steps, never auto-commit.
  it('should_invokeLogOutreachOnce_when_droppingIdentifiedOnContentColumn', () => {
    const onLogOutreach = vi.fn();
    const onPromoteSpeaker = vi.fn();
    const identified = makeSpeaker('IDENTIFIED');
    renderLanes([identified], { onLogOutreach, onPromoteSpeaker });

    dropOnColumn(identified.id, 'content');

    expect(onLogOutreach).toHaveBeenCalledTimes(1);
    expect(onLogOutreach).toHaveBeenCalledWith(expect.objectContaining({ id: identified.id }));
    // Exactly one step — the downstream promote modal must NOT fire.
    expect(onPromoteSpeaker).not.toHaveBeenCalled();
  });

  it('should_invokePromoteModal_when_droppingContactedOnInvitingColumn', () => {
    const onPromoteSpeaker = vi.fn();
    const contacted = makeSpeaker('CONTACTED');
    renderLanes([contacted], { onPromoteSpeaker });

    dropOnColumn(contacted.id, 'inviting');

    expect(onPromoteSpeaker).toHaveBeenCalledTimes(1);
  });

  it('should_invokeSendInvitationModal_when_droppingReadyOnContentColumn', () => {
    const onSendInvitation = vi.fn();
    const ready = makeSpeaker('READY');
    renderLanes([ready], { onSendInvitation, maxSlots: 8 });

    dropOnColumn(ready.id, 'content');

    expect(onSendInvitation).toHaveBeenCalledTimes(1);
  });

  // NFR1 — a drag that would provision/email opens the modal; no mutation fires until confirm.
  it('should_notFireStatusMutation_forLegalInputForwardDrop', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    const updateStatus = vi.mocked(speakerStatusService.updateStatus);
    const onSendInvitation = vi.fn();
    const ready = makeSpeaker('READY');
    renderLanes([ready], { onSendInvitation, maxSlots: 8 });

    // READY→INVITED is within the Inviting phase, so the forward drag crosses into Content.
    dropOnColumn(ready.id, 'content');

    expect(onSendInvitation).toHaveBeenCalledTimes(1);
    expect(updateStatus).not.toHaveBeenCalled();
  });

  // legal-direct (INVITED→ACCEPTED) is the only auto-commit; dropping INVITED on Content
  // advances exactly one step (→ACCEPTED) via the mutation.
  it('should_fireUpdateStatusMutation_when_droppingInvitedOnContentColumn', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    const updateStatus = vi.mocked(speakerStatusService.updateStatus);
    updateStatus.mockResolvedValue({} as never);
    const invited = makeSpeaker('INVITED');
    renderLanes([invited]);

    dropOnColumn(invited.id, 'content');

    await waitFor(() => expect(updateStatus).toHaveBeenCalledTimes(1));
    expect(updateStatus).toHaveBeenCalledWith(eventCode, invited.id, 'ACCEPTED', undefined);
  });

  // FR20 — backward drag snaps back: no modal, no mutation, no toast.
  it('should_snapBack_when_draggingQualityReviewedBackwardToContentColumn', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    const updateStatus = vi.mocked(speakerStatusService.updateStatus);
    const onReviewContent = vi.fn();
    const quality = makeSpeaker('QUALITY_REVIEWED', { isSlotAssigned: false });
    renderLanes([quality], { onReviewContent });

    dropOnColumn(quality.id, 'content');

    expect(onReviewContent).not.toHaveBeenCalled();
    expect(updateStatus).not.toHaveBeenCalled();
    expect(screen.queryByTestId('kanban-drop-toast')).not.toBeInTheDocument();
  });

  // Same-phase drop is a no-op (within-pair advances use the card button, not a drag).
  it('should_noop_when_droppingOnSamePhaseColumn', () => {
    const onLogOutreach = vi.fn();
    const identified = makeSpeaker('IDENTIFIED');
    renderLanes([identified], { onLogOutreach });

    dropOnColumn(identified.id, 'sourcing');

    expect(onLogOutreach).not.toHaveBeenCalled();
  });

  // legal-decline is no longer reachable by drag (no DECLINED column); decline-by-drop
  // forward is impossible. Slot-capacity blocked forward drop surfaces the toast.
  it('should_showSlotCapacityToast_when_droppingReadyForward_andCapacityReached', async () => {
    const ready = makeSpeaker('READY');
    const accepted = makeSpeaker('ACCEPTED', { id: 'acc-1' });
    const invited = makeSpeaker('INVITED', { id: 'inv-1' });
    renderLanes([ready, accepted, invited], { maxSlots: 2 });

    dropOnColumn(ready.id, 'content');

    const toast = await screen.findByTestId('kanban-drop-toast');
    expect(toast).toHaveTextContent(/Slot capacity reached/);
  });

  it('should_disableDraggable_when_speakerIsDeclined', async () => {
    const user = userEvent.setup();
    const declined = makeSpeaker('DECLINED');
    const identified = makeSpeaker('IDENTIFIED', { id: 's-id-1' });
    renderLanes([declined, identified]);
    // Expand the strip so the declined card mounts and its useDraggable runs.
    await user.click(screen.getByTestId('declined-strip-toggle'));
    await screen.findByTestId(`speaker-card-${declined.id}`);

    const declinedCall = dndKitTestHandle.useDraggableCalls.find((c) => c.id === declined.id);
    const identifiedCall = dndKitTestHandle.useDraggableCalls.find((c) => c.id === identified.id);
    expect(declinedCall?.disabled).toBe(true);
    expect(identifiedCall?.disabled).toBe(false);
  });

  it('should_resetDragState_onDragEndWithNoTarget', () => {
    const ready = makeSpeaker('READY');
    renderLanes([ready], { maxSlots: 8 });

    act(() => {
      dndKitTestHandle.onDragStart!({ active: { id: ready.id } });
    });
    expect(screen.getByTestId('phase-column-inviting').getAttribute('data-drop-state')).toBe(
      'source'
    );

    act(() => {
      dndKitTestHandle.onDragEnd!({ active: { id: ready.id }, over: null });
    });
    expect(screen.getByTestId('phase-column-inviting').getAttribute('data-drop-state')).toBe(
      'idle'
    );
  });
});
