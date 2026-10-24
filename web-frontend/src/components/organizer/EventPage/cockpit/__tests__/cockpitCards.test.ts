/**
 * cockpitCards registry — completion-signal model unit tests (Story 14.B.3 / AR3).
 *
 * Every data-derived / workflow-action / event-day card is covered in BOTH its
 * OPEN and its DONE state, the event-day gate is asserted, completed tasks are
 * filtered, and the merge sort is verified (overdue→due-soon→upcoming, pinned top).
 */

import { describe, it, expect } from 'vitest';
import type { EventTaskResponse } from '@/services/taskService';
import {
  getVirtualCards,
  buildAttentionList,
  classifyDue,
  resolveTaskTarget,
  taskToCard,
  isTaskVisible,
  isTriggerPendingOverdue,
  sortTasksByDue,
  isEventDay,
  showRehearsal,
  presentationTarget,
  liveControlTarget,
  VIRTUAL_CARD_IDS,
  WORKFLOW_STATE_ORDER,
  type CockpitCardCtx,
} from '../cockpitCards';

const baseCtx = (over: Partial<CockpitCardCtx> = {}): CockpitCardCtx => ({
  eventCode: 'BAT54',
  workflowState: 'SPEAKER_IDENTIFICATION',
  topicCode: 'cloud-native',
  moderatorUsername: 'nissim.buchs',
  minSlots: 6,
  confirmedSpeakersCount: 0,
  sessionsNeedingSlot: 0,
  sessionsWithMaterialsCount: 0,
  totalSessionsCount: 0,
  awaitingReviewCount: 0,
  awaitingContentCount: 0,
  speakersPublic: false,
  confirmedCount: 0,
  waitlistCount: 0,
  registrationCapacity: 100,
  ...over,
});

const NOW = new Date('2026-06-13T12:00:00Z').getTime();
const ids = (ctx: CockpitCardCtx, now = NOW) => getVirtualCards(ctx, now).map((c) => c.id);
const has = (ctx: CockpitCardCtx, id: string, now = NOW) => ids(ctx, now).includes(id);
const task = (over: Partial<EventTaskResponse> = {}): EventTaskResponse => ({
  id: 't1',
  eventId: 'e1',
  eventCode: 'BAT54',
  templateId: null,
  taskName: 'Some task',
  triggerState: 'topic_selection',
  dueDate: null,
  assignedOrganizerUsername: 'john.doe',
  status: 'todo',
  notes: null,
  completedDate: null,
  completedByUsername: null,
  createdAt: '',
  updatedAt: '',
  ...over,
});

describe('classifyDue', () => {
  it('no due date → upcoming, no day count', () => {
    expect(classifyDue(null, NOW)).toEqual({ severity: 'upcoming' });
  });
  it('past due → overdue with negative days', () => {
    const r = classifyDue('2026-06-10T12:00:00Z', NOW);
    expect(r.severity).toBe('overdue');
    expect(r.days).toBeLessThan(0);
  });
  it('within 7 days → due soon', () => {
    expect(classifyDue('2026-06-17T12:00:00Z', NOW).severity).toBe('dueSoon');
  });
  it('beyond 7 days → upcoming', () => {
    expect(classifyDue('2026-07-30T12:00:00Z', NOW).severity).toBe('upcoming');
  });
});

describe('resolveTaskTarget', () => {
  it.each([
    ['Assign the moderator', 'details'],
    ['Send the final newsletter', 'communications'],
    ['Request the catering offer', 'communications'],
    ['Venue Booking', 'communications'],
    ['Upload event photos', 'wrapup'],
    ['Finalize & publish the agenda', 'publishing'],
    ['2 sessions still need a slot', 'speakers'],
    ['Review speaker submissions', 'speakers'],
    ['Open session Q&A', 'details'],
    ['Namensschilder', 'registrations'],
    ['Namensschilder drucken', 'registrations'],
    ['Print name tags', 'registrations'],
    ['Name badges', 'registrations'],
  ])('maps "%s" → %s tab', (name, tab) => {
    const target = resolveTaskTarget(task({ taskName: name }));
    expect(target?.kind).toBe('tab');
    expect(target && target.kind === 'tab' && target.tab).toBe(tab);
  });

  it('unmatched task → no target (graceful)', () => {
    expect(resolveTaskTarget(task({ taskName: 'Random chore' }))).toBeUndefined();
  });
});

