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
        phase: 'topic',
        published: true,
        publishedAt: '2025-01-15T10:00:00Z',
        cdnInvalidated: true,
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

  describe('auto-publish scheduling', () => {
    it('should schedule auto-publish successfully', async () => {
      const schedule = {
        phase2Enabled: true,
        phase2DaysBeforeEvent: 30,
        phase3Enabled: true,
        phase3DaysBeforeEvent: 14,
      };
      const mockScheduleResponse = {
        scheduled: true,
        ...schedule,
        phase2TriggerDate: '2025-04-15T08:00:00Z',
        phase3TriggerDate: '2025-05-01T08:00:00Z',
      };

      vi.mocked(publishingService.scheduleAutoPublish).mockResolvedValue(mockScheduleResponse);

      const { result } = renderHook(() => usePublishing('BATbern142'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        result.current.scheduleAutoPublish(schedule);
      });

      await waitFor(() => {
        expect(result.current.isScheduling).toBe(false);
      });

      expect(publishingService.scheduleAutoPublish).toHaveBeenCalledWith('BATbern142', schedule);
    });
  });

  describe('real-time updates', () => {
    it('should refetch data when publishing phase completes', async () => {
      const mockPublishResponse = {
        phase: 'topic',
        published: true,
        publishedAt: '2025-01-15T10:00:00Z',
        cdnInvalidated: true,
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
