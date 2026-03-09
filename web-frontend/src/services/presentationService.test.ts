/**
 * Presentation Service Tests (Story 10.8a)
 *
 * Tests for all presentation service functions:
 * - getPresentationData, getPublicOrganizers, getUpcomingEvents
 * - getPresentationSettings, updatePresentationSettings
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  updatePresentationSettings,
  presentationService,
} from './presentationService';
import apiClient from './api/apiClient';

vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('presentationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getPresentationData', () => {
    it('should fetch event data with topics,venue,sessions,speakers include', async () => {
      const mockEvent = { eventCode: 'BATbern142', title: 'BATbern #142' };
      mockApiClient.get.mockResolvedValue({ data: mockEvent });

      const result = await getPresentationData('BATbern142');

      expect(mockApiClient.get).toHaveBeenCalledWith('/events/BATbern142', {
        params: { include: 'topics,venue,sessions,speakers' },
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result).toEqual(mockEvent);
    });
  });

  describe('getPublicOrganizers', () => {
    it('should fetch public organizers list', async () => {
      const mockOrganizers = [{ username: 'admin', firstName: 'Admin' }];
      mockApiClient.get.mockResolvedValue({ data: mockOrganizers });

      const result = await getPublicOrganizers();

      expect(mockApiClient.get).toHaveBeenCalledWith('/public/organizers', {
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result).toEqual(mockOrganizers);
    });
  });

  describe('getUpcomingEvents', () => {
    it('should return strictly future events sorted by date, limited to 3', async () => {
      const now = new Date();
      const future1 = new Date(now.getTime() + 10 * 86400_000).toISOString(); // +10 days
      const future2 = new Date(now.getTime() + 5 * 86400_000).toISOString(); // +5 days
      const future3 = new Date(now.getTime() + 20 * 86400_000).toISOString(); // +20 days
      const future4 = new Date(now.getTime() + 30 * 86400_000).toISOString(); // +30 days
      const past = new Date(now.getTime() - 86400_000).toISOString(); // -1 day

      const mockEvents = [
        { eventCode: 'EVT1', date: future1 },
        { eventCode: 'EVT2', date: future2 },
        { eventCode: 'EVT3', date: future3 },
        { eventCode: 'EVT4', date: future4 },
        { eventCode: 'PAST', date: past },
      ];
      mockApiClient.get.mockResolvedValue({ data: { data: mockEvents } });

      const result = await getUpcomingEvents();

      // Should exclude PAST, sort by date asc, limit to 3
      expect(result).toHaveLength(3);
      expect(result.map((e) => e.eventCode)).toEqual(['EVT2', 'EVT1', 'EVT3']);
    });

    it('should handle API returning flat array (no pagination wrapper)', async () => {
      const now = new Date();
      const future = new Date(now.getTime() + 86400_000).toISOString();
      const mockEvents = [{ eventCode: 'EVT1', date: future }];
      // API returns flat array instead of { data: [...] }
      mockApiClient.get.mockResolvedValue({ data: mockEvents });

      const result = await getUpcomingEvents();

      expect(result).toHaveLength(1);
      expect(result[0].eventCode).toBe('EVT1');
    });

    it('should pass correct query params', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [] } });

      await getUpcomingEvents();

      expect(mockApiClient.get).toHaveBeenCalledWith('/events', {
        params: {
          status: 'AGENDA_PUBLISHED,TOPIC_SELECTION_DONE,TOPIC_SELECTION,CREATED',
          limit: 10,
          sort: 'date',
        },
        headers: { 'Skip-Auth': 'true' },
      });
    });

    it('should return empty array when all events are in the past', async () => {
      const past = new Date(Date.now() - 86400_000).toISOString();
      mockApiClient.get.mockResolvedValue({ data: { data: [{ date: past }] } });

      const result = await getUpcomingEvents();
      expect(result).toHaveLength(0);
    });
  });

  describe('getPresentationSettings', () => {
    it('should fetch presentation settings via public endpoint', async () => {
      const mockSettings = { logoUrl: null, primaryColor: '#000' };
      mockApiClient.get.mockResolvedValue({ data: mockSettings });

      const result = await getPresentationSettings();

      expect(mockApiClient.get).toHaveBeenCalledWith('/public/settings/presentation', {
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result).toEqual(mockSettings);
    });
  });

  describe('updatePresentationSettings', () => {
    it('should PUT updated settings and return result', async () => {
      const mockSettings = { logoUrl: 'https://cdn.example.com/logo.png', primaryColor: '#f00' };
      mockApiClient.put.mockResolvedValue({ data: mockSettings });

      const result = await updatePresentationSettings({ primaryColor: '#f00' });

      expect(mockApiClient.put).toHaveBeenCalledWith('/settings/presentation', {
        primaryColor: '#f00',
      });
      expect(result).toEqual(mockSettings);
    });
  });

  describe('presentationService object export', () => {
    it('should expose all functions', () => {
      expect(presentationService.getPresentationData).toBe(getPresentationData);
      expect(presentationService.getPublicOrganizers).toBe(getPublicOrganizers);
      expect(presentationService.getUpcomingEvents).toBe(getUpcomingEvents);
      expect(presentationService.getPresentationSettings).toBe(getPresentationSettings);
      expect(presentationService.updatePresentationSettings).toBe(updatePresentationSettings);
    });
  });
});
