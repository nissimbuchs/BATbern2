/**
 * AttendeeDashboardPage (Story 7.6)
 *
 * The logged-in attendee's event-history dashboard — every event they participated in, split into
 * upcoming and past. Mirrors the speaker dashboard (PublicLayout, Tailwind-only). Each event card
 * is a link to the public event detail page (/events/{code} upcoming, /archive/{code} past). The
 * Story 7.1 community-topic panel is preserved as a secondary section.
 */

import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { EventEditionLabel } from '@/components/shared/EventEditionLabel/EventEditionLabel';
import { Calendar, MapPin } from 'lucide-react';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { CommunityTopicSuggestPanel } from '@/components/attendee/CommunityTopicSuggestPanel';
import { useAuth } from '@/hooks/useAuth';
import * as attendeeDashboardService from '@/services/attendeeDashboardService';
import type { AttendeeEventCard } from '@/services/attendeeDashboardService';

function formatDate(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

function EventCard({ event, linkPrefix }: { event: AttendeeEventCard; linkPrefix: string }) {
  const { t } = useTranslation('events');
  const statusKey = (event.registrationStatus ?? '').toLowerCase();
  return (
    <Link
      to={`${linkPrefix}${event.eventCode}`}
      className="block transition-colors hover:opacity-90"
      data-testid="attendee-event-card"
    >
      <Card className="p-4 mb-3">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div>
            <EventEditionLabel
              eventCode={event.eventCode}
              className="block text-xs font-semibold uppercase tracking-wide text-muted-foreground"
            />
            <h3 className="text-base font-medium text-foreground">{event.eventTitle}</h3>
            <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground mt-0.5">
              <span className="flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5" aria-hidden="true" />
                {formatDate(event.eventDate ?? undefined)}
              </span>
              {event.eventLocation && (
                <span className="flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5" aria-hidden="true" />
                  {event.eventLocation}
                </span>
              )}
            </div>
          </div>
          {statusKey && (
            <span
              className="self-start rounded bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300"
              data-testid="attendee-event-status"
            >
              {t(`attendee.dashboard.status.${statusKey}`, statusKey)}
            </span>
          )}
        </div>
      </Card>
    </Link>
  );
}

const AttendeeDashboardPage = () => {
  const { t } = useTranslation('events');
  const { user } = useAuth();

  const {
    data: dashboard,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['attendee-dashboard', user?.username],
    queryFn: () => attendeeDashboardService.getDashboard(),
    enabled: !!user,
    retry: false,
  });

  const greetingName = dashboard?.attendeeName || user?.username || '';

  return (
    <PublicLayout>
      <div className="min-h-screen bg-background py-8 px-4" data-testid="attendee-dashboard">
        <div className="max-w-3xl mx-auto">
          <h1 className="text-3xl font-light mb-6">
            {greetingName
              ? t('attendee.dashboard.titleWithName', { name: greetingName })
              : t('attendee.dashboard.title')}
          </h1>

          {isLoading && (
            <div className="flex flex-col items-center justify-center py-20">
              <BATbernLoader size={64} />
            </div>
          )}

          {error && (
            <p className="text-destructive py-8" data-testid="attendee-dashboard-error">
              {t('attendee.dashboard.error')}
            </p>
          )}

          {dashboard && !isLoading && (
            <>
              <section className="mb-8" data-testid="attendee-upcoming">
                <h2 className="text-sm font-medium text-zinc-300 mb-3">
                  {t('attendee.dashboard.upcoming')}
                </h2>
                {dashboard.upcomingEvents && dashboard.upcomingEvents.length > 0 ? (
                  dashboard.upcomingEvents.map((e) => (
                    <EventCard key={e.eventCode} event={e} linkPrefix="/events/" />
                  ))
                ) : (
                  <p className="text-sm text-zinc-500" data-testid="attendee-upcoming-empty">
                    {t('attendee.dashboard.emptyUpcoming')}
                  </p>
                )}
              </section>

              <section className="mb-8" data-testid="attendee-past">
                <h2 className="text-sm font-medium text-zinc-300 mb-3">
                  {t('attendee.dashboard.past')}
                </h2>
                {dashboard.pastEvents && dashboard.pastEvents.length > 0 ? (
                  dashboard.pastEvents.map((e) => (
                    <EventCard key={e.eventCode} event={e} linkPrefix="/archive/" />
                  ))
                ) : (
                  <p className="text-sm text-zinc-500" data-testid="attendee-past-empty">
                    {t('attendee.dashboard.emptyPast')}
                  </p>
                )}
              </section>
            </>
          )}

          {/* Story 7.1: Topics From the Floor — preserved from the old welcome page. */}
          <div className="mt-8">
            <CommunityTopicSuggestPanel />
          </div>
        </div>
      </div>
    </PublicLayout>
  );
};

export default AttendeeDashboardPage;
