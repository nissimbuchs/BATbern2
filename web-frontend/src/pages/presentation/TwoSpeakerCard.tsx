/**
 * TwoSpeakerCard
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Side-by-side multi-speaker layout for session slides.
 *
 * AC #12
 */
import { type JSX } from 'react';

import type { components } from '@/types/generated/events-api.types';
import { SpeakerCard } from './SpeakerCard';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface TwoSpeakerCardProps {
  speakers: SessionSpeaker[];
}

export function TwoSpeakerCard({ speakers }: TwoSpeakerCardProps): JSX.Element {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'flex-start',
        gap: '6rem',
      }}
    >
      {speakers.map((speaker) => (
        <SpeakerCard key={speaker.username ?? speaker.firstName} speaker={speaker} />
      ))}
    </div>
  );
}
