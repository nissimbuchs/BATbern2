/**
 * Kanban time-in-state colour-coding thresholds + classification helpers
 * (Story 11.D.3 — UX-DR5, UX-DR6, UX-DR7).
 *
 * Pure-function module: no React, no i18n, no date-fns formatting. Day arithmetic only.
 * Source of truth for the threshold table: `docs/plans/speaker-workflow-refactor.md` §8.7.
 *
 * Per Resolved Q#1, thresholds are **hardcoded defaults only** — no per-event override
 * mechanism. The `thresholds` parameter on each helper exists purely for unit-test
 * boundary probing; the production call site always passes `DEFAULT_KANBAN_THRESHOLDS`.
 */

import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

const MS_PER_DAY = 86_400_000;

export type ThresholdSeverity = 'normal' | 'warning' | 'error';

/**
 * Per-state thresholds. `warningDays` / `errorDays` count days elapsed since the state's
 * entry timestamp. `warningDaysBeforeDeadline` is used for deadline-bound states
 * (INVITED, QUALITY_REVIEWED) where the helper consults speaker fields / event date
 * instead of pure time-in-state.
 */
export interface KanbanStateThresholds {
  warningDays?: number;
  errorDays?: number;
  warningDaysBeforeDeadline?: number;
}

export type KanbanThresholdConfig = Partial<Record<SpeakerWorkflowState, KanbanStateThresholds>>;

/**
 * §8.7 defaults. DECLINED has no thresholds — terminal state, chip stays default.
 */
export const DEFAULT_KANBAN_THRESHOLDS: KanbanThresholdConfig = {
  IDENTIFIED: { warningDays: 30, errorDays: 60 },
  CONTACTED: { warningDays: 7, errorDays: 14 },
  READY: { warningDays: 3, errorDays: 7 },
  INVITED: { warningDaysBeforeDeadline: 3 },
  ACCEPTED: { warningDays: 14 },
  CONTENT_SUBMITTED: { warningDays: 3, errorDays: 7 },
  QUALITY_REVIEWED: { warningDaysBeforeDeadline: 30 },
  DECLINED: {},
};

/**
 * Resolves the "time in current state" anchor timestamp. Uses per-state timestamps
 * where they exist, falling back to `updatedAt` / `createdAt`. The fallback is
 * imperfect (any field update bumps `updatedAt`) — accepted trade-off per
 * Story 11.D.2 Resolved Q#1 and inherited here per Story 11.D.3 Resolved Q#2.
 *
 * Returns ISO string (or undefined). Callers parse to Date.
 */
export function getStatusChangedAt(speaker: SpeakerPoolEntry): string | undefined {
  switch (speaker.status) {
    case 'INVITED':
      if (speaker.invitedAt) return speaker.invitedAt;
      break;
    case 'ACCEPTED':
      if (speaker.acceptedAt) return speaker.acceptedAt;
      break;
    case 'CONTENT_SUBMITTED':
      if (speaker.contentSubmittedAt) return speaker.contentSubmittedAt;
      break;
    case 'DECLINED':
      if (speaker.declinedAt) return speaker.declinedAt;
      break;
  }
  return speaker.updatedAt ?? speaker.createdAt;
}

export interface ClassifyChipInput {
  speaker: SpeakerPoolEntry;
  /** Anchor timestamp = `getStatusChangedAt(speaker)` parsed to Date. */
  statusChangedAt: Date;
  /** Event date — only consulted for the QUALITY_REVIEWED rule. */
  eventDate: Date | null;
  /** `new Date()` at the time of computation. Injected for deterministic testing. */
  now: Date;
  /** Always `DEFAULT_KANBAN_THRESHOLDS` in production; parameter kept for testability. */
  thresholds: KanbanThresholdConfig;
}

function diffDays(later: Date, earlier: Date): number {
  return Math.floor((later.getTime() - earlier.getTime()) / MS_PER_DAY);
}

function parseDateOrNull(value: string | undefined | null): Date | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

/**
 * §8.7 classification. Returns 'normal' on invalid input (Invalid Date) to avoid
 * surfacing a misleading colour.
 */
export function classifyChipSeverity(input: ClassifyChipInput): ThresholdSeverity {
  const { speaker, statusChangedAt, eventDate, now, thresholds } = input;

  if (Number.isNaN(statusChangedAt.getTime())) {
    return 'normal';
  }

  const state = speaker.status as SpeakerWorkflowState;
  const cfg = thresholds[state] ?? {};
  const daysInState = diffDays(now, statusChangedAt);

  switch (state) {
    case 'INVITED': {
      const deadline = parseDateOrNull(speaker.responseDeadline);
      if (!deadline) return 'normal';
      const daysUntil = diffDays(deadline, now);
      if (daysUntil < 0) return 'error';
      const warningWindow = cfg.warningDaysBeforeDeadline ?? 0;
      if (daysUntil <= warningWindow) return 'warning';
      return 'normal';
    }

    case 'ACCEPTED': {
      const contentDeadline = parseDateOrNull(speaker.contentDeadline);
      if (contentDeadline && diffDays(contentDeadline, now) < 0) return 'error';
      if (cfg.warningDays !== undefined && daysInState >= cfg.warningDays) return 'warning';
      return 'normal';
    }

    case 'QUALITY_REVIEWED': {
      if (speaker.isSlotAssigned) return 'normal';
      if (!eventDate || Number.isNaN(eventDate.getTime())) return 'normal';
      const daysUntilEvent = diffDays(eventDate, now);
      if (daysUntilEvent < 14) return 'error';
      const warningWindow = cfg.warningDaysBeforeDeadline ?? 0;
      if (daysUntilEvent <= warningWindow) return 'warning';
      return 'normal';
    }

    case 'DECLINED':
      return 'normal';

    default: {
      // IDENTIFIED, CONTACTED, READY, CONTENT_SUBMITTED — pure time-in-state rules.
      if (cfg.errorDays !== undefined && daysInState >= cfg.errorDays) return 'error';
      if (cfg.warningDays !== undefined && daysInState >= cfg.warningDays) return 'warning';
      return 'normal';
    }
  }
}

