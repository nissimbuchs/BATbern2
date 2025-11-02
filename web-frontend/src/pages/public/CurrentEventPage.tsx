/**
 * CurrentEventPage Component
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { EventHero } from '@/components/public/Event/EventHero';
import { EventLogistics } from '@/components/public/Event/EventLogistics';
import { CountdownTimer } from '@/components/public/Event/CountdownTimer';
import { useCurrentEvent } from '@/hooks/useCurrentEvent';
import { Loader2 } from 'lucide-react';

const CurrentEventPage = () => {
  const { data: event, isLoading, error } = useCurrentEvent();

  if (isLoading) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-24 flex justify-center">
          <Loader2 className="h-12 w-12 animate-spin text-blue-400" />
        </div>
      </PublicLayout>
    );
  }

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

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12">
        {/* Countdown Timer */}
        <CountdownTimer eventDate={new Date(event.date)} />

        {/* Hero Section */}
        <EventHero event={event} />

        {/* Event Logistics */}
        <div className="mt-12">
          <EventLogistics event={event} />
        </div>

        {/* Topics - Coming in future story when topic model is implemented */}
      </div>
    </PublicLayout>
  );
};

export default CurrentEventPage;