describe('workflow-action cards', () => {
  it('add-details-pick-topic: open in CREATED without a topic, done once topicCode set', () => {
    expect(
      has(baseCtx({ workflowState: 'CREATED', topicCode: null }), 'add-details-pick-topic')
    ).toBe(true);
    expect(
      has(baseCtx({ workflowState: 'CREATED', topicCode: 'x' }), 'add-details-pick-topic')
    ).toBe(false);
  });
  it('confirm-topic: only in TOPIC_SELECTION', () => {
    expect(has(baseCtx({ workflowState: 'TOPIC_SELECTION' }), 'confirm-topic')).toBe(true);
    expect(has(baseCtx({ workflowState: 'CREATED' }), 'confirm-topic')).toBe(false);
    expect(has(baseCtx({ workflowState: 'AGENDA_PUBLISHED' }), 'confirm-topic')).toBe(false);
  });
  it('publish-agenda: only in SLOT_ASSIGNMENT', () => {
    expect(has(baseCtx({ workflowState: 'SLOT_ASSIGNMENT' }), 'publish-agenda')).toBe(true);
    expect(has(baseCtx({ workflowState: 'SPEAKER_IDENTIFICATION' }), 'publish-agenda')).toBe(false);
  });
});

describe('data-derived cards — open vs done', () => {
  it('fill-pool: open below minSlots, done at/above minSlots', () => {
    expect(has(baseCtx({ confirmedSpeakersCount: 2, minSlots: 6 }), 'fill-pool')).toBe(true);
    expect(has(baseCtx({ confirmedSpeakersCount: 6, minSlots: 6 }), 'fill-pool')).toBe(false);
  });
  it('fill-pool: minSlots=0 (config not loaded) keeps the card open, never wrongly done', () => {
    expect(has(baseCtx({ confirmedSpeakersCount: 0, minSlots: 0 }), 'fill-pool')).toBe(true);
  });
  it('needs-slot: open when sessions need a slot, done at zero', () => {
    expect(has(baseCtx({ sessionsNeedingSlot: 2 }), 'needs-slot')).toBe(true);
    expect(has(baseCtx({ sessionsNeedingSlot: 0 }), 'needs-slot')).toBe(false);
  });
  it('review-submissions: open when speakers await review, done at zero', () => {
    expect(has(baseCtx({ awaitingReviewCount: 1 }), 'review-submissions')).toBe(true);
    expect(has(baseCtx({ awaitingReviewCount: 0 }), 'review-submissions')).toBe(false);
  });
  it('outstanding-presentations: open when materials incomplete in AGENDA_PUBLISHED, done when all in', () => {
    const open = baseCtx({
      workflowState: 'AGENDA_PUBLISHED',
      totalSessionsCount: 4,
      sessionsWithMaterialsCount: 2,
    });
    const done = baseCtx({
      workflowState: 'AGENDA_PUBLISHED',
      totalSessionsCount: 4,
      sessionsWithMaterialsCount: 4,
    });
    expect(has(open, 'outstanding-presentations')).toBe(true);
    expect(has(done, 'outstanding-presentations')).toBe(false);
  });
  it('outstanding-presentations: hidden when the event has no sessions (nothing to collect)', () => {
    const noSessions = baseCtx({
      workflowState: 'AGENDA_PUBLISHED',
      totalSessionsCount: 0,
      sessionsWithMaterialsCount: 0,
    });
    expect(has(noSessions, 'outstanding-presentations')).toBe(false);
  });
  it('outstanding-presentations: labels "n of m" collected (T8c)', () => {
    const card = getVirtualCards(
      baseCtx({
        workflowState: 'AGENDA_PUBLISHED',
        totalSessionsCount: 4,
        sessionsWithMaterialsCount: 1,
      }),
      NOW
    ).find((c) => c.id === 'outstanding-presentations');
    expect(card?.labelVars).toEqual({ collected: 1, total: 4 });
  });
  it('outstanding-presentations: due on the event day, severity from the due date (T8c)', () => {
    const ctx = baseCtx({
      workflowState: 'AGENDA_PUBLISHED',
      totalSessionsCount: 4,
      eventDate: '2026-07-20T00:00:00Z',
    });
    const card = getVirtualCards(ctx, NOW).find((c) => c.id === 'outstanding-presentations');
    expect(card?.dueDate).toBe('2026-07-20T00:00:00Z');
    expect(card?.severity).toBe('upcoming');
    expect(card?.dueDays).toBeGreaterThan(7);

    const soon = getVirtualCards({ ...ctx, eventDate: '2026-06-16T00:00:00Z' }, NOW).find(
      (c) => c.id === 'outstanding-presentations'
    );
    expect(soon?.severity).toBe('dueSoon');
  });
  it('outstanding-presentations: without an event date → upcoming, no day count', () => {
    const card = getVirtualCards(
      baseCtx({ workflowState: 'AGENDA_PUBLISHED', totalSessionsCount: 4, eventDate: null }),
      NOW
    ).find((c) => c.id === 'outstanding-presentations');
    expect(card?.severity).toBe('upcoming');
    expect(card?.dueDays).toBeUndefined();
  });

  describe('watch-registrations (T8b): only when there is something to do', () => {
    const published = (over: Partial<CockpitCardCtx>) =>
      baseCtx({ workflowState: 'AGENDA_PUBLISHED', ...over });
    it('hidden below 90 % capacity with an empty waitlist (BATbern60: 142/220)', () => {
      expect(
        has(published({ confirmedCount: 142, registrationCapacity: 220 }), 'watch-registrations')
      ).toBe(false);
    });
    it('shown at ≥ 90 % capacity', () => {
      expect(
        has(published({ confirmedCount: 198, registrationCapacity: 220 }), 'watch-registrations')
      ).toBe(true);
    });
    it('shown as soon as anyone is on the waitlist', () => {
      expect(
        has(
          published({ confirmedCount: 10, registrationCapacity: 220, waitlistCount: 1 }),
          'watch-registrations'
        )
      ).toBe(true);
    });
    it('unlimited capacity: hidden without a waitlist, shown (as ∞) with one', () => {
      expect(
        has(published({ confirmedCount: 500, registrationCapacity: null }), 'watch-registrations')
      ).toBe(false);
      const watch = getVirtualCards(
        published({ confirmedCount: 42, registrationCapacity: null, waitlistCount: 2 }),
        NOW
      ).find((c) => c.id === 'watch-registrations');
      expect(watch?.labelVars).toEqual({ confirmed: 42, capacity: '∞' });
    });
    it('not shown outside AGENDA_PUBLISHED', () => {
      expect(
        has(
          baseCtx({
            workflowState: 'SLOT_ASSIGNMENT',
            confirmedCount: 220,
            registrationCapacity: 220,
          }),
          'watch-registrations'
        )
      ).toBe(false);
    });
  });
});

