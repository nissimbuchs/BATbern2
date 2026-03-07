/**
 * useInfiniteEvents Tests
 *
 * Coverage for:
 * - Initial page fetch with default filters
 * - Filter conversion (topics, search)
 * - hasNext pagination logic (getNextPageParam)
 * - Error handling
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getEvents: vi.fn(),
  },
}));

import { eventApiClient } from '@/services/eventApiClient';
import { useInfiniteEvents } from './useInfiniteEvents';

const mockGetEvents = vi.mocked(eventApiClient.getEvents);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const makePage = (hasNext: boolean, page = 1) => ({
  data: [{ eventCode: `BAT${page}`, title: `Event ${page}` }],
  pagination: { hasNext, page, limit: 20, total: hasNext ? 40 : 1 },
});

describe('useInfiniteEvents', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch first page with default filters', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith(
      { page: 1, limit: 20 },
      expect.objectContaining({ includeArchived: true, workflowState: ['ARCHIVED'] }),
      expect.objectContaining({ expand: ['topics', 'sessions', 'speakers'] })
    );
  });

  it('should convert topic filters to topicCode', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents({ topics: ['cloud-native', 'devops'] }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ topicCode: ['cloud-native', 'devops'] }),
      expect.anything()
    );
  });

  it('should pass search filter', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents({ search: 'kubernetes' }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ search: 'kubernetes' }),
      expect.anything()
    );
  });

  it('should expose hasNextPage=true when more pages exist', async () => {
    mockGetEvents.mockResolvedValue(makePage(true) as never);

    const { result } = renderHook(() => useInfiniteEvents(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.hasNextPage).toBe(true);
  });

  it('should expose hasNextPage=false when no more pages', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.hasNextPage).toBe(false);
  });

  it('should use custom sort parameter', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents({}, '+date'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith(
      expect.anything(),
      expect.anything(),
      expect.objectContaining({ sort: '+date' })
    );
  });

  it('should set isError on fetch failure', async () => {
    mockGetEvents.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => useInfiniteEvents(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should not include topicCode when topics array is empty', async () => {
    mockGetEvents.mockResolvedValue(makePage(false) as never);

    const { result } = renderHook(() => useInfiniteEvents({ topics: [] }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith(
      expect.anything(),
      expect.not.objectContaining({ topicCode: expect.anything() }),
      expect.anything()
    );
  });
});
