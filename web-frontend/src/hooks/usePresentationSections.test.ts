/**
 * usePresentationSections Hook Tests
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #1, #13, #26–28
 */

import { describe, test, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import {
  usePresentationSections,
  getPreBreakSessionSlugs,
  getFirstPostBreakSession,
} from './usePresentationSections';
import type { PresentationEventDetail, PresentationSession } from '@/services/presentationService';

const MOCK_EVENT = {
  eventCode: 'BATbern57',
  eventNumber: 57,
  title: 'Test Event',
  date: '2026-06-01T18:00:00Z',
  registrationDeadline: '2026-05-31T00:00:00Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3',
  venueCapacity: 500,
  organizerUsername: 'test.organizer',
  currentAttendeeCount: 0,
  topicCode: 'zero-trust',
} as PresentationEventDetail;

function makeSession(
  slug: string,
  type: string,
  startTime: string,
  endTime?: string
): PresentationSession {
  return {
    sessionSlug: slug,
    sessionType: type,
    title: slug,
    startTime,
    endTime: endTime ?? startTime,
  } as unknown as PresentationSession;
}

const PRE_BREAK_1 = makeSession('session-1', 'presentation', '2026-06-01T18:00:00Z');
const PRE_BREAK_2 = makeSession('session-2', 'keynote', '2026-06-01T18:45:00Z');
const BREAK = makeSession('break', 'break', '2026-06-01T19:30:00Z', '2026-06-01T19:59:00Z');
const POST_BREAK_1 = makeSession('session-3', 'presentation', '2026-06-01T20:00:00Z');
const POST_BREAK_2 = makeSession('session-4', 'workshop', '2026-06-01T20:45:00Z');
const MODERATION = makeSession('moderation', 'moderation', '2026-06-01T17:55:00Z');
const NETWORKING = makeSession('networking', 'networking', '2026-06-01T21:30:00Z');

describe('usePresentationSections', () => {
  test('returns empty array when event is null', () => {
    const { result } = renderHook(() => usePresentationSections(null, []));
    expect(result.current).toHaveLength(0);
  });

  test('builds fixed sections + speaker sections without break', () => {
    const sessions = [PRE_BREAK_1, PRE_BREAK_2];
    const { result } = renderHook(() => usePresentationSections(MOCK_EVENT, sessions));
    const types = result.current.map((s) => s.type);

    expect(types).toEqual([
      'welcome',
      'about',
      'committee',
      'topic-reveal',
      'agenda-preview',
      'session',
      'session',
      'upcoming-events',
      'apero',
    ]);
  });

  test('inserts break + agenda-recap between pre/post-break sessions', () => {
    const sessions = [PRE_BREAK_1, PRE_BREAK_2, BREAK, POST_BREAK_1, POST_BREAK_2];
    const { result } = renderHook(() => usePresentationSections(MOCK_EVENT, sessions));
    const types = result.current.map((s) => s.type);

    expect(types).toEqual([
      'welcome',
      'about',
      'committee',
      'topic-reveal',
      'agenda-preview',
      'session', // pre-break 1
      'session', // pre-break 2
      'break',
      'agenda-recap',
      'session', // post-break 1
      'session', // post-break 2
      'upcoming-events',
      'apero',
    ]);
  });

  test('includes post-break session starting exactly at break end time (>= boundary)', () => {
    // Real BATbern schedule: break 19:30–20:00, next talk at 20:00.
    // With strict > the 20:00 session was silently excluded, causing navigation to skip it.
    const breakExact = makeSession(
      'break',
      'break',
      '2026-06-01T19:30:00Z',
      '2026-06-01T20:00:00Z'
    );
    const postBreakExact = makeSession('session-exact', 'presentation', '2026-06-01T20:00:00Z');
    const sessions = [PRE_BREAK_1, breakExact, postBreakExact];
    const { result } = renderHook(() => usePresentationSections(MOCK_EVENT, sessions));
    const types = result.current.map((s) => s.type);

    expect(types).toEqual([
      'welcome',
      'about',
      'committee',
      'topic-reveal',
      'agenda-preview',
      'session', // PRE_BREAK_1
      'break',
      'agenda-recap',
      'session', // postBreakExact — must NOT be skipped
      'upcoming-events',
      'apero',
    ]);
    const sessionKeys = result.current.filter((s) => s.type === 'session').map((s) => s.key);
    expect(sessionKeys).toContain('session-session-exact');
  });

  test('excludes moderation and networking sessions (AC #13)', () => {
    const sessions = [PRE_BREAK_1, MODERATION, NETWORKING];
    const { result } = renderHook(() => usePresentationSections(MOCK_EVENT, sessions));
    const keys = result.current.map((s) => s.key);

    expect(keys).not.toContain('session-moderation');
    expect(keys).not.toContain('session-networking');
    expect(keys).toContain('session-session-1');
  });

  test('session sections carry correct session data', () => {
    const { result } = renderHook(() => usePresentationSections(MOCK_EVENT, [PRE_BREAK_1]));
    const sessionSection = result.current.find((s) => s.type === 'session');
    expect(sessionSection?.session?.sessionSlug).toBe('session-1');
  });

  test('inserts one teaser-image section per image after topic-reveal, before agenda-preview (Story 10.22 AC5)', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-1',
          imageUrl: 'https://cdn.batbern.ch/t1.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
        },
        {
          id: 'img-2',
          imageUrl: 'https://cdn.batbern.ch/t2.jpg',
          displayOrder: 1,
          presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);
    const teaserIdx = types.indexOf('topic-reveal');
    const agendaIdx = types.indexOf('agenda-preview');

    expect(teaserIdx).toBeGreaterThan(-1);
    expect(agendaIdx).toBeGreaterThan(teaserIdx);
    // Two teaser-image sections between topic-reveal and agenda-preview
    const between = types.slice(teaserIdx + 1, agendaIdx);
    expect(between).toEqual(['teaser-image', 'teaser-image']);

    // Sections carry imageUrl
    const teaserSections = result.current.filter((s) => s.type === 'teaser-image');
    expect(teaserSections[0].imageUrl).toBe('https://cdn.batbern.ch/t1.jpg');
    expect(teaserSections[1].imageUrl).toBe('https://cdn.batbern.ch/t2.jpg');
  });

  test('teaser-image sections are sorted by displayOrder ascending', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-b',
          imageUrl: 'https://cdn.batbern.ch/b.jpg',
          displayOrder: 1,
          presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
        },
        {
          id: 'img-a',
          imageUrl: 'https://cdn.batbern.ch/a.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const teaserSections = result.current.filter((s) => s.type === 'teaser-image');
    expect(teaserSections[0].imageUrl).toBe('https://cdn.batbern.ch/a.jpg');
    expect(teaserSections[1].imageUrl).toBe('https://cdn.batbern.ch/b.jpg');
  });

  test('places teaser image at AFTER_WELCOME between welcome and about', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-w',
          imageUrl: 'https://cdn.batbern.ch/welcome.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_WELCOME' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);
    const welcomeIdx = types.indexOf('welcome');
    const aboutIdx = types.indexOf('about');

    expect(types[welcomeIdx + 1]).toBe('teaser-image');
    expect(aboutIdx).toBe(welcomeIdx + 2);
  });

  test('places teaser image at AFTER_COMMITTEE between committee and topic-reveal', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-c',
          imageUrl: 'https://cdn.batbern.ch/committee.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_COMMITTEE' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);
    const committeeIdx = types.indexOf('committee');
    const topicIdx = types.indexOf('topic-reveal');

    expect(types[committeeIdx + 1]).toBe('teaser-image');
    expect(topicIdx).toBe(committeeIdx + 2);
  });

  test('places teaser image at AFTER_UPCOMING_EVENTS between upcoming-events and apero', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-u',
          imageUrl: 'https://cdn.batbern.ch/upcoming.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_UPCOMING_EVENTS' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);
    const upcomingIdx = types.indexOf('upcoming-events');
    const aperoIdx = types.indexOf('apero');

    expect(types[upcomingIdx + 1]).toBe('teaser-image');
    expect(aperoIdx).toBe(upcomingIdx + 2);
  });

  test('handles mixed positions — each image at correct slot', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-w',
          imageUrl: 'https://cdn.batbern.ch/welcome.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_WELCOME' as const,
        },
        {
          id: 'img-t',
          imageUrl: 'https://cdn.batbern.ch/topic.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_TOPIC_REVEAL' as const,
        },
        {
          id: 'img-u',
          imageUrl: 'https://cdn.batbern.ch/upcoming.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_UPCOMING_EVENTS' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);

    // welcome → teaser → about → committee → topic-reveal → teaser → agenda-preview → upcoming-events → teaser → apero
    expect(types[0]).toBe('welcome');
    expect(types[1]).toBe('teaser-image'); // AFTER_WELCOME
    expect(types[2]).toBe('about');
    const topicIdx = types.indexOf('topic-reveal');
    expect(types[topicIdx + 1]).toBe('teaser-image'); // AFTER_TOPIC_REVEAL
    const upcomingIdx = types.indexOf('upcoming-events');
    expect(types[upcomingIdx + 1]).toBe('teaser-image'); // AFTER_UPCOMING_EVENTS
    expect(types[types.length - 1]).toBe('apero');
  });

  test('respects displayOrder within same position', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        {
          id: 'img-2',
          imageUrl: 'https://cdn.batbern.ch/second.jpg',
          displayOrder: 1,
          presentationPosition: 'AFTER_WELCOME' as const,
        },
        {
          id: 'img-1',
          imageUrl: 'https://cdn.batbern.ch/first.jpg',
          displayOrder: 0,
          presentationPosition: 'AFTER_WELCOME' as const,
        },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const teaserSections = result.current.filter((s) => s.type === 'teaser-image');
    expect(teaserSections[0].imageUrl).toBe('https://cdn.batbern.ch/first.jpg');
    expect(teaserSections[1].imageUrl).toBe('https://cdn.batbern.ch/second.jpg');
  });

  test('defaults missing presentationPosition to AFTER_TOPIC_REVEAL', () => {
    const eventWithTeaser = {
      ...MOCK_EVENT,
      teaserImages: [
        // presentationPosition intentionally omitted to test fallback
        { id: 'img-x', imageUrl: 'https://cdn.batbern.ch/x.jpg', displayOrder: 0 },
      ],
    } as PresentationEventDetail;

    const { result } = renderHook(() => usePresentationSections(eventWithTeaser, []));
    const types = result.current.map((s) => s.type);
    const topicIdx = types.indexOf('topic-reveal');
    const agendaIdx = types.indexOf('agenda-preview');

    // Should appear between topic-reveal and agenda-preview (default)
    expect(types[topicIdx + 1]).toBe('teaser-image');
    expect(agendaIdx).toBe(topicIdx + 2);
  });
});

