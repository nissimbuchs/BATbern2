/**
 * Speaker workflow transition allow-list (Story 11.D.4 — AC1).
 *
 * The 8-state allow-list mirrored from ADR-009 §0.2 and
 * `docs/architecture/06a-workflow-state-machines.md` lines 296-306. This map is the
 * SINGLE frontend source of truth — the kanban drag-drop dispatcher (AC2-AC6), the
 * detail-drawer secondary-actions list and override-state popover (AC7), and the
 * Playwright E2E suite all consult it.
 *
 * DECLINED is reachable from every non-terminal state. DECLINED has no outgoing
 * transitions (terminal). The provisioning gate at CONTACTED → READY, the
 * slot-capacity gate at READY → INVITED, and the reason requirement on any →
 * DECLINED are NOT encoded here — they are runtime preconditions enforced by
 * `SpeakerWorkflowService.transition()` on the backend (Story 11.B.2). The frontend
 * honours them by routing the drop through the appropriate rich modal (AC4-AC6).
 *
 * Pure module — no React, no hooks, no i18n calls.
 */
import type { TFunction } from 'i18next';
import type { SpeakerWorkflowState } from '@/types/speakerPool.types';

/**
 * The 8 ADR-009 states the kanban renders. Narrows the broader `SpeakerWorkflowState`
 * union (which historically also contained `SLOT_ASSIGNED`, `WITHDREW`, `OVERFLOW`) so
 * legacy union members cannot slip through this module.
 */
export type KanbanState =
  | 'IDENTIFIED'
  | 'CONTACTED'
  | 'READY'
  | 'INVITED'
  | 'ACCEPTED'
  | 'CONTENT_SUBMITTED'
  | 'QUALITY_REVIEWED'
  | 'DECLINED';

export const ALLOWED_TRANSITIONS: Readonly<Record<KanbanState, ReadonlySet<KanbanState>>> = {
  IDENTIFIED: new Set<KanbanState>(['CONTACTED', 'DECLINED']),
  CONTACTED: new Set<KanbanState>(['READY', 'DECLINED']),
  READY: new Set<KanbanState>(['INVITED', 'DECLINED']),
  INVITED: new Set<KanbanState>(['ACCEPTED', 'DECLINED']),
  ACCEPTED: new Set<KanbanState>(['CONTENT_SUBMITTED', 'DECLINED']),
  CONTENT_SUBMITTED: new Set<KanbanState>(['QUALITY_REVIEWED', 'DECLINED']),
  QUALITY_REVIEWED: new Set<KanbanState>(['DECLINED']),
  DECLINED: new Set<KanbanState>(), // terminal
} as const;

/**
 * Returns true when `from → to` is a legal transition per ADR-009 §0.2. Tolerates the
 * broader `SpeakerWorkflowState` union at the input boundary — non-kanban members
 * (legacy `SLOT_ASSIGNED`/`WITHDREW`/`OVERFLOW`) yield `false`.
 */
export function isLegalTransition(from: SpeakerWorkflowState, to: SpeakerWorkflowState): boolean {
  return ALLOWED_TRANSITIONS[from as KanbanState]?.has(to as KanbanState) ?? false;
}

/**
 * Drop intent classification — used by the kanban drag-end dispatcher (AC4) and the
 * detail-drawer override-state popover (AC7.2).
 *
 * - `legal-direct`       → fire the underlying status mutation immediately (no input
 *                          needed). Only `INVITED → ACCEPTED` lands here today (organizer
 *                          marking on behalf; the speaker portal flow uses a different
 *                          endpoint with its own audit principal).
 * - `legal-input`        → open the corresponding rich modal pre-filled:
 *                          - `IDENTIFIED → CONTACTED`  → MarkContactedModal
 *                          - `CONTACTED → READY`        → PromoteSpeakerDialog (11.D.1)
 *                          - `READY → INVITED`          → invitation flow (useSendInvitation)
 *                          - `ACCEPTED → CONTENT_SUBMITTED` → drawer's Content sub-tab (on-behalf form, AC8)
 *                          - `CONTENT_SUBMITTED → QUALITY_REVIEWED` → drawer's Quality Review sub-view
 * - `legal-decline`      → open `StatusChangeDialog` with required-reason field (AC5)
 * - `legal-blocked-slot` → `READY → INVITED` while slotCapacity.reached; reject with
 *                          snackbar toast using `organizer:speakerCard.slotCapacityTooltip` (AC6)
 * - `illegal`            → reject with snackbar toast using the state-machine explanation (AC3)
 */
