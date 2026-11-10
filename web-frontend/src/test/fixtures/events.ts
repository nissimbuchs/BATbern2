/**
 * Production-shaped events (Story 17.0d): past and future events, dated relative to the clock.
 *
 * Never a fixed calendar date: a fixture date that is "in the future" today becomes a past one
 * later and turns a test red for no code change (#1069, #1241: `2026-09-15` became a time bomb).
 * Every builder reads `Date.now()` when it is called, so a test with fake timers gets dates
 * relative to its fake clock. The MSW harness answers `listEvents` from `events()` when a test
 * sets no override (`src/test/msw/fixtureDefaults.ts`).
 */
import type { components } from '../../types/generated/events-core-api.types';

export type Event = components['schemas']['Event'];
export type EventDetail = components['schemas']['EventDetail'];

const DAY_MS = 24 * 60 * 60 * 1000;

/** An ISO instant `days` from now (negative: in the past), at 16:00 UTC (18:00 in Bern). */
export function daysFromNow(days: number): string {
  const d = new Date(Date.now() + days * DAY_MS);
  d.setUTCHours(16, 0, 0, 0);
  return d.toISOString();
}

/** An event; defaults to an open evening event 40 days ahead. Override what matters. */
export function anEvent(overrides: Partial<Event> = {}): Event {
  const date = overrides.date ?? daysFromNow(40);
  return {
    eventCode: 'BATbern61',
    eventNumber: 61,
    title: 'Künstliche Intelligenz & Architektur',
    description: 'Wie KI die Softwarearchitektur verändert, mit Beiträgen aus Zürich und Genève.',
    date,
    registrationDeadline: new Date(Date.parse(date) - 7 * DAY_MS).toISOString(),
    venueName: 'Kursaal Bern',
    venueAddress: 'Kornhausstrasse 3, 3013 Bern',
    venueCapacity: 250,
    registrationCapacity: 250,
    currentAttendeeCount: 0,
    eventType: 'EVENING',
    workflowState: 'AGENDA_PUBLISHED',
    typicalStartTime: '18:00',
    typicalEndTime: '21:00',
    qnaEnabled: true,
    qnaOpenTrigger: 'EVENT_COMPLETED',
    qnaWindowDays: 14,
    createdAt: daysFromNow(-120),
    updatedAt: daysFromNow(-120),
    ...overrides,
  };
}

/** The single-event view of `anEvent` (sessions, if any, carry the detail shape). */
export function anEventDetail(overrides: Partial<EventDetail> = {}): EventDetail {
  const event: Omit<Event, 'sessions'> = anEvent();
  return { ...event, ...overrides };
}

/** An event that took place `daysAgo` days ago (default: about a year, archived). */
export function pastEvent(daysAgo = 365, overrides: Partial<Event> = {}): Event {
  return anEvent({
    eventCode: 'BATbern58',
    eventNumber: 58,
    title: 'Plattformen, Teams & Conway’s Law',
    date: daysFromNow(-daysAgo),
    workflowState: 'ARCHIVED',
    currentAttendeeCount: 212,
    ...overrides,
  });
}

/** An event `daysAhead` days in the future (default 40, registration open). */
export function futureEvent(daysAhead = 40, overrides: Partial<Event> = {}): Event {
  return anEvent({ date: daysFromNow(daysAhead), ...overrides });
}

/**
 * The default event list: past (archived and just completed) and future (open and just created),
 * newest first, as `GET /events` orders it.
 */
export function events(): Event[] {
  return [
    futureEvent(200, {
      eventCode: 'BATbern62',
      eventNumber: 62,
      title: 'Datensouveränität: Cloud in der Schweiz',
      workflowState: 'CREATED',
      eventType: 'FULL_DAY',
      typicalStartTime: '09:00',
      typicalEndTime: '17:00',
    }),
    futureEvent(40),
    pastEvent(30, {
      eventCode: 'BATbern60',
      eventNumber: 60,
      title: 'Résilience & Observability',
      workflowState: 'EVENT_COMPLETED',
      currentAttendeeCount: 247,
    }),
    pastEvent(),
  ];
}
