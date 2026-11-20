/**
 * Published-phase gate for public event surfaces.
 *
 * The generic `GET /api/v1/events` list returns every event regardless of publication
 * state, so public surfaces must filter to events the organizer has actively published.
 * Only a `TOPIC` / `SPEAKERS` / `AGENDA` phase counts — the unpublished sentinel `NONE`
 * is a truthy string, so a bare `Boolean(phase)` check would wrongly pass it. Mirrors the
 * backend `/events/current` gate (`publishedAt != null || currentPublishedPhase != 'none'`).
 */
export const PUBLISHED_PHASES = ['TOPIC', 'SPEAKERS', 'AGENDA'] as const;

export type PublishedPhase = (typeof PUBLISHED_PHASES)[number];

export function isPublishedPhase(phase: string | null | undefined): boolean {
  return PUBLISHED_PHASES.includes((phase ?? '') as PublishedPhase);
}

interface SelfNominationGateInput {
  /** The Public Events read model's flag (GET /public/events, /public/events/*), #1263. */
  selfNominationOpen?: boolean;
}

/**
 * Story 7.2 "I Could Speak on That": may a visitor offer a talk for this event? The server
 * decides (`Event.isSelfNominationOpen()`: topic set and published, speakers not yet) and reports
 * it as `selfNominationOpen` on every public list item, detail and current event; the
 * self-nomination endpoint enforces the same rule. Absent (organizer endpoints, preview) means
 * closed. Callers add their own checks where relevant (the hero: a future event).
 */
export function canOfferSelfNomination(event: SelfNominationGateInput): boolean {
  return event.selfNominationOpen === true;
}
