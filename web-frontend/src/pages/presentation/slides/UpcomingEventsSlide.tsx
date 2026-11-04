/**
 * UpcomingEventsSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #15: Next 3 future BATbern events with date and topic.
 * Shows placeholder if topic TBD.
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import type { components } from '@/types/generated/events-core-api.types';

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
          fontSize: '4.2vw',
          fontWeight: 700,
          marginBottom: '3.5vw',
          color: '#4f9cf9',
        }}
      >
        Nächste BATbern Events
      </h2>

      <div
        style={{
          display: 'flex',
          gap: '3vw',
          justifyContent: 'center',
          alignItems: 'stretch',
          flexWrap: 'wrap',
          maxWidth: '94vw',
        }}
      >
        {upcoming.length === 0 ? (
          <p style={{ fontSize: '2.6vw', color: '#ffffff' }}>Keine weiteren Events geplant.</p>
        ) : (
          upcoming.map((event) => <EventCard key={event.eventCode} event={event} />)
        )}
      </div>
    </div>
  );
}

function EventCard({ event }: { event: Event }): JSX.Element {
  const dateStr = event.date ? format(new Date(event.date), 'dd. MMMM yyyy') : '—';
  const hasTitle = Boolean(event.title);
  const titleDisplay = event.title ?? 'Thema folgt';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        background: 'rgba(255,255,255,0.08)',
        border: '1px solid rgba(255,255,255,0.15)',
        borderRadius: '1.25vw',
        padding: '3.5vw 2.5vw',
        flex: '1 1 0',
        minWidth: '26vw',
        maxWidth: '31vw',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          fontSize: '4.2vw',
          fontWeight: 800,
          color: '#4f9cf9',
          marginBottom: '1.25vw',
          letterSpacing: '-0.01em',
        }}
      >
        #{event.eventCode}
      </div>

      <div
        style={{
          fontSize: '2.5vw',
          color: '#ffffff',
          marginBottom: '1.25vw',
        }}
      >
        {dateStr}
      </div>

      <div
        style={{
          fontSize: '2.8vw',
          fontWeight: 600,
          lineHeight: 1.3,
          color: '#ffffff',
          fontStyle: hasTitle ? 'normal' : 'italic',
        }}
      >
        {titleDisplay}
      </div>
    </div>
  );
}
