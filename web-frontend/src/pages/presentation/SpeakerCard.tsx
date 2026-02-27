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
import { useCompany } from '@/hooks/useCompany/useCompany';

type SessionSpeaker = components['schemas']['SessionSpeaker'];

interface SpeakerCardProps {
  speaker: SessionSpeaker;
}

export function SpeakerCard({ speaker }: SpeakerCardProps): JSX.Element {
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

      <div
        style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}
      >
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
        {logoUrl && (
          <img
            src={logoUrl}
            alt={speaker.company ?? ''}
            style={{
              height: '48px',
              maxWidth: '180px',
              objectFit: 'contain',
            }}
          />
        )}
      </div>
    </div>
  );
}