describe('getPreBreakSessionSlugs', () => {
  test('returns empty array when no break session', () => {
    expect(getPreBreakSessionSlugs([PRE_BREAK_1, PRE_BREAK_2])).toEqual([]);
  });

  test('returns slugs of pre-break speaker sessions', () => {
    const sessions = [PRE_BREAK_1, PRE_BREAK_2, BREAK, POST_BREAK_1];
    const slugs = getPreBreakSessionSlugs(sessions);
    expect(slugs).toContain('session-1');
    expect(slugs).toContain('session-2');
    expect(slugs).not.toContain('session-3');
  });

  test('does not include moderation/networking in pre-break slugs', () => {
    const sessions = [PRE_BREAK_1, MODERATION, BREAK];
    const slugs = getPreBreakSessionSlugs(sessions);
    expect(slugs).not.toContain('moderation');
  });
});

describe('getFirstPostBreakSession', () => {
  test('returns null when no break session', () => {
    expect(getFirstPostBreakSession([PRE_BREAK_1])).toBeNull();
  });

  test('returns first speaker session after break end time', () => {
    const sessions = [PRE_BREAK_1, BREAK, POST_BREAK_1, POST_BREAK_2];
    const first = getFirstPostBreakSession(sessions);
    expect(first?.sessionSlug).toBe('session-3');
  });

  test('returns session that starts exactly at break end time (>= boundary)', () => {
    const breakExact = makeSession(
      'break',
      'break',
      '2026-06-01T19:30:00Z',
      '2026-06-01T20:00:00Z'
    );
    const postBreakExact = makeSession('session-exact', 'presentation', '2026-06-01T20:00:00Z');
    const sessions = [PRE_BREAK_1, breakExact, postBreakExact];
    const first = getFirstPostBreakSession(sessions);
    expect(first?.sessionSlug).toBe('session-exact');
  });

  test('returns null when break has no end time', () => {
    // endTime explicitly undefined — makeSession sets endTime=startTime by default,
    // so we craft a session with explicit endTime undefined via casting
    const breakNoEnd = {
      sessionSlug: 'break',
      sessionType: 'break',
      title: 'break',
      startTime: '2026-06-01T19:30:00Z',
      endTime: undefined,
    } as unknown as PresentationSession;
    const sessions = [PRE_BREAK_1, breakNoEnd, POST_BREAK_1];
    expect(getFirstPostBreakSession(sessions)).toBeNull();
  });
});
