/**
 * AperoSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: BATbern "~" spinner reusing shared CSS @keyframes (AC #14)
 *
 * AC #16: Closing visual with BATbern "~" text centered.
 * AC #14: Spinner uses same CSS @keyframes as BreakSlide, 3s loop.
 */
import { type JSX } from 'react';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import animStyles from '../presentation-animations.module.css';

export function AperoSlide(): JSX.Element {
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
        color: '#ffffff',
        textAlign: 'center',
        padding: '2.5vw',
      }}
    >
      {/* BATbern spinner (AC #14) */}
      <div className={animStyles.loaderWrap} style={{ width: '9.375vw', height: '9.375vw', marginBottom: '2.083vw' }}>
        <BATbernLoader size={180} speed="slow" />
      </div>

      <div
        style={{
          fontSize: '5vw',
          fontWeight: 800,
          color: '#4f9cf9',
          marginBottom: '1.25vw',
          letterSpacing: '-0.02em',
        }}
      >
        Apéro
      </div>

      <div
        style={{
          fontSize: '1.667vw',
          color: 'rgba(255,255,255,0.7)',
        }}
      >
        Vielen Dank für euren Besuch!
      </div>
    </div>
  );
}
