/**
 * useRecentEventPhotos Hook Tests (Story 10.21)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useRecentEventPhotos } from './useRecentEventPhotos';

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getRecentEventPhotos: vi.fn(),
  },
}));

import { eventApiClient } from '@/services/eventApiClient';

const mockGetRecentEventPhotos = vi.mocked(eventApiClient.getRecentEventPhotos);

const createQC = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useRecentEventPhotos', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch recent photos with default params', async () => {
    const mockPhotos = [{ id: 'photo-1', cloudFrontUrl: 'https://cdn.example.com/p1.jpg' }];
    mockGetRecentEventPhotos.mockResolvedValue(
      mockPhotos as Awaited<ReturnType<typeof eventApiClient.getRecentEventPhotos>>
    );

    const { result } = renderHook(() => useRecentEventPhotos(), { wrapper: createWrapper(qc) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockPhotos);
    expect(mockGetRecentEventPhotos).toHaveBeenCalledWith(20, 5);
  });

  it('should fetch with custom limit and lastNEvents', async () => {
    mockGetRecentEventPhotos.mockResolvedValue([]);

    const { result } = renderHook(() => useRecentEventPhotos(10, 3), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGetRecentEventPhotos).toHaveBeenCalledWith(10, 3);
  });

  it('should set isError when fetch fails', async () => {
    mockGetRecentEventPhotos.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useRecentEventPhotos(), { wrapper: createWrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
