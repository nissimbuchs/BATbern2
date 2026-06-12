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
  /** Expanded topic object (present once the topic is set), or a raw code/string, or null. */
  topic?: { name?: string } | string | null;
  currentPublishedPhase?: string | null;
}

/**
 * Story 7.2 "I Could Speak on That": speaker self-nomination is open ONLY during the TOPIC
 * publishing phase — from when the topic is published until the speakers are published. Once the
 * lineup is published (SPEAKERS/AGENDA), the program is set and no further nominations are taken.
 * Callers add their own login gate (the panel self-gates) and, where relevant, a future-event check.
 */
export function canOfferSelfNomination(event: SelfNominationGateInput): boolean {
  return (
    typeof event.topic === 'object' &&
    event.topic != null &&
    (event.currentPublishedPhase ?? '') === 'TOPIC'
  );
}
