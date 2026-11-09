/**
 * useCurrentEvent Hook Tests
 * Story 4.1.3: Event Landing Page Hero Section - Hook Testing
 *
 * Tests for React Query hook that fetches the current published event
 */

import React from 'react';
import { describe, test, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCurrentEvent } from '../useCurrentEvent';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetail } from '@/types/event.types';
import { futureEvent } from '@/test/fixtures';

// Mock the eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getPublicCurrentEvent: vi.fn(),
  },
}));

describe('useCurrentEvent Hook', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries in tests
        },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };

  test('should_returnEventData_when_apiCallSucceeds', async () => {
    const mockEvent: EventDetail = futureEvent(40, {
      eventCode: 'BATbern61',
      title: 'BATbern 2025',
      description: 'Annual architecture conference',
      currentAttendeeCount: 150,
      sessions: [],
    });

    vi.mocked(eventApiClient.getPublicCurrentEvent).mockResolvedValue(mockEvent);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockEvent);
    // Public Events read model: no expand options, the server shapes the payload
    expect(eventApiClient.getPublicCurrentEvent).toHaveBeenCalledWith();
  });

  test('should_returnNull_when_noCurrentEvent', async () => {
    vi.mocked(eventApiClient.getPublicCurrentEvent).mockResolvedValue(null);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toBeNull();
  });

  test('should_handleError_when_apiCallFails', async () => {
    const mockError = new Error('Failed to fetch current event');
    vi.mocked(eventApiClient.getPublicCurrentEvent).mockRejectedValue(mockError);

    const { result } = renderHook(() => useCurrentEvent({ retry: false }), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(mockError);
  });

  test('should_useCorrectQueryKey_when_hookCalled', () => {
    vi.mocked(eventApiClient.getPublicCurrentEvent).mockResolvedValue(null);

    renderHook(() => useCurrentEvent(), { wrapper });

    // Query key is accessible via the query cache
    const query = queryClient
      .getQueryCache()
      .findAll({ queryKey: ['events', 'public', 'current'] });
    expect(query).toHaveLength(1);
  });

  test('should_haveCorrectCacheConfig_when_hookInitialized', async () => {
    vi.mocked(eventApiClient.getPublicCurrentEvent).mockResolvedValue(null);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    // Verify staleTime is configured (data doesn't refetch immediately)
    const query = queryClient.getQueryCache().find({ queryKey: ['events', 'public', 'current'] });
    expect((query?.options as { staleTime?: number } | undefined)?.staleTime).toBe(5 * 60 * 1000); // 5 minutes
  });
});