/**
 * Per-state predicate: returns true for cards that should contribute to the column's
 * "needs attention" sub-line count (AC2 table) — i.e. the same set the chip will
 * render in warning/error colour, scoped to the subline-meaningful subset.
 *
 * - IDENTIFIED, DECLINED, READY: never (no subline / global gate).
 * - CONTACTED: error-only (= "stale > 14 days"; warning cards 7-14 days are not "stale").
 * - INVITED, ACCEPTED, CONTENT_SUBMITTED: any non-normal severity.
 * - QUALITY_REVIEWED: non-normal AND no slot assigned.
 */
export function makeAttentionPredicate(
  state: SpeakerWorkflowState,
  eventDate: Date | null,
  now: Date,
  thresholds: KanbanThresholdConfig
): (speaker: SpeakerPoolEntry) => boolean {
  return (speaker: SpeakerPoolEntry): boolean => {
    if (speaker.status !== state) return false;

    if (state === 'IDENTIFIED' || state === 'DECLINED' || state === 'READY') {
      return false;
    }

    const anchor = parseDateOrNull(getStatusChangedAt(speaker));
    if (!anchor) return false;

    const severity = classifyChipSeverity({
      speaker,
      statusChangedAt: anchor,
      eventDate,
      now,
      thresholds,
    });

    if (state === 'CONTACTED') {
      return severity === 'error';
    }

    if (state === 'QUALITY_REVIEWED') {
      return severity !== 'normal' && !speaker.isSlotAssigned;
    }

    return severity !== 'normal';
  };
}

/**
 * Count of cards in `speakers` whose status === `state` AND match the per-state
 * attention predicate. Used by the column-header sub-line counts (AC2).
 *
 * Invariant: `countAttentionCards(speakers, state, …) === speakers.filter(makeAttentionPredicate(state, …)).length`.
 */
export function countAttentionCards(
  speakers: SpeakerPoolEntry[],
  state: SpeakerWorkflowState,
  eventDate: Date | null,
  now: Date,
  thresholds: KanbanThresholdConfig
): number {
  const predicate = makeAttentionPredicate(state, eventDate, now, thresholds);
  let count = 0;
  for (const s of speakers) {
    if (predicate(s)) count += 1;
  }
  return count;
}

/**
 * Convenience: count INVITED cards split into "approaching deadline" (warning) and
 * "past deadline" (error). Used by the INVITED two-clause sub-line (AC2 INVITED row).
 */
export function countInvitedSplit(
  speakers: SpeakerPoolEntry[],
  now: Date,
  thresholds: KanbanThresholdConfig
): { approaching: number; past: number } {
  let approaching = 0;
  let past = 0;
  for (const speaker of speakers) {
    if (speaker.status !== 'INVITED') continue;
    const anchor = parseDateOrNull(getStatusChangedAt(speaker));
    if (!anchor) continue;
    const severity = classifyChipSeverity({
      speaker,
      statusChangedAt: anchor,
      eventDate: null,
      now,
      thresholds,
    });
    if (severity === 'warning') approaching += 1;
    else if (severity === 'error') past += 1;
  }
  return { approaching, past };
}

/**
 * MUI `<Chip color>` value for a given severity.
 */
export function severityToChipColor(severity: ThresholdSeverity): 'default' | 'warning' | 'error' {
  if (severity === 'warning') return 'warning';
  if (severity === 'error') return 'error';
  return 'default';
}

/**
 * Max severity across cards that match the column's attention predicate. Used by
 * the column-header sub-line to choose `warning` vs `error` styling (AC2 table:
 * CONTENT_SUBMITTED / QUALITY_REVIEWED sub-lines escalate to `error` when any of
 * their attention cards classify to `error`).
 *
 * Returns `'normal'` when no cards match — i.e. the sub-line should not render.
 */
export function attentionMaxSeverity(
  speakers: SpeakerPoolEntry[],
  state: SpeakerWorkflowState,
  eventDate: Date | null,
  now: Date,
  thresholds: KanbanThresholdConfig
): ThresholdSeverity {
  const predicate = makeAttentionPredicate(state, eventDate, now, thresholds);
  let max: ThresholdSeverity = 'normal';
  for (const speaker of speakers) {
    if (!predicate(speaker)) continue;
    const anchor = parseDateOrNull(getStatusChangedAt(speaker));
    if (!anchor) continue;
    const severity = classifyChipSeverity({
      speaker,
      statusChangedAt: anchor,
      eventDate,
      now,
      thresholds,
    });
    if (severity === 'error') return 'error';
    if (severity === 'warning') max = 'warning';
  }
  return max;
}
