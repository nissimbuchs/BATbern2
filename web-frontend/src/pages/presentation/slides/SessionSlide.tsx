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
import type { components } from '@/types/generated/event-sessions-api.types';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface SessionSlideProps {
  session: PresentationSession;
}

/** Long titles and three speakers need room: the talk title must not push the speakers off. */
function titleFontSize(title: string, speakerCount: number): string {
  const base = title.length > 80 ? 3.3 : title.length > 50 ? 3.9 : 4.6;
  return `${speakerCount > 2 ? base - 0.4 : base}vw`;
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
        padding: '2.5vw 3vw',
        color: '#ffffff',
        textAlign: 'center',
      }}
    >
      {/* Session title */}
      <h1
        style={{
          fontSize: titleFontSize(session.title ?? '', speakers.length),
          fontWeight: 700,
          maxWidth: '70vw',
          lineHeight: 1.15,
          margin: '0 0 3.5vw',
        }}
      >
        {session.title}
      </h1>

      {/* Speaker(s) */}
      {speakers.length === 0 ? null : speakers.length === 1 ? (
        <SpeakerCard speaker={speakers[0]} count={1} />
      ) : (
        <TwoSpeakerCard speakers={speakers} />
      )}
    </div>
  );
}
