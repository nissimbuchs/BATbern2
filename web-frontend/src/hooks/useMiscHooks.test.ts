/**
 * Miscellaneous Hooks Tests
 *
 * Coverage for smaller hooks without dedicated test files:
 * - usePublicPartners
 * - useAssignSpeaker / useRemoveSpeaker (useSessionSpeakers)
 * - useDebounce
 * - useUserPortrait
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import MockAdapter from 'axios-mock-adapter';

// ── Mocks ─────────────────────────────────────────────────────────────────────
// HTTP is stubbed at the boundary: the real services and apiClient run.

import apiClient from '@/services/api/apiClient';
import { usePublicPartners } from './usePublicPartners';
import { useAssignSpeaker, useRemoveSpeaker } from './useSessionSpeakers';
import { useDebounce } from './useDebounce';
import { useUserPortrait } from './useUserPortrait';

// The request interceptor strips the Skip-Auth marker before the adapter sees the request,
// so record it with an interceptor that runs first (axios runs the last-added one first).
let http: MockAdapter;
let skipAuthUrls: string[];
let interceptorId: number;
beforeEach(() => {
  http = new MockAdapter(apiClient);
  skipAuthUrls = [];
  interceptorId = apiClient.interceptors.request.use((config) => {
    if (config.headers?.['Skip-Auth'] === 'true') skipAuthUrls.push(config.url ?? '');
    return config;
  });
});
afterEach(() => {
  apiClient.interceptors.request.eject(interceptorId);
  http.restore();
});
const fetched = () => http.history.get;
const ASSIGN_URL = (event: string, session: string) =>
  `/events/${event}/sessions/${session}/speakers`;

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
    http.onGet('/partners').reply(200, partners);

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(skipAuthUrls).toEqual(['/partners']);
    expect(result.current.data).toEqual(partners);
  });

  it('should include page=0, size=100 params', async () => {
    http.onGet('/partners').reply(200, { data: [], total: 0 });

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(fetched()[0].params).toEqual({
      page: 0,
      size: 100,
      include: 'company',
      filter: 'isActive:true',
    });
  });

  it('should set isError on fetch failure', async () => {
    http.onGet('/partners').reply(503);

    const { result } = renderHook(() => usePublicPartners(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should use public-partners query key', async () => {
    http.onGet('/partners').reply(200, { data: [], total: 0 });

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

  it('should POST the assignment with all params', async () => {
    http.onPost(ASSIGN_URL('BAT142', 'cloud-talk')).reply(201, {});

    const { result } = renderHook(() => useAssignSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 'cloud-talk',
        request: { username: 'john.doe', speakerRole: 'PRIMARY_SPEAKER' } as never,
      });
    });

    expect(http.history.post).toHaveLength(1);
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      username: 'john.doe',
      speakerRole: 'PRIMARY_SPEAKER',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    http.onPost(ASSIGN_URL('BAT142', 'cloud-talk')).reply(201, {});
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
    http.onPost(ASSIGN_URL('BAT142', 's1')).reply(404, { message: 'Not found' });

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

  it('should DELETE the speaker with all params', async () => {
    http.onDelete(`${ASSIGN_URL('BAT142', 'cloud-talk')}/john.doe`).reply(204);

    const { result } = renderHook(() => useRemoveSpeaker(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        sessionSlug: 'cloud-talk',
        username: 'john.doe',
      });
    });

    expect(http.history.delete).toHaveLength(1);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    http.onDelete(`${ASSIGN_URL('BAT142', 's1')}/bob`).reply(204);
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
    http.onDelete(`${ASSIGN_URL('BAT142', 's1')}/ghost`).reply(500, { message: 'Server error' });

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

  it('should fetch portrait URL for username with Skip-Auth header', async () => {
    http
      .onGet('/public/users/alice')
      .reply(200, { profilePictureUrl: 'https://cdn.example.com/portrait.jpg' });

    const { result } = renderHook(() => useUserPortrait('alice'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Story 11.C.1: public endpoint must NOT attach the Cognito token; otherwise a stale
    // ID token triggers the apiClient 401 → /login redirect from anonymous archive views.
    expect(skipAuthUrls).toEqual(['/public/users/alice']);
    expect(result.current.data).toBe('https://cdn.example.com/portrait.jpg');
  });

  it('should return null when profilePictureUrl is missing', async () => {
    http.onGet('/public/users/bob').reply(200, {});

    const { result } = renderHook(() => useUserPortrait('bob'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toBeNull();
  });

  // Positive control in each test: an enabled portrait rendered next to the disabled one is
  // fetched, so the absence below is observed after requests had time to go out.
  it('should not fetch when username is undefined', async () => {
    http
      .onGet('/public/users/carol')
      .reply(200, { profilePictureUrl: 'https://cdn.example.com/c.jpg' });

    const { result } = renderHook(() => useUserPortrait(undefined), { wrapper: wrapper(qc) });
    const control = renderHook(() => useUserPortrait('carol'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(control.result.current.data).toBe('https://cdn.example.com/c.jpg'));
    expect(result.current.isLoading).toBe(false);
    expect(fetched().map((r) => r.url)).toEqual(['/public/users/carol']);
  });

  it('should not fetch when enabled is false', async () => {
    http
      .onGet('/public/users/carol')
      .reply(200, { profilePictureUrl: 'https://cdn.example.com/c.jpg' });

    const { result } = renderHook(() => useUserPortrait('alice', false), { wrapper: wrapper(qc) });
    const control = renderHook(() => useUserPortrait('carol'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(control.result.current.data).toBe('https://cdn.example.com/c.jpg'));
    expect(result.current.isLoading).toBe(false);
    expect(fetched().map((r) => r.url)).toEqual(['/public/users/carol']);
  });

  it('should not retry on 404 (legitimate persistent miss)', async () => {
    // Story 11.C.1: 404 means the user isn't a speaker (or doesn't exist); don't retry.
    // Other errors are retried up to 2 times (Fargate-spot replacement absorption).
    http.onGet('/public/users/ghost').reply(404);

    const { result } = renderHook(() => useUserPortrait('ghost'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
    // retry: false for 404 → exactly one network call
    expect(fetched()).toHaveLength(1);
  });
});
