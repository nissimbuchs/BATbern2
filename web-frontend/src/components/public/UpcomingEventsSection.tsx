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
 * Return from login (#1263): an anonymous click on a card's self-nomination button logs in and
 * comes back to `/?nominate=<eventCode>`. The card with that code opens its form once; once the
 * cards and the auth state are known, the parameter is removed (replace), whether a card matched
 * or not. The hero reads the same parameter for its own button (`HomePage`).
 *
 * The cards and the data behind them are shared with the attendee dashboard (story 3,
 * 2026-11-13): `UpcomingEventCards.tsx`, `useUpcomingEventCards`.
 */

import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useUpcomingEventCards } from '@/hooks/useUpcomingEventCards';
import { PlannedEventCard, PublishedEventCard } from '@/components/public/UpcomingEventCards';

interface UpcomingEventsSectionProps {
  currentEventCode: string | undefined;
}

export function UpcomingEventsSection({ currentEventCode }: UpcomingEventsSectionProps) {
  const { t } = useTranslation('events');
  const { isLoading, calendar, publishedByCode } = useUpcomingEventCards(currentEventCode);
  const { isLoading: isAuthLoading } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const nominate = searchParams.get('nominate');

  // The matching card opened its form in this same commit (child effects run first), so the
  // parameter can go: a reload or a back navigation does not open the form again.
  useEffect(() => {
    if (nominate === null || isLoading || isAuthLoading) return;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('nominate');
        return next;
      },
      { replace: true }
    );
  }, [nominate, isLoading, isAuthLoading, setSearchParams]);

  if (isLoading || calendar.length === 0) return null;

  return (
    <div className="mt-16" data-testid="upcoming-events">
      <h2 className="text-2xl font-light text-zinc-100 mb-6">{t('public.upcomingDates.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {calendar.map((e) => {
          const full = publishedByCode.get(e.eventCode);
          return full ? (
            <PublishedEventCard
              key={e.eventCode}
              event={full}
              autoOpenSelfNomination={nominate === e.eventCode}
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
