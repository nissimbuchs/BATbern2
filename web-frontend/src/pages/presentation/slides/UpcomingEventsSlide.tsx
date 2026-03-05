/**
 * UpcomingEventsSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #15: Next 3 future BATbern events with date and topic.
 * Shows placeholder if topic TBD.
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import type { components } from '@/types/generated/events-api.types';

type Event = components['schemas']['Event'];

interface UpcomingEventsSlideProps {
  events: Event[];
}

export function UpcomingEventsSlide({ events }: UpcomingEventsSlideProps): JSX.Element {
  const upcoming = events.slice(0, 3);

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
        color: '#ffffff',
        textAlign: 'center',
        padding: '2.5vw',
      }}
    >
      <h2
        style={{
          fontSize: '2.5vw',
          fontWeight: 700,
          marginBottom: '2.5vw',
          color: '#4f9cf9',
        }}
      >
        Nächste BATbern Events
      </h2>

      <div
        style={{
          display: 'flex',
          gap: '1.667vw',
          justifyContent: 'center',
          flexWrap: 'wrap',
          maxWidth: '62.5vw',
        }}
      >
        {upcoming.length === 0 ? (
          <p style={{ fontSize: '1.25vw', color: 'rgba(255,255,255,0.6)' }}>
            Keine weiteren Events geplant.
          </p>
        ) : (
          upcoming.map((event) => <EventCard key={event.eventCode} event={event} />)
        )}
      </div>
    </div>
  );
}

function EventCard({ event }: { event: Event }): JSX.Element {
  const dateStr = event.date ? format(new Date(event.date), 'dd. MMMM yyyy') : '—';
  const titleDisplay = event.title ?? 'TBD';

  return (
    <div
      style={{
        background: 'rgba(255,255,255,0.08)',
        border: '1px solid rgba(255,255,255,0.15)',
        borderRadius: '0.625vw',
        padding: '1.667vw',
        minWidth: '14.583vw',
        maxWidth: '17.708vw',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          fontSize: '1.667vw',
          fontWeight: 800,
          color: '#4f9cf9',
          marginBottom: '0.625vw',
        }}
      >
        #{event.eventNumber}
      </div>

      <div
        style={{
          fontSize: '1.042vw',
          color: 'rgba(255,255,255,0.7)',
          marginBottom: '0.417vw',
        }}
      >
        {dateStr}
      </div>

      <div
        style={{
          fontSize: '0.917vw',
          color: 'rgba(255,255,255,0.5)',
          fontStyle: titleDisplay === 'TBD' ? 'italic' : 'normal',
        }}
      >
        {titleDisplay}
      </div>
    </div>
  );
}
