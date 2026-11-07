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
 *
 * This file decides LAYOUT only. Which sessions and speakers exist in the payload, and whether
 * talk titles are included, is decided server-side by the Public Events read model
 * (GET /public/events/*, PublicSessionVisibilityPolicy): SPEAKERS phase brings accepted-or-later
 * speakers without titles and no sessions; AGENDA brings slotted, quality-reviewed sessions.
 * | POST_EVENT   | workflowState === EVENT_COMPLETED (≤14 days after event)  |
 * | ARCHIVE      | URL path starts with /archive                             |
 */

import type { EventDetail } from '@/types/event.types';
import type { RegistrationStatus } from '@/types/eventParticipant.types';

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
  /** Shown in POST_EVENT and ARCHIVE; replaced by EventProgram in AGENDA; hidden in SPEAKERS */
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
      // Only AGENDA renders session cards (the EventProgram timeline). The SPEAKERS phase shows
      // the lineup without sessions (2026-10-07), so there is nothing to mount a thread on.
      return phase.sub === 'AGENDA';
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
        // SPEAKERS publishes the lineup only; the server sends no sessions then (2026-10-07).
        sessionCards: false,
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

// ---------------------------------------------------------------------------
// Registration view (#1238) — the one state matrix for the public page
// ---------------------------------------------------------------------------

/**
 * Where the event stands for registration (#1238):
 *
 * | S0 | not published (no `currentPublishedPhase`)                                       |
 * | S1 | published, before the deadline, places free or no capacity                       |
 * | S2 | like S1 but full (`spotsRemaining <= 0`): new registrations join the waitlist     |
 * | S3 | deadline passed (no deadline → the event date counts), before the event day      |
 * | S4 | event day: `EVENT_LIVE`, or the event's calendar day in Bern has begun            |
 * | S5 | after the event: `EVENT_COMPLETED` / `ARCHIVED`, or the archive view             |
 */
export type RegistrationSituation = 'S0' | 'S1' | 'S2' | 'S3' | 'S4' | 'S5';

export type MyRegistrationStatus = RegistrationStatus;

type RegistrationViewEvent = Pick<
  EventDetail,
  | 'date'
  | 'registrationDeadline'
  | 'workflowState'
  | 'registrationCapacity'
  | 'spotsRemaining'
  | 'confirmedCount'
  | 'waitlistCount'
> & { currentPublishedPhase?: PublishedPhase | null };

export interface PlaceNumbers {
  /** registrationCapacity; null without a capacity (then only `taken` is shown) */
  total: number | null;
  /** REGISTERED + CONFIRMED (E1 "vergeben") */
  taken: number;
  /** total − taken, never negative; null without a capacity */
  free: number | null;
  waitlist: number;
  /** Headline under the numbers: full (S2) or closed (S3/S4); none otherwise */
  note: 'FULL' | 'CLOSED' | null;
}

export interface RegistrationView {
  situation: RegistrationSituation;
  /** Hero call to action for someone without an active registration */
  registerCta: 'REGISTER' | 'WAITLIST' | null;
  /** The visitor's own status, shown once, in the hero */
  ownStatus: Exclude<MyRegistrationStatus, 'ATTENDED'> | null;
  /** "Erneut anmelden" next to a cancelled registration (S1/S2 only) */
  registerAgain: boolean;
  /** "Abmelden" in the hero for a logged-in visitor with an active registration (until the day before) */
  unregister: boolean;
  /** "Anmeldung stornieren" by e-mail for an anonymous visitor (until the day before) */
  anonymousCancelLink: boolean;
  /** "Not registered with x@…, other address?" for a logged-in visitor without a registration */
  notRegisteredNotice: boolean;
  /** "Anmeldung geschlossen" for everyone without an active registration (S3/S4) */
  closedNotice: boolean;
  /** One block of place numbers in "Logistik"; null where hidden (S0, S5) */
  places: PlaceNumbers | null;
  /** What `/register/{code}` shows */
  registerPage: 'WIZARD' | 'WIZARD_WAITLIST' | 'CLOSED';
  /** Whether the visitor's own registration is worth fetching (S1–S4) */
  loadMyRegistration: boolean;
}

const COMPLETED_STATES = ['EVENT_COMPLETED', 'ARCHIVED'];
const BERN_DAY = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Zurich',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** Calendar day (YYYY-MM-DD) in Bern for an instant. */
function bernDay(instant: number): string {
  return BERN_DAY.format(new Date(instant));
}

/** True once the event's calendar day has begun in Bern (E2: no unregistering from then on). */
export function isEventDayReached(
  event: Pick<EventDetail, 'date'>,
  now: number = Date.now()
): boolean {
  if (!event.date) {
    return false;
  }
  const start = Date.parse(event.date);
  return Number.isFinite(start) && bernDay(now) >= bernDay(start);
}

export function getRegistrationSituation(
  event: RegistrationViewEvent,
  isArchiveMode: boolean,
  now: number = Date.now()
): RegistrationSituation {
  if (isArchiveMode || COMPLETED_STATES.includes(event.workflowState ?? '')) {
    return 'S5';
  }
  if (event.workflowState === 'EVENT_LIVE' || isEventDayReached(event, now)) {
    return 'S4';
  }
  if (!event.currentPublishedPhase) {
    return 'S0';
  }
  if (isRegistrationClosed(event, now)) {
    return 'S3';
  }
  const full =
    event.registrationCapacity != null && event.spotsRemaining != null && event.spotsRemaining <= 0;
  return full ? 'S2' : 'S1';
}

/**
 * The single answer to "what does the public page show about registration" (#1238). Hero, status,
 * logistics numbers, deregistration hints and `/register/{code}` all read from it.
 *
 * @param myStatus the visitor's registration: a status, `null` when logged in without one,
 *                 `undefined` when anonymous or not loaded
 */
export function getRegistrationView(
  event: RegistrationViewEvent,
  myStatus: MyRegistrationStatus | null | undefined,
  options: { isAuthenticated: boolean; isArchiveMode?: boolean; now?: number }
): RegistrationView {
  const now = options.now ?? Date.now();
  const situation = getRegistrationSituation(event, options.isArchiveMode ?? false, now);
  const open = situation === 'S1' || situation === 'S2';
  const beforeEventDay = open || situation === 'S3';
  const visible = beforeEventDay || situation === 'S4';
  const active = myStatus === 'REGISTERED' || myStatus === 'CONFIRMED' || myStatus === 'WAITLIST';
  const ownStatus =
    visible && (active || myStatus === 'CANCELLED')
      ? (myStatus as RegistrationView['ownStatus'])
      : null;
  const loggedInWithout = options.isAuthenticated && myStatus === null;

  return {
    situation,
    registerCta: open && !active ? (situation === 'S2' ? 'WAITLIST' : 'REGISTER') : null,
    ownStatus,
    registerAgain: open && myStatus === 'CANCELLED',
    unregister: beforeEventDay && options.isAuthenticated && active,
    anonymousCancelLink: beforeEventDay && !options.isAuthenticated,
    notRegisteredNotice: visible && loggedInWithout,
    closedNotice: (situation === 'S3' || situation === 'S4') && !active,
    places: visible ? placeNumbers(event, situation) : null,
    registerPage: open
      ? situation === 'S2'
        ? 'WIZARD_WAITLIST'
        : 'WIZARD'
      : situation === 'S0'
        ? 'WIZARD'
        : 'CLOSED',
    loadMyRegistration: visible,
  };
}

function placeNumbers(
  event: RegistrationViewEvent,
  situation: RegistrationSituation
): PlaceNumbers {
  const taken = event.confirmedCount ?? 0;
  const total = event.registrationCapacity ?? null;
  return {
    total,
    taken,
    free: total == null ? null : Math.max(total - taken, 0),
    waitlist: event.waitlistCount ?? 0,
    note: situation === 'S3' || situation === 'S4' ? 'CLOSED' : situation === 'S2' ? 'FULL' : null,
  };
}
