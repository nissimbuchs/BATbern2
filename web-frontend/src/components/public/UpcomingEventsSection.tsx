/**
 * UpcomingEventsSection Component
 *
 * Displays upcoming events (beyond the current hero event) as a card grid
 * on the HomePage. Rendered above the testimonials section.
 * Hidden when no additional upcoming events exist.
 */

import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { useMyRegistration } from '@/hooks/useMyRegistration';
import { EventCard } from '@/components/public/EventCard';
import type { EventDetailUI } from '@/types/event.types';

interface UpcomingEventsSectionProps {
  currentEventCode: string | undefined;
}

/**
 * EventCardWithStatus — per-card wrapper that calls useMyRegistration individually.
 * Avoids calling hooks in a loop (rules of hooks) by extracting into a component.
 * Story 10.10, AC5 / T10.5.
 */
function UpcomingEventCardWithStatus({ event }: { event: EventDetailUI }) {
  const { data: myReg } = useMyRegistration(event.eventCode);
  return (
    <EventCard
      key={event.eventCode}
      event={event}
      viewMode="grid"
      linkPrefix="/events/"
      myRegistrationStatus={myReg?.status}
      // Story 7.2 "I Could Speak on That": attendees can self-nominate from upcoming-event cards.
      enableSelfNomination
    />
  );
}

export function UpcomingEventsSection({ currentEventCode }: UpcomingEventsSectionProps) {
  const { t } = useTranslation('events');

  // Public Events read model (2026-10-07): the server selects published events from today on,
  // nearest first, and shapes sessions/speakers by phase. Only the event already featured in the
  // hero is skipped here (a layout concern).
  const { data, isLoading } = useQuery({
    queryKey: ['events', 'public', 'upcoming'],
    queryFn: () => eventApiClient.getPublicEvents({ scope: 'upcoming', page: 1, limit: 5 }),
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) return null;

  const upcomingEvents = (data?.data ?? [])
    .filter((e) => e.eventCode !== currentEventCode)
    .slice(0, 4);

  if (upcomingEvents.length === 0) return null;

  return (
    <div className="mt-16">
      <h2 className="text-2xl font-light text-zinc-100 mb-6">{t('public.upcomingEvents.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {upcomingEvents.map((event) => (
          <UpcomingEventCardWithStatus
            key={event.eventCode}
            event={{ ...event, sessions: event.sessions ?? undefined } as EventDetailUI}
          />
        ))}
      </div>
    </div>
  );
}
