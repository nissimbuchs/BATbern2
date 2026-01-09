/**
 * Topic Lookup/Cache Utility Tests (Story 5.2a - Event Batch Import with Topics)
 *
 * Tests the topic category lookup and caching logic used during batch event import.
 * Ensures efficient topic assignment by minimizing API calls.
 *
 * Coverage:
 * - Cache hit/miss scenarios
 * - Topic creation when not found
 * - Error handling and propagation
 * - Cache management (clear, size limits)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getOrCreateTopicByCategory, clearTopicCache, getTopicCacheSize } from './topicLookup';
import { topicService } from '@/services/topicService';
import type { Topic, TopicListResponse } from '@/types/topic.types';

// Mock the topicService
vi.mock('@/services/topicService', () => ({
  topicService: {
    getTopics: vi.fn(),
    createTopic: vi.fn(),
  },
}));

describe('topicLookup', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearTopicCache(); // Clear cache before each test
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getOrCreateTopicByCategory', () => {
    it('should return cached topic ID on subsequent calls', async () => {
      const mockResponse: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-cached-123',
            title: 'Frontend & UI',
            category: 'Frontend & UI',
            description: 'Frontend technologies',
            stalenessScore: 85,
            usageCount: 5,
            active: true,
            createdDate: '2023-01-01',
            lastUsedDate: '2024-06-15',
            colorZone: 'green',
            status: 'AVAILABLE',
          },
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      vi.mocked(topicService.getTopics).mockResolvedValue(mockResponse);

      // First call - should hit API
      const topicId1 = await getOrCreateTopicByCategory('Frontend & UI');

      expect(topicService.getTopics).toHaveBeenCalledTimes(1);
      expect(topicService.getTopics).toHaveBeenCalledWith({
        limit: 100,
      });
      expect(topicId1).toBe('topic-cached-123');

      // Second call - should use cache
      const topicId2 = await getOrCreateTopicByCategory('Frontend & UI');

      expect(topicService.getTopics).toHaveBeenCalledTimes(1); // Still only 1 call
      expect(topicId2).toBe('topic-cached-123');
      expect(getTopicCacheSize()).toBe(1);
    });

    it('should fetch topic from API when not cached', async () => {
      const mockResponse: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-api-456',
            title: 'Cloud & Infrastructure',
            category: 'Cloud & Infrastructure',
            description: 'Cloud technologies',
            stalenessScore: 90,
            usageCount: 3,
            active: true,
            createdDate: '2023-03-01',
            lastUsedDate: '2024-05-20',
            colorZone: 'green',
            status: 'AVAILABLE',
          },
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      vi.mocked(topicService.getTopics).mockResolvedValue(mockResponse);

      const topicId = await getOrCreateTopicByCategory('Cloud & Infrastructure');

      expect(topicService.getTopics).toHaveBeenCalledWith({
        limit: 100,
      });
      expect(topicId).toBe('topic-api-456');
    });

    it('should create new topic when not found in API', async () => {
      const mockEmptyResponse: TopicListResponse = {
        data: [],
        pagination: { page: 1, limit: 20, total: 0 },
      };

      const mockCreatedTopic: Topic = {
        topicCode: 'topic-new-789',
        title: 'Security',
        category: 'Security',
        description: 'Topic category: Security',
        stalenessScore: 100,
        usageCount: 0,
        active: true,
        createdDate: '2025-12-15',
        lastUsedDate: null,
        colorZone: 'green',
        status: 'available',
      };

      vi.mocked(topicService.getTopics).mockResolvedValue(mockEmptyResponse);
      vi.mocked(topicService.createTopic).mockResolvedValue(mockCreatedTopic);

      const topicId = await getOrCreateTopicByCategory('Security');

      expect(topicService.getTopics).toHaveBeenCalledWith({
        limit: 100,
      });
      expect(topicService.createTopic).toHaveBeenCalledWith({
        title: 'Security',
        category: 'technical',
        description: 'Topic: Security',
      });
      expect(topicId).toBe('topic-new-789');
    });

    it('should cache newly created topic', async () => {
      const mockEmptyResponse: TopicListResponse = {
        data: [],
        pagination: { page: 1, limit: 20, total: 0 },
      };

      const mockCreatedTopic: Topic = {
        topicCode: 'topic-created-999',
        title: 'Data & Analytics',
        category: 'Data & Analytics',
        description: 'Topic category: Data & Analytics',
        stalenessScore: 100,
        usageCount: 0,
        active: true,
        createdDate: '2025-12-15',
        lastUsedDate: null,
        colorZone: 'green',
        status: 'available',
      };

      vi.mocked(topicService.getTopics).mockResolvedValue(mockEmptyResponse);
      vi.mocked(topicService.createTopic).mockResolvedValue(mockCreatedTopic);

      // First call - creates topic
      const topicId1 = await getOrCreateTopicByCategory('Data & Analytics');

      expect(topicService.createTopic).toHaveBeenCalledTimes(1);
      expect(topicId1).toBe('topic-created-999');

      // Second call - uses cache
      const topicId2 = await getOrCreateTopicByCategory('Data & Analytics');

      expect(topicService.getTopics).toHaveBeenCalledTimes(1); // Only first lookup
      expect(topicService.createTopic).toHaveBeenCalledTimes(1); // Only first creation
      expect(topicId2).toBe('topic-created-999');
    });

    it('should handle multiple different categories', async () => {
      const mockResponse1: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-1',
            title: 'Cat1',
            category: 'Cat1',
            stalenessScore: 80,
            active: true,
            colorZone: 'green',
            status: 'AVAILABLE',
          } as Topic,
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      const mockResponse2: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-2',
            title: 'Cat2',
            category: 'Cat2',
            stalenessScore: 80,
            active: true,
            colorZone: 'green',
            status: 'AVAILABLE',
          } as Topic,
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      vi.mocked(topicService.getTopics)
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2);

      const id1 = await getOrCreateTopicByCategory('Cat1');
      const id2 = await getOrCreateTopicByCategory('Cat2');

      expect(id1).toBe('topic-1');
      expect(id2).toBe('topic-2');
      expect(getTopicCacheSize()).toBe(2);
    });

    it('should propagate API errors when fetching topics', async () => {
      const error = new Error('Network failure');
      vi.mocked(topicService.getTopics).mockRejectedValue(error);

      await expect(getOrCreateTopicByCategory('Frontend & UI')).rejects.toThrow('Network failure');
    });

    it('should propagate API errors when creating topics', async () => {
      const mockEmptyResponse: TopicListResponse = {
        data: [],
        pagination: { page: 1, limit: 20, total: 0 },
      };

      const error = new Error('Validation failed: title is required');
      vi.mocked(topicService.getTopics).mockResolvedValue(mockEmptyResponse);
      vi.mocked(topicService.createTopic).mockRejectedValue(error);

      await expect(getOrCreateTopicByCategory('Invalid')).rejects.toThrow('Validation failed');
    });
  });

  describe('clearTopicCache', () => {
    it('should clear all cached topics', async () => {
      const mockResponse: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-clear-test',
            title: 'Test',
            category: 'Test',
            stalenessScore: 80,
            active: true,
            colorZone: 'green',
            status: 'AVAILABLE',
          } as Topic,
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      vi.mocked(topicService.getTopics).mockResolvedValue(mockResponse);

      await getOrCreateTopicByCategory('Test');
      expect(getTopicCacheSize()).toBe(1);

      clearTopicCache();
      expect(getTopicCacheSize()).toBe(0);

      // Next call should hit API again
      await getOrCreateTopicByCategory('Test');
      expect(topicService.getTopics).toHaveBeenCalledTimes(2);
    });
  });

  describe('getTopicCacheSize', () => {
    it('should return 0 for empty cache', () => {
      expect(getTopicCacheSize()).toBe(0);
    });

    it('should return correct cache size', async () => {
      const mockResponse1: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-1',
            title: 'Cat1',
            category: 'Cat1',
            stalenessScore: 80,
            active: true,
            colorZone: 'green',
            status: 'AVAILABLE',
          } as Topic,
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      const mockResponse2: TopicListResponse = {
        data: [
          {
            topicCode: 'topic-2',
            title: 'Cat2',
            category: 'Cat2',
            stalenessScore: 80,
            active: true,
            colorZone: 'green',
            status: 'AVAILABLE',
          } as Topic,
        ],
        pagination: { page: 1, limit: 20, total: 1 },
      };

      vi.mocked(topicService.getTopics)
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2);

      expect(getTopicCacheSize()).toBe(0);

      await getOrCreateTopicByCategory('Cat1');
      expect(getTopicCacheSize()).toBe(1);

      await getOrCreateTopicByCategory('Cat2');
      expect(getTopicCacheSize()).toBe(2);
    });
  });
});
