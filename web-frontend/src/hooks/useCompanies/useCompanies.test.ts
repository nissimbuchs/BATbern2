/**
 * useCompanies Hook Tests
 *
 * Coverage for:
 * - useCompanies: fetch with pagination + optional filters
 * - enabled guard via options.enabled
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    getCompanies: vi.fn(),
  },
}));

import { companyApiClient } from '@/services/api/companyApi';
import { useCompanies } from './useCompanies';

const mockGetCompanies = vi.mocked(companyApiClient.getCompanies);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const PAGINATION = { page: 1, limit: 20 };
const MOCK_COMPANIES = { data: [{ id: 'c1', name: 'Acme' }], total: 1 };

describe('useCompanies', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch companies with pagination', async () => {
    mockGetCompanies.mockResolvedValue(MOCK_COMPANIES as never);

    const { result } = renderHook(() => useCompanies(PAGINATION), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompanies).toHaveBeenCalledWith(PAGINATION, undefined, undefined);
    expect(result.current.data).toEqual(MOCK_COMPANIES);
  });

  it('should pass filters and options to the service', async () => {
    mockGetCompanies.mockResolvedValue(MOCK_COMPANIES as never);
    const filters = { search: 'Acme' };
    const options = { expand: ['logo'] };

    const { result } = renderHook(() => useCompanies(PAGINATION, filters as never, options), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCompanies).toHaveBeenCalledWith(PAGINATION, filters, options);
  });

  it('should not fetch when options.enabled=false', () => {
    const { result } = renderHook(() => useCompanies(PAGINATION, undefined, { enabled: false }), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetCompanies).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockGetCompanies.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useCompanies(PAGINATION), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
