/**
 * Analytics Service
 * Story 10.5: Analytics Dashboard — AC1, AC6
 *
 * Wraps the 5 analytics API endpoints.
 * All types come from the generated events-api OpenAPI spec.
 * CRITICAL: Always use this service — never call apiClient directly from components.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

// ─── Re-export generated types for consumer convenience ───────────────────────

export type AnalyticsOverviewResponse = components['schemas']['AnalyticsOverviewResponse'];
export type EventTimelineItem = components['schemas']['EventTimelineItem'];

export type AnalyticsAttendanceResponse = components['schemas']['AnalyticsAttendanceResponse'];
export type AttendanceEventItem = components['schemas']['AttendanceEventItem'];

export type AnalyticsTopicsResponse = components['schemas']['AnalyticsTopicsResponse'];
export type CategoryEventCount = components['schemas']['CategoryEventCount'];
export type TopicScatterItem = components['schemas']['TopicScatterItem'];

export type AnalyticsCompaniesResponse = components['schemas']['AnalyticsCompaniesResponse'];
export type CompanyYearAttendanceItem = components['schemas']['CompanyYearAttendanceItem'];
export type CompanySessionItem = components['schemas']['CompanySessionItem'];
export type CompanyAttendanceShare = components['schemas']['CompanyAttendanceShare'];

export type CompanyDistributionResponse = components['schemas']['CompanyDistributionResponse'];

// ─── Service functions ────────────────────────────────────────────────────────

/**
 * GET /api/v1/analytics/overview
 * All-time KPI totals and event cadence timeline. Not filtered by time range.
 * AC2, AC6.
 */
export const getAnalyticsOverview = async (): Promise<AnalyticsOverviewResponse> => {
  const response = await apiClient.get<AnalyticsOverviewResponse>('/analytics/overview');
  return response.data;
};

/**
 * GET /api/v1/analytics/attendance?fromYear={year}
 * Per-event attendance with returning/new attendee breakdown.
 * AC3, AC6.
 *
 * @param fromYear - Optional year filter (inclusive); undefined = all-time
 */
export const getAnalyticsAttendance = async (
  fromYear?: number
): Promise<AnalyticsAttendanceResponse> => {
  const params = fromYear !== undefined ? { fromYear } : {};
  const response = await apiClient.get<AnalyticsAttendanceResponse>('/analytics/attendance', {
    params,
  });
  return response.data;
};

/**
 * GET /api/v1/analytics/topics?fromYear={year}
 * Events per category and topic scatter data.
 * AC4, AC6.
 *
 * @param fromYear - Optional year filter (inclusive); undefined = all-time
 */
export const getAnalyticsTopics = async (fromYear?: number): Promise<AnalyticsTopicsResponse> => {
  const params = fromYear !== undefined ? { fromYear } : {};
  const response = await apiClient.get<AnalyticsTopicsResponse>('/analytics/topics', { params });
  return response.data;
};

/**
 * GET /api/v1/analytics/companies?fromYear={year}
 * Company analytics: attendance over time, sessions per company, distribution.
 * AC5, AC6.
 *
 * @param fromYear - Optional year filter (inclusive); undefined = all-time
 */
export const getAnalyticsCompanies = async (
  fromYear?: number
): Promise<AnalyticsCompaniesResponse> => {
  const params = fromYear !== undefined ? { fromYear } : {};
  const response = await apiClient.get<AnalyticsCompaniesResponse>('/analytics/companies', {
    params,
  });
  return response.data;
};

/**
 * GET /api/v1/analytics/companies/distribution?eventCode={code}
 * Per-event company attendee distribution for the pie chart event filter.
 * AC5, AC6.
 *
 * @param eventCode - Event code (e.g. "BATbern57")
 */
export const getCompanyDistribution = async (
  eventCode: string
): Promise<CompanyDistributionResponse> => {
  const response = await apiClient.get<CompanyDistributionResponse>(
    '/analytics/companies/distribution',
    { params: { eventCode } }
  );
  return response.data;
};
