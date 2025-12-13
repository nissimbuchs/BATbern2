/**
 * Topic Service Tests (Story 5.2 - Frontend Tests)
 *
 * Comprehensive tests for topicService HTTP client
 * Tests all API methods: get topics, get by ID, create, override staleness, get similar, select for event
 *
 * Coverage:
 * - API request formatting (filters, pagination, includes)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { topicService } from './topicService';
import apiClient from './api/apiClient';
import type {
  Topic,
  TopicListResponse,
  CreateTopicRequest,
  OverrideStalenesRequest,
  TopicFilters,
  SelectTopicForEventRequest,
} from '@/types/topic.types';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('topicService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getTopics', () => {
    it('should fetch topics without filters', async () => {
      const mockResponse: TopicListResponse = {
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

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await topicService.getTopics();

      expect(apiClient.get).toHaveBeenCalledWith('/topics', { params: {} });
      expect(result).toEqual(mockResponse);
    });

    it('should fetch topics with category filter', async () => {
      const mockResponse: TopicListResponse = {
        data: [],
        pagination: { page: 1, limit: 20, total: 0 },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const filters: TopicFilters = {
        category: 'technical',
      };

      await topicService.getTopics(filters);

      expect(apiClient.get).toHaveBeenCalledWith('/topics', {
        params: {
          filter: JSON.stringify({ category: 'technical' }),
        },
      });
    });

    it('should fetch topics with multiple filters and pagination', async () => {
      const mockResponse: TopicListResponse = {
        data: [],
        pagination: { page: 2, limit: 50, total: 120 },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const filters: TopicFilters = {
        category: 'management',
        status: 'active',
        sort: '-stalenessScore',
        page: 2,
        limit: 50,
        include: 'similarity,history',
      };

      await topicService.getTopics(filters);

      expect(apiClient.get).toHaveBeenCalledWith('/topics', {
        params: {
          filter: JSON.stringify({ category: 'management', status: 'active' }),
          sort: '-stalenessScore',
          page: 2,
          limit: 50,
          include: 'similarity,history',
        },
      });
    });

    it('should propagate API errors', async () => {
      const error = new Error('Network failure');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(topicService.getTopics()).rejects.toThrow('Network failure');
    });
  });

  describe('getTopicById', () => {
    it('should fetch topic by ID without includes', async () => {
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

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTopic });

      const result = await topicService.getTopicById('topic-123');

      expect(apiClient.get).toHaveBeenCalledWith('/topics/topic-123', { params: {} });
      expect(result).toEqual(mockTopic);
    });

    it('should fetch topic by ID with includes', async () => {
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
        similarityScores: [{ topicId: 'topic-456', score: 0.75 }],
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTopic });

      const result = await topicService.getTopicById('topic-123', 'similarity,history');

      expect(apiClient.get).toHaveBeenCalledWith('/topics/topic-123', {
        params: { include: 'similarity,history' },
      });
      expect(result).toEqual(mockTopic);
    });

    it('should propagate 404 errors for non-existent topics', async () => {
      const error = new Error('Topic not found');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(topicService.getTopicById('non-existent')).rejects.toThrow('Topic not found');
    });
  });

  describe('createTopic', () => {
    it('should create a new topic', async () => {
      const request: CreateTopicRequest = {
        title: 'AI-Powered DevOps',
        description: 'Exploring AI in DevOps workflows',
        category: 'technical',
      };

      const mockCreatedTopic: Topic = {
        id: 'topic-new',
        ...request,
        stalenessScore: 100,
        usageCount: 0,
        isActive: true,
        createdDate: '2025-12-13',
        lastUsedDate: null,
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCreatedTopic });

      const result = await topicService.createTopic(request);

      expect(apiClient.post).toHaveBeenCalledWith('/topics', request);
      expect(result).toEqual(mockCreatedTopic);
      expect(result.stalenessScore).toBe(100); // Default for new topics
    });

    it('should propagate validation errors', async () => {
      const request: CreateTopicRequest = {
        title: '',
        description: 'Invalid topic',
        category: 'technical',
      };

      const error = new Error('Validation failed: title is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(topicService.createTopic(request)).rejects.toThrow('Validation failed');
    });
  });

  describe('overrideStaleness', () => {
    it('should override staleness score with justification', async () => {
      const request: OverrideStalenesRequest = {
        stalenessScore: 50,
        justification: 'Partner explicitly requested this topic',
      };

      const mockUpdatedTopic: Topic = {
        id: 'topic-123',
        title: 'Cloud Native Architecture',
        description: 'Modern cloud patterns',
        category: 'technical',
        stalenessScore: 50, // Overridden value
        usageCount: 3,
        lastUsedDate: '2024-01-15',
        isActive: true,
        createdDate: '2023-01-01',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockUpdatedTopic });

      const result = await topicService.overrideStaleness('topic-123', request);

      expect(apiClient.put).toHaveBeenCalledWith('/topics/topic-123/override-staleness', request);
      expect(result.stalenessScore).toBe(50);
    });

    it('should propagate authorization errors', async () => {
      const request: OverrideStalenesRequest = {
        stalenessScore: 50,
        justification: 'Override',
      };

      const error = new Error('Unauthorized: ORGANIZER role required');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(topicService.overrideStaleness('topic-123', request)).rejects.toThrow(
        'Unauthorized'
      );
    });
  });

  describe('getSimilarTopics', () => {
    it('should fetch similar topics (>70% similarity)', async () => {
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

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSimilarTopics });

      const result = await topicService.getSimilarTopics('topic-123');

      expect(apiClient.get).toHaveBeenCalledWith('/topics/topic-123/similar');
      expect(result).toEqual(mockSimilarTopics);
      expect(result).toHaveLength(1);
    });

    it('should return empty array when no similar topics found', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await topicService.getSimilarTopics('topic-unique');

      expect(result).toEqual([]);
    });
  });

  describe('selectTopicForEvent', () => {
    it('should select topic for event and transition workflow state', async () => {
      const request: SelectTopicForEventRequest = {
        topicId: 'topic-123',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await topicService.selectTopicForEvent('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/topics', request);
    });

    it('should select topic with justification override', async () => {
      const request: SelectTopicForEventRequest = {
        topicId: 'topic-123',
        justification: 'Partner explicitly requested despite high similarity',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await topicService.selectTopicForEvent('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/topics', request);
    });

    it('should propagate invalid state errors', async () => {
      const request: SelectTopicForEventRequest = {
        topicId: 'topic-123',
      };

      const error = new Error('Invalid workflow state: Event must be in CREATED state');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(topicService.selectTopicForEvent('BATbern56', request)).rejects.toThrow(
        'Invalid workflow state'
      );
    });
  });

  describe('recalculateStaleness', () => {
    it('should trigger staleness recalculation', async () => {
      const mockResponse = 'Staleness scores recalculated for 42 topics';
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await topicService.recalculateStaleness();

      expect(apiClient.post).toHaveBeenCalledWith('/topics/recalculate-staleness');
      expect(result).toBe(mockResponse);
    });

    it('should require ORGANIZER role', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(topicService.recalculateStaleness()).rejects.toThrow('Forbidden');
    });
  });

  describe('calculateSimilarities', () => {
    it('should trigger similarity calculation for all topics', async () => {
      const mockResponse = 'Similarity scores calculated for 42 topics';
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await topicService.calculateSimilarities();

      expect(apiClient.post).toHaveBeenCalledWith('/topics/calculate-similarities');
      expect(result).toBe(mockResponse);
    });

    it('should require ORGANIZER role', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(topicService.calculateSimilarities()).rejects.toThrow('Forbidden');
    });
  });
});
