/**
 * The upcoming events behind the homepage section "Nächste BAT-Termine" and the attendee
 * dashboard (story 3, 2026-11-13): one implementation for both.
 *
 * - `calendar`: the planned calendar (`presentationService.getUpcomingEvents`, the list the
 *   presenter's "Upcoming events" slide shows): date after now, `excludeEventCode` left out,
 *   nearest first, at most `max` (default 3).
 * - `publishedByCode`: the published events (Public Events read model, `scope=upcoming`) by code.
 *   An event in it gets the full `PublishedEventCard`, any other the `PlannedEventCard`. Empty
 *   when the published list fails, so every card falls back to code, title and date.
 *
 * `isLoading` until both answered, so a card does not change shape once the published list
 * arrives.
 */

import { useQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { presentationService } from '@/services/presentationService';
import type { EventDetailUI } from '@/types/event.types';

type CalendarEvent = Awaited<ReturnType<typeof presentationService.getUpcomingEvents>>[number];

export interface UpcomingEventCards {
  isLoading: boolean;
  calendar: CalendarEvent[];
  publishedByCode: Map<string, EventDetailUI>;
}

export function useUpcomingEventCards(
  excludeEventCode: string | undefined,
  max?: number
): UpcomingEventCards {
  const calendar = useQuery({
    queryKey:
      max === undefined
        ? ['events', 'public', 'upcoming-dates', excludeEventCode]
        : ['events', 'public', 'upcoming-dates', excludeEventCode, max],
    queryFn: () =>
      max === undefined
        ? presentationService.getUpcomingEvents(excludeEventCode)
        : presentationService.getUpcomingEvents(excludeEventCode, max),
    staleTime: 5 * 60 * 1000,
  });

  // Public Events read model (2026-10-07): the server selects published events from today on,
  // nearest first, and shapes sessions/speakers by phase.
  const published = useQuery({
    queryKey: ['events', 'public', 'upcoming'],
    queryFn: () => eventApiClient.getPublicEvents({ scope: 'upcoming', page: 1, limit: 5 }),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const publishedByCode = new Map(
    (published.data?.data ?? []).map(
      (e) => [e.eventCode, { ...e, sessions: e.sessions ?? undefined } as EventDetailUI] as const
    )
  );

  return {
    isLoading: calendar.isLoading || published.isLoading,
    calendar: calendar.data ?? [],
    publishedByCode,
  };
}
