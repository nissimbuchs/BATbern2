/**
 * Speaker Pool Service Tests (Story 5.2 - Frontend Tests)
 *
 * Comprehensive tests for speakerPoolService HTTP client
 * Tests all API methods: add speaker to pool, get speaker pool
 *
 * Coverage:
 * - API request formatting (event code, speaker data)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { speakerPoolService } from './speakerPoolService';
import apiClient from './api/apiClient';
import type {
  SpeakerPoolEntry,
  AddSpeakerToPoolRequest,
  SpeakerPoolResponse,
} from '@/types/speakerPool.types';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('speakerPoolService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('addSpeakerToPool', () => {
    it('should add speaker to event pool with all fields', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture, DevOps, Kubernetes',
        assignedOrganizerId: 'org-john-doe',
        notes: 'Excellent speaker, presented at last 3 events',
      };

      const mockResponse: SpeakerPoolResponse = {
        id: 'pool-123',
        eventId: 'event-456',
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture, DevOps, Kubernetes',
        assignedOrganizerId: 'org-john-doe',
        status: 'identified',
        notes: 'Excellent speaker, presented at last 3 events',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.addSpeakerToPool('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/speakers/pool', request);
      expect(result).toEqual(mockResponse);
      expect(result.status).toBe('identified');
    });

    it('should add speaker with minimal required fields', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'John Doe',
      };

      const mockResponse: SpeakerPoolResponse = {
        id: 'pool-124',
        eventId: 'event-456',
        speakerName: 'John Doe',
        status: 'identified',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.addSpeakerToPool('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/speakers/pool', request);
      expect(result.speakerName).toBe('John Doe');
      expect(result.company).toBeUndefined();
      expect(result.expertise).toBeUndefined();
    });

    it('should propagate validation errors for empty speaker name', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: '',
      };

      const error = new Error('Validation failed: speakerName is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(speakerPoolService.addSpeakerToPool('BATbern56', request)).rejects.toThrow(
        'Validation failed'
      );
    });

    it('should propagate 404 errors for non-existent events', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(speakerPoolService.addSpeakerToPool('BATbern999', request)).rejects.toThrow(
        'Event not found'
      );
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(speakerPoolService.addSpeakerToPool('BATbern56', request)).rejects.toThrow(
        'Forbidden'
      );
    });

    it('should handle network failures gracefully', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Jane Smith',
      };

      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(speakerPoolService.addSpeakerToPool('BATbern56', request)).rejects.toThrow(
        'Network error'
      );
    });
  });

  describe('getSpeakerPool', () => {
    it('should fetch speaker pool for event', async () => {
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

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSpeakerPool });

      const result = await speakerPoolService.getSpeakerPool('BATbern56');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/speakers/pool');
      expect(result).toEqual(mockSpeakerPool);
      expect(result).toHaveLength(2);
      expect(result[0].status).toBe('identified');
      expect(result[1].status).toBe('contacted');
    });

    it('should return empty array when no speakers in pool', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await speakerPoolService.getSpeakerPool('BATbern56');

      expect(result).toEqual([]);
      expect(result).toHaveLength(0);
    });

    it('should propagate 404 errors for non-existent events', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerPoolService.getSpeakerPool('BATbern999')).rejects.toThrow(
        'Event not found'
      );
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Unauthorized: JWT token required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerPoolService.getSpeakerPool('BATbern56')).rejects.toThrow('Unauthorized');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: connection refused');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerPoolService.getSpeakerPool('BATbern56')).rejects.toThrow('Network error');
    });

    it('should handle malformed responses gracefully', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: null });

      const result = await speakerPoolService.getSpeakerPool('BATbern56');

      expect(result).toBeNull();
    });
  });

  describe('Service Singleton', () => {
    it('should export a singleton instance', () => {
      expect(speakerPoolService).toBeDefined();
      expect(speakerPoolService).toBeInstanceOf(Object);
      expect(typeof speakerPoolService.addSpeakerToPool).toBe('function');
      expect(typeof speakerPoolService.getSpeakerPool).toBe('function');
    });

    it('should maintain state across multiple calls', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'Test Speaker',
      };

      const mockResponse: SpeakerPoolResponse = {
        id: 'pool-125',
        eventId: 'event-456',
        speakerName: 'Test Speaker',
        status: 'identified',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      // Call twice to ensure singleton behavior
      await speakerPoolService.addSpeakerToPool('BATbern56', request);
      await speakerPoolService.addSpeakerToPool('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledTimes(2);
    });
  });
});
