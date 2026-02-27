/**
 * useKeyboardNavigation Hook
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Handles keyboard/remote navigation for the moderator presentation page.
 *
 * ACs: #2–6, #23–24
 */

import { useEffect, useCallback } from 'react';

interface UseKeyboardNavigationOptions {
  sectionCount: number;
  currentIndex: number;
  isBlankActive: boolean;
  onNext: () => void;
  onPrev: () => void;
  onToggleBlank: () => void;
}

export function useKeyboardNavigation({
  sectionCount,
  currentIndex,
  isBlankActive,
  onNext,
  onPrev,
  onToggleBlank,
}: UseKeyboardNavigationOptions): void {
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      // Guard: ignore key events from input/textarea elements
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
        return;
      }

      switch (e.key) {
        case 'ArrowRight':
        case 'ArrowDown':
        case 'PageDown':
        case ' ':
          // Prevent default scroll for these keys
          e.preventDefault();
          if (!isBlankActive && currentIndex < sectionCount - 1) {
            onNext();
          }
          break;

        case 'ArrowLeft':
        case 'ArrowUp':
        case 'PageUp':
          // Prevent default scroll for these keys
          e.preventDefault();
          if (!isBlankActive && currentIndex > 0) {
            onPrev();
          }
          break;

        case 'b':
        case 'B':
          onToggleBlank();
          break;

        case 'f':
        case 'F':
          // AC #5: F toggles fullscreen
          if (!document.fullscreenElement) {
            void document.documentElement.requestFullscreen();
          } else {
            void document.exitFullscreen();
          }
          break;

        case 'Escape':
          if (isBlankActive) {
            onToggleBlank();
          } else if (document.fullscreenElement) {
            // Escape exits fullscreen natively; no extra handling needed
          }
          break;

        default:
          break;
      }
    },
    [sectionCount, currentIndex, isBlankActive, onNext, onPrev, onToggleBlank]
  );

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);
}
