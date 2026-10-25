/**
 * UpcomingEventDatesSection Component (#1110)
 *
 * The next three BAT dates after the event featured in the hero: one card each, date and
 * title only. Unlike UpcomingEventsSection (published events, full cards) this shows the
 * planned calendar, so it includes events that have no published phase yet. It reuses the list
 * the presenter's "Upcoming events" slide already shows. Hidden when no future event exists.
 */

import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { presentationService } from '@/services/presentationService';

const MAX_DATES = 3;

interface UpcomingEventDatesSectionProps {
  currentEventCode: string | undefined;
}

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('de-CH', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

export function UpcomingEventDatesSection({ currentEventCode }: UpcomingEventDatesSectionProps) {
  const { t } = useTranslation('events');

  const { data } = useQuery({
    queryKey: ['events', 'public', 'upcoming-dates', currentEventCode],
    queryFn: () => presentationService.getUpcomingEvents(currentEventCode),
    staleTime: 5 * 60 * 1000,
  });

  const events = (data ?? []).slice(0, MAX_DATES);
  if (events.length === 0) return null;

  return (
    <div className="mt-12" data-testid="upcoming-event-dates">
      <h2 className="text-2xl font-light text-zinc-100 mb-6">{t('public.upcomingDates.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {events.map((event) => (
          <div
            key={event.eventCode}
            data-testid="upcoming-event-date-card"
            className="min-w-0 bg-zinc-900/50 rounded-lg border border-zinc-800 p-6"
          >
            <p className="text-sm text-blue-400">{formatDate(event.date)}</p>
            <p className="mt-2 text-lg font-light text-zinc-100 wrap-anywhere">{event.title}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
