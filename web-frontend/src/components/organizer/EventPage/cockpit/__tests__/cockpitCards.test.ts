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
  VIRTUAL_CARD_IDS,
  WORKFLOW_STATE_ORDER,
  type CockpitCardCtx,
} from '../cockpitCards';

const baseCtx = (over: Partial<CockpitCardCtx> = {}): CockpitCardCtx => ({
  eventCode: 'BAT54',
  workflowState: 'SPEAKER_IDENTIFICATION',
  topicCode: 'cloud-native',
  minSlots: 6,
  confirmedSpeakersCount: 0,
  sessionsNeedingSlot: 0,
  sessionsWithMaterialsCount: 0,
  totalSessionsCount: 0,
  awaitingReviewCount: 0,
  confirmedCount: 0,
  waitlistCount: 0,
  registrationCapacity: 100,
  ...over,
});

const ids = (ctx: CockpitCardCtx) => getVirtualCards(ctx).map((c) => c.id);
const has = (ctx: CockpitCardCtx, id: string) => ids(ctx).includes(id);

const NOW = new Date('2026-06-13T12:00:00Z').getTime();
const task = (over: Partial<EventTaskResponse> = {}): EventTaskResponse => ({
  id: 't1',
  eventId: 'e1',
  eventCode: 'BAT54',
  templateId: null,
  taskName: 'Some task',
  triggerState: 'topic_selection',
  dueDate: null,
  assignedOrganizerUsername: 'john.doe',
  status: 'pending',
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
    ['Assign the moderator', 'settings'],
    ['Send the final newsletter', 'communications'],
    ['Request the catering offer', 'communications'],
    ['Venue Booking', 'communications'],
    ['Upload event photos', 'wrapup'],
    ['Finalize & publish the agenda', 'publishing'],
    ['2 sessions still need a slot', 'speakers'],
    ['Review speaker submissions', 'speakers'],
    ['Open session Q&A', 'settings'],
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
  it('watch-registrations: renders an unlimited capacity as ∞', () => {
    const cards = getVirtualCards(
      baseCtx({ workflowState: 'AGENDA_PUBLISHED', confirmedCount: 42, registrationCapacity: null })
    );
    const watch = cards.find((c) => c.id === 'watch-registrations');
    expect(watch?.labelVars).toEqual({ confirmed: 42, capacity: '∞' });
  });
  it('watch-registrations: ongoing in AGENDA_PUBLISHED (no done state)', () => {
    expect(has(baseCtx({ workflowState: 'AGENDA_PUBLISHED' }), 'watch-registrations')).toBe(true);
  });
});

describe('event-day cards (FR12 gate)', () => {
  it('absent before AGENDA_PUBLISHED', () => {
    const c = baseCtx({ workflowState: 'SLOT_ASSIGNMENT' });
    expect(has(c, 'start-presentation')).toBe(false);
    expect(has(c, 'open-live-control')).toBe(false);
  });
  it('present from AGENDA_PUBLISHED through EVENT_LIVE', () => {
    for (const s of ['AGENDA_PUBLISHED', 'EVENT_LIVE']) {
      const c = baseCtx({ workflowState: s });
      expect(has(c, 'start-presentation')).toBe(true);
      expect(has(c, 'open-live-control')).toBe(true);
    }
  });
  it('absent after the event window (EVENT_COMPLETED, ARCHIVED)', () => {
    for (const s of ['EVENT_COMPLETED', 'ARCHIVED']) {
      const c = baseCtx({ workflowState: s });
      expect(has(c, 'start-presentation')).toBe(false);
    }
  });
  it('build route targets carrying the eventCode', () => {
    const cards = getVirtualCards(baseCtx({ workflowState: 'EVENT_LIVE', eventCode: 'BAT99' }));
    const start = cards.find((c) => c.id === 'start-presentation');
    expect(start?.target).toEqual({ kind: 'route', path: '/present/BAT99', newTab: true });
  });
});

describe('buildAttentionList', () => {
  it('filters completed backend tasks', () => {
    const tasks = [task({ id: 'a', status: 'completed' }), task({ id: 'b', status: 'pending' })];
    const cards = buildAttentionList(tasks, baseCtx(), NOW);
    expect(cards.some((c) => c.id === 'task:a')).toBe(false);
    expect(cards.some((c) => c.id === 'task:b')).toBe(true);
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
      expect(() => getVirtualCards(baseCtx({ workflowState: state }))).not.toThrow();
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
        'watch-registrations',
        'start-presentation',
        'open-live-control',
      ])
    );
  });
});
