/**
 * Cockpit attention-card registry + completion-signal model
 * (Epic 14 Phase B — Stories 14.B.2 / 14.B.3 / 14.B.4; FR9–FR12, AR3, AR4).
 *
 * Pure, framework-free so the §12.1 per-card completion model can be unit-tested
 * in isolation. The Cockpit "Needs your attention" list is a BLEND of three card
 * sources, and EVERY card in the prototype `STATES` map resolves to exactly one
 * typed completion detector here (no card is left "no signal yet"):
 *
 *   (a) Backend task cards  — real `EventTaskResponse` rows (task-backed):
 *        deep-link target via `resolveTaskTarget`, "done" = status === 'completed'
 *        (filtered out before display). Covers the seeded tasks the prototype shows
 *        as cards: moderator, newsletters, catering, partner-meeting, photos,
 *        session-Q&A, slides-online, and the `Venue Booking` task (AR4 — task-backed,
 *        trigger TOPIC_SELECTION, −90d; hidden when its status is completed).
 *   (b) Data-derived virtual cards — computed from event metrics / sessions /
 *        speaker-pool / registrations; each carries a named `isDone` predicate.
 *   (c) Event-day virtual cards — gated `>= AGENDA_PUBLISHED`, pinned top, urgent,
 *        no done-state (live actions, not tasks).
 *
 * No backend change — all inputs come from existing client data (NFR9/AR9).
 */

import type { EventTaskResponse } from '@/services/taskService';
import {
  WORKFLOW_STATE_ORDER,
  getWorkflowStepNumber,
  type EventTabId,
  type WorkflowStateType,
} from '@/utils/workflow/workflowState';

/** Speakers & Agenda sub-view keys — stable across Phase C's relabel (14.B.2 note). */
export type SpeakersSubView = 'pool' | 'agenda' | 'slots';

/** Where a card's deep-link button leads. */
export type CardTarget =
  | { kind: 'tab'; tab: EventTabId; view?: SpeakersSubView }
  | { kind: 'route'; path: string; newTab?: boolean };

/** Urgency strip + sort bucket. `live` = pinned event-day action. */
export type CardSeverity = 'overdue' | 'dueSoon' | 'upcoming' | 'live';

/**
 * Everything the registry needs to decide which cards are open, derived entirely
 * from existing client data sources (no new backend):
 * - `minSlots` ← `useEventType(event.eventType)` (EventSlotConfigurationResponse)
 * - `awaitingReviewCount` ← `useSpeakerPool` entries with status CONTENT_SUBMITTED
 * - the rest ← `useEvent(['metrics','sessions','registrations','workflow'])`
 */
export interface CockpitCardCtx {
  eventCode: string;
  workflowState: string;
  /**
   * The event moderator (= `Event.organizerUsername`, the same field the Settings
   * "Event Moderator" selector edits). Stamped onto virtual / event-day cards as
   * their assignee avatar, since those aren't backed by an assignable task row.
   */
  moderatorUsername?: string | null;
  topicCode?: string | null;
  /** Minimum viable speaker count for this event type (from the event-type config). */
  minSlots: number;
  confirmedSpeakersCount: number;
  sessionsNeedingSlot: number;
  sessionsWithMaterialsCount: number;
  totalSessionsCount: number;
  /** Speaker-pool entries that submitted content but are not yet quality-reviewed. */
  awaitingReviewCount: number;
  confirmedCount: number;
  waitlistCount: number;
  registrationCapacity: number | null;
}

/** A resolved card ready to render. */
export interface CockpitCard {
  id: string;
  /** i18n key suffix under `eventPage.cockpit.cards`. */
  labelKey: string;
  labelVars?: Record<string, string | number>;
  target?: CardTarget;
  severity: CardSeverity;
  /** Days until/over due (negative = overdue). Undefined for virtual/event-day cards. */
  dueDays?: number;
  /** Event-day live actions sort above everything else. */
  pinned?: boolean;
  /** Assignee username (task cards only) for the avatar. */
  assignee?: string | null;
  /** True for real backend tasks (drives data-testid + completion semantics). */
  taskBacked?: boolean;
}

