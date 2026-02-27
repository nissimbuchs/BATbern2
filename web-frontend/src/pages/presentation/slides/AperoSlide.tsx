/**
 * AperoSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #16: Closing visual with BATbern "~" text centered.
 * No spinner animation — added in Story 10.8b.
 */
import { type JSX } from 'react';

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
        height: '100vh',
        color: '#ffffff',
        textAlign: 'center',
        padding: '3rem',
      }}
    >
      {/* BATbern tilde symbol — closing visual */}
      <div
        style={{
          fontSize: '10rem',
          fontWeight: 800,
          color: '#4f9cf9',
          lineHeight: 1,
          marginBottom: '2rem',
          userSelect: 'none',
        }}
      >
        ~
      </div>

      <div
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          color: 'rgba(255,255,255,0.9)',
          marginBottom: '1rem',
        }}
      >
        Apéro
      </div>

      <div
        style={{
          fontSize: '1.5rem',
          color: 'rgba(255,255,255,0.55)',
        }}
      >
        Vielen Dank für euren Besuch!
      </div>
    </div>
  );
}
