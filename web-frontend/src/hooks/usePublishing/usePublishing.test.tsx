import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePublishing } from './usePublishing';
import * as publishingService from '@/services/publishingService/publishingService';

// Mock publishingService
vi.mock('@/services/publishingService/publishingService');

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('usePublishing', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('publishPhase', () => {
    it('should publish phase successfully', async () => {
      const mockPublishResponse = {
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

      vi.mocked(publishingService.publishPhase).mockResolvedValue(mockPublishResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.publishPhase('topic');
      });

      await waitFor(() => {
        expect(result.current.isPublishing).toBe(false);
      });

      expect(publishingService.publishPhase).toHaveBeenCalledWith('BATbern142', 'topic');
    });

    it('should handle validation error when content not ready', async () => {
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
              },
            ],
          },
        },
      };

      vi.mocked(publishingService.publishPhase).mockRejectedValue(mockError);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.publishPhase('agenda');
      });

      await waitFor(() => {
        expect(result.current.publishError).toBeTruthy();
        expect(result.current.validationErrors).toHaveLength(1);
      });
    });
  });

  describe('unpublishPhase', () => {
    it('should unpublish phase successfully', async () => {
      const mockUnpublishResponse = {
        eventCode: 'BATbern142',
        unpublishedPhase: 'SPEAKERS',
        newCurrentPhase: 'TOPIC',
        unpublishedAt: '2025-01-15T11:00:00Z',
        unpublishedBy: 'john.doe',
      };

      vi.mocked(publishingService.unpublishPhase).mockResolvedValue(mockUnpublishResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.unpublishPhase('speakers');
      });

      await waitFor(() => {
        expect(result.current.isUnpublishing).toBe(false);
      });

      expect(publishingService.unpublishPhase).toHaveBeenCalledWith('BATbern142', 'speakers');
    });
  });

  describe('preview', () => {
    it('should fetch publish preview', async () => {
      const mockPreview = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        mode: 'PROGRESSIVE',
        previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=preview',
        content: {
          topic: { title: 'BATbern 2025' },
          speakers: [{ displayName: 'John Doe' }],
        },
        validation: {
          isValid: true,
          errors: [],
        },
      };

      vi.mocked(publishingService.getPublishPreview).mockResolvedValue(mockPreview);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.fetchPreview('speakers');
      });

      await waitFor(() => {
        expect(result.current.preview).toEqual(mockPreview);
      });

      expect(publishingService.getPublishPreview).toHaveBeenCalledWith('BATbern142', 'speakers');
    });
  });

  describe('change log', () => {
    it('should fetch change log', async () => {
      const mockChangeLog = {
        eventCode: 'BATbern142',
        changes: [
          {
            timestamp: '2025-01-15T14:00:00Z',
            changedBy: 'john.doe',
            changeType: 'SPEAKER_ADDED',
            description: 'Added speaker: Jane Smith',
            affectedPhase: 'SPEAKERS',
          },
        ],
      };

      vi.mocked(publishingService.getChangeLog).mockResolvedValue(mockChangeLog);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.changeLog).toEqual(mockChangeLog);
      });

      expect(publishingService.getChangeLog).toHaveBeenCalledWith('BATbern142');
    });
  });

  describe('auto-publish scheduling', () => {
    it('should schedule auto-publish successfully', async () => {
      const mockScheduleResponse = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        scheduledDate: '2025-04-15T08:00:00Z',
        isEnabled: true,
        ruleArn: 'arn:aws:events:eu-central-1:123456789012:rule/batbern-auto-publish',
      };

      vi.mocked(publishingService.scheduleAutoPublish).mockResolvedValue(mockScheduleResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.scheduleAutoPublish('speakers', {
          scheduledDate: '2025-04-15T08:00:00Z',
        });
      });

      await waitFor(() => {
        expect(result.current.isScheduling).toBe(false);
      });

      expect(publishingService.scheduleAutoPublish).toHaveBeenCalledWith('BATbern142', 'speakers', {
        scheduledDate: '2025-04-15T08:00:00Z',
      });
    });

    it('should cancel auto-publish schedule successfully', async () => {
      const mockCancelResponse = {
        eventCode: 'BATbern142',
        phase: 'SPEAKERS',
        cancelledAt: '2025-01-15T15:00:00Z',
        cancelledBy: 'john.doe',
      };

      vi.mocked(publishingService.cancelAutoPublish).mockResolvedValue(mockCancelResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.cancelAutoPublish('speakers');
      });

      await waitFor(() => {
        expect(result.current.isCancelling).toBe(false);
      });

      expect(publishingService.cancelAutoPublish).toHaveBeenCalledWith('BATbern142', 'speakers');
    });
  });

  describe('real-time updates', () => {
    it('should refetch data when publishing phase completes', async () => {
      const mockPublishResponse = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        eventCode: 'BATbern142',
        versionNumber: 1,
        publishedPhase: 'TOPIC',
        publishedAt: '2025-01-15T10:00:00Z',
        publishedBy: 'john.doe',
        cdnInvalidationId: 'INV123',
        cdnInvalidationStatus: 'PENDING',
      };

      vi.mocked(publishingService.publishPhase).mockResolvedValue(mockPublishResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.publishPhase('topic');
      });

      await waitFor(() => {
        expect(result.current.isPublishing).toBe(false);
      });
    });
  });
});
