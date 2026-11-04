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
        maxWidth: '80vw',
        margin: '0 auto',
      }}
    >
      <h2
        style={{
          fontSize: '4.5vw',
          fontWeight: 700,
          marginBottom: '3vw',
          color: '#4f9cf9',
        }}
      >
        Über BATbern
      </h2>

      <p
        style={{
          fontSize: '2.7vw',
          lineHeight: 1.5,
          color: '#ffffff',
          marginBottom: '2.5vw',
        }}
      >
        {aboutText}
      </p>

      <div
        style={{
          fontSize: '2.4vw',
          color: '#ffffff',
        }}
      >
        <strong style={{ fontSize: '4.5vw', color: '#4f9cf9' }}>{partnerCount}</strong>{' '}
        Partner-Unternehmen
      </div>
    </div>
  );
}
