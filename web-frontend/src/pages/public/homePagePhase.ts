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
  /** True when registration CTA is shown (PRE_EVENT only); false for COMING_SOON, POST_EVENT, and ARCHIVE */
  registrationEnabled: boolean;
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
  /** Show session materials download links — true in POST_EVENT and ARCHIVE */
  showSessionMaterials: boolean;
}

/**
 * Whether the per-session Q&A thread should be mounted on the public homepage (Story 7.5).
 *
 * Q&A is decoupled from `showSessionMaterials`: the 2026-06-11 rework made Q&A open either
 * PRE-event (`SPEAKERS_PUBLISHED` trigger) or post-event (`EVENT_COMPLETED`), and the
 * 2026-06-12 manual "Open Q&A" lets an organizer open windows INDEPENDENT of the configured
 * trigger. So `qnaOpenTrigger` is NOT a reliable signal for "are there open windows" — we mount
 * the thread wherever session cards render in a Q&A-eligible phase and let `SessionQnaThread`
 * self-gate on window existence (it renders nothing on a 404). We only skip when Q&A is
 * explicitly disabled for the event (`qnaEnabled === false`).
 *
 * Note: SPEAKERS / POST_EVENT / ARCHIVE render SessionCards while the AGENDA sub-phase renders
 * the EventProgram timeline instead — both mount SessionQnaThread, so this returns true for all
 * of them (the timeline cards carry the Q&A thread too).
 */
export function showSessionQna(
  phase: HomePagePhase,
  event: Pick<EventDetail, 'qnaEnabled'>
): boolean {
  if (event.qnaEnabled === false) {
    return false;
  }
  switch (phase.kind) {
    case 'POST_EVENT':
    case 'ARCHIVE':
      return true;
    case 'PRE_EVENT':
      // SPEAKERS renders SessionCards, AGENDA renders the EventProgram timeline — both mount
      // the Q&A thread, so pre-event Q&A stays visible across both published phases.
      return phase.sub === 'SPEAKERS' || phase.sub === 'AGENDA';
    default:
      return false;
  }
}

/**
 * Whether public registration (and the waitlist) is closed because the deadline has passed
 * (feedback #1). Mirrors the backend guard (`RegistrationService.assertRegistrationOpen`): closes
 * on `registrationDeadline`, falling back to the event start `date` when no explicit deadline is
 * set so a missing deadline never leaves registration open forever. The backend is authoritative
 * (409 `REGISTRATION_CLOSED`); this only hides the register CTA on the public page.
 *
 * Combine with {@link getSectionVisibility}: `registrationEnabled && !isRegistrationClosed(event)`.
 */
export function isRegistrationClosed(
  event: Pick<EventDetail, 'registrationDeadline' | 'date'>,
  now: number = Date.now()
): boolean {
  const deadline = event.registrationDeadline ?? event.date;
  if (!deadline) {
    return false;
  }
  const parsed = Date.parse(deadline);
  return Number.isFinite(parsed) && now > parsed;
}

export function getSectionVisibility(phase: HomePagePhase): SectionVisibility {
  switch (phase.kind) {
    case 'COMING_SOON':
      return {
        registrationEnabled: false,
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
        showSessionMaterials: false,
      };

    case 'PRE_EVENT': {
      const showSpeakers = phase.sub === 'SPEAKERS' || phase.sub === 'AGENDA';
      return {
        registrationEnabled: true,
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
        showSessionMaterials: false,
      };
    }

    case 'POST_EVENT':
      return {
        registrationEnabled: false,
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
        showSessionMaterials: true,
      };

    case 'ARCHIVE':
      return {
        registrationEnabled: false,
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
        showSessionMaterials: true,
      };
  }
}
