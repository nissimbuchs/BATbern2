/**
 * SessionSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #12: Speaker photo, full name, company, session title.
 * Multi-speaker sessions show side-by-side cards.
 */
import { type JSX } from 'react';

import type { PresentationSession } from '@/services/presentationService';
import { SpeakerCard } from '../SpeakerCard';
import { TwoSpeakerCard } from '../TwoSpeakerCard';
import type { components } from '@/types/generated/events-api.types';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface SessionSlideProps {
  session: PresentationSession;
}

export function SessionSlide({ session }: SessionSlideProps): JSX.Element {
  const speakers: SessionSpeaker[] = (session.speakers as SessionSpeaker[] | undefined) ?? [];

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
        padding: '2.5vw',
        color: '#ffffff',
        textAlign: 'center',
      }}
    >
      {/* Session title */}
      <h1
        style={{
          fontSize: '2.917vw',
          fontWeight: 700,
          maxWidth: '46.875vw',
          lineHeight: 1.2,
          marginBottom: '2.5vw',
        }}
      >
        {session.title}
      </h1>

      {/* Speaker(s) */}
      {speakers.length === 0 ? null : speakers.length === 1 ? (
        <SpeakerCard speaker={speakers[0]} />
      ) : (
        <TwoSpeakerCard speakers={speakers} />
      )}
    </div>
  );
}
