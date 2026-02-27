/**
 * TopicBackground
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Ken Burns zoom animation (AC #8)
 *
 * Full-bleed background image with dark overlay.
 * Ken Burns: scale 1.0 → 1.06 → 1.0, 30-second loop, continuous.
 *
 * ACs: #33–36, #8
 */
import { type JSX } from 'react';
import { motion } from 'framer-motion';

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
      {/* Background image — Ken Burns zoom (AC #8) */}
      <motion.img
        src={src}
        alt=""
        aria-hidden="true"
        animate={{ scale: [1.0, 1.06, 1.0] }}
        transition={{ duration: 30, repeat: Infinity, ease: 'easeInOut' }}
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
