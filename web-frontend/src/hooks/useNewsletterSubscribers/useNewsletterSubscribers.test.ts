/**
 * useNewsletterSubscribers Hook Tests
 *
 * Coverage for:
 * - useNewsletterSubscriberList: paginated fetch, prefetch next page
 * - useUnsubscribeSubscriber: mutation + cache invalidation
 * - useResubscribeSubscriber: mutation + cache invalidation
 * - useDeleteSubscriber: mutation + cache invalidation
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/newsletterApi', () => ({
  listNewsletterSubscribers: vi.fn(),
  unsubscribeNewsletterSubscriber: vi.fn(),
  resubscribeNewsletterSubscriber: vi.fn(),
  deleteNewsletterSubscriber: vi.fn(),
}));

import {
  listNewsletterSubscribers,
  unsubscribeNewsletterSubscriber,
  resubscribeNewsletterSubscriber,
  deleteNewsletterSubscriber,
} from '@/services/api/newsletterApi';
import { useNewsletterSubscriberList } from './useNewsletterSubscriberList';
import {
  useUnsubscribeSubscriber,
  useResubscribeSubscriber,
  useDeleteSubscriber,
} from './useNewsletterSubscriberMutations';

const mockList = vi.mocked(listNewsletterSubscribers);
const mockUnsubscribe = vi.mocked(unsubscribeNewsletterSubscriber);
const mockResubscribe = vi.mocked(resubscribeNewsletterSubscriber);
const mockDelete = vi.mocked(deleteNewsletterSubscriber);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const mockPaginatedResponse = {
  data: [
    {
      id: 'sub-1',
      email: 'alice@example.com',
      firstName: 'Alice',
      language: 'en',
      source: 'explicit',
      subscribedAt: '2026-01-01T00:00:00Z',
      unsubscribedAt: null,
    },
  ],
  pagination: {
    page: 1,
    limit: 20,
    totalItems: 1,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  },
};

const mockSubscriberResponse = {
  id: 'sub-1',
  email: 'alice@example.com',
  firstName: 'Alice',
  language: 'en',
  source: 'explicit',
  subscribedAt: '2026-01-01T00:00:00Z',
  unsubscribedAt: '2026-03-01T00:00:00Z',
};

describe('useNewsletterSubscriberList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_fetchSubscribers_when_enabled', async () => {
    const qc = createQC();
    mockList.mockResolvedValueOnce(mockPaginatedResponse);

    const { result } = renderHook(
      () =>
        useNewsletterSubscriberList({
          filters: { status: 'all', sortBy: 'subscribedAt', sortDir: 'desc' },
          pagination: { page: 1, limit: 20 },
        }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockPaginatedResponse);
    expect(mockList).toHaveBeenCalledWith(
      { status: 'all', sortBy: 'subscribedAt', sortDir: 'desc' },
      { page: 1, limit: 20 }
    );
  });

  it('should_prefetchNextPage_when_hasNext', async () => {
    const qc = createQC();
    const responseWithNext = {
      ...mockPaginatedResponse,
      pagination: { ...mockPaginatedResponse.pagination, hasNext: true },
    };
    mockList.mockResolvedValue(responseWithNext);

    const { result } = renderHook(
      () =>
        useNewsletterSubscriberList({
          filters: { status: 'all' },
          pagination: { page: 1, limit: 20 },
        }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    // prefetchQuery should have been called for page 2
    await waitFor(() => expect(mockList).toHaveBeenCalledTimes(2));
    expect(mockList).toHaveBeenCalledWith({ status: 'all' }, { page: 2, limit: 20 });
  });

  it('should_notFetch_when_disabled', () => {
    const qc = createQC();

    const { result } = renderHook(
      () =>
        useNewsletterSubscriberList({
          filters: {},
          pagination: { page: 1, limit: 20 },
          enabled: false,
        }),
      { wrapper: wrapper(qc) }
    );

    expect(result.current.isFetching).toBe(false);
    expect(mockList).not.toHaveBeenCalled();
  });
});

describe('useUnsubscribeSubscriber', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_callUnsubscribeApi_when_mutated', async () => {
    const qc = createQC();
    mockUnsubscribe.mockResolvedValueOnce(mockSubscriberResponse);

    const { result } = renderHook(() => useUnsubscribeSubscriber(), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      result.current.mutate('sub-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockUnsubscribe).toHaveBeenCalledWith('sub-1');
  });

  it('should_invalidateQueries_when_successful', async () => {
    const qc = createQC();
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    mockUnsubscribe.mockResolvedValueOnce(mockSubscriberResponse);

    const { result } = renderHook(() => useUnsubscribeSubscriber(), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      result.current.mutate('sub-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });
});

describe('useResubscribeSubscriber', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_callResubscribeApi_when_mutated', async () => {
    const qc = createQC();
    mockResubscribe.mockResolvedValueOnce({ ...mockSubscriberResponse, unsubscribedAt: null });

    const { result } = renderHook(() => useResubscribeSubscriber(), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      result.current.mutate('sub-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockResubscribe).toHaveBeenCalledWith('sub-1');
  });
});

describe('useDeleteSubscriber', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_callDeleteApi_when_mutated', async () => {
    const qc = createQC();
    mockDelete.mockResolvedValueOnce(undefined);

    const { result } = renderHook(() => useDeleteSubscriber(), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      result.current.mutate('sub-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockDelete).toHaveBeenCalledWith('sub-1');
  });

  it('should_invalidateQueries_when_deleteSuccessful', async () => {
    const qc = createQC();
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    mockDelete.mockResolvedValueOnce(undefined);

    const { result } = renderHook(() => useDeleteSubscriber(), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      result.current.mutate('sub-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['newsletter-subscribers'] });
  });
});
