/**
 * useNewsletterSubscriberMutations Hook Tests
 *
 * Coverage for all four mutation hooks:
 * - useUnsubscribeSubscriber
 * - useResubscribeSubscriber
 * - useDeleteSubscriber
 * - useUnsuppressSubscriber
 *
 * Story 10.28 / 10.29
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/newsletterApi', () => ({
  unsubscribeNewsletterSubscriber: vi.fn(),
  resubscribeNewsletterSubscriber: vi.fn(),
  deleteNewsletterSubscriber: vi.fn(),
  unsuppressNewsletterSubscriber: vi.fn(),
}));

import {
  unsubscribeNewsletterSubscriber,
  resubscribeNewsletterSubscriber,
  deleteNewsletterSubscriber,
  unsuppressNewsletterSubscriber,
} from '@/services/api/newsletterApi';
import {
  useUnsubscribeSubscriber,
  useResubscribeSubscriber,
  useDeleteSubscriber,
  useUnsuppressSubscriber,
} from './useNewsletterSubscriberMutations';

const mockUnsubscribe = vi.mocked(unsubscribeNewsletterSubscriber);
const mockResubscribe = vi.mocked(resubscribeNewsletterSubscriber);
const mockDelete = vi.mocked(deleteNewsletterSubscriber);
const mockUnsuppress = vi.mocked(unsuppressNewsletterSubscriber);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const SUBSCRIBER_RESPONSE = { id: 'sub-1', email: 'a@b.com', status: 'ACTIVE' };

// ── useUnsubscribeSubscriber ─────────────────────────────────────────────────

describe('useUnsubscribeSubscriber', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call unsubscribeNewsletterSubscriber with id', async () => {
    mockUnsubscribe.mockResolvedValue(SUBSCRIBER_RESPONSE as never);

    const { result } = renderHook(() => useUnsubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-1');
    });

    expect(mockUnsubscribe).toHaveBeenCalledWith('sub-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate newsletter-subscribers cache on success', async () => {
    mockUnsubscribe.mockResolvedValue(SUBSCRIBER_RESPONSE as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUnsubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });

  it('should set isError on failure', async () => {
    mockUnsubscribe.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useUnsubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('bad-id').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useResubscribeSubscriber ─────────────────────────────────────────────────

describe('useResubscribeSubscriber', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call resubscribeNewsletterSubscriber with id', async () => {
    mockResubscribe.mockResolvedValue(SUBSCRIBER_RESPONSE as never);

    const { result } = renderHook(() => useResubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-2');
    });

    expect(mockResubscribe).toHaveBeenCalledWith('sub-2');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate newsletter-subscribers cache on success', async () => {
    mockResubscribe.mockResolvedValue(SUBSCRIBER_RESPONSE as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useResubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-2');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });

  it('should set isError on failure', async () => {
    mockResubscribe.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useResubscribeSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('bad-id').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteSubscriber ──────────────────────────────────────────────────────

describe('useDeleteSubscriber', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteNewsletterSubscriber with id', async () => {
    mockDelete.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-3');
    });

    expect(mockDelete).toHaveBeenCalledWith('sub-3');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate newsletter-subscribers cache on success', async () => {
    mockDelete.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-3');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });

  it('should set isError on failure', async () => {
    mockDelete.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useDeleteSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('bad-id').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUnsuppressSubscriber (Story 10.29 AC8) ────────────────────────────────

describe('useUnsuppressSubscriber', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call unsuppressNewsletterSubscriber with id', async () => {
    mockUnsuppress.mockResolvedValue(SUBSCRIBER_RESPONSE as never);

    const { result } = renderHook(() => useUnsuppressSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-4');
    });

    expect(mockUnsuppress).toHaveBeenCalledWith('sub-4');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate newsletter-subscribers cache on success', async () => {
    mockUnsuppress.mockResolvedValue(SUBSCRIBER_RESPONSE as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUnsuppressSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('sub-4');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });

  it('should set isError on failure', async () => {
    mockUnsuppress.mockRejectedValue(new Error('Already active'));

    const { result } = renderHook(() => useUnsuppressSubscriber(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('bad-id').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
