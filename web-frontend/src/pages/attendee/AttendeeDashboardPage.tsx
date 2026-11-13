/**
 * AttendeeDashboardPage (Story 7.6)
 *
 * The logged-in attendee's event-history dashboard — every event they participated in, split into
 * upcoming and past. Mirrors the speaker dashboard (PublicLayout, Tailwind-only). The
 * Story 7.1 community-topic panel is preserved as a secondary section.
 *
 * Upcoming (story 3, owner 2026-11-13): three cards, the homepage's upcoming-event cards. Every
 * upcoming event the user is registered for comes first (all of them, even more than three), then
 * the next events of the calendar they are not registered for, until there are three. A published
 * event links to its event page, where the user registers; an unpublished one shows code, title
 * and date only, without a link. No register button on the card. A registered event missing from
 * the published list (for example when that list fails) keeps the local card below.
 *
 * Past: the local card, linking to /archive/{code}.
 *
 * "What's new" (story 2 of 2026-11-13): the card sits on top, the user menu of both headers links
 * to it (#whats-new). Its entries link to #earlier-profiles, #suggest-topics and #attendee-past.
 */

import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { EventEditionLabel } from '@/components/shared/EventEditionLabel/EventEditionLabel';
import { Calendar, MapPin } from 'lucide-react';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { CommunityTopicSuggestPanel } from '@/components/attendee/CommunityTopicSuggestPanel';
import { useAuth } from '@/hooks/useAuth';
import { EarlierProfilesSection } from '@/components/user/EarlierProfilesSection/EarlierProfilesSection';
import { PlannedEventCard, PublishedEventCard } from '@/components/public/UpcomingEventCards';
import { useUpcomingEventCards } from '@/hooks/useUpcomingEventCards';
import * as attendeeDashboardService from '@/services/attendeeDashboardService';
import type { AttendeeEventCard } from '@/services/attendeeDashboardService';
import { WhatsNewCard } from '@/features/whatsNew/WhatsNewCard';
import { scrollToFragment } from '@/features/whatsNew/scrollToFragment';

/** Cards in the upcoming list: registered ones count, unregistered ones fill up to this. */
const UPCOMING_CARDS = 3;
/** Calendar events to ask for: enough to fill three after skipping the registered ones. */
const CALENDAR_CANDIDATES = 10;
/** Arriving with a #fragment: its target may render only after its query answers. */
const FRAGMENT_TRIES = 15;
const FRAGMENT_TRY_MS = 200;

/** Scrolls to the #fragment of the URL once its element is on the page. */
function useScrollToFragment() {
  const { hash } = useLocation();
  useEffect(() => {
    const fragment = hash.replace(/^#/, '');
    if (!fragment) return undefined;
    let tries = 0;
    const timer = window.setInterval(() => {
      tries += 1;
      if (scrollToFragment(fragment) || tries >= FRAGMENT_TRIES) window.clearInterval(timer);
    }, FRAGMENT_TRY_MS);
    return () => window.clearInterval(timer);
  }, [hash]);
}

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
                {formatDate(event.eventDate)}
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
  useScrollToFragment();

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

  // Not excluding any event: unlike the homepage, there is no hero here.
  const upcoming = useUpcomingEventCards(undefined, CALENDAR_CANDIDATES);

  const greetingName = dashboard?.attendeeName || user?.username || '';
  const loading = isLoading || (!!dashboard && upcoming.isLoading);

  const registeredUpcoming = [...(dashboard?.upcomingEvents ?? [])].sort(
    (a, b) => new Date(a.eventDate ?? 0).getTime() - new Date(b.eventDate ?? 0).getTime()
  );
  const registeredCodes = new Set(registeredUpcoming.map((e) => e.eventCode));
  const unregistered = upcoming.calendar
    .filter((e) => !registeredCodes.has(e.eventCode))
    .slice(0, Math.max(0, UPCOMING_CARDS - registeredUpcoming.length));

  return (
    <PublicLayout>
      <div className="min-h-screen bg-background py-8 px-4" data-testid="attendee-dashboard">
        <div className="max-w-3xl mx-auto">
          <h1 className="text-3xl font-light mb-6">
            {greetingName
              ? t('attendee.dashboard.titleWithName', { name: greetingName })
              : t('attendee.dashboard.title')}
          </h1>

          <WhatsNewCard />

          {/* #1183 D: earlier registrations under the same name, on a profile without login */}
          <EarlierProfilesSection variant="banner" />

          {loading && (
            <div className="flex flex-col items-center justify-center py-20">
              <BATbernLoader size={64} />
            </div>
          )}

          {error && (
            <p className="text-destructive py-8" data-testid="attendee-dashboard-error">
              {t('attendee.dashboard.error')}
            </p>
          )}

          {dashboard && !loading && (
            <>
              <section className="mb-8" data-testid="attendee-upcoming">
                <h2 className="text-sm font-medium text-zinc-300 mb-3">
                  {t('attendee.dashboard.upcoming')}
                </h2>
                {registeredUpcoming.length + unregistered.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {registeredUpcoming.map((e) => {
                      const full = upcoming.publishedByCode.get(e.eventCode);
                      return full ? (
                        <PublishedEventCard key={e.eventCode} event={full} />
                      ) : (
                        <div key={e.eventCode} className="min-w-0">
                          <EventCard event={e} linkPrefix="/events/" />
                        </div>
                      );
                    })}
                    {unregistered.map((e) => {
                      const full = upcoming.publishedByCode.get(e.eventCode);
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
                ) : (
                  <p className="text-sm text-zinc-500" data-testid="attendee-upcoming-empty">
                    {t('attendee.dashboard.emptyUpcoming')}
                  </p>
                )}
              </section>

              <section id="attendee-past" className="mb-8 scroll-mt-28" data-testid="attendee-past">
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
          <div id="suggest-topics" className="mt-8 scroll-mt-28">
            <CommunityTopicSuggestPanel />
          </div>
        </div>
      </div>
    </PublicLayout>
  );
};

export default AttendeeDashboardPage;
