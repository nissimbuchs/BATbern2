/**
 * Company Hooks Tests
 *
 * Coverage for:
 * - useCompanies: paginated list with filters, enabled guard
 * - useCompany: single company fetch, enabled guard
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    getCompanies: vi.fn(),
    getCompany: vi.fn(),
  },
}));

import { companyApiClient } from '@/services/api/companyApi';
import { useCompanies } from './useCompanies/useCompanies';
import { useCompany } from './useCompany/useCompany';

const mockGetCompanies = vi.mocked(companyApiClient.getCompanies);
const mockGetCompany = vi.mocked(companyApiClient.getCompany);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_COMPANY = { name: 'GoogleZH', displayName: 'Google Zurich', isPartner: true };
const MOCK_PAGE = { data: [MOCK_COMPANY], total: 1, page: 1, limit: 20 };

// ── useCompanies ──────────────────────────────────────────────────────────────

describe('useCompanies', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch companies with pagination', async () => {
    mockGetCompanies.mockResolvedValue(MOCK_PAGE as never);

    const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompanies).toHaveBeenCalledWith({ page: 1, limit: 20 }, undefined, undefined);
    expect(result.current.data).toEqual(MOCK_PAGE);
  });

  it('should pass filters and expand options', async () => {
    mockGetCompanies.mockResolvedValue(MOCK_PAGE as never);

    const { result } = renderHook(
      () => useCompanies({ page: 1, limit: 10 }, { isPartner: true }, { expand: ['logo'] }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompanies).toHaveBeenCalledWith(
      { page: 1, limit: 10 },
      { isPartner: true },
      { expand: ['logo'] }
    );
  });

  it('should not fetch when enabled is false', () => {
    const { result } = renderHook(
      () => useCompanies({ page: 1, limit: 20 }, undefined, { enabled: false }),
      { wrapper: wrapper(qc) }
    );

    expect(result.current.isLoading).toBe(false);
    expect(mockGetCompanies).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetCompanies.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useCompanies({ page: 1, limit: 20 }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should use query key based on pagination and filters', async () => {
    mockGetCompanies.mockResolvedValue(MOCK_PAGE as never);

    renderHook(() => useCompanies({ page: 2, limit: 10 }, { isPartner: true }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'companies');
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'companies');
    expect(query).toBeDefined();
  });
});

// ── useCompany ────────────────────────────────────────────────────────────────

describe('useCompany', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch company by name', async () => {
    mockGetCompany.mockResolvedValue(MOCK_COMPANY as never);

    const { result } = renderHook(() => useCompany('GoogleZH'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompany).toHaveBeenCalledWith('GoogleZH', undefined);
    expect(result.current.data).toEqual(MOCK_COMPANY);
  });

  it('should fetch with expand options', async () => {
    mockGetCompany.mockResolvedValue(MOCK_COMPANY as never);

    const { result } = renderHook(
      () => useCompany('GoogleZH', { expand: ['logo', 'statistics'] }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompany).toHaveBeenCalledWith('GoogleZH', { expand: ['logo', 'statistics'] });
  });

  it('should not fetch when name is empty string', () => {
    const { result } = renderHook(() => useCompany(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetCompany).not.toHaveBeenCalled();
  });

  it('should not fetch when name is whitespace only', () => {
    const { result } = renderHook(() => useCompany('   '), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetCompany).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetCompany.mockRejectedValue(new Error('Not Found'));

    const { result } = renderHook(() => useCompany('UnknownCo'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
