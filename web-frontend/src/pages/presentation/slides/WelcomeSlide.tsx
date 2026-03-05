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
import animStyles from '../presentation-animations.module.css';

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
        height: '100%',
        textAlign: 'center',
        padding: '2.083vw',
        color: '#ffffff',
      }}
    >
      {/* BATbern animated logo */}
      <div className={animStyles.loaderWrap} style={{ width: '9.375vw', height: '9.375vw' }}>
        <BATbernLoader size={180} speed="slow" />
      </div>

      {/* BATbern wordmark */}
      <div
        style={{
          fontSize: '4.167vw',
          fontWeight: 800,
          letterSpacing: '-0.02em',
          color: '#4f9cf9',
          marginTop: '1.25vw',
          marginBottom: '0.417vw',
        }}
      >
        BAT<span style={{ color: 'rgba(255,255,255,0.9)' }}>bern</span>
      </div>

      {/* Hashtag */}
      <div
        style={{
          fontSize: '1.667vw',
          color: 'rgba(255,255,255,0.7)',
          marginBottom: '1.667vw',
        }}
      >
        {hashtag}
      </div>

      {/* Event title */}
      {eventTitle && (
        <h1
          style={{
            fontSize: '3.75vw',
            fontWeight: 700,
            margin: `0 0 1.667vw`,
            maxWidth: '46.875vw',
            lineHeight: 1.15,
          }}
        >
          {eventTitle}
        </h1>
      )}

      {/* Date and venue */}
      <div
        style={{
          fontSize: '1.458vw',
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
