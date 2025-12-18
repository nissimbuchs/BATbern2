/**
 * Event Type Service Tests (Story 5.1 - Frontend Tests)
 *
 * Comprehensive tests for eventTypeService HTTP client
 * Tests all API methods: get all event types, get by type, update configuration
 *
 * Coverage:
 * - API request formatting and response handling
 * - Error propagation (404, 403, validation errors)
 * - Type safety (EventType enum, configuration requests)
 * - RBAC enforcement (ORGANIZER-only PUT operations)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { eventTypeService } from './eventTypeService';
import apiClient from './api/apiClient';
import type { components } from '@/types/generated/events-api.types';

// Extract generated types
type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];
type EventType = components['schemas']['EventType'];

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('eventTypeService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getAllEventTypes', () => {
    it('should fetch all event type configurations', async () => {
      const mockResponse: EventSlotConfigurationResponse[] = [
        {
          type: 'FULL_DAY' as EventType,
          maxSpeakers: 8,
          maxTopics: 4,
          defaultDurationMinutes: 480,
          allowsLunch: true,
          allowsDinner: false,
        },
        {
          type: 'AFTERNOON' as EventType,
          maxSpeakers: 4,
          maxTopics: 2,
          defaultDurationMinutes: 240,
          allowsLunch: false,
          allowsDinner: false,
        },
        {
          type: 'EVENING' as EventType,
          maxSpeakers: 4,
          maxTopics: 2,
          defaultDurationMinutes: 180,
          allowsLunch: false,
          allowsDinner: true,
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await eventTypeService.getAllEventTypes();

      expect(apiClient.get).toHaveBeenCalledWith('/events/types');
      expect(result).toEqual(mockResponse);
      expect(result).toHaveLength(3);
      expect(result[0].type).toBe('FULL_DAY');
    });

    it('should propagate API errors', async () => {
      const error = new Error('Network failure');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(eventTypeService.getAllEventTypes()).rejects.toThrow('Network failure');
    });
  });

  describe('getEventType', () => {
    it('should fetch FULL_DAY event type configuration', async () => {
      const mockResponse: EventSlotConfigurationResponse = {
        type: 'FULL_DAY' as EventType,
        maxSpeakers: 8,
        maxTopics: 4,
        defaultDurationMinutes: 480,
        allowsLunch: true,
        allowsDinner: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await eventTypeService.getEventType('FULL_DAY' as EventType);

      expect(apiClient.get).toHaveBeenCalledWith('/events/types/FULL_DAY');
      expect(result).toEqual(mockResponse);
      expect(result.maxSpeakers).toBe(8);
      expect(result.allowsLunch).toBe(true);
    });

    it('should fetch AFTERNOON event type configuration', async () => {
      const mockResponse: EventSlotConfigurationResponse = {
        type: 'AFTERNOON' as EventType,
        maxSpeakers: 4,
        maxTopics: 2,
        defaultDurationMinutes: 240,
        allowsLunch: false,
        allowsDinner: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await eventTypeService.getEventType('AFTERNOON' as EventType);

      expect(apiClient.get).toHaveBeenCalledWith('/events/types/AFTERNOON');
      expect(result).toEqual(mockResponse);
    });

    it('should fetch EVENING event type configuration', async () => {
      const mockResponse: EventSlotConfigurationResponse = {
        type: 'EVENING' as EventType,
        maxSpeakers: 4,
        maxTopics: 2,
        defaultDurationMinutes: 180,
        allowsLunch: false,
        allowsDinner: true,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await eventTypeService.getEventType('EVENING' as EventType);

      expect(apiClient.get).toHaveBeenCalledWith('/events/types/EVENING');
      expect(result.allowsDinner).toBe(true);
    });

    it('should propagate 404 errors for invalid event types', async () => {
      const error = new Error('Event type not found');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(eventTypeService.getEventType('INVALID' as EventType)).rejects.toThrow(
        'Event type not found'
      );
    });
  });

  describe('updateEventType', () => {
    it('should update FULL_DAY event type configuration (ORGANIZER only)', async () => {
      const request: UpdateEventSlotConfigurationRequest = {
        maxSpeakers: 10,
        maxTopics: 5,
        defaultDurationMinutes: 480,
        allowsLunch: true,
        allowsDinner: false,
      };

      const mockUpdatedResponse: EventSlotConfigurationResponse = {
        type: 'FULL_DAY' as EventType,
        ...request,
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockUpdatedResponse });

      const result = await eventTypeService.updateEventType('FULL_DAY' as EventType, request);

      expect(apiClient.put).toHaveBeenCalledWith('/events/types/FULL_DAY', request);
      expect(result).toEqual(mockUpdatedResponse);
      expect(result.maxSpeakers).toBe(10);
      expect(result.maxTopics).toBe(5);
    });

    it('should update AFTERNOON event type configuration', async () => {
      const request: UpdateEventSlotConfigurationRequest = {
        maxSpeakers: 6,
        maxTopics: 3,
        defaultDurationMinutes: 240,
        allowsLunch: true,
        allowsDinner: false,
      };

      const mockUpdatedResponse: EventSlotConfigurationResponse = {
        type: 'AFTERNOON' as EventType,
        ...request,
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockUpdatedResponse });

      const result = await eventTypeService.updateEventType('AFTERNOON' as EventType, request);

      expect(apiClient.put).toHaveBeenCalledWith('/events/types/AFTERNOON', request);
      expect(result.maxSpeakers).toBe(6);
    });

    it('should propagate 403 Forbidden if user is not ORGANIZER', async () => {
      const request: UpdateEventSlotConfigurationRequest = {
        maxSpeakers: 10,
        maxTopics: 5,
        defaultDurationMinutes: 480,
        allowsLunch: true,
        allowsDinner: false,
      };

      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        eventTypeService.updateEventType('FULL_DAY' as EventType, request)
      ).rejects.toThrow('Forbidden');
    });

    it('should propagate 400 Bad Request for invalid configuration', async () => {
      const request: UpdateEventSlotConfigurationRequest = {
        maxSpeakers: -1, // Invalid value
        maxTopics: 5,
        defaultDurationMinutes: 480,
        allowsLunch: true,
        allowsDinner: false,
      };

      const error = new Error('Validation failed: maxSpeakers must be positive');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        eventTypeService.updateEventType('FULL_DAY' as EventType, request)
      ).rejects.toThrow('Validation failed');
    });

    it('should handle conflicting boolean configurations', async () => {
      const request: UpdateEventSlotConfigurationRequest = {
        maxSpeakers: 8,
        maxTopics: 4,
        defaultDurationMinutes: 480,
        allowsLunch: true,
        allowsDinner: true, // Typically FULL_DAY doesn't allow dinner
      };

      const mockUpdatedResponse: EventSlotConfigurationResponse = {
        type: 'FULL_DAY' as EventType,
        ...request,
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockUpdatedResponse });

      const result = await eventTypeService.updateEventType('FULL_DAY' as EventType, request);

      expect(result.allowsLunch).toBe(true);
      expect(result.allowsDinner).toBe(true);
    });
  });
});
