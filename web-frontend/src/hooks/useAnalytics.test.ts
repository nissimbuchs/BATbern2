/**
 * useAnalytics Tests (Story 10.5 — M1)
 *
 * Tests:
 * - useAnalyticsOverview returns data from service
 * - useAnalyticsAttendance passes fromYear to service
 * - useAnalyticsTopics passes fromYear to service
 * - useAnalyticsCompanies passes fromYear to service
 * - useCompanyDistribution is disabled when eventCode is empty
 * - useCompanyDistribution is enabled and calls service when eventCode is provided
 */

import { createElement, type ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  useAnalyticsOverview,
  useAnalyticsAttendance,
  useAnalyticsTopics,
  useAnalyticsCompanies,
  useCompanyDistribution,
} from './useAnalytics';

vi.mock('@/services/analyticsService', () => ({
  getAnalyticsOverview: vi.fn(),
  getAnalyticsAttendance: vi.fn(),
  getAnalyticsTopics: vi.fn(),
  getAnalyticsCompanies: vi.fn(),
  getCompanyDistribution: vi.fn(),
}));

import {
  getAnalyticsOverview,
  getAnalyticsAttendance,
  getAnalyticsTopics,
  getAnalyticsCompanies,
  getCompanyDistribution,
} from '@/services/analyticsService';

const makeWrapper = () => {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children);
};

describe('useAnalyticsOverview', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns data from getAnalyticsOverview', async () => {
    const payload = { totalEvents: 57 };
    vi.mocked(getAnalyticsOverview).mockResolvedValue(payload as never);

    const { result } = renderHook(() => useAnalyticsOverview(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
  });
});

describe('useAnalyticsAttendance', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls service with undefined when no fromYear', async () => {
    vi.mocked(getAnalyticsAttendance).mockResolvedValue({ events: [] } as never);

    const { result } = renderHook(() => useAnalyticsAttendance(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsAttendance).toHaveBeenCalledWith(undefined);
  });

  it('calls service with fromYear when provided', async () => {
    vi.mocked(getAnalyticsAttendance).mockResolvedValue({ events: [] } as never);

    const { result } = renderHook(() => useAnalyticsAttendance(2023), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsAttendance).toHaveBeenCalledWith(2023);
  });
});

describe('useAnalyticsTopics', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls service with undefined when no fromYear', async () => {
    vi.mocked(getAnalyticsTopics).mockResolvedValue({ eventsPerCategory: [] } as never);

    const { result } = renderHook(() => useAnalyticsTopics(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsTopics).toHaveBeenCalledWith(undefined);
  });

  it('calls service with fromYear when provided', async () => {
    vi.mocked(getAnalyticsTopics).mockResolvedValue({ eventsPerCategory: [] } as never);

    const { result } = renderHook(() => useAnalyticsTopics(2022), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsTopics).toHaveBeenCalledWith(2022);
  });
});

describe('useAnalyticsCompanies', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls service with undefined when no fromYear', async () => {
    vi.mocked(getAnalyticsCompanies).mockResolvedValue({ attendanceOverTime: [] } as never);

    const { result } = renderHook(() => useAnalyticsCompanies(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsCompanies).toHaveBeenCalledWith(undefined);
  });

  it('calls service with fromYear when provided', async () => {
    vi.mocked(getAnalyticsCompanies).mockResolvedValue({ attendanceOverTime: [] } as never);

    const { result } = renderHook(() => useAnalyticsCompanies(2021), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getAnalyticsCompanies).toHaveBeenCalledWith(2021);
  });
});

describe('useCompanyDistribution', () => {
  beforeEach(() => vi.clearAllMocks());

  it('does not call service when eventCode is empty', async () => {
    const { result } = renderHook(() => useCompanyDistribution(''), { wrapper: makeWrapper() });

    // Wait a tick and confirm it stayed idle
    await new Promise((r) => setTimeout(r, 50));
    expect(result.current.fetchStatus).toBe('idle');
    expect(getCompanyDistribution).not.toHaveBeenCalled();
  });

  it('calls service with eventCode when provided', async () => {
    const payload = { eventCode: 'BATbern57', distribution: [] };
    vi.mocked(getCompanyDistribution).mockResolvedValue(payload as never);

    const { result } = renderHook(() => useCompanyDistribution('BATbern57'), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getCompanyDistribution).toHaveBeenCalledWith('BATbern57');
    expect(result.current.data).toEqual(payload);
  });
});
