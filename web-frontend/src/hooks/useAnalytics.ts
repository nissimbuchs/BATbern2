/**
 * Analytics Hooks
 * Story 10.5: Analytics Dashboard — AC1
 *
 * React Query hooks for the 5 analytics endpoints.
 * Query keys follow the pattern: ['analytics', endpoint, ...params]
 */

import { useQuery } from '@tanstack/react-query';
import {
  getAnalyticsAttendance,
  getAnalyticsCompanies,
  getAnalyticsOverview,
  getAnalyticsTopics,
  getCompanyDistribution,
} from '@/services/analyticsService';
import type {
  AnalyticsAttendanceResponse,
  AnalyticsCompaniesResponse,
  AnalyticsOverviewResponse,
  AnalyticsTopicsResponse,
  CompanyDistributionResponse,
} from '@/services/analyticsService';

export const useAnalyticsOverview = () =>
  useQuery<AnalyticsOverviewResponse>({
    queryKey: ['analytics', 'overview'],
    queryFn: getAnalyticsOverview,
  });

export const useAnalyticsAttendance = (fromYear?: number) =>
  useQuery<AnalyticsAttendanceResponse>({
    queryKey: ['analytics', 'attendance', fromYear],
    queryFn: () => getAnalyticsAttendance(fromYear),
  });

export const useAnalyticsTopics = (fromYear?: number) =>
  useQuery<AnalyticsTopicsResponse>({
    queryKey: ['analytics', 'topics', fromYear],
    queryFn: () => getAnalyticsTopics(fromYear),
  });

export const useAnalyticsCompanies = (fromYear?: number) =>
  useQuery<AnalyticsCompaniesResponse>({
    queryKey: ['analytics', 'companies', fromYear],
    queryFn: () => getAnalyticsCompanies(fromYear),
  });

export const useCompanyDistribution = (eventCode: string) =>
  useQuery<CompanyDistributionResponse>({
    queryKey: ['analytics', 'companies', 'distribution', eventCode],
    queryFn: () => getCompanyDistribution(eventCode),
    enabled: !!eventCode,
  });