describe('speakers-not-reviewed (T8d)', () => {
  const pub = (over: Partial<CockpitCardCtx>) =>
    baseCtx({ workflowState: 'AGENDA_PUBLISHED', speakersPublic: true, ...over });

  it('shown once speakers are public and someone is not yet quality-reviewed', () => {
    const card = getVirtualCards(
      pub({ awaitingContentCount: 1, awaitingReviewCount: 2 }),
      NOW
    ).find((c) => c.id === 'speakers-not-reviewed');
    expect(card).toBeDefined();
    expect(card?.severity).toBe('overdue');
    expect(card?.labelVars).toEqual({ content: 1, review: 2 });
    expect(card?.target).toEqual({ kind: 'tab', tab: 'speakers', view: 'pool' });
  });
  it('done when every speaker is quality-reviewed', () => {
    expect(
      has(pub({ awaitingContentCount: 0, awaitingReviewCount: 0 }), 'speakers-not-reviewed')
    ).toBe(false);
  });
  it('not shown while speakers are not public yet', () => {
    expect(
      has(
        baseCtx({
          workflowState: 'SLOT_ASSIGNMENT',
          speakersPublic: false,
          awaitingContentCount: 1,
        }),
        'speakers-not-reviewed'
      )
    ).toBe(false);
  });
  it('applies from the speakers phase even before the agenda is published', () => {
    expect(
      has(
        baseCtx({
          workflowState: 'SLOT_ASSIGNMENT',
          speakersPublic: true,
          awaitingContentCount: 1,
        }),
        'speakers-not-reviewed'
      )
    ).toBe(true);
  });
  it('gone after the event', () => {
    expect(
      has(
        baseCtx({
          workflowState: 'EVENT_COMPLETED',
          speakersPublic: true,
          awaitingContentCount: 1,
        }),
        'speakers-not-reviewed'
      )
    ).toBe(false);
  });
  it('replaces review-submissions once speakers are public (no double count)', () => {
    const ctx = baseCtx({
      workflowState: 'SLOT_ASSIGNMENT',
      speakersPublic: true,
      awaitingReviewCount: 1,
    });
    expect(has(ctx, 'speakers-not-reviewed')).toBe(true);
    expect(has(ctx, 'review-submissions')).toBe(false);
  });
});

