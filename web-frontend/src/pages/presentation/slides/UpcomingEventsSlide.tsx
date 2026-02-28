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
        height: '100vh',
        color: '#ffffff',
        textAlign: 'center',
        padding: '3rem',
      }}
    >
      <h2
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          marginBottom: '3rem',
          color: '#4f9cf9',
        }}
      >
        Nächste BATbern Events
      </h2>

      <div
        style={{
          display: 'flex',
          gap: '2rem',
          justifyContent: 'center',
          flexWrap: 'wrap',
          maxWidth: '1200px',
        }}
      >
        {upcoming.length === 0 ? (
          <p style={{ fontSize: '1.5rem', color: 'rgba(255,255,255,0.6)' }}>
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
        borderRadius: '12px',
        padding: '2rem',
        minWidth: '280px',
        maxWidth: '340px',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          fontSize: '2rem',
          fontWeight: 800,
          color: '#4f9cf9',
          marginBottom: '0.75rem',
        }}
      >
        #{event.eventNumber}
      </div>

      <div
        style={{
          fontSize: '1.25rem',
          color: 'rgba(255,255,255,0.7)',
          marginBottom: '0.5rem',
        }}
      >
        {dateStr}
      </div>

      <div
        style={{
          fontSize: '1.1rem',
          color: 'rgba(255,255,255,0.5)',
          fontStyle: titleDisplay === 'TBD' ? 'italic' : 'normal',
        }}
      >
        {titleDisplay}
      </div>
    </div>
  );
}
