/**
 * HomePage Component (Story 4.1.3, 4.1.4)
 * BATbern-public design with dynamic event data from backend
 * Displays current event with hero, logistics, countdown, speakers, sessions, venue, and social sharing
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { HeroSection } from '@/components/public/Hero/HeroSection';
import { EventLogistics } from '@/components/public/Event/EventLogistics';
import { CountdownTimer } from '@/components/public/Event/CountdownTimer';
import { SpeakerGrid } from '@/components/public/Event/SpeakerGrid';
import { SessionCards } from '@/components/public/Event/SessionCards';
import { EventProgram } from '@/components/public/Event/EventProgram';
import { VenueMap } from '@/components/public/Event/VenueMap';
import { SocialSharing } from '@/components/public/Event/SocialSharing';
import { OpenGraphTags } from '@/components/SEO/OpenGraphTags';
// import { TopicBadges } from '@/components/public/Event/TopicBadges'; // TODO: Uncomment when topics are added
import { TestimonialSection } from '@/components/public/Testimonials/TestimonialSection';
import { useCurrentEvent } from '@/hooks/useCurrentEvent';
import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const HomePage = () => {
  const { t } = useTranslation('events');
  const { data: event, isLoading, error } = useCurrentEvent();

  // Loading state
  if (isLoading) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-24 flex justify-center">
          <Loader2 className="h-12 w-12 animate-spin text-blue-400" />
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
          <p className="mt-4 text-zinc-400">
            {t('public.errors.checkBackLater')}
          </p>
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

  return (
    <PublicLayout>
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
        countdownTimer={eventDateObj ? <CountdownTimer eventDate={eventDateObj} /> : undefined}
      />

      {/* Event Content */}
      <div className="container mx-auto px-4">
        {/* Event Logistics */}
        <div className="mt-12 bg-zinc-900/50 rounded-lg border border-zinc-800 p-8">
          <h3 className="text-xl font-light text-zinc-100 mb-6">{t('public.logistics.title')}</h3>
          <EventLogistics event={event} />
        </div>

        {/* Event Topics - TODO: Add when topics are implemented in backend */}
        {/* {event.topics && event.topics.length > 0 && (
          <div className="mt-12">
            <h3 className="text-xl font-light text-zinc-100 mb-4">Topics</h3>
            <TopicBadges topics={event.topics} />
          </div>
        )} */}

        {/* Event Program Timeline */}
        {event.sessions && event.sessions.length > 0 && (
          <EventProgram sessions={event.sessions} />
        )}

        {/* Speaker Grid */}
        {event.sessions && event.sessions.length > 0 && (
          <SpeakerGrid sessions={event.sessions} />
        )}

        {/* Session Cards */}
        {event.sessions && event.sessions.length > 0 && (
          <SessionCards sessions={event.sessions} />
        )}

        {/* Venue Map */}
        {event.venueName && event.venueAddress && (
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

        {/* Testimonials Section */}
        <div className="mt-16 pb-12">
          <TestimonialSection />
        </div>
      </div>
    </PublicLayout>
  );
};

export default HomePage;
