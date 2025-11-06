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

// Mock the eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getCurrentEvent: vi.fn(),
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
    const mockEvent: EventDetail = {
      id: 'evt-123',
      code: 'BAT25',
      title: 'BATbern 2025',
      description: 'Annual architecture conference',
      startDate: '2025-06-15T00:00:00Z',
      endDate: '2025-06-16T00:00:00Z',
      status: 'published',
      eventFormat: 'hybrid',
      registrationDeadline: '2025-06-01T00:00:00Z',
      maxAttendees: 200,
      currentAttendees: 150,
      topics: [],
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    };

    vi.mocked(eventApiClient.getCurrentEvent).mockResolvedValue(mockEvent);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockEvent);
    expect(eventApiClient.getCurrentEvent).toHaveBeenCalledWith({
      expand: ['topics', 'venue', 'speakers', 'sessions'],
    });
  });

  test('should_returnNull_when_noCurrentEvent', async () => {
    vi.mocked(eventApiClient.getCurrentEvent).mockResolvedValue(null);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toBeNull();
  });

  test('should_handleError_when_apiCallFails', async () => {
    const mockError = new Error('Failed to fetch current event');
    vi.mocked(eventApiClient.getCurrentEvent).mockRejectedValue(mockError);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(mockError);
  });

  test('should_useCorrectQueryKey_when_hookCalled', () => {
    vi.mocked(eventApiClient.getCurrentEvent).mockResolvedValue(null);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    // Query key is accessible via the query cache
    const query = queryClient.getQueryCache().findAll({ queryKey: ['events', 'current'] });
    expect(query).toHaveLength(1);
  });

  test('should_haveCorrectCacheConfig_when_hookInitialized', async () => {
    vi.mocked(eventApiClient.getCurrentEvent).mockResolvedValue(null);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    // Verify staleTime is configured (data doesn't refetch immediately)
    const query = queryClient.getQueryCache().find({ queryKey: ['events', 'current'] });
    expect(query?.options.staleTime).toBe(5 * 60 * 1000); // 5 minutes
  });
});
