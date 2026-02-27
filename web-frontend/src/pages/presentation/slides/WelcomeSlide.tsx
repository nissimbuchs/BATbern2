/**
 * WelcomeSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #7: BATbern logo, event hashtag, topic title, date, venue name
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import type { PresentationEventDetail } from '@/services/presentationService';

interface WelcomeSlideProps {
  event: PresentationEventDetail;
}

export function WelcomeSlide({ event }: WelcomeSlideProps): JSX.Element {
  const hashtag = `#BATbern${event.eventNumber}`;
  // Use event.title as the main presentation title (the event's own name, e.g. "Zero Trust Journey").
  // event.topic?.name is the backlog categorisation — not the display title.
  const eventTitle = event.title;
  const dateStr = event.date ? format(new Date(event.date), 'dd. MMMM yyyy') : '';
  const venueName = event.venueName ?? event.venue?.name ?? '';

  return (
    <div
      style={{
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        textAlign: 'center',
        padding: '2rem',
        color: '#ffffff',
      }}
    >
      {/* BATbern animated logo */}
      <BATbernLoader size={180} speed="slow" />

      {/* BATbern wordmark */}
      <div
        style={{
          fontSize: '5rem',
          fontWeight: 800,
          letterSpacing: '-0.02em',
          color: '#4f9cf9',
          marginTop: '1.5rem',
          marginBottom: '0.5rem',
        }}
      >
        BAT<span style={{ color: 'rgba(255,255,255,0.9)' }}>bern</span>
      </div>

      {/* Hashtag */}
      <div
        style={{
          fontSize: '2rem',
          color: 'rgba(255,255,255,0.7)',
          marginBottom: '2rem',
        }}
      >
        {hashtag}
      </div>

      {/* Event title */}
      {eventTitle && (
        <h1
          style={{
            fontSize: '4.5rem',
            fontWeight: 700,
            margin: '0 0 2rem',
            maxWidth: '900px',
            lineHeight: 1.15,
          }}
        >
          {eventTitle}
        </h1>
      )}

      {/* Date and venue */}
      <div
        style={{
          fontSize: '1.75rem',
          color: 'rgba(255,255,255,0.75)',
        }}
      >
        {dateStr}
        {venueName && (
          <>
            {' · '}
            {venueName}
          </>
        )}
      </div>
    </div>
  );
}
