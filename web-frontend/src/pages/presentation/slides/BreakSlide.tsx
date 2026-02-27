/**
 * BreakSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: BATbern "~" spinner, coffee cup + steam, floating beans (ACs #11–13)
 *
 * AC #25: "Pause" heading + "Weiter um HH:MM"
 * AC #11: BATbern "~" spinner (CSS @keyframes rotation, 3s loop)
 * AC #12: Animated coffee cup with rising steam lines
 * AC #13: 8–12 floating coffee beans with staggered upward float + gentle sway
 *
 * ACs: #11–13, #25, #29
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import type { PresentationSession } from '@/services/presentationService';
import animStyles from '../presentation-animations.module.css';

interface BreakSlideProps {
  firstPostBreakSession?: PresentationSession | null;
}

// Fixed bean positions to avoid random layout shifts on re-render
const BEAN_CONFIGS = [
  { left: '12%', delay: '0s', duration: '3.2s' },
  { left: '22%', delay: '0.6s', duration: '4.1s' },
  { left: '34%', delay: '1.1s', duration: '3.7s' },
  { left: '46%', delay: '0.3s', duration: '4.5s' },
  { left: '56%', delay: '1.8s', duration: '3.4s' },
  { left: '66%', delay: '0.9s', duration: '4.8s' },
  { left: '74%', delay: '2.1s', duration: '3.6s' },
  { left: '83%', delay: '0.5s', duration: '4.2s' },
  { left: '28%', delay: '1.5s', duration: '5.0s' },
  { left: '62%', delay: '2.6s', duration: '3.9s' },
];

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
        overflow: 'hidden',
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

      {/* Floating coffee beans (AC #13) */}
      {BEAN_CONFIGS.map((bean, i) => (
        <span
          key={i}
          className={animStyles.coffeeBean}
          style={{
            left: bean.left,
            bottom: '10%',
            animationDelay: bean.delay,
            animationDuration: bean.duration,
          }}
        >
          ☕
        </span>
      ))}

      {/* BATbern "~" spinner (AC #11) */}
      <div className={animStyles.spinner} style={{ marginBottom: '1.5rem' }} aria-hidden="true">
        ~
      </div>

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

      {/* Animated coffee cup with steam (AC #12) */}
      <div
        style={{
          marginTop: '3rem',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '0.2rem',
        }}
      >
        {/* Steam lines */}
        <div style={{ display: 'flex', gap: '0.5rem', height: '24px', alignItems: 'flex-end' }}>
          <span
            className={animStyles.steamLine}
            style={{ fontSize: '1rem', color: 'rgba(255,255,255,0.4)' }}
          >
            〜
          </span>
          <span
            className={animStyles.steamLine}
            style={{ fontSize: '1rem', color: 'rgba(255,255,255,0.4)' }}
          >
            〜
          </span>
          <span
            className={animStyles.steamLine}
            style={{ fontSize: '1rem', color: 'rgba(255,255,255,0.4)' }}
          >
            〜
          </span>
        </div>
        {/* Coffee cup */}
        <div style={{ fontSize: '3rem' }}>☕</div>
      </div>
    </div>
  );
}
