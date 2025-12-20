/**
 * Speaker Status Service Tests (Story 5.4 - Frontend Tests)
 *
 * Comprehensive tests for speakerStatusService HTTP client
 * Tests all API methods: update status, get history, get summary
 *
 * Coverage:
 * - API request formatting (event code, speaker ID, status data)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 * - State transition validation errors (422)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { speakerStatusService } from './speakerStatusService';
import apiClient from './api/apiClient';
import type { components } from '@/types/generated/speakers-api.types';

type UpdateStatusRequest = components['schemas']['UpdateStatusRequest'];
type SpeakerStatusResponse = components['schemas']['SpeakerStatusResponse'];
type StatusHistoryItem = components['schemas']['StatusHistoryItem'];
type StatusSummaryResponse = components['schemas']['StatusSummaryResponse'];

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}));

describe('speakerStatusService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('updateStatus', () => {
    it('should update speaker status with reason', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-123',
        eventCode: 'BATbern56',
        currentStatus: 'accepted',
        previousStatus: 'ready',
        changedByUsername: 'john.doe',
        changeReason: 'Speaker confirmed availability via email',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-123',
        'accepted',
        'Speaker confirmed availability via email'
      );

      expect(apiClient.put).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-uuid-123/status',
        {
          newStatus: 'accepted',
          reason: 'Speaker confirmed availability via email',
        }
      );
      expect(result).toEqual(mockResponse);
      expect(result.currentStatus).toBe('accepted');
      expect(result.previousStatus).toBe('ready');
    });

    it('should update speaker status without reason', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-456',
        eventCode: 'BATbern56',
        currentStatus: 'contacted',
        previousStatus: 'open',
        changedByUsername: 'jane.doe',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-456',
        'contacted'
      );

      expect(apiClient.put).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-uuid-456/status',
        {
          newStatus: 'contacted',
        }
      );
      expect(result.changeReason).toBeUndefined();
    });

    it('should handle OPEN -> CONTACTED transition', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-789',
        eventCode: 'BATbern56',
        currentStatus: 'contacted',
        previousStatus: 'open',
        changedByUsername: 'organizer.john',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-789',
        'contacted'
      );

      expect(result.currentStatus).toBe('contacted');
      expect(result.previousStatus).toBe('open');
    });

    it('should handle CONTACTED -> READY transition', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-789',
        eventCode: 'BATbern56',
        currentStatus: 'ready',
        previousStatus: 'contacted',
        changedByUsername: 'organizer.john',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-789',
        'ready'
      );

      expect(result.currentStatus).toBe('ready');
      expect(result.previousStatus).toBe('contacted');
    });

    it('should handle READY -> ACCEPTED transition', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-789',
        eventCode: 'BATbern56',
        currentStatus: 'accepted',
        previousStatus: 'ready',
        changedByUsername: 'organizer.john',
        changeReason: 'Speaker accepted invitation',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-789',
        'accepted',
        'Speaker accepted invitation'
      );

      expect(result.currentStatus).toBe('accepted');
      expect(result.previousStatus).toBe('ready');
    });

    it('should handle CONTACTED -> DECLINED transition', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-789',
        eventCode: 'BATbern56',
        currentStatus: 'declined',
        previousStatus: 'contacted',
        changedByUsername: 'organizer.john',
        changeReason: 'Speaker has scheduling conflict',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await speakerStatusService.updateStatus(
        'BATbern56',
        'speaker-uuid-789',
        'declined',
        'Speaker has scheduling conflict'
      );

      expect(result.currentStatus).toBe('declined');
      expect(result.previousStatus).toBe('contacted');
    });

    it('should propagate 422 error for invalid state transition (ACCEPTED -> DECLINED)', async () => {
      const error = new Error('Invalid state transition from ACCEPTED to DECLINED');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus(
          'BATbern56',
          'speaker-uuid-123',
          'declined',
          'Changed mind'
        )
      ).rejects.toThrow('Invalid state transition');
    });

    it('should propagate 422 error for invalid state transition (DECLINED -> ACCEPTED)', async () => {
      const error = new Error('Invalid state transition from DECLINED to ACCEPTED');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus(
          'BATbern56',
          'speaker-uuid-123',
          'accepted',
          'Speaker changed mind'
        )
      ).rejects.toThrow('Invalid state transition');
    });

    it('should propagate validation errors for reason exceeding 2000 characters', async () => {
      const longReason = 'a'.repeat(2001);
      const error = new Error('Validation failed: reason must not exceed 2000 characters');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-123', 'accepted', longReason)
      ).rejects.toThrow('Validation failed');
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const error = new Error('Speaker not found: speaker-uuid-999');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-999', 'contacted')
      ).rejects.toThrow('Speaker not found');
    });

    it('should propagate 404 errors for non-existent event', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus('BATbern999', 'speaker-uuid-123', 'contacted')
      ).rejects.toThrow('Event not found');
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-123', 'contacted')
      ).rejects.toThrow('Forbidden');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-123', 'contacted')
      ).rejects.toThrow('Network error');
    });
  });

  describe('getStatusHistory', () => {
    it('should fetch complete status history', async () => {
      const mockHistory: StatusHistoryItem[] = [
        {
          id: 'history-123',
          previousStatus: 'ready',
          newStatus: 'accepted',
          changedByUsername: 'john.doe',
          changeReason: 'Speaker confirmed availability',
          changedAt: '2025-12-13T12:00:00Z',
        },
        {
          id: 'history-122',
          previousStatus: 'contacted',
          newStatus: 'ready',
          changedByUsername: 'jane.doe',
          changeReason: 'Speaker expressed interest',
          changedAt: '2025-12-12T10:00:00Z',
        },
        {
          id: 'history-121',
          previousStatus: 'open',
          newStatus: 'contacted',
          changedByUsername: 'john.doe',
          changeReason: 'Initial outreach sent',
          changedAt: '2025-12-10T09:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockHistory });

      const result = await speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-123');

      expect(apiClient.get).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-uuid-123/status/history'
      );
      expect(result).toEqual(mockHistory);
      expect(result).toHaveLength(3);
      expect(result[0].newStatus).toBe('accepted');
      expect(result[2].newStatus).toBe('contacted');
    });

    it('should return empty array when no history exists', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-123');

      expect(result).toEqual([]);
      expect(result).toHaveLength(0);
    });

    it('should handle history with no reasons', async () => {
      const mockHistory: StatusHistoryItem[] = [
        {
          id: 'history-123',
          previousStatus: 'open',
          newStatus: 'contacted',
          changedByUsername: 'john.doe',
          changedAt: '2025-12-13T12:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockHistory });

      const result = await speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-123');

      expect(result[0].changeReason).toBeUndefined();
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const error = new Error('Speaker not found: speaker-uuid-999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-999')
      ).rejects.toThrow('Speaker not found');
    });

    it('should propagate 404 errors for non-existent event', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerStatusService.getStatusHistory('BATbern999', 'speaker-uuid-123')
      ).rejects.toThrow('Event not found');
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Unauthorized: JWT token required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-123')
      ).rejects.toThrow('Unauthorized');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: connection refused');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerStatusService.getStatusHistory('BATbern56', 'speaker-uuid-123')
      ).rejects.toThrow('Network error');
    });
  });

  describe('getStatusSummary', () => {
    it('should fetch status summary with all metrics', async () => {
      const mockSummary: StatusSummaryResponse = {
        eventCode: 'BATbern56',
        statusCounts: {
          OPEN: 2,
          CONTACTED: 5,
          READY: 3,
          ACCEPTED: 6,
          DECLINED: 1,
        },
        totalSpeakers: 17,
        acceptedCount: 6,
        declinedCount: 1,
        pendingCount: 10,
        acceptanceRate: 35.29,
        minSlotsRequired: 6,
        maxSlotsAllowed: 8,
        thresholdMet: true,
        overflowDetected: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSummary });

      const result = await speakerStatusService.getStatusSummary('BATbern56');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/speakers/status-summary');
      expect(result).toEqual(mockSummary);
      expect(result.acceptedCount).toBe(6);
      expect(result.acceptanceRate).toBe(35.29);
      expect(result.thresholdMet).toBe(true);
      expect(result.overflowDetected).toBe(false);
    });

    it('should handle threshold not met scenario', async () => {
      const mockSummary: StatusSummaryResponse = {
        eventCode: 'BATbern56',
        statusCounts: {
          OPEN: 5,
          CONTACTED: 3,
          READY: 2,
          ACCEPTED: 4,
          DECLINED: 1,
        },
        totalSpeakers: 15,
        acceptedCount: 4,
        declinedCount: 1,
        pendingCount: 10,
        acceptanceRate: 26.67,
        minSlotsRequired: 6,
        maxSlotsAllowed: 8,
        thresholdMet: false,
        overflowDetected: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSummary });

      const result = await speakerStatusService.getStatusSummary('BATbern56');

      expect(result.thresholdMet).toBe(false);
      expect(result.acceptedCount).toBeLessThan(result.minSlotsRequired ?? 0);
    });

    it('should handle overflow detected scenario', async () => {
      const mockSummary: StatusSummaryResponse = {
        eventCode: 'BATbern56',
        statusCounts: {
          OPEN: 1,
          CONTACTED: 2,
          READY: 1,
          ACCEPTED: 9,
          DECLINED: 2,
        },
        totalSpeakers: 15,
        acceptedCount: 9,
        declinedCount: 2,
        pendingCount: 4,
        acceptanceRate: 60.0,
        minSlotsRequired: 6,
        maxSlotsAllowed: 8,
        thresholdMet: true,
        overflowDetected: true,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSummary });

      const result = await speakerStatusService.getStatusSummary('BATbern56');

      expect(result.overflowDetected).toBe(true);
      expect(result.acceptedCount).toBeGreaterThan(result.maxSlotsAllowed ?? 0);
    });

    it('should handle event with no speakers', async () => {
      const mockSummary: StatusSummaryResponse = {
        eventCode: 'BATbern56',
        statusCounts: {},
        totalSpeakers: 0,
        acceptedCount: 0,
        declinedCount: 0,
        pendingCount: 0,
        acceptanceRate: 0.0,
        minSlotsRequired: 6,
        maxSlotsAllowed: 8,
        thresholdMet: false,
        overflowDetected: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockSummary });

      const result = await speakerStatusService.getStatusSummary('BATbern56');

      expect(result.totalSpeakers).toBe(0);
      expect(result.acceptanceRate).toBe(0.0);
    });

    it('should propagate 404 errors for non-existent event', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerStatusService.getStatusSummary('BATbern999')).rejects.toThrow(
        'Event not found'
      );
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerStatusService.getStatusSummary('BATbern56')).rejects.toThrow('Forbidden');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerStatusService.getStatusSummary('BATbern56')).rejects.toThrow(
        'Network error'
      );
    });
  });

  describe('Service Singleton', () => {
    it('should export a singleton instance', () => {
      expect(speakerStatusService).toBeDefined();
      expect(speakerStatusService).toBeInstanceOf(Object);
      expect(typeof speakerStatusService.updateStatus).toBe('function');
      expect(typeof speakerStatusService.getStatusHistory).toBe('function');
      expect(typeof speakerStatusService.getStatusSummary).toBe('function');
    });

    it('should maintain state across multiple calls', async () => {
      const mockResponse: SpeakerStatusResponse = {
        speakerId: 'speaker-uuid-123',
        eventCode: 'BATbern56',
        currentStatus: 'contacted',
        previousStatus: 'open',
        changedByUsername: 'john.doe',
        changedAt: '2025-12-13T10:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      // Call twice to ensure singleton behavior
      await speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-123', 'contacted');
      await speakerStatusService.updateStatus('BATbern56', 'speaker-uuid-123', 'contacted');

      expect(apiClient.put).toHaveBeenCalledTimes(2);
    });
  });
});
