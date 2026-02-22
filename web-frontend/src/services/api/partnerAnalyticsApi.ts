/**
 * Partner Analytics API Client
 * Story 8.1: Partner Attendance Dashboard — AC1, AC2, AC3, AC4
 *
 * Endpoints served by partner-coordination-service via API Gateway.
 * CRITICAL: Path is relative to baseURL (which already includes /api/v1).
 */

import apiClient from '@/services/api/apiClient';

// ─── Types ───────────────────────────────────────────────────────────────────

export interface AttendanceSummaryRecord {
  eventCode: string;
  eventDate: string; // ISO-8601 date-time
  totalAttendees: number;
  companyAttendees: number;
}

export interface PartnerDashboardData {
  attendanceSummary: AttendanceSummaryRecord[];
  costPerAttendee: number | null;
}

// ─── API functions ────────────────────────────────────────────────────────────

/**
 * Fetch the attendance dashboard for a partner company.
 *
 * @param companyName - Partner company name (ADR-003)
 * @param fromYear    - Earliest year to include (undefined → backend default: current year - 5)
 */
export const getAttendanceDashboard = async (
  companyName: string,
  fromYear?: number
): Promise<PartnerDashboardData> => {
  const params: Record<string, number> = {};
  if (fromYear !== undefined) {
    params.fromYear = fromYear;
  }

  const response = await apiClient.get<PartnerDashboardData>(
    `/partners/${companyName}/analytics/dashboard`,
    { params }
  );
  return response.data;
};

/**
 * Trigger Excel download for the attendance report.
 * Uses a temporary anchor element to initiate the browser download.
 *
 * @param companyName - Partner company name (ADR-003)
 * @param fromYear    - Earliest year to include
 */
export const exportAttendanceReport = async (
  companyName: string,
  fromYear?: number
): Promise<void> => {
  const params: Record<string, number> = {};
  if (fromYear !== undefined) {
    params.fromYear = fromYear;
  }

  const response = await apiClient.get(`/partners/${companyName}/analytics/export`, {
    params,
    responseType: 'blob',
  });

  // Extract filename from Content-Disposition header or use default
  const contentDisposition = response.headers['content-disposition'] as string | undefined;
  let filename = `attendance-${companyName}.xlsx`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename="?([^";\n]+)"?/);
    if (match) {
      filename = match[1];
    }
  }

  // Trigger browser download
  const url = URL.createObjectURL(new Blob([response.data as BlobPart]));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  setTimeout(() => URL.revokeObjectURL(url), 100);
};
