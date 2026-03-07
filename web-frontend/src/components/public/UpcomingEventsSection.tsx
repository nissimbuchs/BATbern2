/**
 * UpcomingEventsSection Component
 *
 * Displays upcoming events (beyond the current hero event) as a card grid
 * on the HomePage. Rendered above the testimonials section.
 * Hidden when no additional upcoming events exist.
 */

import { useTranslation } from 'react-i18next';
import { useEvents } from '@/hooks/useEvents';
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
    />
  );
}

export function UpcomingEventsSection({ currentEventCode }: UpcomingEventsSectionProps) {
  const { t } = useTranslation('events');

  const { data, isLoading } = useEvents({ page: 1, limit: 5 }, undefined, {
    expand: ['topics', 'sessions', 'speakers'],
  });

  if (isLoading) return null;

  const now = new Date();
  const upcomingEvents = (data?.data ?? [])
    .filter((e) => e.eventCode !== currentEventCode && new Date(e.date) > now)
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
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
