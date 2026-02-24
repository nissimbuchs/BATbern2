/**
 * Timetable Service Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { timetableService } from '../timetableService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

describe('TimetableService', () => {
  const mockGet = vi.mocked(apiClient.get);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getTimetable', () => {
    it('should call the correct endpoint and return data', async () => {
      const mockResponse = {
        eventCode: 'BATbern57',
        slots: [
          { id: '1', slotType: 'MODERATION', startTime: '18:00', durationMinutes: 10 },
          { id: '2', slotType: 'SPEAKER_SLOT', startTime: '18:10', durationMinutes: 30 },
        ],
      };
      mockGet.mockResolvedValueOnce({ data: mockResponse });

      const result = await timetableService.getTimetable('BATbern57');

      expect(mockGet).toHaveBeenCalledWith('/events/BATbern57/timetable');
      expect(result).toEqual(mockResponse);
    });

    it('should pass through errors from the API', async () => {
      mockGet.mockRejectedValueOnce(new Error('Network error'));

      await expect(timetableService.getTimetable('BATbern57')).rejects.toThrow('Network error');
    });
  });
});
