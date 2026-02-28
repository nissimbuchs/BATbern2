/**
 * analyticsService Tests (Story 10.5 — M1)
 *
 * Tests:
 * - getAnalyticsOverview calls GET /analytics/overview
 * - getAnalyticsAttendance calls GET /analytics/attendance without params when undefined
 * - getAnalyticsAttendance calls GET /analytics/attendance with fromYear param when provided
 * - getAnalyticsTopics calls GET /analytics/topics without params when undefined
 * - getAnalyticsTopics calls GET /analytics/topics with fromYear param when provided
 * - getAnalyticsCompanies calls GET /analytics/companies without params when undefined
 * - getAnalyticsCompanies calls GET /analytics/companies with fromYear param when provided
 * - getCompanyDistribution calls GET /analytics/companies/distribution with eventCode
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getAnalyticsOverview,
  getAnalyticsAttendance,
  getAnalyticsTopics,
  getAnalyticsCompanies,
  getCompanyDistribution,
} from './analyticsService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

const mockGet = vi.mocked(apiClient.get);

describe('analyticsService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getAnalyticsOverview', () => {
    it('calls GET /analytics/overview and returns data', async () => {
      const payload = { totalEvents: 57 };
      mockGet.mockResolvedValue({ data: payload });

      const result = await getAnalyticsOverview();

      expect(mockGet).toHaveBeenCalledWith('/analytics/overview');
      expect(result).toEqual(payload);
    });
  });

  describe('getAnalyticsAttendance', () => {
    it('calls without params when fromYear is undefined', async () => {
      mockGet.mockResolvedValue({ data: { events: [] } });

      await getAnalyticsAttendance(undefined);

      expect(mockGet).toHaveBeenCalledWith('/analytics/attendance', { params: {} });
    });

    it('calls with fromYear param when provided', async () => {
      mockGet.mockResolvedValue({ data: { events: [] } });

      await getAnalyticsAttendance(2023);

      expect(mockGet).toHaveBeenCalledWith('/analytics/attendance', { params: { fromYear: 2023 } });
    });
  });

  describe('getAnalyticsTopics', () => {
    it('calls without params when fromYear is undefined', async () => {
      mockGet.mockResolvedValue({ data: { eventsPerCategory: [] } });

      await getAnalyticsTopics(undefined);

      expect(mockGet).toHaveBeenCalledWith('/analytics/topics', { params: {} });
    });

    it('calls with fromYear param when provided', async () => {
      mockGet.mockResolvedValue({ data: { eventsPerCategory: [] } });

      await getAnalyticsTopics(2022);

      expect(mockGet).toHaveBeenCalledWith('/analytics/topics', { params: { fromYear: 2022 } });
    });
  });

  describe('getAnalyticsCompanies', () => {
    it('calls without params when fromYear is undefined', async () => {
      mockGet.mockResolvedValue({ data: { attendanceOverTime: [] } });

      await getAnalyticsCompanies(undefined);

      expect(mockGet).toHaveBeenCalledWith('/analytics/companies', { params: {} });
    });

    it('calls with fromYear param when provided', async () => {
      mockGet.mockResolvedValue({ data: { attendanceOverTime: [] } });

      await getAnalyticsCompanies(2021);

      expect(mockGet).toHaveBeenCalledWith('/analytics/companies', { params: { fromYear: 2021 } });
    });
  });

  describe('getCompanyDistribution', () => {
    it('calls GET /analytics/companies/distribution with eventCode', async () => {
      const payload = { eventCode: 'BATbern57', distribution: [] };
      mockGet.mockResolvedValue({ data: payload });

      const result = await getCompanyDistribution('BATbern57');

      expect(mockGet).toHaveBeenCalledWith('/analytics/companies/distribution', {
        params: { eventCode: 'BATbern57' },
      });
      expect(result).toEqual(payload);
    });
  });
});
