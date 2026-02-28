/**
 * useTouchNavigation Hook Tests
 * Story 10.8 mobile enhancement: swipe navigation for touch/tablet devices.
 */

import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useTouchNavigation } from './useTouchNavigation';

function renderNav(overrides: Partial<Parameters<typeof useTouchNavigation>[0]> = {}) {
  const defaults = {
    isBlankActive: false,
    onNext: vi.fn(),
    onPrev: vi.fn(),
    onToggleBlank: vi.fn(),
    ...overrides,
  };
  return {
    ...renderHook(() => useTouchNavigation(defaults)),
    mocks: defaults,
  };
}

/**
 * JSDOM does not implement Touch or TouchEvent constructors.
 * We build plain Events and bolt on the touch-list properties via
 * Object.defineProperty so the hook's listener can read e.touches[0]
 * (touchstart) and e.changedTouches[0] (touchend) exactly as on a real browser.
 */
function dispatchTouchStart(clientX: number, clientY: number) {
  const evt = new Event('touchstart', { bubbles: true, cancelable: true }) as TouchEvent;
  const touch = { clientX, clientY, identifier: 1, target: document.body } as Touch;
  Object.defineProperty(evt, 'touches', { value: [touch] });
  Object.defineProperty(evt, 'changedTouches', { value: [touch] });
  window.dispatchEvent(evt);
}

function dispatchTouchEnd(clientX: number, clientY: number) {
  const evt = new Event('touchend', { bubbles: true, cancelable: true }) as TouchEvent;
  const touch = { clientX, clientY, identifier: 1, target: document.body } as Touch;
  Object.defineProperty(evt, 'touches', { value: [] });
  Object.defineProperty(evt, 'changedTouches', { value: [touch] });
  window.dispatchEvent(evt);
}

/** Fire a touchstart then touchend simulating a finger swipe. */
function swipe(startX: number, startY: number, endX: number, endY: number) {
  dispatchTouchStart(startX, startY);
  dispatchTouchEnd(endX, endY);
}

describe('useTouchNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('swipe left → advance (onNext)', () => {
    test('left swipe beyond threshold calls onNext', () => {
      const { mocks } = renderNav();
      swipe(300, 400, 200, 400); // dx = -100 (left)
      expect(mocks.onNext).toHaveBeenCalledOnce();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });

    test('swipe exactly at threshold boundary calls onNext', () => {
      const { mocks } = renderNav();
      swipe(300, 400, 250, 400); // dx = -50 exactly
      expect(mocks.onNext).toHaveBeenCalledOnce();
    });
  });

  describe('swipe right → go back (onPrev)', () => {
    test('right swipe beyond threshold calls onPrev', () => {
      const { mocks } = renderNav();
      swipe(200, 400, 300, 400); // dx = +100 (right)
      expect(mocks.onPrev).toHaveBeenCalledOnce();
      expect(mocks.onNext).not.toHaveBeenCalled();
    });
  });

  describe('ignored gestures', () => {
    test('short horizontal swipe below threshold does nothing', () => {
      const { mocks } = renderNav();
      swipe(300, 400, 260, 400); // dx = -40 (below 50 threshold)
      expect(mocks.onNext).not.toHaveBeenCalled();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });

    test('predominantly vertical swipe does nothing', () => {
      const { mocks } = renderNav();
      swipe(300, 200, 240, 350); // |dx|=60, |dy|=150 → vertical dominates
      expect(mocks.onNext).not.toHaveBeenCalled();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });

    test('diagonal swipe where |dy| equals |dx| does nothing', () => {
      const { mocks } = renderNav();
      swipe(300, 300, 200, 200); // |dx|=100, |dy|=100 → not horizontal dominant
      expect(mocks.onNext).not.toHaveBeenCalled();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });
  });

  describe('blank active — any qualifying swipe dismisses overlay', () => {
    test('left swipe when blank active calls onToggleBlank, not onNext', () => {
      const { mocks } = renderNav({ isBlankActive: true });
      swipe(300, 400, 200, 400);
      expect(mocks.onToggleBlank).toHaveBeenCalledOnce();
      expect(mocks.onNext).not.toHaveBeenCalled();
    });

    test('right swipe when blank active calls onToggleBlank, not onPrev', () => {
      const { mocks } = renderNav({ isBlankActive: true });
      swipe(200, 400, 300, 400);
      expect(mocks.onToggleBlank).toHaveBeenCalledOnce();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });

    test('short swipe (below threshold) when blank active does nothing', () => {
      const { mocks } = renderNav({ isBlankActive: true });
      swipe(300, 400, 270, 400); // dx = -30, below threshold
      expect(mocks.onToggleBlank).not.toHaveBeenCalled();
    });
  });

  describe('cleanup', () => {
    test('removes touchstart and touchend listeners on unmount', () => {
      const removeListener = vi.spyOn(window, 'removeEventListener');
      const { unmount } = renderNav();
      unmount();
      expect(removeListener).toHaveBeenCalledWith('touchstart', expect.any(Function));
      expect(removeListener).toHaveBeenCalledWith('touchend', expect.any(Function));
    });
  });

  describe('ref stability — callbacks update without re-registering listeners', () => {
    test('updated onNext callback is used without remounting', () => {
      const firstNext = vi.fn();
      const { mocks, rerender } = renderHook(
        (props: Parameters<typeof useTouchNavigation>[0]) => useTouchNavigation(props),
        {
          initialProps: {
            isBlankActive: false,
            onNext: firstNext,
            onPrev: vi.fn(),
            onToggleBlank: vi.fn(),
          },
        }
      );
      const secondNext = vi.fn();
      rerender({ ...mocks, onNext: secondNext });
      swipe(300, 400, 200, 400); // left swipe
      expect(secondNext).toHaveBeenCalledOnce();
      expect(firstNext).not.toHaveBeenCalled();
    });
  });
});
