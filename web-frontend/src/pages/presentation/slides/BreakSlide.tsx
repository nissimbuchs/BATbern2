/**
 * BreakSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #25: "Pause" heading centered in BATbern blue + "Weiter um HH:MM"
 * derived from firstPostBreakSession.startTime via date-fns format().
 * Additional dark overlay rgba(0,0,0,0.2) on top of background.
 * No coffee cup, steam, or bean animations — added in Story 10.8b.
 *
 * ACs: #25, #29
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import type { PresentationSession } from '@/services/presentationService';

interface BreakSlideProps {
  firstPostBreakSession?: PresentationSession | null;
}

export function BreakSlide({ firstPostBreakSession }: BreakSlideProps): JSX.Element {
  const resumeTime = firstPostBreakSession?.startTime
    ? format(new Date(firstPostBreakSession.startTime), 'HH:mm')
    : null;

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
        color: '#ffffff',
        textAlign: 'center',
      }}
    >
      {/* Additional heavy overlay for break feel */}
      <div
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.2)',
          zIndex: -1,
        }}
      />

      <h1
        style={{
          fontSize: '6rem',
          fontWeight: 800,
          color: '#4f9cf9',
          margin: 0,
          letterSpacing: '-0.02em',
        }}
      >
        Pause
      </h1>

      {resumeTime && (
        <p
          style={{
            fontSize: '2.5rem',
            color: 'rgba(255,255,255,0.8)',
            marginTop: '2rem',
            fontWeight: 400,
          }}
        >
          Weiter um {resumeTime}
        </p>
      )}
    </div>
  );
}
