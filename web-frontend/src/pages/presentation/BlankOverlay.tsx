/**
 * BlankOverlay
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * B-key overlay wrapping BreakSlide.
 * CSS opacity transition — no Framer Motion AnimatePresence (deferred to 10.8b).
 *
 * ACs: #23–24, #29
 */

import { type JSX, type ReactNode } from 'react';

interface BlankOverlayProps {
  isActive: boolean;
  children: ReactNode;
}

export function BlankOverlay({ isActive, children }: BlankOverlayProps): JSX.Element {
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 100,
        opacity: isActive ? 1 : 0,
        visibility: isActive ? 'visible' : 'hidden',
        transition: 'opacity 0.2s, visibility 0.2s',
      }}
    >
      {children}
    </div>
  );
}