describe('event-day cards (T8: only on the event day)', () => {
  // Event stored as midnight UTC (like BATbern60) = 01:00 on that day in Zurich.
  const EVENT = '2026-11-06T00:00:00Z';
  const BEFORE = new Date('2026-10-24T10:00:00Z').getTime();
  const ON_DAY = new Date('2026-11-06T09:00:00Z').getTime();
  const ctxAt = (state: string, eventDate: string | null = EVENT) =>
    baseCtx({ workflowState: state, eventDate });

  it('absent before AGENDA_PUBLISHED', () => {
    const c = ctxAt('SLOT_ASSIGNMENT');
    expect(has(c, 'start-presentation', ON_DAY)).toBe(false);
    expect(has(c, 'open-live-control', ON_DAY)).toBe(false);
  });
  it('absent in AGENDA_PUBLISHED before the event day (two weeks out)', () => {
    const c = ctxAt('AGENDA_PUBLISHED');
    expect(has(c, 'start-presentation', BEFORE)).toBe(false);
    expect(has(c, 'open-live-control', BEFORE)).toBe(false);
  });
  it('present in AGENDA_PUBLISHED on the event day', () => {
    const c = ctxAt('AGENDA_PUBLISHED');
    expect(has(c, 'start-presentation', ON_DAY)).toBe(true);
    expect(has(c, 'open-live-control', ON_DAY)).toBe(true);
  });
  it('uses the Zurich calendar day, not UTC', () => {
    const c = ctxAt('AGENDA_PUBLISHED');
    // 2026-11-05T23:30Z = 00:30 on 6 Nov in Zurich → event day
    expect(has(c, 'start-presentation', new Date('2026-11-05T23:30:00Z').getTime())).toBe(true);
    // 2026-11-05T22:30Z = 23:30 on 5 Nov in Zurich → not yet
    expect(has(c, 'start-presentation', new Date('2026-11-05T22:30:00Z').getTime())).toBe(false);
  });
  it('present throughout EVENT_LIVE, whatever the date', () => {
    const c = ctxAt('EVENT_LIVE');
    expect(has(c, 'start-presentation', BEFORE)).toBe(true);
    expect(has(c, 'open-live-control', BEFORE)).toBe(true);
  });
  it('absent in AGENDA_PUBLISHED without an event date', () => {
    expect(has(ctxAt('AGENDA_PUBLISHED', null), 'start-presentation', ON_DAY)).toBe(false);
  });
  it('absent after the event window (EVENT_COMPLETED, ARCHIVED)', () => {
    for (const s of ['EVENT_COMPLETED', 'ARCHIVED']) {
      expect(has(ctxAt(s), 'start-presentation', ON_DAY)).toBe(false);
    }
  });
  it('build route targets carrying the eventCode', () => {
    const cards = getVirtualCards(
      baseCtx({ workflowState: 'EVENT_LIVE', eventCode: 'BAT99' }),
      NOW
    );
    const start = cards.find((c) => c.id === 'start-presentation');
    expect(start?.target).toEqual({ kind: 'route', path: '/present/BAT99', newTab: true });
    expect(start?.target).toEqual(presentationTarget('BAT99'));
    const live = cards.find((c) => c.id === 'open-live-control');
    expect(live?.target).toEqual(liveControlTarget('BAT99'));
  });
  it('stamps the event date as the due date on event-day cards', () => {
    const cards = getVirtualCards(ctxAt('AGENDA_PUBLISHED'), ON_DAY);
    expect(cards.find((c) => c.id === 'start-presentation')?.dueDate).toBe(EVENT);
    expect(cards.find((c) => c.id === 'open-live-control')?.dueDate).toBe(EVENT);
  });
  it('stamps the event moderator as the assignee on every virtual card', () => {
    const cards = getVirtualCards(
      baseCtx({ workflowState: 'EVENT_LIVE', moderatorUsername: 'sandra.keller' }),
      NOW
    );
    expect(cards.length).toBeGreaterThan(0);
    expect(cards.every((c) => c.assignee === 'sandra.keller')).toBe(true);
  });
  it('leaves the assignee null when no moderator is set', () => {
    const cards = getVirtualCards(baseCtx({ moderatorUsername: null }), NOW);
    expect(cards.every((c) => c.assignee === null)).toBe(true);
  });

  describe('isEventDay / showRehearsal', () => {
    it('isEventDay compares Zurich calendar days', () => {
      expect(isEventDay(EVENT, ON_DAY)).toBe(true);
      expect(isEventDay(EVENT, BEFORE)).toBe(false);
      expect(isEventDay(null, ON_DAY)).toBe(false);
      expect(isEventDay('not-a-date', ON_DAY)).toBe(false);
    });
    it('rehearsal links show in AGENDA_PUBLISHED before the event day only', () => {
      expect(showRehearsal(ctxAt('AGENDA_PUBLISHED'), BEFORE)).toBe(true);
      expect(showRehearsal(ctxAt('AGENDA_PUBLISHED'), ON_DAY)).toBe(false);
      expect(showRehearsal(ctxAt('EVENT_LIVE'), BEFORE)).toBe(false);
      expect(showRehearsal(ctxAt('SLOT_ASSIGNMENT'), BEFORE)).toBe(false);
    });
  });
});

