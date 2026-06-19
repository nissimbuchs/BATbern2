/**
 * 4-phase kanban column model (Epic 14 — Story 14.C.2 / 14.C.3).
 *
 * Replaces the 8-lane board with four phase columns while keeping every card's
 * exact 8-state chip. This module is pure (no React / no i18n) so the phase
 * grouping + the workflow-safe drag geometry can be unit-tested directly.
 *
 * The legal transition allow-list itself stays in `speakerTransitions.ts` and is
 * NOT re-encoded here — this module only answers "which phase is a state in" and
 * "what single forward transition does a cross-column drop trigger".
 */
import type { KanbanState } from './speakerTransitions';

export type PhaseKey = 'sourcing' | 'inviting' | 'content' | 'confirmed';

export interface PhaseColumn {
  key: PhaseKey;
  /** The 8-state members rendered inside this column (order = card sort order within a severity band). */
  states: readonly KanbanState[];
}

/**
 * FR15 — the four phase columns. DECLINED is intentionally absent: it renders as a
 * collapsible bottom strip (FR18), not a column.
 */
export const PHASE_COLUMNS: readonly PhaseColumn[] = [
  { key: 'sourcing', states: ['IDENTIFIED', 'CONTACTED'] },
  { key: 'inviting', states: ['READY', 'INVITED'] },
  { key: 'content', states: ['ACCEPTED', 'CONTENT_SUBMITTED'] },
  { key: 'confirmed', states: ['QUALITY_REVIEWED'] },
] as const;

/** All non-terminal states the four columns render (DECLINED excluded). */
export const PHASE_COLUMN_STATES: readonly KanbanState[] = PHASE_COLUMNS.flatMap((c) => c.states);

const STATE_TO_PHASE: Readonly<Record<KanbanState, PhaseKey | null>> = {
  IDENTIFIED: 'sourcing',
  CONTACTED: 'sourcing',
  READY: 'inviting',
  INVITED: 'inviting',
  ACCEPTED: 'content',
  CONTENT_SUBMITTED: 'content',
  QUALITY_REVIEWED: 'confirmed',
  DECLINED: null, // strip, not a column
};

/** Phase a given state belongs to, or null for DECLINED (the strip). */
export function phaseForState(state: KanbanState): PhaseKey | null {
  return STATE_TO_PHASE[state] ?? null;
}

const PHASE_INDEX: Readonly<Record<PhaseKey, number>> = {
  sourcing: 0,
  inviting: 1,
  content: 2,
  confirmed: 3,
};

/**
 * The single forward successor of a state along the linear funnel. Returns null
 * for QUALITY_REVIEWED (no forward step) and DECLINED (terminal). This is the
 * step a cross-column forward drag advances — exactly one (FR19): dropping
 * IDENTIFIED on the *Content* column still only triggers IDENTIFIED → CONTACTED.
 */
export function forwardSuccessor(state: KanbanState): KanbanState | null {
  switch (state) {
    case 'IDENTIFIED':
      return 'CONTACTED';
    case 'CONTACTED':
      return 'READY';
    case 'READY':
      return 'INVITED';
    case 'INVITED':
      return 'ACCEPTED';
    case 'ACCEPTED':
      return 'CONTENT_SUBMITTED';
    case 'CONTENT_SUBMITTED':
      return 'QUALITY_REVIEWED';
    default:
      return null;
  }
}

export type ColumnDrop = { kind: 'forward'; from: KanbanState; to: KanbanState } | { kind: 'noop' };

/**
 * Resolve a drop of a card (`from` state) onto a phase column (`targetPhase`).
 *
 * - A drop onto a phase strictly *after* the card's current phase is a **forward**
 *   move and resolves to the card's single forward successor (FR19 — one step only).
 * - A drop onto the same or an earlier phase is a **noop** (snap-back, FR20). The
 *   one legal back-transition (QUALITY_REVIEWED → CONTENT_SUBMITTED) and Decline live
 *   on the card/drawer affordances, not on the drag, so the allow-list is untouched.
 */
export function resolveColumnDrop(from: KanbanState, targetPhase: PhaseKey): ColumnDrop {
  const fromPhase = phaseForState(from);
  if (!fromPhase) return { kind: 'noop' }; // DECLINED is not draggable
  if (PHASE_INDEX[targetPhase] <= PHASE_INDEX[fromPhase]) return { kind: 'noop' };
  const to = forwardSuccessor(from);
  if (!to) return { kind: 'noop' };
  return { kind: 'forward', from, to };
}
