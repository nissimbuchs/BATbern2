/**
 * TopicRevealSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #10: Topic name + topic image in visually dominant layout
 */
import { type JSX } from 'react';

import type { PresentationEventDetail } from '@/services/presentationService';

interface TopicRevealSlideProps {
  event: PresentationEventDetail;
}

export function TopicRevealSlide({ event }: TopicRevealSlideProps): JSX.Element {
  // Use event.title (the event's own name) as the display title, not the backlog category name
  const topicName = event.title;

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
        padding: '3.333vw',
        color: '#ffffff',
      }}
    >
      <div
        style={{
          fontSize: '2.2vw',
          color: '#ffffff',
          letterSpacing: '0.25em',
          textTransform: 'uppercase',
          marginBottom: '2.5vw',
        }}
      >
        Das heutige Thema
      </div>

      <h1
        style={{
          fontSize: '7.5vw',
          fontWeight: 800,
          lineHeight: 1.1,
          maxWidth: '88vw',
          textShadow: '0 2px 20px rgba(0,0,0,0.6)',
        }}
      >
        {topicName}
      </h1>
    </div>
  );
}