describe('task visibility (T3 — shared with the my-tasks widget)', () => {
  const PAST = '2026-06-01T12:00:00Z';
  const FUTURE = '2026-07-01T12:00:00Z';
  it.each([
    ['todo', FUTURE, true],
    ['in_progress', null, true],
    ['completed', PAST, false],
    ['cancelled', PAST, false],
    ['pending', FUTURE, false],
    ['pending', null, false],
    ['pending', PAST, true],
  ])('status %s due %s → visible %s', (status, dueDate, visible) => {
    expect(isTaskVisible(task({ status: status as never, dueDate }), NOW)).toBe(visible);
  });
  it('flags only an overdue pending task as "trigger not reached"', () => {
    expect(isTriggerPendingOverdue(task({ status: 'pending', dueDate: PAST }), NOW)).toBe(true);
    expect(isTriggerPendingOverdue(task({ status: 'todo', dueDate: PAST }), NOW)).toBe(false);
    expect(isTriggerPendingOverdue(task({ status: 'pending', dueDate: FUTURE }), NOW)).toBe(false);
  });
  it('sortTasksByDue: overdue first, soonest next, undated last', () => {
    const sorted = sortTasksByDue([
      task({ id: 'none', dueDate: null }),
      task({ id: 'later', dueDate: '2026-08-01T00:00:00Z' }),
      task({ id: 'over', dueDate: PAST }),
      task({ id: 'soon', dueDate: '2026-06-15T00:00:00Z' }),
    ]);
    expect(sorted.map((t) => t.id)).toEqual(['over', 'soon', 'later', 'none']);
  });
});

