/**
 * usePublicOrganizers Hook Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePublicOrganizers } from '../usePublicOrganizers';
import React from 'react';

vi.mock('@/services/publicOrganizerService', () => ({
  publicOrganizerService: {
    getPublicOrganizers: vi.fn(),
  },
}));

import { publicOrganizerService } from '@/services/publicOrganizerService';

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

const createWrapper = (queryClient: QueryClient) => {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe('usePublicOrganizers', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = createTestQueryClient();
    vi.clearAllMocks();
  });

  it('should fetch organizers and return data', async () => {
    const mockOrganizers = [
      { username: 'alice', firstName: 'Alice', lastName: 'Smith', role: 'ORGANIZER' },
    ];
    vi.mocked(publicOrganizerService.getPublicOrganizers).mockResolvedValueOnce(
      mockOrganizers as never
    );

    const { result } = renderHook(() => usePublicOrganizers(), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockOrganizers);
    expect(publicOrganizerService.getPublicOrganizers).toHaveBeenCalledTimes(1);
  });

  it('should return error state when fetch fails', async () => {
    // Use mockRejectedValue (not Once) because the hook has retry:2 — all 3 attempts must fail
    vi.mocked(publicOrganizerService.getPublicOrganizers).mockRejectedValue(
      new Error('Network error')
    );

    const { result } = renderHook(() => usePublicOrganizers(), {
      wrapper: createWrapper(queryClient),
    });

    // Hook has retry:2; React Query adds ~1s+2s delays between retries — wait long enough
    await waitFor(
      () => {
        expect(result.current.isError).toBe(true);
      },
      { timeout: 10000 }
    );

    expect(result.current.error?.message).toBe('Network error');
  });
});
