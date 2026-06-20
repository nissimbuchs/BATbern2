/**
 * HomePage Component (Story 4.1.3, 4.1.4, 4.2, 5.7)
 * BATbern-public design with dynamic event data from backend
 * Displays current event with hero, logistics, countdown, speakers, sessions, venue, and social sharing
 *
 * Story 5.7 (BAT-11): Supports preview mode for organizers
 * - Route "/" shows current published event
 * - Route "/events/:eventCode" shows specific event (with optional preview mode)
 * - Query params: ?preview=true&phase=speakers&mode=progressive
 *
 * Story 4.2 (BAT-109): Supports archive mode
 * - Route "/archive/:eventCode" shows archived event with timeline only
 *
 * ## Display Phase Logic
 * All section visibility is driven by a single HomePagePhase discriminated union.
 * See homePagePhase.ts for the authoritative section visibility matrix.
 */

import { lazy, Suspense, useState } from 'react';
import { useParams, useSearchParams, useLocation, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PublicLayout } from '@/components/public/PublicLayout';
import { HeroSection } from '@/components/public/Hero/HeroSection';
import { EventLogistics } from '@/components/public/Event/EventLogistics';
import { CapacityIndicator } from '@/components/public/Event/CapacityIndicator';
import { CountdownTimer } from '@/components/public/Event/CountdownTimer';
import { SpeakerGrid } from '@/components/public/Event/SpeakerGrid';
import { SessionCards } from '@/components/public/Event/SessionCards';
import { EventProgram } from '@/components/public/Event/EventProgram';
import { VenueMap } from '@/components/public/Event/VenueMap';
import { SocialSharing } from '@/components/public/Event/SocialSharing';
import { EventDescriptionSection } from '@/components/public/Event/EventDescriptionSection';
import { OpenGraphTags } from '@/components/SEO/OpenGraphTags';
import { TestimonialSection } from '@/components/public/Testimonials/TestimonialSection';
import { EventPhotosMarquee } from '@/components/public/EventPhotos/EventPhotosMarquee';
import { UpcomingEventsSection } from '@/components/public/UpcomingEventsSection';
import { SpeakerSelfNominatePanel } from '@/components/attendee/SpeakerSelfNominatePanel';
import { canOfferSelfNomination } from '@/utils/eventPublication';
// Below-the-fold + backend/Turnstile-dependent → lazy-loaded so it stays off the eager
// homepage bundle (its Turnstile/config code too) and only mounts after the page's event
// data has resolved, by which point runtime config is loaded. Keeps the config gate from
// ever being on the homepage's critical render path.
const NewsletterSubscribeWidget = lazy(() =>
  import('@/components/public/NewsletterSubscribeWidget').then((m) => ({
    default: m.NewsletterSubscribeWidget,
  }))
);
import { useCurrentEvent } from '@/hooks/useCurrentEvent';
import { useMyRegistration } from '@/hooks/useMyRegistration';
import { useEventPhotos } from '@/hooks/useEventPhotos';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetail } from '@/types/event.types';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { RegistrationStatusBanner } from '@/components/public/RegistrationStatusBanner';
import { DeregistrationByEmailModal } from '@/components/public/DeregistrationByEmailModal';
import { useTranslation } from 'react-i18next';
import {
  getHomepagePhase,
  getSectionVisibility,
  isRegistrationClosed,
  showSessionQna,
} from './homePagePhase';

const REGISTRATION_WORKFLOW_STATES = ['AGENDA_PUBLISHED', 'EVENT_LIVE'];

