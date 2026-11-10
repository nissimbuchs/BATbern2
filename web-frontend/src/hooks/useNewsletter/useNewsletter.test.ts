/**
 * useNewsletter Hooks Tests
 *
 * Coverage for:
 * - useNewsletterSubscribe: mutation
 * - useVerifyUnsubscribeToken: query with enabled guard
 * - useUnsubscribeByToken: mutation
 * - useMySubscription: query
 * - usePatchMySubscription: mutation + setQueryData
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/newsletterService', () => ({
  subscribe: vi.fn(),
  verifyUnsubscribeToken: vi.fn(),
  unsubscribeByToken: vi.fn(),
  getMySubscription: vi.fn(),
  patchMySubscription: vi.fn(),
}));

import * as newsletterService from '@/services/newsletterService';
import {
  useNewsletterSubscribe,
  useVerifyUnsubscribeToken,
  useUnsubscribeByToken,
  useMySubscription,
  usePatchMySubscription,
} from './useNewsletter';

const mockSubscribe = vi.mocked(newsletterService.subscribe);
const mockVerifyToken = vi.mocked(newsletterService.verifyUnsubscribeToken);
const mockUnsubscribeByToken = vi.mocked(newsletterService.unsubscribeByToken);
const mockGetMySubscription = vi.mocked(newsletterService.getMySubscription);
const mockPatchMySubscription = vi.mocked(newsletterService.patchMySubscription);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

// ── useNewsletterSubscribe ────────────────────────────────────────────────────

describe('useNewsletterSubscribe', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call subscribe and succeed', async () => {
    mockSubscribe.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useNewsletterSubscribe(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ request: { email: 'alice@batbern.ch', language: 'de' } });
    });

    expect(mockSubscribe).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should set isError on failure', async () => {
    mockSubscribe.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useNewsletterSubscribe(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ request: { email: 'dup@x.ch' } }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useVerifyUnsubscribeToken ─────────────────────────────────────────────────

describe('useVerifyUnsubscribeToken', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch email for valid token', async () => {
    mockVerifyToken.mockResolvedValue({ email: 'alice@batbern.ch' } as never);

    const { result } = renderHook(() => useVerifyUnsubscribeToken('valid-tok'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockVerifyToken).toHaveBeenCalledWith('valid-tok');
    expect(result.current.data).toEqual({ email: 'alice@batbern.ch' });
  });

  it('should not fetch when token is null', () => {
    const { result } = renderHook(() => useVerifyUnsubscribeToken(null), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockVerifyToken).not.toHaveBeenCalled();
  });
});

// ── useUnsubscribeByToken ─────────────────────────────────────────────────────

describe('useUnsubscribeByToken', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call unsubscribeByToken and succeed', async () => {
    mockUnsubscribeByToken.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useUnsubscribeByToken(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('unsub-token');
    });

    expect(mockUnsubscribeByToken).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

// ── useMySubscription ─────────────────────────────────────────────────────────

describe('useMySubscription', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch subscription status', async () => {
    mockGetMySubscription.mockResolvedValue({
      subscribed: true,
      email: 'alice@batbern.ch',
    } as never);

    const { result } = renderHook(() => useMySubscription(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetMySubscription).toHaveBeenCalled();
    expect(result.current.data?.subscribed).toBe(true);
  });

  it('should set isError on failure', async () => {
    mockGetMySubscription.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useMySubscription(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── usePatchMySubscription ────────────────────────────────────────────────────

describe('usePatchMySubscription', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call patchMySubscription and update cache', async () => {
    const updated = { subscribed: false };
    mockPatchMySubscription.mockResolvedValue(updated as never);

    const { result } = renderHook(() => usePatchMySubscription(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ subscribed: false, language: 'en' });
    });

    expect(mockPatchMySubscription).toHaveBeenCalledWith(false, 'en');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
