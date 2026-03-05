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
      <div className={animStyles.loaderWrap} style={{ width: '6.25vw', height: '6.25vw', marginBottom: '1.667vw' }}>
        <BATbernLoader size={120} speed="slow" />
      </div>

      <div
        style={{
          fontSize: '2.5vw',
          fontWeight: 700,
          color: 'rgba(255,255,255,0.9)',
          marginBottom: '0.833vw',
        }}
      >
        Apéro
      </div>

      <div
        style={{
          fontSize: '1.25vw',
          color: 'rgba(255,255,255,0.55)',
        }}
      >
        Vielen Dank für euren Besuch!
      </div>
    </div>
  );
}