describe('buildAttentionList', () => {
  it('filters completed, cancelled and not-yet-triggered tasks (T3)', () => {
    const tasks = [
      task({ id: 'a', status: 'completed' }),
      task({ id: 'b', status: 'todo' }),
      task({ id: 'c', status: 'cancelled' as never }),
      task({ id: 'd', status: 'pending', dueDate: '2026-11-07T00:00:00Z' }), // Slides Are Online
    ];
    const cards = buildAttentionList(tasks, baseCtx(), NOW);
    expect(cards.map((c) => c.id).filter((id) => id.startsWith('task:'))).toEqual(['task:b']);
  });

  it('keeps an overdue pending task, marked "trigger not reached" (safety net)', () => {
    const cards = buildAttentionList(
      [task({ id: 'cat', status: 'pending', dueDate: '2026-06-01T00:00:00Z' })],
      baseCtx(),
      NOW
    );
    const card = cards.find((c) => c.id === 'task:cat');
    expect(card?.severity).toBe('overdue');
    expect(card?.noteKey).toBe('triggerNotReached');
    const active = taskToCard(task({ status: 'todo', dueDate: '2026-06-01T00:00:00Z' }), NOW);
    expect(active.noteKey).toBeUndefined();
  });

  it('pins event-day cards to the very top, then sorts by severity', () => {
    const tasks = [
      task({ id: 'soon', dueDate: '2026-06-17T12:00:00Z' }), // dueSoon
      task({ id: 'over', dueDate: '2026-06-01T12:00:00Z' }), // overdue
    ];
    const cards = buildAttentionList(tasks, baseCtx({ workflowState: 'EVENT_LIVE' }), NOW);
    expect(cards[0].pinned).toBe(true); // an event-day card leads
    const overIdx = cards.findIndex((c) => c.id === 'task:over');
    const soonIdx = cards.findIndex((c) => c.id === 'task:soon');
    expect(overIdx).toBeLessThan(soonIdx); // overdue before due-soon
  });

  it('taskToCard carries assignee + verbatim name (no translation key)', () => {
    const c = taskToCard(task({ taskName: 'Book the room', assignedOrganizerUsername: 'sk' }), NOW);
    expect(c.taskBacked).toBe(true);
    expect(c.assignee).toBe('sk');
    expect(c.labelVars?.name).toBe('Book the room');
    expect(c.labelKey).toBe('');
  });
});

describe('§12.1 exhaustiveness', () => {
  it('every workflow state resolves to a deterministic card list (no card left "no signal")', () => {
    for (const state of WORKFLOW_STATE_ORDER) {
      expect(() => getVirtualCards(baseCtx({ workflowState: state }), NOW)).not.toThrow();
    }
  });
  it('the registry enumerates the expected virtual cards', () => {
    expect(new Set(VIRTUAL_CARD_IDS)).toEqual(
      new Set([
        'add-details-pick-topic',
        'confirm-topic',
        'publish-agenda',
        'fill-pool',
        'needs-slot',
        'review-submissions',
        'outstanding-presentations',
        'speakers-not-reviewed',
        'watch-registrations',
        'start-presentation',
        'open-live-control',
      ])
    );
  });
});