/* ------------------------------------------------------------------ *
 * State-window helpers
 * ------------------------------------------------------------------ */

const stepOf = (s: string) => getWorkflowStepNumber(s); // 0 if invalid
const isState = (s: string, ...names: WorkflowStateType[]) =>
  names.includes(s as WorkflowStateType);
/** state >= ref in the linear 8-step order (both must be valid). */
const stateGte = (s: string, ref: WorkflowStateType) => {
  const a = stepOf(s);
  const b = stepOf(ref);
  return a > 0 && a >= b;
};

/* ------------------------------------------------------------------ *
 * Due-date classification (shared by task cards)
 * ------------------------------------------------------------------ */

const DAY_MS = 24 * 60 * 60 * 1000;
/** Cards due within this many days are "due soon" (amber); beyond → "upcoming". */
export const DUE_SOON_DAYS = 7;

export interface DueInfo {
  severity: Extract<CardSeverity, 'overdue' | 'dueSoon' | 'upcoming'>;
  /** Whole days until due; negative when overdue; undefined when no due date. */
  days?: number;
}

/** Classify a task's due date into severity + day count (now injected for tests). */
export function classifyDue(dueDate: string | null | undefined, now: number): DueInfo {
  if (!dueDate) return { severity: 'upcoming' };
  const due = new Date(dueDate).getTime();
  if (Number.isNaN(due)) return { severity: 'upcoming' };
  const days = Math.ceil((due - now) / DAY_MS);
  if (days < 0) return { severity: 'overdue', days };
  if (days <= DUE_SOON_DAYS) return { severity: 'dueSoon', days };
  return { severity: 'upcoming', days };
}

/* ------------------------------------------------------------------ *
 * Backend-task → deep-link target mapping
 * ------------------------------------------------------------------ */

/**
 * Ordered name→target rules (DE + EN). Real `EventTaskResponse` rows carry no
 * deep-link target, so we derive one from the task name. ORDER MATTERS — the
 * first matching rule wins (`.find`), so specific rules MUST precede the broad
 * communications catch-all (e.g. "thank-you slides" → wrapup, not communications).
 * Unmatched tasks get no jump button (graceful — the card still shows).
 */
const TASK_TARGET_RULES: Array<{ re: RegExp; target: CardTarget }> = [
  { re: /moderat/i, target: { kind: 'tab', tab: 'settings' } },
  { re: /q\s*&\s*a|q&a|apéro|apero|session q/i, target: { kind: 'tab', tab: 'settings' } },
  { re: /photo|foto/i, target: { kind: 'tab', tab: 'wrapup' } },
  { re: /thank|danke|appreciat/i, target: { kind: 'tab', tab: 'wrapup' } },
  { re: /publish|veröffentlich|agenda finali/i, target: { kind: 'tab', tab: 'publishing' } },
  { re: /slot|zeitplan|zeitfenster/i, target: { kind: 'tab', tab: 'speakers', view: 'slots' } },
  {
    re: /review|submission|einreichung|begutacht/i,
    target: { kind: 'tab', tab: 'speakers', view: 'agenda' },
  },
  { re: /speaker|referent|pool|line.?up/i, target: { kind: 'tab', tab: 'speakers', view: 'pool' } },
  // Communications catch-alls (newsletter / registrant notice / venue & caterer / partner / slides)
  {
    re: /newsletter|registrant|notice|kommunikat|teilnehmer|catering|caterer|venue|location|booking|partner|slides|präsentation online/i,
    target: { kind: 'tab', tab: 'communications' },
  },
];

export function resolveTaskTarget(task: EventTaskResponse): CardTarget | undefined {
  const name = task.taskName ?? '';
  return TASK_TARGET_RULES.find((r) => r.re.test(name))?.target;
}

