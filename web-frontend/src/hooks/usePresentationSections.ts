/**
 * usePresentationSections Hook
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Derives the ordered section array from event data.
 * Section types map 1-to-1 to slide components.
 *
 * ACs: #1, #13, #26–28
 */

import { useMemo } from 'react';
import type { PresentationEventDetail, PresentationSession } from '@/services/presentationService';
import type { User } from '@/types/user.types';
import type { components } from '@/types/generated/events-api.types';
import type { PresentationSettings } from '@/services/presentationService';

type TeaserImageItem = components['schemas']['TeaserImageItem'];

function insertTeaserImages(
  sections: PresentationSection[],
  images: TeaserImageItem[],
  position: string
): void {
  [...images]
    .filter((img) => (img.presentationPosition ?? 'AFTER_TOPIC_REVEAL') === position)
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .forEach((img) => {
      sections.push({
        type: 'teaser-image',
        key: `teaser-image-${img.id}`,
        imageUrl: img.imageUrl,
      });
    });
}

export type SectionType =
  | 'welcome'
  | 'about'
  | 'committee'
  | 'topic-reveal'
  | 'teaser-image'
  | 'agenda-preview'
  | 'session'
  | 'break'
  | 'agenda-recap'
  | 'upcoming-events'
  | 'apero';

export interface PresentationSection {
  type: SectionType;
  key: string;
  session?: PresentationSession;
  /** Present only for type === 'teaser-image' (Story 10.22) */
  imageUrl?: string;
}

// Session types that become individual speaker slides
const SPEAKER_SESSION_TYPES = new Set(['keynote', 'presentation', 'workshop', 'panel_discussion']);

// Session types that are breaks/structural (break flow, not speaker slide)
const BREAK_SESSION_TYPES = new Set(['break', 'lunch']);

export function usePresentationSections(
  event: PresentationEventDetail | null,
  sessions: PresentationSession[]
): PresentationSection[] {
  return useMemo(() => {
    if (!event) {
      return [];
    }

    const sections: PresentationSection[] = [];
    const teaserImages = event.teaserImages ?? [];

    // §1 Welcome
    sections.push({ type: 'welcome', key: 'welcome' });
    insertTeaserImages(sections, teaserImages, 'AFTER_WELCOME');

    // §2 About
    sections.push({ type: 'about', key: 'about' });

    // §3 Committee
    sections.push({ type: 'committee', key: 'committee' });
    insertTeaserImages(sections, teaserImages, 'AFTER_COMMITTEE');

    // §4 Topic Reveal
    sections.push({ type: 'topic-reveal', key: 'topic-reveal' });
    // §4.5 Teaser Images at AFTER_TOPIC_REVEAL (Story 10.22 AC5; default position)
    insertTeaserImages(sections, teaserImages, 'AFTER_TOPIC_REVEAL');

    // §5 Agenda Preview
    sections.push({ type: 'agenda-preview', key: 'agenda-preview' });

    // Sort sessions by startTime ascending (nulls last)
    // Defensive: guard against non-array sessions (paginated API response)
    const sessionsArray = Array.isArray(sessions) ? sessions : [];
    const sortedSessions = [...sessionsArray].sort((a, b) => {
      if (!a.startTime && !b.startTime) {
        return 0;
      }
      if (!a.startTime) {
        return 1;
      }
      if (!b.startTime) {
        return -1;
      }
      return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
    });

    // Find the first break/lunch session
    const breakSession = sortedSessions.find(
      (s) => s.sessionType && BREAK_SESSION_TYPES.has(s.sessionType)
    );

    // Speaker sessions only — exclude moderation/networking/break/lunch
    const speakerSessions = sortedSessions.filter(
      (s) => s.sessionType && SPEAKER_SESSION_TYPES.has(s.sessionType)
    );

    if (breakSession?.startTime) {
      // Pre-break speaker sections
      const preBreak = speakerSessions.filter(
        (s) => s.startTime && new Date(s.startTime) < new Date(breakSession.startTime!)
      );
      // Post-break speaker sections — sessions starting AT OR AFTER break end (>=, not >).
      // Using strictly-greater-than would exclude sessions that start exactly when the
      // break ends, which is the normal case in BATbern schedules (e.g. break 19:30–20:00,
      // next talk at 20:00). Fix: use >= so boundary-equal sessions are included.
      const breakBoundary = breakSession.endTime ?? breakSession.startTime!;
      const postBreak = speakerSessions.filter(
        (s) => s.startTime && new Date(s.startTime) >= new Date(breakBoundary)
      );

      preBreak.forEach((s) => {
        sections.push({ type: 'session', key: `session-${s.sessionSlug}`, session: s });
      });

      // §N Break (sequential nav)
      sections.push({ type: 'break', key: 'break', session: breakSession });

      // §N+1 Agenda Recap
      sections.push({ type: 'agenda-recap', key: 'agenda-recap' });

      postBreak.forEach((s) => {
        sections.push({ type: 'session', key: `session-${s.sessionSlug}`, session: s });
      });
    } else {
      // No break — all speaker sessions in sequence
      speakerSessions.forEach((s) => {
        sections.push({ type: 'session', key: `session-${s.sessionSlug}`, session: s });
      });
    }

    // Upcoming Events
    sections.push({ type: 'upcoming-events', key: 'upcoming-events' });
    insertTeaserImages(sections, teaserImages, 'AFTER_UPCOMING_EVENTS');

    // Apéro
    sections.push({ type: 'apero', key: 'apero' });

    return sections;
  }, [event, sessions]);
}

/**
 * Returns the slugs of all pre-break speaker sessions (for AgendaRecap greying).
 */
export function getPreBreakSessionSlugs(sessions: PresentationSession[]): string[] {
  const safeArr = Array.isArray(sessions) ? sessions : [];
  const sorted = [...safeArr].sort((a, b) => {
    if (!a.startTime) {
      return 1;
    }
    if (!b.startTime) {
      return -1;
    }
    return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
  });

  const breakSession = sorted.find((s) => s.sessionType && BREAK_SESSION_TYPES.has(s.sessionType));

  if (!breakSession?.startTime) {
    return [];
  }

  return sorted
    .filter(
      (s) =>
        SPEAKER_SESSION_TYPES.has(s.sessionType ?? '') &&
        s.startTime &&
        new Date(s.startTime) < new Date(breakSession.startTime!)
    )
    .map((s) => s.sessionSlug);
}

/**
 * Returns the first post-break speaker session (for break resume time).
 */
export function getFirstPostBreakSession(
  sessions: PresentationSession[]
): PresentationSession | null {
  const safeArr = Array.isArray(sessions) ? sessions : [];
  const sorted = [...safeArr].sort((a, b) => {
    if (!a.startTime) {
      return 1;
    }
    if (!b.startTime) {
      return -1;
    }
    return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
  });

  const breakSession = sorted.find((s) => s.sessionType && BREAK_SESSION_TYPES.has(s.sessionType));

  if (!breakSession?.endTime) {
    return null;
  }

  // Use >= so a session starting exactly at break end time is included (same fix as postBreak filter above).
  return (
    sorted.find(
      (s) =>
        SPEAKER_SESSION_TYPES.has(s.sessionType ?? '') &&
        s.startTime &&
        new Date(s.startTime) >= new Date(breakSession.endTime!)
    ) ?? null
  );
}

export type { PresentationEventDetail, PresentationSession, PresentationSettings };
export type { User };
export type { components };
