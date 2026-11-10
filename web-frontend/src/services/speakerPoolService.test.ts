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
  PatchSpeakerPoolRequest,
  SpeakerPoolResponse,
  SendInvitationRequest,
  SendInvitationResponse,
} from '@/types/speakerPool.types';
import { aSpeakerPoolEntry } from '@/test/fixtures';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
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
        ...aSpeakerPoolEntry(),
        id: 'pool-123',
        eventId: 'event-456',
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture, DevOps, Kubernetes',
        assignedOrganizerId: 'org-john-doe',
        status: 'IDENTIFIED',
        notes: 'Excellent speaker, presented at last 3 events',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.addSpeakerToPool('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/speakers/pool', request);
      expect(result).toEqual(mockResponse);
      expect(result.status).toBe('IDENTIFIED');
    });

    it('should add speaker with minimal required fields', async () => {
      const request: AddSpeakerToPoolRequest = {
        speakerName: 'John Doe',
      };

      const mockResponse: SpeakerPoolResponse = {
        ...aSpeakerPoolEntry(),
        id: 'pool-124',
        eventId: 'event-456',
        speakerName: 'John Doe',
        status: 'IDENTIFIED',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.addSpeakerToPool('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern56/speakers/pool', request);
      expect(result.speakerName).toBe('John Doe');
      // EMS sends every property; an unset one is null
      expect(result.company).toBeNull();
      expect(result.expertise).toBeNull();
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
          ...aSpeakerPoolEntry(),
          id: 'pool-123',
          eventId: 'event-456',
          speakerName: 'Dr. Jane Smith',
          company: 'TechCorp Solutions AG',
          expertise: 'Cloud Architecture, DevOps',
          assignedOrganizerId: 'org-john-doe',
          status: 'IDENTIFIED',
          notes: 'Excellent speaker',
          createdAt: '2025-12-13T10:00:00Z',
          updatedAt: '2025-12-13T10:00:00Z',
        },
        {
          ...aSpeakerPoolEntry(),
          id: 'pool-124',
          eventId: 'event-456',
          speakerName: 'Prof. Robert Johnson',
          company: 'University of Bern',
          expertise: 'AI/ML, Data Science',
          assignedOrganizerId: 'org-jane-doe',
          status: 'CONTACTED',
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
      expect(result[0].status).toBe('IDENTIFIED');
      expect(result[1].status).toBe('CONTACTED');
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

  describe('deleteSpeakerFromPool', () => {
    it('should delete speaker from event pool', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      await speakerPoolService.deleteSpeakerFromPool('BATbern56', 'speaker-uuid-123');

      expect(apiClient.delete).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool/speaker-uuid-123'
      );
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const error = new Error('Speaker not found');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(
        speakerPoolService.deleteSpeakerFromPool('BATbern56', 'nonexistent-id')
      ).rejects.toThrow('Speaker not found');
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(
        speakerPoolService.deleteSpeakerFromPool('BATbern56', 'speaker-uuid-123')
      ).rejects.toThrow('Forbidden');
    });
  });

  describe('patchSpeakerPool', () => {
    it('should patch speaker pool entry with all fields', async () => {
      const request: PatchSpeakerPoolRequest = {
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp Solutions AG',
        expertise: 'Cloud Architecture',
        assignedOrganizerId: 'org-new-assignee',
        notes: 'Updated notes',
      };

      const mockResponse: SpeakerPoolResponse = {
        ...aSpeakerPoolEntry(),
        id: 'pool-123',
        eventId: 'event-456',
        speakerName: 'Dr. Jane Smith',
        assignedOrganizerId: 'org-new-assignee',
        status: 'IDENTIFIED',
        notes: 'Updated notes',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T14:00:00Z',
      };

      vi.mocked(apiClient.patch).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.patchSpeakerPool(
        'BATbern56',
        'speaker-uuid-123',
        request
      );

      expect(apiClient.patch).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool/speaker-uuid-123',
        request
      );
      expect(result).toEqual(mockResponse);
      expect(result.assignedOrganizerId).toBe('org-new-assignee');
    });

    it('should patch speaker pool entry with partial fields', async () => {
      const request: PatchSpeakerPoolRequest = {
        notes: 'Only updating notes',
      };

      const mockResponse: SpeakerPoolResponse = {
        ...aSpeakerPoolEntry(),
        id: 'pool-123',
        eventId: 'event-456',
        speakerName: 'Dr. Jane Smith',
        status: 'IDENTIFIED',
        notes: 'Only updating notes',
        createdAt: '2025-12-13T10:00:00Z',
        updatedAt: '2025-12-13T14:00:00Z',
      };

      vi.mocked(apiClient.patch).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.patchSpeakerPool(
        'BATbern56',
        'speaker-uuid-123',
        request
      );

      expect(result.notes).toBe('Only updating notes');
    });

    it('should propagate errors on patch failure', async () => {
      const error = new Error('Speaker not found');
      vi.mocked(apiClient.patch).mockRejectedValue(error);

      await expect(
        speakerPoolService.patchSpeakerPool('BATbern56', 'nonexistent-id', { notes: 'test' })
      ).rejects.toThrow('Speaker not found');
    });
  });

  describe('sendInvitation', () => {
    it('should send invitation with options', async () => {
      const options: SendInvitationRequest = {
        responseDeadline: '2026-02-01T00:00:00Z',
        contentDeadline: '2026-03-01T00:00:00Z',
        locale: 'de',
        email: 'speaker@example.com',
      };

      const mockResponse: SendInvitationResponse = {
        speakerPoolId: 'pool-entry-uuid-123',
        username: 'speaker-username',
        email: 'speaker@example.com',
        status: 'INVITED',
        invitedAt: '2025-12-15T10:00:00Z',
        responseDeadline: '2026-02-01T00:00:00Z',
        contentDeadline: '2026-03-01T00:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.sendInvitation(
        'BATbern56',
        'speaker-username',
        options
      );

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-username/send-invitation',
        options
      );
      expect(result).toEqual(mockResponse);
      expect(result.status).toBe('INVITED');
    });

    it('should send invitation without options (empty body)', async () => {
      const mockResponse: SendInvitationResponse = {
        speakerPoolId: 'pool-entry-uuid-456',
        username: 'speaker-username',
        email: 'speaker@example.com',
        status: 'INVITED',
        invitedAt: '2025-12-15T10:00:00Z',
        responseDeadline: '2025-12-29T00:00:00Z',
        contentDeadline: null,
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerPoolService.sendInvitation('BATbern56', 'speaker-username');

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-username/send-invitation',
        {}
      );
      expect(result).toEqual(mockResponse);
    });

    it('should propagate errors for invalid state transitions', async () => {
      const error = new Error('Speaker already invited');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerPoolService.sendInvitation('BATbern56', 'speaker-username')
      ).rejects.toThrow('Speaker already invited');
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
        ...aSpeakerPoolEntry(),
        id: 'pool-125',
        eventId: 'event-456',
        speakerName: 'Test Speaker',
        status: 'IDENTIFIED',
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