/* ------------------------------------------------------------------ *
 * Data-derived + workflow-action + event-day virtual cards
 * ------------------------------------------------------------------ */

interface VirtualCardDef {
  id: string;
  labelKey: string;
  severity: CardSeverity;
  pinned?: boolean;
  target: CardTarget | ((ctx: CockpitCardCtx) => CardTarget);
  /** State-window membership — the card only exists for these states. */
  appliesTo: (ctx: CockpitCardCtx) => boolean;
  /** Completion detector — `true` hides the card (FR10). */
  isDone: (ctx: CockpitCardCtx) => boolean;
  labelVars?: (ctx: CockpitCardCtx) => Record<string, string | number>;
}

const VIRTUAL_CARDS: VirtualCardDef[] = [
  // --- Workflow-action (done = underlying field/state change) ---
  {
    id: 'add-details-pick-topic',
    labelKey: 'addDetailsPickTopic',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'details' },
    appliesTo: (c) => isState(c.workflowState, 'CREATED'),
    isDone: (c) => !!c.topicCode,
  },
  {
    id: 'confirm-topic',
    labelKey: 'confirmTopic',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'details' },
    // Lives only in TOPIC_SELECTION → leaving the state IS the completion signal.
    appliesTo: (c) => isState(c.workflowState, 'TOPIC_SELECTION'),
    isDone: () => false,
  },
  {
    id: 'publish-agenda',
    labelKey: 'publishAgenda',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'publishing' },
    appliesTo: (c) => isState(c.workflowState, 'SLOT_ASSIGNMENT'),
    isDone: () => false,
  },
  // --- Data-derived (named predicate over existing data) ---
  {
    id: 'fill-pool',
    labelKey: 'fillPool',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'speakers', view: 'pool' },
    appliesTo: (c) => isState(c.workflowState, 'TOPIC_SELECTION', 'SPEAKER_IDENTIFICATION'),
    // Min-viable threshold from the event-type config (minSlots). minSlots>0 guard
    // avoids hiding the card when the config hasn't loaded yet.
    isDone: (c) => c.minSlots > 0 && c.confirmedSpeakersCount >= c.minSlots,
    labelVars: (c) => ({ confirmed: c.confirmedSpeakersCount, min: c.minSlots }),
  },
  {
    id: 'needs-slot',
    labelKey: 'needsSlot',
    severity: 'upcoming',
    target: { kind: 'tab', tab: 'speakers', view: 'slots' },
    appliesTo: (c) =>
      isState(c.workflowState, 'SPEAKER_IDENTIFICATION', 'SLOT_ASSIGNMENT', 'AGENDA_PUBLISHED'),
    isDone: (c) => c.sessionsNeedingSlot === 0,
    labelVars: (c) => ({ count: c.sessionsNeedingSlot }),
  },
  {
    id: 'review-submissions',
    labelKey: 'reviewSubmissions',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'speakers', view: 'agenda' },
    appliesTo: (c) => isState(c.workflowState, 'SPEAKER_IDENTIFICATION', 'SLOT_ASSIGNMENT'),
    // Real signal from the speaker pool: any entry awaiting quality review.
    isDone: (c) => c.awaitingReviewCount === 0,
    labelVars: (c) => ({ count: c.awaitingReviewCount }),
  },
  {
    id: 'outstanding-presentations',
    labelKey: 'outstandingPresentations',
    severity: 'dueSoon',
    target: { kind: 'tab', tab: 'speakers', view: 'agenda' },
    appliesTo: (c) => isState(c.workflowState, 'AGENDA_PUBLISHED'),
    // Done when every session has materials — OR when there are no sessions at all
    // (nothing to collect), so the card never lingers for a session-less event.
    isDone: (c) =>
      c.totalSessionsCount === 0 || c.sessionsWithMaterialsCount >= c.totalSessionsCount,
  },
  {
    id: 'watch-registrations',
    labelKey: 'watchRegistrations',
    severity: 'upcoming',
    target: { kind: 'tab', tab: 'registrations' },
    appliesTo: (c) => isState(c.workflowState, 'AGENDA_PUBLISHED'),
    isDone: () => false, // ongoing — no done state
    labelVars: (c) => ({
      confirmed: c.confirmedCount,
      capacity: c.registrationCapacity ?? '∞',
    }),
  },
  // --- Event-day (no done-state; pinned top while in the window) ---
  {
    id: 'start-presentation',
    labelKey: 'startPresentation',
    severity: 'live',
    pinned: true,
    target: (c) => ({ kind: 'route', path: `/present/${c.eventCode}`, newTab: true }),
    appliesTo: (c) =>
      stateGte(c.workflowState, 'AGENDA_PUBLISHED') &&
      stepOf(c.workflowState) <= stepOf('EVENT_LIVE'),
    isDone: () => false,
  },
  {
    id: 'open-live-control',
    labelKey: 'openLiveControl',
    severity: 'live',
    pinned: true,
    target: (c) => ({ kind: 'route', path: `/organizer/events/${c.eventCode}/live-control` }),
    appliesTo: (c) =>
      stateGte(c.workflowState, 'AGENDA_PUBLISHED') &&
      stepOf(c.workflowState) <= stepOf('EVENT_LIVE'),
    isDone: () => false,
  },
];

