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
 * - Hides speakers, sessions, and venue sections
 */

import { useState } from 'react';
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
import { InfiniteMarquee } from '@/components/public/Testimonials/InfiniteMarquee';
import { UpcomingEventsSection } from '@/components/public/UpcomingEventsSection';
import { NewsletterSubscribeWidget } from '@/components/public/NewsletterSubscribeWidget';
import { useCurrentEvent } from '@/hooks/useCurrentEvent';
import { useMyRegistration } from '@/hooks/useMyRegistration';
import { useEventPhotos } from '@/hooks/useEventPhotos';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetail } from '@/types/event.types';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { RegistrationStatusBanner } from '@/components/public/RegistrationStatusBanner';
import { DeregistrationByEmailModal } from '@/components/public/DeregistrationByEmailModal';
import { useTranslation } from 'react-i18next';

const DEREGISTRATION_WORKFLOW_STATES = ['AGENDA_PUBLISHED', 'AGENDA_FINALIZED', 'EVENT_LIVE'];

const HomePage = () => {
  const { t } = useTranslation('events');
  const { t: tCommon } = useTranslation('common');
  const { t: tReg } = useTranslation('registration');
  const [deregisterModalOpen, setDeregisterModalOpen] = useState(false);
  const { eventCode } = useParams<{ eventCode?: string }>();
  const [searchParams] = useSearchParams();
  const location = useLocation();

  // Check if we're in archive mode (Story 4.2)
  const isArchiveMode = location.pathname.startsWith('/archive');

  // Event-specific photos for archive detail view (Story 10.21 — AC7)
  const { data: eventPhotos } = useEventPhotos(eventCode ?? '', isArchiveMode);

  // Check if we're in preview mode
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
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: 2,
  });

  // Use current event query if no eventCode specified
  const currentEventQuery = useCurrentEvent();

  // Select the appropriate query result
  const { data: event, isLoading, error } = eventCode ? specificEventQuery : currentEventQuery;

  // AC2/AC4: Registration status banner (Story 10.10)
  // Hook returns undefined immediately for unauthenticated users — no API call made (AC8)
  const BANNER_WORKFLOW_STATES = ['AGENDA_PUBLISHED', 'AGENDA_FINALIZED', 'EVENT_LIVE'];
  const { data: myRegistration, isLoading: isRegistrationLoading } = useMyRegistration(
    !isArchiveMode &&
      event &&
      event.workflowState &&
      BANNER_WORKFLOW_STATES.includes(event.workflowState)
      ? event.eventCode
      : undefined
  );

  // Loading state
  if (isLoading) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-24 flex justify-center">
          <BATbernLoader size={96} />
        </div>
      </PublicLayout>
    );
  }

  // Error state or no event available
  if (error || !event) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-24 text-center">
          <h2 className="text-2xl font-light text-zinc-300">
            {error ? t('public.errors.loadFailed') : t('public.errors.noEvent')}
          </h2>
          <p className="mt-4 text-zinc-400">{t('public.errors.checkBackLater')}</p>
        </div>
      </PublicLayout>
    );
  }

  // Fallback values
  const eventTitle = event.title || t('public.comingSoon');
  const registerLink = event.eventCode ? `/register/${event.eventCode}` : '/register';
  const eventDate = event.date;
  const eventLocation = event.venueName;
  const eventDateObj = eventDate ? new Date(eventDate) : null;
  const eventUrl = typeof window !== 'undefined' ? window.location.href : '';
  const eventDescription = event.description || `Join us for ${eventTitle} in ${eventLocation}`;

  // Archive mode: construct back URL preserving query parameters (filters, sort)
  const backToArchiveUrl = isArchiveMode
    ? location.search
      ? `/archive${location.search}`
      : '/archive'
    : null;

  // Progressive publishing phase-based display (Story 5.7)
  // API returns uppercase: 'TOPIC', 'SPEAKERS', 'AGENDA'
  // Default to 'AGENDA' (show all) if not set (backward compatibility)
  const currentPhase =
    ('currentPublishedPhase' in event
      ? (event.currentPublishedPhase as 'TOPIC' | 'SPEAKERS' | 'AGENDA' | null | undefined)
      : null) || 'AGENDA';

  // Archive mode (Story 4.2 / 10.21): Show timetable + speakers; hide logistics/venue/registration
  // In archive mode always show speakers — event is fully published and done
  const showSpeakersAndSessions =
    isArchiveMode || currentPhase === 'SPEAKERS' || currentPhase === 'AGENDA';
  const showTimetable = isArchiveMode || currentPhase === 'AGENDA'; // Always show in archive mode
  const showSessionList = !isArchiveMode && currentPhase === 'SPEAKERS'; // Only show when speakers published, not when agenda published (timetable replaces it)
  const showVenue = !isArchiveMode; // Hide venue in archive mode

  // Preview Mode Banner (Story 5.7) - shown above navigation
  // Fixed positioning to stay above the fixed navigation
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

  return (
    <PublicLayout topBanner={previewBanner}>
      {/* SEO Meta Tags */}
      <OpenGraphTags
        title={eventTitle}
        description={eventDescription}
        url={eventUrl}
        image={event.themeImageUrl || undefined}
        type="event"
      />

      {/* Hero Section */}
      <HeroSection
        title={eventTitle}
        date={eventDate}
        location={eventLocation}
        ctaLink={registerLink}
        eventCode={event.eventCode}
        themeImageUrl={event.themeImageUrl || undefined}
        countdownTimer={eventDateObj ? <CountdownTimer eventDate={eventDateObj} /> : undefined}
        spotsRemaining={event.spotsRemaining}
      />

      {/* Event Description Section (Story 10.23) — shown below hero, hidden when null/empty */}
      <div className="container mx-auto px-4">
        <EventDescriptionSection description={event?.description} />
      </div>

      {/* Event Content */}
      <div className="container mx-auto px-4">
        {/* Registration Status Banner (Story 10.10) — AC2, AC4
            Shown below hero when authenticated user has a registration for the current event.
            Only for events in AGENDA_PUBLISHED / AGENDA_FINALIZED / EVENT_LIVE states. */}
        {!isArchiveMode && (
          <RegistrationStatusBanner
            status={myRegistration?.status ?? null}
            eventCode={event.eventCode ?? ''}
            isLoading={isRegistrationLoading}
            waitlistPosition={myRegistration?.waitlistPosition}
          />
        )}

        {/* Story 10.12 (AC9): "Cancel your registration" link for anonymous and authenticated registrants.
            Shown when event is in a state where registrations can be cancelled. */}
        {!isArchiveMode &&
          event.workflowState &&
          DEREGISTRATION_WORKFLOW_STATES.includes(event.workflowState) && (
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

        {/* Back Button - Only shown in archive mode (Story 4.2) */}
        {isArchiveMode && backToArchiveUrl && (
          <Link
            to={backToArchiveUrl}
            className="inline-block mt-8 mb-4 text-blue-400 hover:text-blue-300 transition-colors"
          >
            {tCommon('archive.detail.backToArchive')}
          </Link>
        )}

        {/* Event Logistics - Hidden in archive mode (Story 4.2) */}
        {!isArchiveMode && (
          <div className="mt-12 bg-zinc-900/50 rounded-lg border border-zinc-800 p-8">
            <h3 className="text-xl font-light text-zinc-100 mb-6">{t('public.logistics.title')}</h3>
            <EventLogistics event={event} />
            {/* AC7 (Story 10.11): Capacity badge — only when capacity is configured */}
            <div className="mt-4">
              <CapacityIndicator
                registrationCapacity={event.registrationCapacity}
                spotsRemaining={event.spotsRemaining}
                waitlistCount={event.waitlistCount}
              />
            </div>
          </div>
        )}

        {/* Event Program Timeline (Timetable) - Only show when agenda is published */}
        {showTimetable && event.sessions && event.sessions.length > 0 && (
          <EventProgram
            sessions={event.sessions}
            isArchived={event.workflowState === 'ARCHIVED'}
            eventCode={event.eventCode}
          />
        )}

        {/* Session Cards (List View) - Only show when speakers published, NOT when agenda published */}
        {showSessionList && event.sessions && event.sessions.length > 0 && (
          <SessionCards sessions={event.sessions} />
        )}

        {/* Speaker Grid - Show when speakers or agenda is published */}
        {showSpeakersAndSessions && event.sessions && event.sessions.length > 0 && (
          <SpeakerGrid sessions={event.sessions} />
        )}

        {/* Venue Map - Hidden in archive mode (Story 4.2) */}
        {showVenue && event.venueName && event.venueAddress && (
          <VenueMap
            venue={{
              id: event.eventCode,
              name: event.venueName,
              address: event.venueAddress,
              capacity: event.venueCapacity,
            }}
          />
        )}

        {/* Social Sharing */}
        <SocialSharing eventTitle={eventTitle} eventUrl={eventUrl} />

        {/* Upcoming Events Section — hidden in archive mode */}
        {!isArchiveMode && <UpcomingEventsSection currentEventCode={event.eventCode} />}

        {/* Event Photos Marquee — archive detail only (Story 10.21 — AC7) */}
        {isArchiveMode && eventPhotos && eventPhotos.length > 0 && (
          <section className="relative left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] w-screen overflow-hidden py-6 mt-12">
            <InfiniteMarquee direction="left" speed="slow">
              {eventPhotos.map((photo) => (
                <img
                  key={photo.id}
                  src={photo.displayUrl}
                  alt={photo.filename || 'BATbern event photo'}
                  className="rounded-lg object-cover h-48 w-64 shrink-0"
                />
              ))}
            </InfiniteMarquee>
          </section>
        )}

        {/* Testimonials / recent photos marquee — homepage & event pages only */}
        {!isArchiveMode && (
          <div className="mt-16 pb-12">
            <TestimonialSection />
          </div>
        )}

        {/* Newsletter Subscribe Widget — footer */}
        <div className="border-t pt-4 pb-8">
          <NewsletterSubscribeWidget />
        </div>
      </div>
    </PublicLayout>
  );
};

export default HomePage;
