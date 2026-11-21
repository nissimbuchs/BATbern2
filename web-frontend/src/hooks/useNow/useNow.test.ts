import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNow } from './useNow';

describe('useNow', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-21T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('should_returnTheTimeOfMount_when_rendered', () => {
    const { result } = renderHook(() => useNow());
    expect(result.current).toBe(new Date('2026-11-21T10:00:00Z').getTime());
  });

  it('should_keepTheTimeOfMount_when_noRefreshIsGiven', () => {
    const { result, rerender } = renderHook(() => useNow());
    act(() => {
      vi.advanceTimersByTime(120_000);
    });
    rerender();
    expect(result.current).toBe(new Date('2026-11-21T10:00:00Z').getTime());
  });

  it('should_moveOn_when_theRefreshIntervalPasses', () => {
    const { result } = renderHook(() => useNow(60_000));
    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    expect(result.current).toBe(new Date('2026-11-21T10:01:00Z').getTime());
  });

  it('should_stopTheInterval_when_unmounted', () => {
    const { unmount } = renderHook(() => useNow(60_000));
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
