/**
 * SectionDots
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Subtle dot-per-section progress indicator at bottom center.
 */
import { type JSX } from 'react';

interface SectionDotsProps {
  count: number;
  current: number;
}

export function SectionDots({ count, current }: SectionDotsProps): JSX.Element {
  return (
    <div
      style={{
        position: 'fixed',
        bottom: '1.25vw',
        left: '50%',
        transform: 'translateX(-50%)',
        display: 'flex',
        gap: '0.333vw',
        zIndex: 10,
      }}
    >
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          style={{
            width: i === current ? '0.5vw' : '0.333vw',
            height: i === current ? '0.5vw' : '0.333vw',
            borderRadius: '50%',
            backgroundColor: i === current ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.3)',
            transition: 'all 0.2s',
          }}
        />
      ))}
    </div>
  );
}
