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
 *
 * The cards and the data behind them are shared with the attendee dashboard (story 3,
 * 2026-11-13): `UpcomingEventCards.tsx`, `useUpcomingEventCards`.
 */

import { useTranslation } from 'react-i18next';
import { useUpcomingEventCards } from '@/hooks/useUpcomingEventCards';
import { PlannedEventCard, PublishedEventCard } from '@/components/public/UpcomingEventCards';

interface UpcomingEventsSectionProps {
  currentEventCode: string | undefined;
}

export function UpcomingEventsSection({ currentEventCode }: UpcomingEventsSectionProps) {
  const { t } = useTranslation('events');
  const { isLoading, calendar, publishedByCode } = useUpcomingEventCards(currentEventCode);

  if (isLoading || calendar.length === 0) return null;

  return (
    <div className="mt-16" data-testid="upcoming-events">
      <h2 className="text-2xl font-light text-zinc-100 mb-6">{t('public.upcomingDates.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {calendar.map((e) => {
          const full = publishedByCode.get(e.eventCode);
          return full ? (
            <PublishedEventCard key={e.eventCode} event={full} />
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
