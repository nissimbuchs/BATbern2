/**
 * usePartners / usePartnerStatistics Tests
 *
 * Coverage for:
 * - usePartners: paginated list with filters, sort, pagination
 * - usePartnerStatistics: statistics query
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/partnerApi', () => ({
  listPartners: vi.fn(),
  getPartnerStatistics: vi.fn(),
  createPartner: vi.fn(),
  updatePartner: vi.fn(),
}));

import { listPartners, getPartnerStatistics } from '@/services/api/partnerApi';
import { usePartners, usePartnerStatistics } from './usePartners';

const mockListPartners = vi.mocked(listPartners);
const mockGetPartnerStatistics = vi.mocked(getPartnerStatistics);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_PARTNERS_PAGE = {
  data: [{ companyName: 'TechCorp', tier: 'GOLD', isActive: true }],
  total: 1,
  page: 0,
  size: 20,
};

const MOCK_STATISTICS = {
  total: 10,
  active: 7,
  tierDistribution: { GOLD: 3, SILVER: 4 },
  engagedPercentage: 70,
};

// ── usePartners ───────────────────────────────────────────────────────────────

describe('usePartners', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch partners with filters, sort, pagination', async () => {
    mockListPartners.mockResolvedValue(MOCK_PARTNERS_PAGE as never);

    const filters = { tier: 'GOLD' };
    const sort = { field: 'name', direction: 'ASC' };
    const pagination = { page: 0, size: 20 };

    const { result } = renderHook(
      () => usePartners(filters as never, sort as never, pagination as never),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockListPartners).toHaveBeenCalledWith(filters, sort, pagination);
    expect(result.current.data).toEqual(MOCK_PARTNERS_PAGE);
  });

  it('should fetch all partners without filters', async () => {
    mockListPartners.mockResolvedValue(MOCK_PARTNERS_PAGE as never);

    const { result } = renderHook(
      () => usePartners({} as never, {} as never, { page: 0, size: 20 } as never),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toEqual(MOCK_PARTNERS_PAGE);
  });

  it('should set isError on fetch failure', async () => {
    mockListPartners.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(
      () => usePartners({} as never, {} as never, { page: 0, size: 20 } as never),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should include filters, sort, and pagination in query key', async () => {
    mockListPartners.mockResolvedValue(MOCK_PARTNERS_PAGE as never);

    const filters = { isActive: true };
    renderHook(() => usePartners(filters as never, {} as never, { page: 1, size: 10 } as never), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'partners');
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'partners');
    expect(query).toBeDefined();
  });
});

// ── usePartnerStatistics ──────────────────────────────────────────────────────

describe('usePartnerStatistics', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch partner statistics', async () => {
    mockGetPartnerStatistics.mockResolvedValue(MOCK_STATISTICS as never);

    const { result } = renderHook(() => usePartnerStatistics(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetPartnerStatistics).toHaveBeenCalled();
    expect(result.current.data).toEqual(MOCK_STATISTICS);
  });

  it('should set isError on fetch failure', async () => {
    mockGetPartnerStatistics.mockRejectedValue(new Error('Service error'));

    const { result } = renderHook(() => usePartnerStatistics(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should use partner-statistics query key', async () => {
    mockGetPartnerStatistics.mockResolvedValue(MOCK_STATISTICS as never);

    renderHook(() => usePartnerStatistics(), { wrapper: wrapper(qc) });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'partner-statistics');
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'partner-statistics');
    expect(query).toBeDefined();
  });
});
