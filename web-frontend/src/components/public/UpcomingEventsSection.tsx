/**
 * UpcomingEventsSection Component
 *
 * "Nächste BAT-Termine": the next three events after the one featured in the hero, nearest
 * first. One section since 2026-11-07 (owner); it replaces the separate dates section of #1110.
 *
 * Which events: the planned calendar (`presentationService.getUpcomingEvents`, the list the
 * presenter's "Upcoming events" slide shows): not archived, date after now, the hero event
 * excluded, first three. That includes events that have no published phase yet.
 *
 * How each looks:
 * - published (in the Public Events read model, `scope=upcoming`): the full EventCard with
 *   link, topic, lineup or agenda, the visitor's registration chip and self-nomination;
 * - not published: the same card style with code, title and date only, and no link. Nothing
 *   else of an unpublished event is shown (2026-06-11: unpublished details leaked).
 *
 * Hidden when no event follows the hero. If the published list fails, every card falls back to
 * date and title.
 */

import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { presentationService } from '@/services/presentationService';
import { useMyRegistration } from '@/hooks/useMyRegistration';
import { EventCard } from '@/components/public/EventCard';
import { Card, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Badge } from '@/components/public/ui/badge';
import type { EventDetailUI } from '@/types/event.types';

const MAX_EVENTS = 3;

interface UpcomingEventsSectionProps {
  currentEventCode: string | undefined;
}

/**
 * Per-card wrapper that calls useMyRegistration individually (rules of hooks).
 * Story 10.10, AC5 / T10.5.
 */
function PublishedEventCard({ event }: { event: EventDetailUI }) {
  const { data: myReg } = useMyRegistration(event.eventCode);
  return (
    <div className="min-w-0" data-testid="upcoming-event-card">
      <EventCard
        event={event}
        viewMode="grid"
        linkPrefix="/events/"
        myRegistrationStatus={myReg?.status}
        // Story 7.2 "I Could Speak on That": attendees can self-nominate from upcoming-event cards.
        enableSelfNomination
      />
    </div>
  );
}

/** An event without a published phase: code, title and date in the EventCard style, no link. */
function PlannedEventCard({
  eventCode,
  title,
  date,
}: {
  eventCode: string;
  title: string;
  date: string;
}) {
  const formattedDate = new Date(date).toLocaleDateString('de-CH', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
  return (
    <div className="min-w-0" data-testid="upcoming-event-date-card">
      <Card className="bg-zinc-700 border-zinc-600 h-full">
        <CardHeader>
          <div className="mb-2">
            <Badge
              variant="outline"
              className="bg-blue-400/10 text-blue-400 border-blue-400/20 font-mono"
            >
              {eventCode}
            </Badge>
          </div>
          <CardTitle className="font-light text-xl text-zinc-100 wrap-anywhere">{title}</CardTitle>
          <p className="text-sm text-zinc-400 mt-2">{formattedDate}</p>
        </CardHeader>
      </Card>
    </div>
  );
}

export function UpcomingEventsSection({ currentEventCode }: UpcomingEventsSectionProps) {
  const { t } = useTranslation('events');

  const calendar = useQuery({
    queryKey: ['events', 'public', 'upcoming-dates', currentEventCode],
    queryFn: () => presentationService.getUpcomingEvents(currentEventCode),
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

  // Wait for both, so a card does not change shape once the published list arrives.
  if (calendar.isLoading || published.isLoading) return null;

  const events = (calendar.data ?? []).slice(0, MAX_EVENTS);
  if (events.length === 0) return null;

  const publishedByCode = new Map(
    (published.data?.data ?? []).map((e) => [e.eventCode, e] as const)
  );

  return (
    <div className="mt-16" data-testid="upcoming-events">
      <h2 className="text-2xl font-light text-zinc-100 mb-6">{t('public.upcomingDates.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {events.map((e) => {
          const full = publishedByCode.get(e.eventCode);
          return full ? (
            <PublishedEventCard
              key={e.eventCode}
              event={{ ...full, sessions: full.sessions ?? undefined } as EventDetailUI}
            />
          ) : (
            <PlannedEventCard
              key={e.eventCode}
              eventCode={e.eventCode}
              title={e.title}
              date={e.date}
            />
          );
        })}
      </div>
    </div>
  );
}
