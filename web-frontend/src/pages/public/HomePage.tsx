/**
 * HomePage Component (Story 4.1.3)
 * BATbern-public design with dynamic event data from backend
 * Displays current event with hero, logistics, countdown, and topics
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { HeroSection } from '@/components/public/Hero/HeroSection';
import { EventLogistics } from '@/components/public/Event/EventLogistics';
import { CountdownTimer } from '@/components/public/Event/CountdownTimer';
// import { TopicBadges } from '@/components/public/Event/TopicBadges'; // TODO: Uncomment when topics are added
import { TestimonialSection } from '@/components/public/Testimonials/TestimonialSection';
import { useCurrentEvent } from '@/hooks/useCurrentEvent';
import { Loader2 } from 'lucide-react';

const HomePage = () => {
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
            {error ? 'Failed to load event' : 'No current event available'}
          </h2>
          <p className="mt-4 text-zinc-400">
            Please check back later for upcoming events.
          </p>
        </div>
      </PublicLayout>
    );
  }

  // Fallback values
  const eventTitle = event.title || 'Coming soon...';
  const registerLink = event.eventCode ? `/register/${event.eventCode}` : '/register';
  const eventDate = event.date;
  const eventLocation = event.venueName;
  const eventDateObj = eventDate ? new Date(eventDate) : null;

  return (
    <PublicLayout>
      {/* Hero Section */}
      <HeroSection
        title={eventTitle}
        date={eventDate}
        location={eventLocation}
        ctaLink={registerLink}
        countdownTimer={eventDateObj ? <CountdownTimer eventDate={eventDateObj} /> : undefined}
      />

      {/* Event Logistics */}
      <div className="container mx-auto px-4">
        <div className="mt-12 bg-zinc-900/50 rounded-lg border border-zinc-800 p-8">
          <h3 className="text-xl font-light text-zinc-100 mb-6">Event Details</h3>
          <EventLogistics event={event} />
        </div>

        {/* Event Topics - TODO: Add when topics are implemented in backend */}
        {/* {event.topics && event.topics.length > 0 && (
          <div className="mt-12">
            <h3 className="text-xl font-light text-zinc-100 mb-4">Topics</h3>
            <TopicBadges topics={event.topics} />
          </div>
        )} */}

        {/* Testimonials Section */}
        <div className="mt-16 pb-12">
          <TestimonialSection />
        </div>
      </div>
    </PublicLayout>
  );
};

export default HomePage;
