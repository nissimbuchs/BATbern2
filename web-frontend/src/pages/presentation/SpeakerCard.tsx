/**
 * SpeakerCard
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Single speaker card for session slides.
 *
 * AC #12
 */
import { type JSX } from 'react';

import type { components } from '@/types/generated/event-sessions-api.types';
import { useCompany } from '@/hooks/useCompany/useCompany';
import { LogoBadge } from '@/components/shared/LogoBadge/LogoBadge';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface SpeakerCardProps {
  speaker: SessionSpeaker;
  /** Speakers on the slide: one card gets the most room, three still fit side by side. */
  count?: number;
}

/** vw sizes per speaker count, for the big screen (owner 2026-11-04: "use the estate"). */
const SIZES = {
  1: { photo: 15, name: 3.2, logo: 4.5, logoWidth: 16, width: 60 },
  2: { photo: 13, name: 2.8, logo: 4, logoWidth: 14, width: 34 },
  3: { photo: 10.5, name: 2.3, logo: 3.4, logoWidth: 12, width: 23 },
} as const;

export function SpeakerCard({ speaker, count = 1 }: SpeakerCardProps): JSX.Element {
  const size = SIZES[Math.min(Math.max(count, 1), 3) as 1 | 2 | 3];
  const fullName =
    [speaker.firstName, speaker.lastName].filter(Boolean).join(' ') || speaker.username || '';

  // Use pre-loaded logo when available; fall back to useCompany fetch by company name.
  const { data: company } = useCompany(speaker.companyLogoUrl ? '' : (speaker.company ?? ''), {
    expand: ['logo'],
  });
  const logoUrl = speaker.companyLogoUrl ?? company?.logo?.url;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '1.2vw',
        textAlign: 'center',
        maxWidth: `${size.width}vw`,
      }}
    >
      {/* Speaker photo */}
      <div
        style={{
          width: `${size.photo}vw`,
          height: `${size.photo}vw`,
          borderRadius: '50%',
          overflow: 'hidden',
          border: '0.2vw solid rgba(255,255,255,0.6)',
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
              fontSize: `${size.photo / 3}vw`,
              color: '#ffffff',
            }}
          >
            {fullName.charAt(0).toUpperCase()}
          </div>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.9vw' }}>
        <p
          style={{
            margin: 0,
            fontSize: `${size.name}vw`,
            fontWeight: 600,
            lineHeight: 1.15,
            color: '#ffffff',
          }}
        >
          {fullName}
        </p>
        {logoUrl && (
          // White chip behind the logo so transparent logos stay legible on the dark slide
          // background (Vanessa #791).
          <LogoBadge
            src={logoUrl}
            alt={speaker.companyDisplayName ?? speaker.company ?? ''}
            imgStyle={{ height: `${size.logo}vw`, maxWidth: `${size.logoWidth}vw` }}
            style={{ padding: '0.6vw 0.9vw', borderRadius: '0.6vw' }}
          />
        )}
      </div>
    </div>
  );
}
