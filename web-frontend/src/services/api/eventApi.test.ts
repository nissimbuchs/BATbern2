/**
 * Event API Service Tests
 *
 * Tests for event API client methods
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { batchRegisterParticipant } from './eventApi';
import apiClient from './apiClient';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse,
} from '@/types/participantImport.types';

// Mock the apiClient module
vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('eventApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('batchRegisterParticipant', () => {
    it('should batch register participant for multiple events', async () => {
      const request: BatchRegistrationRequest = {
        participant: {
          userId: 'user-123',
          firstName: 'John',
          lastName: 'Doe',
          email: 'john.doe@example.com',
          companyName: 'TechCorp AG',
        },
        eventCodes: ['BAT2025', 'BAT2024'],
      };

      const mockResponse: BatchRegistrationResponse = {
        participantId: 'participant-123',
        results: [
          {
            eventCode: 'BAT2025',
            registrationId: 'reg-1',
            success: true,
          },
          {
            eventCode: 'BAT2024',
            registrationId: 'reg-2',
            success: true,
          },
        ],
        summary: {
          total: 2,
          successful: 2,
          failed: 0,
        },
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await batchRegisterParticipant(request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/batch_registrations', request);
      expect(result).toEqual(mockResponse);
    });

    it('should handle partial success in batch registration', async () => {
      const request: BatchRegistrationRequest = {
        participant: {
          userId: 'user-456',
          firstName: 'Jane',
          lastName: 'Smith',
          email: 'jane.smith@example.com',
          companyName: 'SwissSoft GmbH',
        },
        eventCodes: ['BAT2025', 'BAT2024', 'BAT2023'],
      };

      const mockResponse: BatchRegistrationResponse = {
        participantId: 'participant-456',
        results: [
          {
            eventCode: 'BAT2025',
            registrationId: 'reg-3',
            success: true,
          },
          {
            eventCode: 'BAT2024',
            success: false,
            error: 'Event is full',
          },
          {
            eventCode: 'BAT2023',
            registrationId: 'reg-4',
            success: true,
          },
        ],
        summary: {
          total: 3,
          successful: 2,
          failed: 1,
        },
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await batchRegisterParticipant(request);

      expect(result.summary.successful).toBe(2);
      expect(result.summary.failed).toBe(1);
      expect(result.results[1].success).toBe(false);
      expect(result.results[1].error).toBe('Event is full');
    });

    it('should handle API errors', async () => {
      const request: BatchRegistrationRequest = {
        participant: {
          userId: 'user-789',
          firstName: 'Bob',
          lastName: 'Johnson',
          email: 'bob.johnson@example.com',
          companyName: 'DevCorp',
        },
        eventCodes: ['BAT2025'],
      };

      const error = new Error('Network error');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(batchRegisterParticipant(request)).rejects.toThrow('Network error');
    });

    it('should send correct payload structure', async () => {
      const request: BatchRegistrationRequest = {
        participant: {
          userId: 'user-abc',
          firstName: 'Alice',
          lastName: 'Williams',
          email: 'alice@example.com',
          companyName: 'CloudTech',
        },
        eventCodes: ['BAT2025'],
      };

      const mockResponse: BatchRegistrationResponse = {
        participantId: 'participant-abc',
        results: [
          {
            eventCode: 'BAT2025',
            registrationId: 'reg-5',
            success: true,
          },
        ],
        summary: {
          total: 1,
          successful: 1,
          failed: 0,
        },
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      await batchRegisterParticipant(request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/batch_registrations', {
        participant: {
          userId: 'user-abc',
          firstName: 'Alice',
          lastName: 'Williams',
          email: 'alice@example.com',
          companyName: 'CloudTech',
        },
        eventCodes: ['BAT2025'],
      });
    });

    it('should handle empty event codes array', async () => {
      const request: BatchRegistrationRequest = {
        participant: {
          userId: 'user-xyz',
          firstName: 'Test',
          lastName: 'User',
          email: 'test@example.com',
          companyName: 'TestCo',
        },
        eventCodes: [],
      };

      const mockResponse: BatchRegistrationResponse = {
        participantId: 'participant-xyz',
        results: [],
        summary: {
          total: 0,
          successful: 0,
          failed: 0,
        },
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await batchRegisterParticipant(request);

      expect(result.results).toHaveLength(0);
      expect(result.summary.total).toBe(0);
    });
  });
});
