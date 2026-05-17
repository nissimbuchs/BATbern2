/**
 * Primary-action helper for the speaker kanban card (Story 11.D.2).
 *
 * Maps a speaker's current workflow state to the single most-likely next action,
 * surfaced as a full-width button along the bottom edge of the card. The mapping is
 * the source of truth from `docs/plans/speaker-workflow-refactor.md` §8.2.
 *
 * Two return shapes:
 *   - `kind: 'button'`  → render an MUI `<Button>` (the common case).
 *   - `kind: 'chip'`    → render an MUI `<Chip>` (only QUALITY_REVIEWED with assigned slot).
 *
 * The helper is pure — no React, no hooks — so it can be unit-tested directly.
 */
import type { TFunction } from 'i18next';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

export interface PrimaryActionCallbacks {
  onLogOutreach: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker: (speaker: SpeakerPoolEntry) => void;
  onSendInvitation: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer for the ACCEPTED card. Story 11.D.4 — drawer's Content sub-tab. */
  onEnterContent: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer for the CONTENT_SUBMITTED card. Story 11.D.4 — drawer's Quality Review sub-view. */
  onReviewContent: (speaker: SpeakerPoolEntry) => void;
  onSpeakerClick: (speaker: SpeakerPoolEntry) => void;
  onAssignSessionSlot: (speaker: SpeakerPoolEntry) => void;
}

export interface SlotCapacityState {
  reached: boolean;
  invited: number;
  accepted: number;
  slots: number;
}

/**
 * Computes slot-capacity from the speaker pool + the event's max slots.
 * Post-acceptance states (CONTENT_SUBMITTED, QUALITY_REVIEWED) still occupy a slot
 * per ADR-009. Pure function — kanban + drawer call this to share the same gate
 * (Story 11.D.4 review patch — drawer must honor the same gate as the kanban).
 */
export function computeSlotCapacity(
  speakers: ReadonlyArray<SpeakerPoolEntry>,
  maxSlots: number | undefined
): SlotCapacityState {
  const acceptedCount = speakers.filter((s) =>
    ['ACCEPTED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED'].includes(s.status)
  ).length;
  const invitedCount = speakers.filter((s) => s.status === 'INVITED').length;
  const max = maxSlots ?? 0;
  return {
    reached: max > 0 && acceptedCount + invitedCount >= max,
    invited: invitedCount,
    accepted: acceptedCount,
    slots: max,
  };
}

export type PrimaryAction =
  | {
      kind: 'button';
      label: string;
      onClick: () => void;
      disabled: boolean;
      tooltip?: string;
      testIdSuffix: string;
    }
  | {
      kind: 'chip';
      label: string;
      tooltip?: string;
      testIdSuffix: string;
    }
  | { kind: 'none' };

/**
 * Returns the primary action for a card based on its current workflow state.
 * Returns `{ kind: 'none' }` for unknown states (defensive — should not happen in practice).
 */
export function getPrimaryAction(
  speaker: SpeakerPoolEntry,
  callbacks: PrimaryActionCallbacks,
  slotCapacity: SlotCapacityState,
  t: TFunction
): PrimaryAction {
  const status = speaker.status as SpeakerWorkflowState;

  switch (status) {
    case 'IDENTIFIED':
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.logOutreach'),
        onClick: () => callbacks.onLogOutreach(speaker),
        disabled: false,
        testIdSuffix: 'log-outreach',
      };

    case 'CONTACTED':
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.promoteToSpeaker'),
        onClick: () => callbacks.onPromoteSpeaker(speaker),
        disabled: false,
        testIdSuffix: 'promote-to-speaker',
      };

    case 'READY': {
      const disabled = slotCapacity.reached;
      const tooltip = disabled
        ? t('organizer:speakerCard.slotCapacityTooltip', {
            invited: slotCapacity.invited,
            accepted: slotCapacity.accepted,
            slots: slotCapacity.slots,
          })
        : undefined;
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.sendInvitation'),
        onClick: () => callbacks.onSendInvitation(speaker),
        disabled,
        tooltip,
        testIdSuffix: 'send-invitation',
      };
    }

    case 'INVITED':
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.viewResponseStatus'),
        onClick: () => callbacks.onSpeakerClick(speaker),
        disabled: false,
        testIdSuffix: 'view-response-status',
      };

    case 'ACCEPTED':
      // Story 11.D.4 — opens drawer pre-positioned at the on-behalf content form.
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.enterContent'),
        onClick: () => callbacks.onEnterContent(speaker),
        disabled: false,
        testIdSuffix: 'enter-content',
      };

    case 'CONTENT_SUBMITTED':
      // Story 11.D.4 — opens drawer pre-positioned at the Quality Review sub-view.
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.reviewContent'),
        onClick: () => callbacks.onReviewContent(speaker),
        disabled: false,
        testIdSuffix: 'review-content',
      };

    case 'QUALITY_REVIEWED': {
      // `isSlotAssigned` is a derived flag computed at read time (Story 11.B.3) —
      // strict-true check avoids treating `undefined` (e.g. stale snapshot before the
      // derivation ran) as "no slot". Fall back to `sessionId != null` as a defensive
      // secondary signal that matches the existing session-lookup pattern.
      const hasAssignedSlot = speaker.isSlotAssigned === true || speaker.sessionId != null;
      if (hasAssignedSlot) {
        return {
          kind: 'chip',
          label: t('organizer:speakerCard.publishable'),
          tooltip: t('organizer:speakerCard.publishableTooltip'),
          testIdSuffix: 'publishable',
        };
      }
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.assignSessionSlot'),
        onClick: () => callbacks.onAssignSessionSlot(speaker),
        disabled: false,
        testIdSuffix: 'assign-session-slot',
      };
    }

    case 'DECLINED':
      return {
        kind: 'button',
        label: t('organizer:speakerCard.primaryAction.viewDetails'),
        onClick: () => callbacks.onSpeakerClick(speaker),
        disabled: false,
        testIdSuffix: 'view-details',
      };

    default:
      return { kind: 'none' };
  }
}