const HomePage = () => {
  const { t } = useTranslation('events');
  const { t: tCommon } = useTranslation('common');
  const { t: tReg } = useTranslation('registration');
  const [deregisterModalOpen, setDeregisterModalOpen] = useState(false);
  const { eventCode } = useParams<{ eventCode?: string }>();
  const [searchParams] = useSearchParams();
  const location = useLocation();

  // Archive mode (Story 4.2)
  const isArchiveMode = location.pathname.startsWith('/archive');

  // Preview mode (Story 5.7)
  const isPreview = searchParams.get('preview') === 'true';
  const previewPhase = searchParams.get('phase') || 'speakers';
  const previewMode = searchParams.get('mode') || 'progressive';

  // Fetch specific event if eventCode is provided, otherwise fetch current event
  const specificEventQuery = useQuery<EventDetail | null, Error>({
    queryKey: ['events', eventCode, { preview: isPreview, phase: previewPhase, mode: previewMode }],
    queryFn: () => {
      if (!eventCode) return Promise.reject(new Error('Event code is required'));
      // TODO: Add preview support to API - for now just fetches published data
      return eventApiClient.getEvent(eventCode, {
        expand: ['topics', 'venue', 'speakers', 'sessions'],
      });
    },
    enabled: !!eventCode,
    staleTime: 5 * 60 * 1000,
    retry: 2,
  });

  const currentEventQuery = useCurrentEvent();
  const { data: event, isLoading, error } = eventCode ? specificEventQuery : currentEventQuery;

  // Event-specific photos — enabled for archive pages and EVENT_COMPLETED/ARCHIVED on homepage
  const isCompletedOrArchived =
    event?.workflowState === 'ARCHIVED' || event?.workflowState === 'EVENT_COMPLETED';
  const { data: eventPhotos } = useEventPhotos(
    eventCode ?? event?.eventCode ?? '',
    isArchiveMode || isCompletedOrArchived
  );

  // Registration status (Story 10.10) — only fetch when registration is actionable
  const registrationActive =
    !isArchiveMode &&
    !isCompletedOrArchived &&
    !!event?.workflowState &&
    REGISTRATION_WORKFLOW_STATES.includes(event.workflowState);

  const { data: myRegistration, isLoading: isRegistrationLoading } = useMyRegistration(
    registrationActive ? (event?.eventCode ?? undefined) : undefined
  );

  // Loading state — reserve a full viewport height so the footer starts below
  // the fold (where it lands once content loads). Without this the short
  // placeholder put the footer ~300px down, and swapping in the real
  // min-h-screen hero shoved it far down → a ~0.47 CLS shift (the dominant CLS
  // culprit, traced via a layout-shift PerformanceObserver, 2026-06-01).
  if (isLoading) {
    return (
      <PublicLayout>
        <div
          className="container mx-auto px-4 py-24 flex min-h-screen items-center justify-center"
          data-testid="homepage-loading"
        >
          <BATbernLoader size={96} />
        </div>
      </PublicLayout>
    );
  }

  // Error state or no event available
  if (error || !event) {
    return (
      <PublicLayout>
        <div
          className="container mx-auto px-4 py-24 text-center min-h-screen"
          data-testid="event-load-error"
        >
          <h2 className="text-2xl font-light text-zinc-300">
            {error ? t('public.errors.loadFailed') : t('public.errors.noEvent')}
          </h2>
          <p className="mt-4 text-zinc-400">{t('public.errors.checkBackLater')}</p>
        </div>
      </PublicLayout>
    );
  }

  // ---------------------------------------------------------------------------
  // Derived display values
  // ---------------------------------------------------------------------------

  const eventTitle = event.title || t('public.comingSoon');
  const registerLink = event.eventCode ? `/register/${event.eventCode}` : '/register';
  const eventDate = event.date;
  const eventLocation = event.venueName;
  const eventDateObj = eventDate ? new Date(eventDate) : null;
  // Story 7.2 "I Could Speak on That": the current/hero event must also offer self-nomination
  // while it is still upcoming, its topic is set, and it is published — not only the secondary
  // upcoming-event cards. The panel self-gates on login; the event must be in the future
  // (a past/archived hero event never accepts nominations — the window closes at event start).
  const heroSelfNominationEligible =
    !!event.eventCode &&
    !!eventDateObj &&
    eventDateObj.getTime() > Date.now() &&
    canOfferSelfNomination(event);
  const heroTopicName =
    event.topic && typeof event.topic === 'object' ? event.topic.name : undefined;
  const eventUrl = typeof window !== 'undefined' ? window.location.href : '';
  const eventDescription = event.description || `Join us for ${eventTitle} in ${eventLocation}`;
  const hasSessions = !!(event.sessions && event.sessions.length > 0);

  const backToArchiveUrl = isArchiveMode
    ? location.search
      ? `/archive${location.search}`
      : '/archive'
    : null;

  // ---------------------------------------------------------------------------
  // Phase + visibility — single source of truth
  // ---------------------------------------------------------------------------

  const phase = getHomepagePhase(event, isArchiveMode, eventPhotos);
  const vis = getSectionVisibility(phase);
  const qnaVisible = showSessionQna(phase, event);

  // Feedback #1: registration + waitlist close on the deadline. Hide the register CTA once the
  // deadline passes even if the phase would otherwise allow it (backend enforces with 409).
  const registrationClosed = isRegistrationClosed(event);
  const showRegisterCta = vis.registrationEnabled && !registrationClosed;

  // Story 7.4: surface the "Thank the organizers" nav button only on a live/completed event.
  const thankableEventCode =
    event.eventCode &&
    (event.workflowState === 'EVENT_LIVE' || event.workflowState === 'EVENT_COMPLETED')
      ? event.eventCode
      : undefined;

  const canDeregister =
    !!event.workflowState && REGISTRATION_WORKFLOW_STATES.includes(event.workflowState);

  // ---------------------------------------------------------------------------
  // Preview mode banner (Story 5.7)
  // ---------------------------------------------------------------------------

  const previewBanner = isPreview ? (
    <div className="fixed top-0 left-0 right-0 z-[60] bg-blue-500 text-white py-3 px-4 text-center">
      <div className="container mx-auto flex items-center justify-center gap-4 flex-wrap">
        <span className="font-semibold">🔍 {t('public.preview.title')}</span>
        <span className="text-sm">
          {t('public.preview.phase')}: {previewPhase}
        </span>
        <span className="text-sm">
          {t('public.preview.mode')}: {previewMode}
        </span>
        <span className="text-xs opacity-90">{t('public.preview.description')}</span>
      </div>
    </div>
  ) : undefined;

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <PublicLayout topBanner={previewBanner} thankableEventCode={thankableEventCode}>
      {/* SEO Meta Tags */}
      <OpenGraphTags
        title={eventTitle}
        description={eventDescription}
        url={eventUrl}
        image={event.themeImageUrl || undefined}
        type="event"
      />

      {/* Hero — always shown */}
      <HeroSection
        title={eventTitle}
        date={eventDate}
        location={eventLocation}
        ctaLink={registerLink}
        eventCode={event.eventCode}
        hideRegisterCta={!showRegisterCta}
        themeImageUrl={event.themeImageUrl || undefined}
        countdownTimer={eventDateObj ? <CountdownTimer eventDate={eventDateObj} /> : undefined}
        spotsRemaining={event.spotsRemaining}
      />

      {/* Feedback #1: registration deadline passed — the CTA is hidden; tell the visitor why. Only
          when the phase would otherwise offer registration (not COMING_SOON / POST_EVENT / ARCHIVE). */}
      {registrationClosed && vis.registrationEnabled && (
        <div className="container mx-auto px-4 mt-6">
          <div
            className="max-w-md mx-auto rounded-md border border-border bg-muted px-4 py-3 text-center text-sm text-muted-foreground"
            data-testid="registration-closed-banner"
          >
            {t('public.registrationClosed')}
          </div>
        </div>
      )}

      {/* Story 7.2: self-nominate on the current event (topic set + published + upcoming).
          The panel renders nothing for anonymous visitors, so this stays invisible until login. */}
      {heroSelfNominationEligible && (
        <div className="container mx-auto px-4 mt-6">
          <div className="max-w-md mx-auto" data-testid="hero-self-nominate">
            <SpeakerSelfNominatePanel eventCode={event.eventCode!} topicName={heroTopicName} />
          </div>
        </div>
      )}

      {/* Event Description — hidden in COMING_SOON */}
      {vis.eventDescription && (
        <div className="container mx-auto px-4">
          <EventDescriptionSection description={event?.description} />
        </div>
      )}

      <div className="container mx-auto px-4">
        {/* Registration Status Banner (Story 10.10) */}
        {registrationActive && (
          <RegistrationStatusBanner
            status={myRegistration?.status ?? null}
            eventCode={event.eventCode ?? ''}
            isLoading={isRegistrationLoading}
            waitlistPosition={myRegistration?.waitlistPosition}
          />
        )}

        {/* Deregistration link (Story 10.12) */}
        {registrationActive && canDeregister && (
          <>
            <div className="mt-2 text-center">
              <button
                onClick={() => setDeregisterModalOpen(true)}
                className="text-sm text-zinc-400 hover:text-zinc-200 underline transition-colors"
              >
                {tReg('deregistration.homepage.cancelLink')}
              </button>
            </div>
            <DeregistrationByEmailModal
              open={deregisterModalOpen}
              onClose={() => setDeregisterModalOpen(false)}
              eventCode={event.eventCode ?? ''}
            />
          </>
        )}

        {/* Back to archive link — only in ARCHIVE mode (Story 4.2) */}
        {vis.backLink && backToArchiveUrl && (
          <Link
            to={backToArchiveUrl}
            className="inline-block mt-8 mb-4 text-blue-400 hover:text-blue-300 transition-colors"
            data-testid="back-to-archive"
          >
            {tCommon('archive.detail.backToArchive')}
          </Link>
        )}

        {/* Event Logistics — always shown */}
        {vis.eventLogistics && (
          <div className="mt-12 bg-zinc-900/50 rounded-lg border border-zinc-800 p-8">
            <h3 className="text-xl font-light text-zinc-100 mb-6">{t('public.logistics.title')}</h3>
            <EventLogistics event={event} />
            {/* Capacity badge — only when registration is active (Story 10.11) */}
            {registrationActive && (
              <div className="mt-4">
                <CapacityIndicator
                  registrationCapacity={event.registrationCapacity}
                  spotsRemaining={event.spotsRemaining}
                  waitlistCount={event.waitlistCount}
                />
              </div>
            )}
          </div>
        )}

        {/* Event Program (timetable) — AGENDA phase only */}
        {vis.eventProgram && hasSessions && (
          <EventProgram
            sessions={event.sessions!}
            isArchived={isCompletedOrArchived}
            eventCode={event.eventCode}
            showQna={qnaVisible}
          />
        )}

        {/* Session Cards (list) — SPEAKERS phase, POST_EVENT, ARCHIVE */}
        {vis.sessionCards && hasSessions && (
          <SessionCards
            sessions={event.sessions!}
            showMaterials={vis.showSessionMaterials}
            showQna={qnaVisible}
            eventCode={event.eventCode}
          />
        )}

        {/* Speaker Grid — SPEAKERS phase onward, POST_EVENT, ARCHIVE */}
        {vis.speakerGrid && hasSessions && <SpeakerGrid sessions={event.sessions!} />}

        {/* Venue Map — hidden in POST_EVENT and ARCHIVE */}
        {vis.venueMap && event.venueName && event.venueAddress && (
          <VenueMap
            venue={{
              id: event.eventCode,
              name: event.venueName,
              address: event.venueAddress,
              capacity: event.venueCapacity,
            }}
          />
        )}

        {/* Social Sharing — always shown */}
        <SocialSharing eventTitle={eventTitle} eventUrl={eventUrl} />

        {/* Upcoming Events — always shown */}
        <UpcomingEventsSection currentEventCode={event.eventCode} />

        {/* Event-specific photos marquee — POST_EVENT and ARCHIVE when photos exist.
            Each photo opens a zoomable, swipeable lightbox (lazy-loaded). */}
        {vis.eventPhotosMarquee && eventPhotos && eventPhotos.length > 0 && (
          <EventPhotosMarquee photos={eventPhotos} />
        )}

        {/* Testimonials + Partners — always shown */}
        <div className="mt-16 pb-12">
          <TestimonialSection skipPhotoRow={vis.testimonialsSkipPhotoRow} />
        </div>

        {/* Newsletter Subscribe Widget — always shown (lazy, below the fold) */}
        <div className="border-t pt-4 pb-8">
          <Suspense fallback={null}>
            <NewsletterSubscribeWidget />
          </Suspense>
        </div>
      </div>
    </PublicLayout>
  );
};

export default HomePage;
