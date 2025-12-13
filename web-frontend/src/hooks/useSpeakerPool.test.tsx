/**
 * useSpeakerPool Hook Tests (Story 5.2 - Frontend Tests)
 *
 * Comprehensive tests for Speaker Pool React Query hooks
 * Tests all hooks: useSpeakerPool, useAddSpeakerToPool
 *
 * Coverage:
 * - Query hooks (data fetching, caching, error handling)
 * - Mutation hooks (optimistic updates, cache invalidation)
 * - React Query integration and behavior
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSpeakerPool, useAddSpeakerToPool, speakerPoolKeys } from './useSpeakerPool';
import { speakerPoolService } from '@/services/speakerPoolService';
import type {
  SpeakerPoolEntry,
  AddSpeakerToPoolRequest,
  SpeakerPoolResponse,
} from '@/types/speakerPool.types';

// Mock the speaker pool service
vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    getSpeakerPool: vi.fn(),
    addSpeakerToPool: vi.fn(),
  },
}));

describe('useSpeakerPool hooks', () => {
  let queryClient: QueryClient;

  // Create wrapper with QueryClient for hooks
  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries for tests
          gcTime: 0, // Disable cache for tests
        },
        mutations: {
          retry: false,
        },
      },
    });

    return ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('speakerPoolKeys', () => {
    it('should generate correct query keys for all operations', () => {
      expect(speakerPoolKeys.all).toEqual(['speakerPool']);
      expect(speakerPoolKeys.lists()).toEqual(['speakerPool', 'list']);
      expect(speakerPoolKeys.list('BATbern56')).toEqual(['speakerPool', 'list', 'BATbern56']);
    });
  });

  describe('useSpeakerPool', () => {
    const mockSpeakerPool: SpeakerPoolEntry[] = [
      {
        id: 'pool-123',
        eventId: 'event-456',
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture, DevOps',
        assignedOrganizerId: 'org-john-doe',
        status: 'identified',
        notes: 'Excellent speaker',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      },
      {
        id: 'pool-124',
        eventId: 'event-456',
        speakerName: 'Prof. Robert Johnson',
        company: 'University of Bern',
        expertise: 'AI/ML, Data Science',
        assignedOrganizerId: 'org-jane-doe',
        status: 'contacted',
        notes: 'Follow up next week',
        createdAt: '2025-12-13T11:00:00Z',
        updatedAt: '2025-12-13T12:00:00Z',
      },
    ];

    it('should fetch speaker pool for event', async () => {
      vi.mocked(speakerPoolService.getSpeakerPool).mockResolvedValue(mockSpeakerPool);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerPoolService.getSpeakerPool).toHaveBeenCalledWith('BATbern56');
      expect(result.current.data).toEqual(mockSpeakerPool);
      expect(result.current.data).toHaveLength(2);
    });

    it('should return empty array when no speakers in pool', async () => {
      vi.mocked(speakerPoolService.getSpeakerPool).mockResolvedValue([]);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(result.current.data).toEqual([]);
      expect(result.current.data).toHaveLength(0);
    });

    it('should handle loading state', async () => {
      vi.mocked(speakerPoolService.getSpeakerPool).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockSpeakerPool), 100))
      );

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toBeDefined();
    });

    it('should not fetch when event code is empty', () => {
      const { result } = renderHook(() => useSpeakerPool(''), {
        wrapper: createWrapper(),
      });

      expect(result.current.isFetching).toBe(false);
      expect(speakerPoolService.getSpeakerPool).not.toHaveBeenCalled();
    });

    it('should handle 404 errors for non-existent events', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(speakerPoolService.getSpeakerPool).mockRejectedValue(error);

      const { result } = renderHook(() => useSpeakerPool('BATbern999'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
      expect(result.current.data).toBeUndefined();
    });

    it('should handle authorization errors', async () => {
      const error = new Error('Unauthorized: JWT token required');
      vi.mocked(speakerPoolService.getSpeakerPool).mockRejectedValue(error);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should cache results with 2 minute stale time', async () => {
      vi.mocked(speakerPoolService.getSpeakerPool).mockResolvedValue(mockSpeakerPool);

      const { result } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Second render should use cached data
      const { result: result2 } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      });

      expect(result2.current.data).toEqual(mockSpeakerPool);
      expect(speakerPoolService.getSpeakerPool).toHaveBeenCalledTimes(1); // Only called once due to cache
    });

    it('should fetch different data for different events', async () => {
      const pool1: SpeakerPoolEntry[] = [
        {
          id: 'pool-1',
          eventId: 'event-1',
          speakerName: 'Speaker 1',
          status: 'identified',
          createdAt: '2025-12-13T10:00:00Z',
          updatedAt: '2025-12-13T10:00:00Z',
        },
      ];

      const pool2: SpeakerPoolEntry[] = [
        {
          id: 'pool-2',
          eventId: 'event-2',
          speakerName: 'Speaker 2',
          status: 'contacted',
          createdAt: '2025-12-13T11:00:00Z',
          updatedAt: '2025-12-13T11:00:00Z',
        },
      ];

      vi.mocked(speakerPoolService.getSpeakerPool)
        .mockResolvedValueOnce(pool1)
        .mockResolvedValueOnce(pool2);

      const { result: result1 } = renderHook(() => useSpeakerPool('BATbern56'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result1.current.isSuccess).toBe(true));

      const { result: result2 } = renderHook(() => useSpeakerPool('BATbern57'), {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      });

      await waitFor(() => expect(result2.current.isSuccess).toBe(true));

      expect(result1.current.data).toEqual(pool1);
      expect(result2.current.data).toEqual(pool2);
      expect(speakerPoolService.getSpeakerPool).toHaveBeenCalledTimes(2);
    });
  });

  describe('useAddSpeakerToPool', () => {
    const mockResponse: SpeakerPoolResponse = {
      id: 'pool-125',
      eventId: 'event-456',
      speakerName: 'Dr. Michael Chen',
      company: 'AI Research Lab',
      expertise: 'Machine Learning, Neural Networks',
      assignedOrganizerId: 'org-john-doe',
      status: 'identified',
      notes: 'Recommended by partner',
      createdAt: '2025-12-13T13:00:00Z',
      updatedAt: '2025-12-13T13:00:00Z',
    };

    it('should add speaker to event pool with all fields', async () => {
      vi.mocked(speakerPoolService.addSpeakerToPool).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Dr. Michael Chen',
        company: 'AI Research Lab',
        expertise: 'Machine Learning, Neural Networks',
        assignedOrganizerId: 'org-john-doe',
        notes: 'Recommended by partner',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerPoolService.addSpeakerToPool).toHaveBeenCalledWith('BATbern56', request);
      expect(result.current.data).toEqual(mockResponse);
    });

    it('should add speaker with minimal required fields', async () => {
      const minimalResponse: SpeakerPoolResponse = {
        id: 'pool-126',
        eventId: 'event-456',
        speakerName: 'John Doe',
        status: 'identified',
        createdAt: '2025-12-13T13:00:00Z',
        updatedAt: '2025-12-13T13:00:00Z',
      };

      vi.mocked(speakerPoolService.addSpeakerToPool).mockResolvedValue(minimalResponse);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'John Doe',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(result.current.data?.speakerName).toBe('John Doe');
      expect(result.current.data?.company).toBeUndefined();
    });

    it('should invalidate speaker pool cache after adding speaker', async () => {
      vi.mocked(speakerPoolService.addSpeakerToPool).mockResolvedValue(mockResponse);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'New Speaker',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: speakerPoolKeys.list('BATbern56'),
      });
    });

    it('should handle validation errors', async () => {
      const error = new Error('Validation failed: speakerName is required');
      vi.mocked(speakerPoolService.addSpeakerToPool).mockRejectedValue(error);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: '',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should handle event not found errors', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(speakerPoolService.addSpeakerToPool).mockRejectedValue(error);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      result.current.mutate({ eventCode: 'BATbern999', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should handle authorization errors', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(speakerPoolService.addSpeakerToPool).mockRejectedValue(error);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: timeout');
      vi.mocked(speakerPoolService.addSpeakerToPool).mockRejectedValue(error);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should handle multiple concurrent mutations', async () => {
      const response1: SpeakerPoolResponse = {
        id: 'pool-1',
        eventId: 'event-456',
        speakerName: 'Speaker 1',
        status: 'identified',
        createdAt: '2025-12-13T13:00:00Z',
        updatedAt: '2025-12-13T13:00:00Z',
      };

      const response2: SpeakerPoolResponse = {
        id: 'pool-2',
        eventId: 'event-456',
        speakerName: 'Speaker 2',
        status: 'identified',
        createdAt: '2025-12-13T13:01:00Z',
        updatedAt: '2025-12-13T13:01:00Z',
      };

      vi.mocked(speakerPoolService.addSpeakerToPool)
        .mockResolvedValueOnce(response1)
        .mockResolvedValueOnce(response2);

      const { result } = renderHook(() => useAddSpeakerToPool(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({
        eventCode: 'BATbern56',
        request: { speakerName: 'Speaker 1' },
      });

      result.current.mutate({
        eventCode: 'BATbern56',
        request: { speakerName: 'Speaker 2' },
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(speakerPoolService.addSpeakerToPool).toHaveBeenCalledTimes(2);
    });
  });
});