/** Build the OPEN virtual cards for a state (data-derived + workflow-action + event-day). */
export function getVirtualCards(ctx: CockpitCardCtx): CockpitCard[] {
  return VIRTUAL_CARDS.filter((d) => d.appliesTo(ctx) && !d.isDone(ctx)).map((d) => ({
    id: d.id,
    labelKey: d.labelKey,
    labelVars: d.labelVars?.(ctx),
    target: typeof d.target === 'function' ? d.target(ctx) : d.target,
    severity: d.severity,
    pinned: d.pinned,
    // Virtual cards aren't task-backed, so they have no assignee of their own —
    // the event moderator owns them by default (the avatar matches task cards).
    assignee: ctx.moderatorUsername ?? null,
  }));
}

/** Map an OPEN backend task to a card (callers filter completed tasks out first). */
export function taskToCard(task: EventTaskResponse, now: number): CockpitCard {
  const due = classifyDue(task.dueDate, now);
  return {
    id: `task:${task.id}`,
    labelKey: '', // task name is shown verbatim, not translated
    labelVars: { name: task.taskName },
    target: resolveTaskTarget(task),
    severity: due.severity,
    dueDays: due.days,
    assignee: task.assignedOrganizerUsername,
    taskBacked: true,
  };
}

/* ------------------------------------------------------------------ *
 * Merge + sort
 * ------------------------------------------------------------------ */

const SEVERITY_RANK: Record<CardSeverity, number> = {
  live: 0,
  overdue: 1,
  dueSoon: 2,
  upcoming: 3,
};

/**
 * Merge open backend-task cards with virtual cards and sort:
 * pinned (event-day) first, then severity (overdue→due-soon→upcoming),
 * then soonest due first (FR9).
 */
export function buildAttentionList(
  tasks: EventTaskResponse[],
  ctx: CockpitCardCtx,
  now: number
): CockpitCard[] {
  const taskCards = tasks.filter((t) => t.status !== 'completed').map((t) => taskToCard(t, now));
  const cards = [...getVirtualCards(ctx), ...taskCards];

  return cards.sort((a, b) => {
    if (!!a.pinned !== !!b.pinned) return a.pinned ? -1 : 1;
    const sev = SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity];
    if (sev !== 0) return sev;
    // Soonest due first; undated cards (virtual/derived) sort LAST within a bucket
    // rather than masquerading as "due today".
    return (a.dueDays ?? Infinity) - (b.dueDays ?? Infinity);
  });
}

/** Exposed for the §12.1 exhaustiveness unit test. */
export const VIRTUAL_CARD_IDS = VIRTUAL_CARDS.map((c) => c.id);
export { WORKFLOW_STATE_ORDER };
