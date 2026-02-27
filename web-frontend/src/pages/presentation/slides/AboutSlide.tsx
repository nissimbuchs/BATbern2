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
        height: '100vh',
        textAlign: 'center',
        padding: '4rem',
        color: '#ffffff',
        maxWidth: '900px',
        margin: '0 auto',
      }}
    >
      <h2
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          marginBottom: '2.5rem',
          color: '#4f9cf9',
        }}
      >
        Über BATbern
      </h2>

      <p
        style={{
          fontSize: '1.75rem',
          lineHeight: 1.6,
          color: 'rgba(255,255,255,0.9)',
          marginBottom: '3rem',
        }}
      >
        {aboutText}
      </p>

      <div
        style={{
          fontSize: '1.5rem',
          color: 'rgba(255,255,255,0.65)',
        }}
      >
        <strong style={{ fontSize: '3rem', color: '#4f9cf9' }}>{partnerCount}</strong>{' '}
        Partner-Unternehmen
      </div>
    </div>
  );
}
