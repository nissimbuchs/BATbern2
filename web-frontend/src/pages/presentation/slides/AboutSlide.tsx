/**
 * AboutSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #8: aboutText + partnerCount from presentation settings
 */
import { type JSX } from 'react';

interface AboutSlideProps {
  aboutText: string;
  partnerCount: number;
}

export function AboutSlide({ aboutText, partnerCount }: AboutSlideProps): JSX.Element {
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
        maxWidth: '46.875vw',
        margin: '0 auto',
      }}
    >
      <h2
        style={{
          fontSize: '2.5vw',
          fontWeight: 700,
          marginBottom: '2.083vw',
          color: '#4f9cf9',
        }}
      >
        Über BATbern
      </h2>

      <p
        style={{
          fontSize: '1.458vw',
          lineHeight: 1.6,
          color: 'rgba(255,255,255,0.9)',
          marginBottom: '2.5vw',
        }}
      >
        {aboutText}
      </p>

      <div
        style={{
          fontSize: '1.25vw',
          color: 'rgba(255,255,255,0.65)',
        }}
      >
        <strong style={{ fontSize: '2.5vw', color: '#4f9cf9' }}>{partnerCount}</strong>{' '}
        Partner-Unternehmen
      </div>
    </div>
  );
}
