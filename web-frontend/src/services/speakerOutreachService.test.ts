/**
 * Speaker Outreach Service Tests (Story 5.3 - Frontend Tests)
 *
 * Comprehensive tests for speakerOutreachService HTTP client
 * Tests all API methods: record outreach, get outreach history
 *
 * Coverage:
 * - API request formatting (event code, speaker ID, outreach data)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 *
 * Test Scenarios:
 * - AC2: should_recordOutreach_when_markContactedButtonClicked
 * - AC3: should_saveContactNotes_when_notesProvided
 * - AC4: should_getOutreachHistory_when_contactHistoryExists
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { speakerOutreachService } from './speakerOutreachService';
import apiClient from './api/apiClient';
import type {
  OutreachHistory,
  RecordOutreachRequest,
  OutreachHistoryResponse,
} from '@/types/speakerOutreach.types';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('speakerOutreachService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('recordOutreach', () => {
    it('should record outreach with email contact method', async () => {
      // AC2: Mark speaker as contacted with date/time
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
        notes: 'Sent email about Kubernetes security presentation',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-123',
        speakerPoolId: 'speaker-456',
        contactDate: '2025-12-14T10:00:00Z',
        contactMethod: 'email',
        notes: 'Sent email about Kubernetes security presentation',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerOutreachService.recordOutreach(
        'BATbern56',
        'speaker-456',
        request
      );

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-456/outreach',
        request
      );
      expect(result).toEqual(mockResponse);
      expect(result.contactMethod).toBe('email');
    });

    it('should record outreach with phone contact method', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'phone',
        contactDate: '2025-12-14T14:30:00Z',
        notes: 'Called to discuss presentation topic and availability',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-124',
        speakerPoolId: 'speaker-456',
        contactDate: '2025-12-14T14:30:00Z',
        contactMethod: 'phone',
        notes: 'Called to discuss presentation topic and availability',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T14:30:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerOutreachService.recordOutreach(
        'BATbern56',
        'speaker-456',
        request
      );

      expect(result.contactMethod).toBe('phone');
    });

    it('should record outreach with in-person contact method', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'in_person',
        contactDate: '2025-12-14T09:00:00Z',
        notes: 'Met at conference and discussed presentation opportunity',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-125',
        speakerPoolId: 'speaker-456',
        contactDate: '2025-12-14T09:00:00Z',
        contactMethod: 'in_person',
        notes: 'Met at conference and discussed presentation opportunity',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T09:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerOutreachService.recordOutreach(
        'BATbern56',
        'speaker-456',
        request
      );

      expect(result.contactMethod).toBe('in_person');
    });

    it('should record outreach without notes (AC3: notes are optional)', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-126',
        speakerPoolId: 'speaker-456',
        contactDate: '2025-12-14T10:00:00Z',
        contactMethod: 'email',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerOutreachService.recordOutreach(
        'BATbern56',
        'speaker-456',
        request
      );

      expect(result.notes).toBeUndefined();
    });

    it('should propagate 404 error when speaker not found', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
      };

      const error = new Error('Speaker not found: speaker-999');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerOutreachService.recordOutreach('BATbern56', 'speaker-999', request)
      ).rejects.toThrow('Speaker not found: speaker-999');
    });

    it('should propagate 422 error when speaker in invalid state', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
      };

      const error = new Error('Speaker not in valid state for contact: DECLINED');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerOutreachService.recordOutreach('BATbern56', 'speaker-456', request)
      ).rejects.toThrow('Speaker not in valid state for contact: DECLINED');
    });
  });

  describe('getOutreachHistory', () => {
    it('should get outreach history for speaker (AC4)', async () => {
      // AC4: Timeline of all outreach attempts per speaker
      const mockHistory: OutreachHistory[] = [
        {
          id: 'outreach-1',
          speakerPoolId: 'speaker-456',
          contactDate: '2025-12-14T10:00:00Z',
          contactMethod: 'email',
          notes: 'Initial contact email sent',
          organizerUsername: 'john.doe',
          createdAt: '2025-12-14T10:00:00Z',
        },
        {
          id: 'outreach-2',
          speakerPoolId: 'speaker-456',
          contactDate: '2025-12-11T14:30:00Z',
          contactMethod: 'phone',
          notes: 'Follow-up call, speaker interested',
          organizerUsername: 'jane.smith',
          createdAt: '2025-12-11T14:30:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockHistory });

      const result = await speakerOutreachService.getOutreachHistory('BATbern56', 'speaker-456');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/speakers/speaker-456/outreach');
      expect(result).toEqual(mockHistory);
      expect(result).toHaveLength(2);
    });

    it('should return empty array when no outreach history exists', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await speakerOutreachService.getOutreachHistory('BATbern56', 'speaker-789');

      expect(result).toEqual([]);
    });

    it('should propagate 404 error when speaker not found', async () => {
      const error = new Error('Speaker not found: speaker-999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerOutreachService.getOutreachHistory('BATbern56', 'speaker-999')
      ).rejects.toThrow('Speaker not found: speaker-999');
    });
  });

  describe('bulkRecordOutreach', () => {
    it('should record outreach for multiple speakers (AC6)', async () => {
      // AC6: Bulk mark multiple speakers as contacted
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
        notes: 'Bulk email campaign to all identified speakers',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-bulk',
        speakerPoolId: 'speaker-1',
        contactDate: '2025-12-14T10:00:00Z',
        contactMethod: 'email',
        notes: 'Bulk email campaign to all identified speakers',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T10:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      await speakerOutreachService.bulkRecordOutreach(
        'BATbern56',
        ['speaker-1', 'speaker-2'],
        request
      );

      expect(apiClient.post).toHaveBeenCalledTimes(2);
      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-1/outreach',
        request
      );
      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/speaker-2/outreach',
        request
      );
    });

    it('should handle partial failures in bulk operations', async () => {
      const request: RecordOutreachRequest = {
        contactMethod: 'email',
        contactDate: '2025-12-14T10:00:00Z',
      };

      const mockResponse: OutreachHistoryResponse = {
        id: 'outreach-bulk',
        speakerPoolId: 'speaker-1',
        contactDate: '2025-12-14T10:00:00Z',
        contactMethod: 'email',
        organizerUsername: 'john.doe',
        createdAt: '2025-12-14T10:00:00Z',
      };

      // First call succeeds, second fails
      vi.mocked(apiClient.post)
        .mockResolvedValueOnce({ data: mockResponse })
        .mockRejectedValueOnce(new Error('Speaker not found: speaker-999'));

      await expect(
        speakerOutreachService.bulkRecordOutreach(
          'BATbern56',
          ['speaker-1', 'speaker-999'],
          request
        )
      ).rejects.toThrow();
    });
  });
});
