/**
 * Partner Analytics API Client Tests
 * Story 8.1: Partner Attendance Dashboard — AC1–4
 *
 * Tests for analytics API functions:
 * - getAttendanceDashboard: Fetch attendance data for partner company
 * - exportAttendanceReport: Trigger Excel download of attendance report
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import apiClient from '@/services/api/apiClient';
import {
  getAttendanceDashboard,
  exportAttendanceReport,
  type PartnerDashboardData,
} from './partnerAnalyticsApi';

describe('Partner Analytics API Client - Story 8.1', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const mockDashboardData: PartnerDashboardData = {
    attendanceSummary: [
      {
        eventCode: 'BAT-42',
        eventDate: '2026-01-15T18:00:00Z',
        totalAttendees: 120,
        companyAttendees: 8,
      },
      {
        eventCode: 'BAT-41',
        eventDate: '2025-11-20T18:00:00Z',
        totalAttendees: 110,
        companyAttendees: 5,
      },
    ],
    costPerAttendee: 150.0,
  };

  describe('getAttendanceDashboard', () => {
    it('should_callGetEndpoint_when_getAttendanceDashboardInvoked', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockDashboardData });

      const result = await getAttendanceDashboard('Acme Corp');

      expect(apiClient.get).toHaveBeenCalledWith('/partners/Acme Corp/analytics/dashboard', {
        params: {},
      });
      expect(result).toEqual(mockDashboardData);
    });

    it('should_passFromYearParam_when_fromYearProvided', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockDashboardData });

      await getAttendanceDashboard('Acme Corp', 2024);

      expect(apiClient.get).toHaveBeenCalledWith('/partners/Acme Corp/analytics/dashboard', {
        params: { fromYear: 2024 },
      });
    });

    it('should_omitFromYearParam_when_fromYearUndefined', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockDashboardData });

      await getAttendanceDashboard('Acme Corp');

      expect(apiClient.get).toHaveBeenCalledWith('/partners/Acme Corp/analytics/dashboard', {
        params: {},
      });
    });

    it('should_returnDashboardData_when_apiCallSucceeds', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockDashboardData });

      const result = await getAttendanceDashboard('Test Co');

      expect(result.attendanceSummary).toHaveLength(2);
      expect(result.costPerAttendee).toBe(150.0);
    });

    it('should_handleNullCostPerAttendee_when_noPartnershipCost', async () => {
      const dataWithNullCost = { ...mockDashboardData, costPerAttendee: null };
      vi.mocked(apiClient.get).mockResolvedValue({ data: dataWithNullCost });

      const result = await getAttendanceDashboard('Test Co');

      expect(result.costPerAttendee).toBeNull();
    });

    it('should_propagateError_when_apiCallFails', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Unauthorized'));

      await expect(getAttendanceDashboard('Test Co')).rejects.toThrow('Unauthorized');
    });
  });

  describe('exportAttendanceReport', () => {
    let createElementSpy: ReturnType<typeof vi.spyOn>;
    let appendChildSpy: ReturnType<typeof vi.spyOn>;
    let removeChildSpy: ReturnType<typeof vi.spyOn>;
    let createObjectURLSpy: ReturnType<typeof vi.spyOn>;
    let revokeObjectURLSpy: ReturnType<typeof vi.spyOn>;
    const mockAnchor = {
      href: '',
      download: '',
      click: vi.fn(),
    };

    beforeEach(() => {
      createElementSpy = vi
        .spyOn(document, 'createElement')
        .mockReturnValue(mockAnchor as unknown as HTMLAnchorElement);
      appendChildSpy = vi
        .spyOn(document.body, 'appendChild')
        .mockReturnValue(mockAnchor as unknown as HTMLAnchorElement);
      removeChildSpy = vi
        .spyOn(document.body, 'removeChild')
        .mockReturnValue(mockAnchor as unknown as HTMLAnchorElement);
      createObjectURLSpy = vi
        .spyOn(URL, 'createObjectURL')
        .mockReturnValue('blob:http://localhost/mock-blob');
      revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    });

    afterEach(() => {
      createElementSpy.mockRestore();
      appendChildSpy.mockRestore();
      removeChildSpy.mockRestore();
      createObjectURLSpy.mockRestore();
      revokeObjectURLSpy.mockRestore();
    });

    it('should_callGetEndpointWithBlob_when_exportInvoked', async () => {
      const mockBlob = new Blob(['data'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      vi.mocked(apiClient.get).mockResolvedValue({
        data: mockBlob,
        headers: {},
      });

      await exportAttendanceReport('Acme Corp');

      expect(apiClient.get).toHaveBeenCalledWith('/partners/Acme Corp/analytics/export', {
        params: {},
        responseType: 'blob',
      });
    });

    it('should_passFromYearParam_when_fromYearProvided', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({
        data: new Blob(['data']),
        headers: {},
      });

      await exportAttendanceReport('Acme Corp', 2023);

      expect(apiClient.get).toHaveBeenCalledWith('/partners/Acme Corp/analytics/export', {
        params: { fromYear: 2023 },
        responseType: 'blob',
      });
    });

    it('should_triggerDownload_when_reportReceived', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({
        data: new Blob(['spreadsheet data']),
        headers: {},
      });

      await exportAttendanceReport('Acme Corp');

      expect(document.createElement).toHaveBeenCalledWith('a');
      expect(mockAnchor.click).toHaveBeenCalled();
      expect(document.body.appendChild).toHaveBeenCalled();
      expect(document.body.removeChild).toHaveBeenCalled();
    });

    it('should_useDefaultFilename_when_noContentDisposition', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({
        data: new Blob(['data']),
        headers: {},
      });

      await exportAttendanceReport('Acme Corp');

      expect(mockAnchor.download).toBe('attendance-Acme Corp.xlsx');
    });

    it('should_extractFilename_when_contentDispositionPresent', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({
        data: new Blob(['data']),
        headers: {
          'content-disposition': 'attachment; filename="report-2026.xlsx"',
        },
      });

      await exportAttendanceReport('Acme Corp');

      expect(mockAnchor.download).toBe('report-2026.xlsx');
    });

    it('should_revokeObjectURL_when_downloadComplete', async () => {
      vi.useFakeTimers();
      vi.mocked(apiClient.get).mockResolvedValue({
        data: new Blob(['data']),
        headers: {},
      });

      await exportAttendanceReport('Acme Corp');

      vi.advanceTimersByTime(200);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/mock-blob');
      vi.useRealTimers();
    });

    it('should_propagateError_when_exportFails', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Server Error'));

      await expect(exportAttendanceReport('Test Co')).rejects.toThrow('Server Error');
    });
  });
});
