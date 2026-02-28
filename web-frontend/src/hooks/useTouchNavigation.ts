/**
 * useTouchNavigation Hook
 * Story 10.8 mobile enhancement: swipe left/right to navigate sections.
 *
 * Attaches passive window-level touch listeners so it never blocks scroll
 * on other pages. The hook fires:
 *   - Swipe left  (dx < -threshold, |dx| > |dy|) → onNext
 *   - Swipe right (dx > +threshold, |dx| > |dy|) → onPrev
 *   - Any qualifying swipe while blank is active   → onToggleBlank (dismiss break)
 *
 * Tap detection (movement < TAP_MAX_MOVE) is intentionally NOT handled here —
 * that belongs to the TouchZones component which owns tap zones for left/right/bottom.
 */

import { useEffect, useRef } from 'react';

/** Minimum horizontal distance (px) that counts as a swipe. */
const SWIPE_THRESHOLD = 50;

interface UseTouchNavigationOptions {
  isBlankActive: boolean;
  onNext: () => void;
  onPrev: () => void;
  onToggleBlank: () => void;
}

export function useTouchNavigation({
  isBlankActive,
  onNext,
  onPrev,
  onToggleBlank,
}: UseTouchNavigationOptions): void {
  // Stable refs so the listener never needs to be re-registered when callbacks change
  const isBlankRef = useRef(isBlankActive);
  const onNextRef = useRef(onNext);
  const onPrevRef = useRef(onPrev);
  const onToggleBlankRef = useRef(onToggleBlank);

  isBlankRef.current = isBlankActive;
  onNextRef.current = onNext;
  onPrevRef.current = onPrev;
  onToggleBlankRef.current = onToggleBlank;

  useEffect(() => {
    let startX = 0;
    let startY = 0;

    const handleTouchStart = (e: TouchEvent) => {
      const t = e.touches[0];
      startX = t.clientX;
      startY = t.clientY;
    };

    const handleTouchEnd = (e: TouchEvent) => {
      const t = e.changedTouches[0];
      const dx = t.clientX - startX;
      const dy = t.clientY - startY;

      // Ignore taps and near-taps (too little movement)
      if (Math.abs(dx) < SWIPE_THRESHOLD) return;

      // Ignore swipes that are not strictly more horizontal than vertical
      if (Math.abs(dy) >= Math.abs(dx)) return;

      if (isBlankRef.current) {
        onToggleBlankRef.current();
        return;
      }

      if (dx < 0) {
        onNextRef.current(); // swipe left → advance
      } else {
        onPrevRef.current(); // swipe right → go back
      }
    };

    window.addEventListener('touchstart', handleTouchStart, { passive: true });
    window.addEventListener('touchend', handleTouchEnd, { passive: true });

    return () => {
      window.removeEventListener('touchstart', handleTouchStart);
      window.removeEventListener('touchend', handleTouchEnd);
    };
  }, []); // empty: listeners are stable; refs carry current values
}
