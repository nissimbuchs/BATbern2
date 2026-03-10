/**
 * homePagePhase.ts
 *
 * Single source of truth for the public homepage display phases.
 * All section visibility on the homepage is derived from these pure functions —
 * no conditional rendering logic lives in the JSX itself.
 *
 * ## Display Phases
 *
 * | Phase        | Trigger                                                   |
 * |--------------|-----------------------------------------------------------|
 * | COMING_SOON  | currentPublishedPhase is null (event not yet published)   |
 * | PRE_EVENT    | currentPublishedPhase is TOPIC / SPEAKERS / AGENDA        |
 * | POST_EVENT   | workflowState === EVENT_COMPLETED (≤14 days after event)  |
 * | ARCHIVE      | URL path starts with /archive                             |
 */

import type { EventDetail } from '@/types/event.types';

type PublishedPhase = 'TOPIC' | 'SPEAKERS' | 'AGENDA';

/** Minimal shape needed from event photos — we only care about presence */
interface HasId {
  id: string | number;
}

// ---------------------------------------------------------------------------
// Phase type
// ---------------------------------------------------------------------------

export type HomePagePhase =
  | { kind: 'COMING_SOON' }
  | { kind: 'PRE_EVENT'; sub: PublishedPhase }
  | { kind: 'POST_EVENT'; hasEventPhotos: boolean }
  | { kind: 'ARCHIVE'; hasEventPhotos: boolean };

// ---------------------------------------------------------------------------
// Phase computation
// ---------------------------------------------------------------------------

export function getHomepagePhase(
  event: EventDetail,
  isArchiveMode: boolean,
  eventPhotos: HasId[] | undefined
): HomePagePhase {
  if (isArchiveMode) {
    return { kind: 'ARCHIVE', hasEventPhotos: !!(eventPhotos && eventPhotos.length > 0) };
  }

  if (event.workflowState === 'EVENT_COMPLETED') {
    return { kind: 'POST_EVENT', hasEventPhotos: !!(eventPhotos && eventPhotos.length > 0) };
  }

  const phase =
    ('currentPublishedPhase' in event
      ? (event.currentPublishedPhase as PublishedPhase | null | undefined)
      : null) ?? null;

  if (!phase) {
    return { kind: 'COMING_SOON' };
  }

  return { kind: 'PRE_EVENT', sub: phase };
}

// ---------------------------------------------------------------------------
// Section visibility
// ---------------------------------------------------------------------------

export interface SectionVisibility {
  /** Shown when publication phase is set (all phases except COMING_SOON) */
  eventDescription: boolean;
  /** Always shown */
  eventLogistics: boolean;
  /** Hidden only in POST_EVENT and ARCHIVE */
  venueMap: boolean;
  /** Shown from SPEAKERS phase onward, and in POST_EVENT / ARCHIVE */
  speakerGrid: boolean;
  /** Shown in SPEAKERS phase, POST_EVENT, and ARCHIVE; replaced by EventProgram in AGENDA */
  sessionCards: boolean;
  /** Shown only in AGENDA phase */
  eventProgram: boolean;
  /** Shown in POST_EVENT and ARCHIVE when event has its own photos */
  eventPhotosMarquee: boolean;
  /** Always shown */
  testimonials: boolean;
  /** Skip the recent-photos row when event-specific photos are already displayed above */
  testimonialsSkipPhotoRow: boolean;
  /** Always shown */
  upcomingEvents: boolean;
  /** Back-to-archive link — only in ARCHIVE mode */
  backLink: boolean;
}

export function getSectionVisibility(phase: HomePagePhase): SectionVisibility {
  switch (phase.kind) {
    case 'COMING_SOON':
      return {
        eventDescription: false,
        eventLogistics: true,
        venueMap: true,
        speakerGrid: false,
        sessionCards: false,
        eventProgram: false,
        eventPhotosMarquee: false,
        testimonials: true,
        testimonialsSkipPhotoRow: false,
        upcomingEvents: true,
        backLink: false,
      };

    case 'PRE_EVENT': {
      const showSpeakers = phase.sub === 'SPEAKERS' || phase.sub === 'AGENDA';
      return {
        eventDescription: true,
        eventLogistics: true,
        venueMap: true,
        speakerGrid: showSpeakers,
        sessionCards: phase.sub === 'SPEAKERS',
        eventProgram: phase.sub === 'AGENDA',
        eventPhotosMarquee: false,
        testimonials: true,
        testimonialsSkipPhotoRow: false,
        upcomingEvents: true,
        backLink: false,
      };
    }

    case 'POST_EVENT':
      return {
        eventDescription: true,
        eventLogistics: true,
        venueMap: false,
        speakerGrid: true,
        sessionCards: true,
        eventProgram: false,
        eventPhotosMarquee: phase.hasEventPhotos,
        testimonials: true,
        testimonialsSkipPhotoRow: phase.hasEventPhotos,
        upcomingEvents: true,
        backLink: false,
      };

    case 'ARCHIVE':
      return {
        eventDescription: true,
        eventLogistics: true,
        venueMap: false,
        speakerGrid: true,
        sessionCards: true,
        eventProgram: false,
        eventPhotosMarquee: phase.hasEventPhotos,
        testimonials: true,
        testimonialsSkipPhotoRow: phase.hasEventPhotos,
        upcomingEvents: true,
        backLink: true,
      };
  }
}
