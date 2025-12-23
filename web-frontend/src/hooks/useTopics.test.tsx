/**
 * useTopics Hook Tests (Story 5.2 - Frontend Tests)
 *
 * Comprehensive tests for Topic React Query hooks
 * Tests all hooks: useTopics, useTopic, useSimilarTopics, useCreateTopic, useOverrideStaleness, useSelectTopicForEvent
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
import {
  useTopics,
  useTopic,
  useSimilarTopics,
  useCreateTopic,
  useOverrideStaleness,
  useSelectTopicForEvent,
  topicKeys,
} from './useTopics';
import { topicService } from '@/services/topicService';
import type {
  Topic,
  TopicListResponse,
  CreateTopicRequest,
  OverrideStalenesRequest,
  SelectTopicForEventRequest,
} from '@/types/topic.types';

// Mock the topic service
vi.mock('@/services/topicService', () => ({
  topicService: {
    getTopics: vi.fn(),
    getTopicById: vi.fn(),
    getSimilarTopics: vi.fn(),
    createTopic: vi.fn(),
    overrideStaleness: vi.fn(),
    selectTopicForEvent: vi.fn(),
  },
}));

describe('useTopics hooks', () => {
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

  describe('topicKeys', () => {
    it('should generate correct query keys for all operations', () => {
      expect(topicKeys.all).toEqual(['topics']);
      expect(topicKeys.lists()).toEqual(['topics', 'list']);
      expect(topicKeys.list({ category: 'technical' })).toEqual([
        'topics',
        'list',
        { category: 'technical' },
      ]);
      expect(topicKeys.details()).toEqual(['topics', 'detail']);
      expect(topicKeys.detail('topic-123')).toEqual(['topics', 'detail', 'topic-123']);
      expect(topicKeys.similar('topic-123')).toEqual(['topics', 'similar', 'topic-123']);
    });
  });

  describe('useTopics', () => {
    const mockTopicListResponse: TopicListResponse = {
      data: [
        {
          id: 'topic-123',
          title: 'Cloud Native Architecture',
          description: 'Modern cloud patterns',
          category: 'technical',
          stalenessScore: 85,
          usageCount: 3,
          lastUsedDate: '2024-01-15',
          isActive: true,
          createdDate: '2023-01-01',
        },
      ],
      pagination: {
        page: 1,
        limit: 20,
        total: 1,
      },
    };

    it('should fetch topics without filters', async () => {
      vi.mocked(topicService.getTopics).mockResolvedValue(mockTopicListResponse);

      const { result } = renderHook(() => useTopics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.getTopics).toHaveBeenCalledWith(undefined);
      expect(result.current.data).toEqual(mockTopicListResponse);
      expect(result.current.data?.data).toHaveLength(1);
    });

    it('should fetch topics with filters', async () => {
      vi.mocked(topicService.getTopics).mockResolvedValue(mockTopicListResponse);

      const filters = {
        category: 'technical',
        sort: '-stalenessScore',
        page: 2,
        limit: 50,
      };

      const { result } = renderHook(() => useTopics(filters), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.getTopics).toHaveBeenCalledWith(filters);
      expect(result.current.data).toEqual(mockTopicListResponse);
    });

    it('should handle loading state', async () => {
      vi.mocked(topicService.getTopics).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockTopicListResponse), 100))
      );

      const { result } = renderHook(() => useTopics(), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toBeDefined();
    });

    it('should handle errors gracefully', async () => {
      const error = new Error('Network failure');
      vi.mocked(topicService.getTopics).mockRejectedValue(error);

      const { result } = renderHook(() => useTopics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
      expect(result.current.data).toBeUndefined();
    });

    it('should cache results with 5 minute stale time', async () => {
      vi.mocked(topicService.getTopics).mockResolvedValue(mockTopicListResponse);

      const { result } = renderHook(() => useTopics(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Second render should use cached data
      const { result: result2 } = renderHook(() => useTopics(), {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      });

      expect(result2.current.data).toEqual(mockTopicListResponse);
      expect(topicService.getTopics).toHaveBeenCalledTimes(1); // Only called once due to cache
    });
  });

  describe('useTopic', () => {
    const mockTopic: Topic = {
      id: 'topic-123',
      title: 'Cloud Native Architecture',
      description: 'Modern cloud patterns',
      category: 'technical',
      stalenessScore: 85,
      usageCount: 3,
      lastUsedDate: '2024-01-15',
      isActive: true,
      createdDate: '2023-01-01',
    };

    it('should fetch topic by ID without includes', async () => {
      vi.mocked(topicService.getTopicById).mockResolvedValue(mockTopic);

      const { result } = renderHook(() => useTopic('topic-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.getTopicById).toHaveBeenCalledWith('topic-123', undefined);
      expect(result.current.data).toEqual(mockTopic);
    });

    it('should fetch topic with includes parameter', async () => {
      const topicWithSimilarity: Topic = {
        ...mockTopic,
        similarityScores: [{ topicCode: 'cloud-security', score: 0.75 }],
      };

      vi.mocked(topicService.getTopicById).mockResolvedValue(topicWithSimilarity);

      const { result } = renderHook(() => useTopic('topic-123', 'similarity,history'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.getTopicById).toHaveBeenCalledWith('topic-123', 'similarity,history');
      expect(result.current.data?.similarityScores).toBeDefined();
    });

    it('should not fetch when ID is empty', () => {
      const { result } = renderHook(() => useTopic(''), {
        wrapper: createWrapper(),
      });

      expect(result.current.isFetching).toBe(false);
      expect(topicService.getTopicById).not.toHaveBeenCalled();
    });

    it('should handle 404 errors for non-existent topics', async () => {
      const error = new Error('Topic not found');
      vi.mocked(topicService.getTopicById).mockRejectedValue(error);

      const { result } = renderHook(() => useTopic('non-existent'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useSimilarTopics', () => {
    const mockSimilarTopics: Topic[] = [
      {
        id: 'topic-456',
        title: 'Cloud Native Design Patterns',
        description: 'Similar topic',
        category: 'technical',
        stalenessScore: 90,
        usageCount: 2,
        lastUsedDate: '2024-02-01',
        isActive: true,
        createdDate: '2023-06-01',
      },
    ];

    it('should fetch similar topics for given ID', async () => {
      vi.mocked(topicService.getSimilarTopics).mockResolvedValue(mockSimilarTopics);

      const { result } = renderHook(() => useSimilarTopics('topic-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.getSimilarTopics).toHaveBeenCalledWith('topic-123');
      expect(result.current.data).toEqual(mockSimilarTopics);
      expect(result.current.data).toHaveLength(1);
    });

    it('should not fetch when ID is empty', () => {
      const { result } = renderHook(() => useSimilarTopics(''), {
        wrapper: createWrapper(),
      });

      expect(result.current.isFetching).toBe(false);
      expect(topicService.getSimilarTopics).not.toHaveBeenCalled();
    });

    it('should cache similar topics with 1 hour stale time', async () => {
      vi.mocked(topicService.getSimilarTopics).mockResolvedValue(mockSimilarTopics);

      const { result } = renderHook(() => useSimilarTopics('topic-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // Second render should use cached data
      const { result: result2 } = renderHook(() => useSimilarTopics('topic-123'), {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      });

      expect(result2.current.data).toEqual(mockSimilarTopics);
      expect(topicService.getSimilarTopics).toHaveBeenCalledTimes(1);
    });

    it('should return empty array when no similar topics found', async () => {
      vi.mocked(topicService.getSimilarTopics).mockResolvedValue([]);

      const { result } = renderHook(() => useSimilarTopics('topic-unique'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(result.current.data).toEqual([]);
    });
  });

  describe('useCreateTopic', () => {
    const mockCreatedTopic: Topic = {
      id: 'topic-new',
      title: 'AI-Powered DevOps',
      description: 'Exploring AI in DevOps',
      category: 'technical',
      stalenessScore: 100,
      usageCount: 0,
      isActive: true,
      createdDate: '2025-12-13',
      lastUsedDate: null,
    };

    it('should create a new topic', async () => {
      vi.mocked(topicService.createTopic).mockResolvedValue(mockCreatedTopic);

      const { result } = renderHook(() => useCreateTopic(), {
        wrapper: createWrapper(),
      });

      const request: CreateTopicRequest = {
        title: 'AI-Powered DevOps',
        description: 'Exploring AI in DevOps',
        category: 'technical',
      };

      result.current.mutate(request);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.createTopic).toHaveBeenCalledWith(request);
      expect(result.current.data).toEqual(mockCreatedTopic);
    });

    it('should invalidate topic lists cache after creation', async () => {
      vi.mocked(topicService.createTopic).mockResolvedValue(mockCreatedTopic);

      const wrapper = createWrapper();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateTopic(), { wrapper });

      const request: CreateTopicRequest = {
        title: 'New Topic',
        description: 'Description',
        category: 'technical',
      };

      result.current.mutate(request);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: topicKeys.lists() });
    });

    it('should handle validation errors', async () => {
      const error = new Error('Validation failed: title is required');
      vi.mocked(topicService.createTopic).mockRejectedValue(error);

      const { result } = renderHook(() => useCreateTopic(), {
        wrapper: createWrapper(),
      });

      const request: CreateTopicRequest = {
        title: '',
        description: 'Invalid',
        category: 'technical',
      };

      result.current.mutate(request);

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useOverrideStaleness', () => {
    const mockUpdatedTopic: Topic = {
      id: 'topic-123',
      title: 'Cloud Native Architecture',
      description: 'Modern cloud patterns',
      category: 'technical',
      stalenessScore: 50, // Overridden
      usageCount: 3,
      lastUsedDate: '2024-01-15',
      isActive: true,
      createdDate: '2023-01-01',
    };

    it('should override staleness score with justification', async () => {
      vi.mocked(topicService.overrideStaleness).mockResolvedValue(mockUpdatedTopic);

      const { result } = renderHook(() => useOverrideStaleness(), {
        wrapper: createWrapper(),
      });

      const request: OverrideStalenesRequest = {
        stalenessScore: 50,
        justification: 'Partner explicitly requested',
      };

      result.current.mutate({ id: 'topic-123', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.overrideStaleness).toHaveBeenCalledWith('topic-123', request);
      expect(result.current.data?.stalenessScore).toBe(50);
    });

    it('should invalidate topic detail and lists cache after override', async () => {
      vi.mocked(topicService.overrideStaleness).mockResolvedValue(mockUpdatedTopic);

      const wrapper = createWrapper();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useOverrideStaleness(), { wrapper });

      const request: OverrideStalenesRequest = {
        stalenessScore: 50,
        justification: 'Override reason',
      };

      result.current.mutate({ id: 'topic-123', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: topicKeys.detail('topic-123') });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: topicKeys.lists() });
    });

    it('should handle authorization errors', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(topicService.overrideStaleness).mockRejectedValue(error);

      const { result } = renderHook(() => useOverrideStaleness(), {
        wrapper: createWrapper(),
      });

      const request: OverrideStalenesRequest = {
        stalenessScore: 50,
        justification: 'Override',
      };

      result.current.mutate({ id: 'topic-123', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useSelectTopicForEvent', () => {
    it('should select topic for event and transition workflow state', async () => {
      vi.mocked(topicService.selectTopicForEvent).mockResolvedValue();

      const { result } = renderHook(() => useSelectTopicForEvent(), {
        wrapper: createWrapper(),
      });

      const request: SelectTopicForEventRequest = {
        topicCode: 'mobile-iot',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.selectTopicForEvent).toHaveBeenCalledWith('BATbern56', request);
    });

    it('should select topic with justification override', async () => {
      vi.mocked(topicService.selectTopicForEvent).mockResolvedValue();

      const { result } = renderHook(() => useSelectTopicForEvent(), {
        wrapper: createWrapper(),
      });

      const request: SelectTopicForEventRequest = {
        topicCode: 'mobile-iot',
        justification: 'Partner explicitly requested despite high similarity',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(topicService.selectTopicForEvent).toHaveBeenCalledWith('BATbern56', request);
    });

    it('should invalidate event and topic caches after selection', async () => {
      vi.mocked(topicService.selectTopicForEvent).mockResolvedValue();

      const wrapper = createWrapper();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });

      const request: SelectTopicForEventRequest = {
        topicCode: 'mobile-iot',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern56'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: topicKeys.detail('mobile-iot') });
    });

    it('should handle invalid workflow state errors', async () => {
      const error = new Error('Invalid workflow state: Event must be in CREATED state');
      vi.mocked(topicService.selectTopicForEvent).mockRejectedValue(error);

      const { result } = renderHook(() => useSelectTopicForEvent(), {
        wrapper: createWrapper(),
      });

      const request: SelectTopicForEventRequest = {
        topicCode: 'mobile-iot',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });

    it('should handle event not found errors', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(topicService.selectTopicForEvent).mockRejectedValue(error);

      const { result } = renderHook(() => useSelectTopicForEvent(), {
        wrapper: createWrapper(),
      });

      const request: SelectTopicForEventRequest = {
        topicCode: 'mobile-iot',
      };

      result.current.mutate({ eventCode: 'BATbern999', request });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error).toEqual(error);
    });
  });
});
