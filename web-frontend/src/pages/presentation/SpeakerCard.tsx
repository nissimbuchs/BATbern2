/**
 * SpeakerCard
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Single speaker card for session slides.
 *
 * AC #12
 */
import { type JSX } from 'react';

import type { components } from '@/types/generated/events-api.types';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface SpeakerCardProps {
  speaker: SessionSpeaker;
}

export function SpeakerCard({ speaker }: SpeakerCardProps): JSX.Element {
  const fullName =
    [speaker.firstName, speaker.lastName].filter(Boolean).join(' ') || speaker.username || '';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '1rem',
        textAlign: 'center',
      }}
    >
      {/* Speaker photo */}
      <div
        style={{
          width: '160px',
          height: '160px',
          borderRadius: '50%',
          overflow: 'hidden',
          border: '3px solid rgba(255,255,255,0.3)',
          flexShrink: 0,
        }}
      >
        {speaker.profilePictureUrl ? (
          <img
            src={speaker.profilePictureUrl}
            alt={fullName}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <div
            style={{
              width: '100%',
              height: '100%',
              background: 'rgba(255,255,255,0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '3rem',
              color: 'rgba(255,255,255,0.5)',
            }}
          >
            {fullName.charAt(0).toUpperCase()}
          </div>
        )}
      </div>

      <div>
        <p
          style={{
            margin: 0,
            fontSize: '2rem',
            fontWeight: 600,
            color: '#ffffff',
          }}
        >
          {fullName}
        </p>
        {speaker.company && (
          <p
            style={{
              margin: '0.25rem 0 0',
              fontSize: '1.25rem',
              color: 'rgba(255,255,255,0.7)',
            }}
          >
            {speaker.company}
          </p>
        )}
      </div>
    </div>
  );
}