export type DropIntent =
  | { kind: 'legal-direct' }
  | {
      kind: 'legal-input';
      modal: 'mark-contacted' | 'promote' | 'invitation' | 'content-form' | 'quality-review';
    }
  | { kind: 'legal-decline' }
  | { kind: 'legal-blocked-slot' }
  | { kind: 'illegal' };

/**
 * Classifies a proposed drop. The caller already verified `from !== to`. `slotCapacityReached`
 * is the in-page mirror of the backend `SpeakerWorkflowService.transition(INVITED)` gate
 * (Story 11.B.2); see AC6 defensive note.
 */
export function classifyDrop(
  from: SpeakerWorkflowState,
  to: SpeakerWorkflowState,
  slotCapacityReached: boolean
): DropIntent {
  if (!isLegalTransition(from, to)) return { kind: 'illegal' };
  if (to === 'DECLINED') return { kind: 'legal-decline' };

  // The five "legal-input" transitions per the JSDoc table above.
  if (from === 'IDENTIFIED' && to === 'CONTACTED') {
    return { kind: 'legal-input', modal: 'mark-contacted' };
  }
  if (from === 'CONTACTED' && to === 'READY') {
    return { kind: 'legal-input', modal: 'promote' };
  }
  if (from === 'READY' && to === 'INVITED') {
    return slotCapacityReached
      ? { kind: 'legal-blocked-slot' }
      : { kind: 'legal-input', modal: 'invitation' };
  }
  if (from === 'ACCEPTED' && to === 'CONTENT_SUBMITTED') {
    return { kind: 'legal-input', modal: 'content-form' };
  }
  if (from === 'CONTENT_SUBMITTED' && to === 'QUALITY_REVIEWED') {
    return { kind: 'legal-input', modal: 'quality-review' };
  }

  // INVITED → ACCEPTED is the only `legal-direct` today.
  return { kind: 'legal-direct' };
}

/**
 * Rejection-explanation key lookup used by the invalid-drop toast (AC3). Maps an
 * illegal `(from, to)` pair to a small i18n key under `organizer:kanbanDrag.rejection.*`.
 * Falls back to `cannotMoveBackwards` for any combination not explicitly enumerated —
 * the table is exhaustive for forward skip-ahead attempts; everything else is a
 * backwards move from the organizer's perspective.
 */
function explanationKey(from: KanbanState, to: KanbanState): string {
  // Skip-ahead from IDENTIFIED — must Contact first to log outreach.
  if (
    from === 'IDENTIFIED' &&
    (to === 'READY' ||
      to === 'INVITED' ||
      to === 'ACCEPTED' ||
      to === 'CONTENT_SUBMITTED' ||
      to === 'QUALITY_REVIEWED')
  ) {
    return 'mustPromoteFirst';
  }
  // Skip-ahead from CONTACTED — must Promote (READY) first to provision the speaker.
  if (
    from === 'CONTACTED' &&
    (to === 'INVITED' ||
      to === 'ACCEPTED' ||
      to === 'CONTENT_SUBMITTED' ||
      to === 'QUALITY_REVIEWED')
  ) {
    return 'mustPromoteFirst';
  }
  // Skip-ahead from READY — must Invite (and have acceptance) first.
  if (
    from === 'READY' &&
    (to === 'ACCEPTED' || to === 'CONTENT_SUBMITTED' || to === 'QUALITY_REVIEWED')
  ) {
    return 'mustInviteFirst';
  }
  // Skip-ahead from INVITED — speaker must accept first.
  if (from === 'INVITED' && (to === 'CONTENT_SUBMITTED' || to === 'QUALITY_REVIEWED')) {
    return 'mustAcceptFirst';
  }
  // Skip-ahead from ACCEPTED — content must be submitted first.
  if (from === 'ACCEPTED' && to === 'QUALITY_REVIEWED') {
    return 'mustSubmitContentFirst';
  }
  // All remaining illegal combinations are backwards moves.
  return 'cannotMoveBackwards';
}

/**
 * Returns the localized invalid-drop toast text per AC3. Composition happens in TS so
 * locale files store atomic strings.
 */
export function getRejectionExplanation(
  from: SpeakerWorkflowState,
  to: SpeakerWorkflowState,
  t: TFunction
): string {
  const key = explanationKey(from as KanbanState, to as KanbanState);
  return t('organizer:kanbanDrag.rejection.template', {
    from: t(`organizer:speakerStatus.${from}`),
    to: t(`organizer:speakerStatus.${to}`),
    explanation: t(`organizer:kanbanDrag.rejection.${key}`),
  });
}
