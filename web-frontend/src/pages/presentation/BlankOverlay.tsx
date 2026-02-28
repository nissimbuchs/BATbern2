/**
 * BlankOverlay
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Framer Motion AnimatePresence fade in/out (AC #9)
 *
 * B-key overlay wrapping BreakSlide.
 * Fades in over 0.3s on B-press; fades out over 0.3s on second B-press.
 *
 * ACs: #9, #23–24, #29
 */

import { type JSX, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

interface BlankOverlayProps {
  isActive: boolean;
  children: ReactNode;
  /** Called when the overlay itself is tapped/clicked — allows touch-to-dismiss on mobile. */
  onDismiss?: () => void;
}

export function BlankOverlay({ isActive, children, onDismiss }: BlankOverlayProps): JSX.Element {
  return (
    <AnimatePresence>
      {isActive && (
        <motion.div
          key="blank-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 100,
            cursor: onDismiss ? 'pointer' : undefined,
          }}
          onClick={onDismiss}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
