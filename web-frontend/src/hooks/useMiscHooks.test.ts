/**
 * Miscellaneous Hooks Tests
 *
 * Coverage for smaller hooks without dedicated test files:
 * - usePublicPartners
 * - useAssignSpeaker / useRemoveSpeaker (useSessionSpeakers)
 * - useDebounce
 * - useUserPortrait
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock('@/services/api/sessionApiClient', () => ({
  sessionApiClient: {
    assignSpeaker: vi.fn(),
    removeSpeaker: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';
import { sessionApiClient } from '@/services/api/sessionApiClient';
import { usePublicPartners } from './usePublicPartners';
import { useAssignSpeaker, useRemoveSpeaker } from './useSessionSpeakers';
import { useDebounce } from './useDebounce';
import { useUserPortrait } from './useUserPortrait';

const mockGet = vi.mocked(apiClient.get);
const mockAssignSpeaker = vi.mocked(sessionApiClient.assignSpeaker);
const mockRemoveSpeaker = vi.mocked(sessionApiClient.removeSpeaker);

// ── Helpers ───────────────────────────────────────────────────────────────────

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

// ── usePublicPartners ─────────────────────────────────────────────────────────

describe('usePublicPartners', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch public partners via GET /partners with Skip-Auth header', async () => {
    const partners = { data: [{ companyName: 'TechCorp' }], total: 1 };
    mockGet.mockResolvedValue({ data: partners });

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGet).toHaveBeenCalledWith(
      '/partners',
      expect.objectContaining({
        headers: expect.objectContaining({ 'Skip-Auth': 'true' }),
      })
    );
    expect(result.current.data).toEqual(partners);
  });

  it('should include page=0, size=100 params', async () => {
    mockGet.mockResolvedValue({ data: { data: [], total: 0 } });

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGet).toHaveBeenCalledWith(
      '/partners',
      expect.objectContaining({
        params: expect.objectContaining({ page: 0, size: 100 }),
      })
    );
  });

  it('should set isError on fetch failure', async () => {
    mockGet.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should use public-partners query key', async () => {
    mockGet.mockResolvedValue({ data: { data: [], total: 0 } });

    renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'public-partners');
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'public-partners');
    expect(query).toBeDefined();
  });
});

// ── useAssignSpeaker ──────────────────────────────────────────────────────────

describe('useAssignSpeaker', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call assignSpeaker with all params', async () => {
    mockAssignSpeaker.mockResolvedValue({} as never);

    const { result } = renderHook(() => useAssignSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 'cloud-talk',
        request: { username: 'john.doe', speakerRole: 'PRIMARY_SPEAKER' } as never,
      });
    });

    expect(mockAssignSpeaker).toHaveBeenCalledWith('BAT142', 'cloud-talk', {
      username: 'john.doe',
      speakerRole: 'PRIMARY_SPEAKER',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockAssignSpeaker.mockResolvedValue({} as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useAssignSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 'cloud-talk',
        request: { username: 'alice' } as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockAssignSpeaker.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useAssignSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', sessionSlug: 's1', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useRemoveSpeaker ──────────────────────────────────────────────────────────

describe('useRemoveSpeaker', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call removeSpeaker with all params', async () => {
    mockRemoveSpeaker.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useRemoveSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 'cloud-talk',
        username: 'john.doe',
      });
    });

    expect(mockRemoveSpeaker).toHaveBeenCalledWith('BAT142', 'cloud-talk', 'john.doe');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockRemoveSpeaker.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useRemoveSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 's1',
        username: 'bob',
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockRemoveSpeaker.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useRemoveSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', sessionSlug: 's1', username: 'ghost' })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDebounce ───────────────────────────────────────────────────────────────

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should return initial value immediately', () => {
    const { result } = renderHook(() => useDebounce('hello', 300));

    expect(result.current).toBe('hello');
  });

  it('should not update value before delay', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 300),
      { initialProps: { value: 'initial' } }
    );

    rerender({ value: 'updated' });
    vi.advanceTimersByTime(200);

    expect(result.current).toBe('initial');
  });

  it('should update value after delay', async () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 300),
      { initialProps: { value: 'initial' } }
    );

    rerender({ value: 'updated' });
    act(() => vi.advanceTimersByTime(300));

    expect(result.current).toBe('updated');
  });

  it('should reset timer on rapid value changes', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 300),
      { initialProps: { value: 'a' } }
    );

    rerender({ value: 'ab' });
    vi.advanceTimersByTime(150);
    rerender({ value: 'abc' });
    vi.advanceTimersByTime(150);

    expect(result.current).toBe('a'); // still debounced

    act(() => vi.advanceTimersByTime(300));
    expect(result.current).toBe('abc');
  });

  it('should use default delay of 500ms', () => {
    const { result, rerender } = renderHook(({ value }: { value: string }) => useDebounce(value), {
      initialProps: { value: 'initial' },
    });

    rerender({ value: 'updated' });
    act(() => vi.advanceTimersByTime(499));
    expect(result.current).toBe('initial');

    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe('updated');
  });

  it('should work with numeric values', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: number }) => useDebounce(value, 200),
      { initialProps: { value: 1 } }
    );

    rerender({ value: 42 });
    act(() => vi.advanceTimersByTime(200));

    expect(result.current).toBe(42);
  });
});

// ── useUserPortrait ───────────────────────────────────────────────────────────

describe('useUserPortrait', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch portrait URL for username', async () => {
    mockGet.mockResolvedValue({
      data: { profilePictureUrl: 'https://cdn.example.com/portrait.jpg' },
    });

    const { result } = renderHook(() => useUserPortrait('alice'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGet).toHaveBeenCalledWith('/speakers/alice');
    expect(result.current.data).toBe('https://cdn.example.com/portrait.jpg');
  });

  it('should return null when profilePictureUrl is missing', async () => {
    mockGet.mockResolvedValue({ data: {} });

    const { result } = renderHook(() => useUserPortrait('bob'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toBeNull();
  });

  it('should not fetch when username is undefined', () => {
    const { result } = renderHook(() => useUserPortrait(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('should not fetch when enabled is false', () => {
    const { result } = renderHook(() => useUserPortrait('alice', false), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGet.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useUserPortrait('ghost'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
