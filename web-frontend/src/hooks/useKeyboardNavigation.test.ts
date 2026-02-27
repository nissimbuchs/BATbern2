/**
 * useKeyboardNavigation Hook Tests
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * ACs: #2–6, #23–24
 */

import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { fireEvent } from '@testing-library/react';
import { useKeyboardNavigation } from './useKeyboardNavigation';

function renderNav(overrides: Partial<Parameters<typeof useKeyboardNavigation>[0]> = {}) {
  const defaults = {
    sectionCount: 5,
    currentIndex: 2,
    isBlankActive: false,
    onNext: vi.fn(),
    onPrev: vi.fn(),
    onToggleBlank: vi.fn(),
    ...overrides,
  };
  return {
    ...renderHook(() => useKeyboardNavigation(defaults)),
    mocks: defaults,
  };
}

function press(key: string, target: EventTarget = document.body) {
  fireEvent.keyDown(target, { key, bubbles: true });
}

describe('useKeyboardNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('forward navigation (AC #2)', () => {
    test('ArrowRight calls onNext when not at last section', () => {
      const { mocks } = renderNav({ currentIndex: 2, sectionCount: 5 });
      press('ArrowRight');
      expect(mocks.onNext).toHaveBeenCalledOnce();
    });

    test('PageDown calls onNext', () => {
      const { mocks } = renderNav({ currentIndex: 1 });
      press('PageDown');
      expect(mocks.onNext).toHaveBeenCalledOnce();
    });

    test('Space calls onNext', () => {
      const { mocks } = renderNav({ currentIndex: 1 });
      press(' ');
      expect(mocks.onNext).toHaveBeenCalledOnce();
    });

    test('ArrowRight does nothing at last section (AC #3)', () => {
      const { mocks } = renderNav({ currentIndex: 4, sectionCount: 5 });
      press('ArrowRight');
      expect(mocks.onNext).not.toHaveBeenCalled();
    });
  });

  describe('backward navigation (AC #2, #4)', () => {
    test('ArrowLeft calls onPrev when not at first section', () => {
      const { mocks } = renderNav({ currentIndex: 2 });
      press('ArrowLeft');
      expect(mocks.onPrev).toHaveBeenCalledOnce();
    });

    test('PageUp calls onPrev', () => {
      const { mocks } = renderNav({ currentIndex: 2 });
      press('PageUp');
      expect(mocks.onPrev).toHaveBeenCalledOnce();
    });

    test('ArrowLeft does nothing at first section (AC #4)', () => {
      const { mocks } = renderNav({ currentIndex: 0 });
      press('ArrowLeft');
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });
  });

  describe('blank overlay / B-key (ACs #23–24)', () => {
    test('B key calls onToggleBlank', () => {
      const { mocks } = renderNav();
      press('b');
      expect(mocks.onToggleBlank).toHaveBeenCalledOnce();
    });

    test('Shift+B calls onToggleBlank', () => {
      const { mocks } = renderNav();
      press('B');
      expect(mocks.onToggleBlank).toHaveBeenCalledOnce();
    });

    test('navigation blocked when blank is active', () => {
      const { mocks } = renderNav({ isBlankActive: true, currentIndex: 2 });
      press('ArrowRight');
      press('ArrowLeft');
      expect(mocks.onNext).not.toHaveBeenCalled();
      expect(mocks.onPrev).not.toHaveBeenCalled();
    });

    test('Escape when blank active calls onToggleBlank', () => {
      const { mocks } = renderNav({ isBlankActive: true });
      press('Escape');
      expect(mocks.onToggleBlank).toHaveBeenCalledOnce();
    });
  });

  describe('fullscreen (AC #5)', () => {
    test('f key requests fullscreen when not in fullscreen', () => {
      const requestFullscreen = vi.fn().mockResolvedValue(undefined);
      Object.defineProperty(document.documentElement, 'requestFullscreen', {
        value: requestFullscreen,
        writable: true,
        configurable: true,
      });
      Object.defineProperty(document, 'fullscreenElement', {
        value: null,
        writable: true,
        configurable: true,
      });
      renderNav();
      press('f');
      expect(requestFullscreen).toHaveBeenCalledOnce();
    });
  });

  describe('input guard', () => {
    test('keys from INPUT element are ignored', () => {
      const { mocks } = renderNav();
      const input = document.createElement('input');
      document.body.appendChild(input);
      press('ArrowRight', input);
      expect(mocks.onNext).not.toHaveBeenCalled();
      document.body.removeChild(input);
    });

    test('keys from TEXTAREA element are ignored', () => {
      const { mocks } = renderNav();
      const textarea = document.createElement('textarea');
      document.body.appendChild(textarea);
      press('ArrowRight', textarea);
      expect(mocks.onNext).not.toHaveBeenCalled();
      document.body.removeChild(textarea);
    });
  });

  describe('cleanup', () => {
    test('removes event listener on unmount', () => {
      const removeListener = vi.spyOn(window, 'removeEventListener');
      const { unmount } = renderNav();
      unmount();
      expect(removeListener).toHaveBeenCalledWith('keydown', expect.any(Function));
    });
  });
});
