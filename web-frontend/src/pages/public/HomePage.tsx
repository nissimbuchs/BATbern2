/**
 * HomePage Component
 * BATbern-public design with dynamic event data from backend
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { HeroSection } from '@/components/public/Hero/HeroSection';
import { useCurrentEvent } from '@/hooks/useCurrentEvent';

const HomePage = () => {
  const { data: event } = useCurrentEvent();

  // Fallback title if no event is available
  const eventTitle = event?.title || 'Zero Trust Journey';
  const registerLink = event ? `/register/${event.eventCode}` : '/register';
  const eventDate = event?.date;
  const eventLocation = event?.venueName;

  return (
    <PublicLayout>
      <HeroSection
        title={eventTitle}
        date={eventDate}
        location={eventLocation}
        ctaLink={registerLink}
      />
    </PublicLayout>
  );
};

export default HomePage;
