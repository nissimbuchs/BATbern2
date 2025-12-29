import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from '@/services/api/apiClient';
import { publishingService } from './publishingService';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('publishingService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('publishPhase', () => {
    it('should publish phase successfully', async () => {
      const mockData = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        eventCode: 'BATbern142',
        versionNumber: 1,
        publishedPhase: 'TOPIC',
        publishedAt: '2025-01-15T10:00:00Z',
        publishedBy: 'john.doe',
        cdnInvalidationId: 'INV123',
        cdnInvalidationStatus: 'PENDING',
        contentSnapshot: {},
        isCurrent: true,
      };

      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockData });

      const result = await publishingService.publishPhase('BATbern142', 'topic', {
        mode: 'progressive',
        notifySubscribers: true,
      });

      expect(postSpy).toHaveBeenCalledWith('/events/BATbern142/publish/topic', {
        mode: 'progressive',
        notifySubscribers: true,
      });
      expect(result).toEqual(mockData);
    });

    it('should handle 422 validation error when content not ready', async () => {
      const mockError = {
        response: {
          status: 422,
          data: {
            error: 'PUBLISH_VALIDATION_FAILED',
            message: 'Content not ready for this publishing phase',
            phase: 'AGENDA',
            validationErrors: [
              {
                field: 'sessions.timing',
                message: 'Not all sessions have timing assigned',
                requirement: 'All sessions must have startTime and endTime for agenda phase',
              },
            ],
          },
        },
      };

      vi.spyOn(apiClient, 'post').mockRejectedValue(mockError);

      await expect(
        publishingService.publishPhase('BATbern142', 'agenda', { mode: 'progressive' })
      ).rejects.toEqual(mockError);
    });

    it('should preserve 401 authentication error', async () => {
      const mockError = {
        response: {
          status: 401,
          data: { message: 'Unauthorized' },
        },
      };

      vi.spyOn(apiClient, 'post').mockRejectedValue(mockError);

      await expect(
        publishingService.publishPhase('BATbern142', 'topic', { mode: 'progressive' })
      ).rejects.toEqual(mockError);
    });

    it('should preserve 403 forbidden error', async () => {
      const mockError = {
        response: {
          status: 403,
          data: { message: 'Insufficient permissions' },
        },
      };

      vi.spyOn(apiClient, 'post').mockRejectedValue(mockError);

      await expect(
        publishingService.publishPhase('BATbern142', 'topic', { mode: 'progressive' })
      ).rejects.toEqual(mockError);
    });
  });

  describe('unpublishPhase', () => {
    it('should unpublish phase successfully', async () => {
      const mockData = {
        eventCode: 'BATbern142',
        unpublishedPhase: 'SPEAKERS',
        newCurrentPhase: 'TOPIC',
        unpublishedAt: '2025-01-15T11:00:00Z',
        unpublishedBy: 'john.doe',
      };

      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockData });

      const result = await publishingService.unpublishPhase('BATbern142', 'speakers');

      expect(postSpy).toHaveBeenCalledWith('/events/BATbern142/unpublish/speakers');
      expect(result).toEqual(mockData);
    });
  });

  describe('getPublishPreview', () => {
    it('should get publish preview', async () => {
      const mockData = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        mode: 'PROGRESSIVE',
        previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=preview',
        content: {
          topic: { title: 'BATbern 2025', date: '2025-05-15', venue: 'Kornhausforum' },
          speakers: [{ displayName: 'John Doe', companyName: 'Acme Corp' }],
        },
        validation: {
          isValid: true,
          errors: [],
        },
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockData });

      const result = await publishingService.getPublishPreview(
        'BATbern142',
        'speakers',
        'progressive'
      );

      expect(getSpy).toHaveBeenCalledWith('/events/BATbern142/publish/speakers/preview', {
        params: { mode: 'progressive' },
      });
      expect(result).toEqual(mockData);
    });
  });

  describe('getVersionHistory', () => {
    it('should get version history', async () => {
      const mockData = [
        {
          id: '123e4567-e89b-12d3-a456-426614174000',
          eventCode: 'BATbern142',
          versionNumber: 2,
          publishedPhase: 'SPEAKERS',
          publishedAt: '2025-01-15T12:00:00Z',
          publishedBy: 'john.doe',
          isCurrent: true,
        },
        {
          id: '123e4567-e89b-12d3-a456-426614174001',
          eventCode: 'BATbern142',
          versionNumber: 1,
          publishedPhase: 'TOPIC',
          publishedAt: '2025-01-15T10:00:00Z',
          publishedBy: 'john.doe',
          isCurrent: false,
        },
      ];

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockData });

      const result = await publishingService.getVersionHistory('BATbern142');

      expect(getSpy).toHaveBeenCalledWith('/events/BATbern142/publish/versions');
      expect(result).toEqual(mockData);
    });
  });

  describe('rollbackVersion', () => {
    it('should rollback to previous version', async () => {
      const mockData = {
        id: '123e4567-e89b-12d3-a456-426614174002',
        eventCode: 'BATbern142',
        versionNumber: 3,
        publishedPhase: 'TOPIC',
        publishedAt: '2025-01-15T13:00:00Z',
        publishedBy: 'john.doe',
        isCurrent: true,
        rolledBackAt: '2025-01-15T13:00:00Z',
        rolledBackBy: 'john.doe',
      };

      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockData });

      const result = await publishingService.rollbackVersion('BATbern142', 1, {
        reason: 'Incorrect speaker information published',
      });

      expect(postSpy).toHaveBeenCalledWith(
        '/events/BATbern142/publish/rollback/1',
        {
          reason: 'Incorrect speaker information published',
        }
      );
      expect(result).toEqual(mockData);
    });

    it('should handle 404 when version not found', async () => {
      const mockError = {
        response: {
          status: 404,
          data: { message: 'Version not found' },
        },
      };

      vi.spyOn(apiClient, 'post').mockRejectedValue(mockError);

      await expect(
        publishingService.rollbackVersion('BATbern142', 999, { reason: 'Test rollback' })
      ).rejects.toEqual(mockError);
    });
  });

  describe('getChangeLog', () => {
    it('should get change log for published event', async () => {
      const mockData = {
        eventCode: 'BATbern142',
        changes: [
          {
            timestamp: '2025-01-15T14:00:00Z',
            changedBy: 'john.doe',
            changeType: 'SPEAKER_ADDED',
            description: 'Added speaker: Jane Smith',
            affectedPhase: 'SPEAKERS',
          },
          {
            timestamp: '2025-01-15T13:00:00Z',
            changedBy: 'john.doe',
            changeType: 'SESSION_TIMING_UPDATED',
            description: 'Updated session timing for John Doe',
            affectedPhase: 'AGENDA',
          },
        ],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockData });

      const result = await publishingService.getChangeLog('BATbern142');

      expect(getSpy).toHaveBeenCalledWith('/events/BATbern142/publish/changelog');
      expect(result).toEqual(mockData);
    });
  });

  describe('scheduleAutoPublish', () => {
    it('should schedule auto-publish for phase', async () => {
      const mockData = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        scheduledDate: '2025-04-15T08:00:00Z',
        isEnabled: true,
        ruleArn:
          'arn:aws:events:eu-central-1:123456789012:rule/batbern-auto-publish-BATbern142-speakers',
      };

      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockData });

      const result = await publishingService.scheduleAutoPublish('BATbern142', 'speakers', {
        scheduledDate: '2025-04-15T08:00:00Z',
        notifySubscribers: true,
      });

      expect(postSpy).toHaveBeenCalledWith(
        '/events/BATbern142/publish/schedule',
        {
          scheduledDate: '2025-04-15T08:00:00Z',
          notifySubscribers: true,
        }
      );
      expect(result).toEqual(mockData);
    });
  });

  describe('cancelAutoPublish', () => {
    it('should cancel auto-publish schedule', async () => {
      const mockData = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        cancelledAt: '2025-01-15T15:00:00Z',
        cancelledBy: 'john.doe',
      };

      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: mockData });

      const result = await publishingService.cancelAutoPublish('BATbern142', 'speakers');

      expect(deleteSpy).toHaveBeenCalledWith(
        '/events/BATbern142/publish/schedule'
      );
      expect(result).toEqual(mockData);
    });
  });
});
