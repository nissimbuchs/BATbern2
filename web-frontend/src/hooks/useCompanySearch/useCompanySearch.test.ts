/**
 * useCompanySearch Tests
 *
 * Coverage for:
 * - Minimum 3-character guard
 * - Search with valid query
 * - Custom limit
 * - Whitespace trimming
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    searchCompanies: vi.fn(),
  },
}));

import { companyApiClient } from '@/services/api/companyApi';
import { useCompanySearch } from './useCompanySearch';

const mockSearchCompanies = vi.mocked(companyApiClient.searchCompanies);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useCompanySearch', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch companies when query >= 3 chars', async () => {
    const results = [{ name: 'AcmeCH', displayName: 'Acme Switzerland' }];
    mockSearchCompanies.mockResolvedValue(results as never);

    const { result } = renderHook(() => useCompanySearch('Acm'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockSearchCompanies).toHaveBeenCalledWith('Acm', 10);
    expect(result.current.data).toEqual(results);
  });

  it('should use custom limit', async () => {
    mockSearchCompanies.mockResolvedValue([] as never);

    const { result } = renderHook(() => useCompanySearch('Tech', 5), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockSearchCompanies).toHaveBeenCalledWith('Tech', 5);
  });

  it('should not fetch when query < 3 chars', () => {
    const { result } = renderHook(() => useCompanySearch('Ac'), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockSearchCompanies).not.toHaveBeenCalled();
  });

  it('should not fetch when query is empty', () => {
    const { result } = renderHook(() => useCompanySearch(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockSearchCompanies).not.toHaveBeenCalled();
  });

  it('should trim whitespace before checking length', () => {
    const { result } = renderHook(() => useCompanySearch('  a  '), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockSearchCompanies).not.toHaveBeenCalled();
  });

  it('should search with trimmed query', async () => {
    mockSearchCompanies.mockResolvedValue([] as never);

    const { result } = renderHook(() => useCompanySearch('  Acme  '), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockSearchCompanies).toHaveBeenCalledWith('Acme', 10);
  });

  it('should set isError on fetch failure', async () => {
    mockSearchCompanies.mockRejectedValue(new Error('Search failed'));

    const { result } = renderHook(() => useCompanySearch('Acme'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
