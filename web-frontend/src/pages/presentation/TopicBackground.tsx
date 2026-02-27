/**
 * TopicBackground
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Full-bleed static background image with dark overlay.
 * Ken Burns zoom animation is deferred to Story 10.8b.
 *
 * ACs: #33–36
 */
import { type JSX } from 'react';

interface TopicBackgroundProps {
  imageUrl?: string | null;
}

export function TopicBackground({ imageUrl }: TopicBackgroundProps): JSX.Element {
  const src = imageUrl || '/images/batbern-default-bg.jpg';

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        overflow: 'hidden',
      }}
    >
      {/* Background image — no animation in 10.8a */}
      <img
        src={src}
        alt=""
        aria-hidden="true"
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          objectFit: 'cover',
        }}
      />
      {/* Dark overlay for legibility (AC #34) */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.65)',
        }}
      />
    </div>
  );
}
