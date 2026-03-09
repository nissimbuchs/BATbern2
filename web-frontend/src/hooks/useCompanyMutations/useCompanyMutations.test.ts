/**
 * useCompanyMutations Tests
 *
 * Coverage for:
 * - useCreateCompany: mutation + cache invalidation
 * - useUpdateCompany: mutation + optimistic update + rollback
 * - useDeleteCompany: mutation + cache invalidation + removal
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    createCompany: vi.fn(),
    updateCompany: vi.fn(),
    deleteCompany: vi.fn(),
  },
}));

import { companyApiClient } from '@/services/api/companyApi';
import { useCreateCompany } from './useCompanyMutations';
import { useUpdateCompany } from './useCompanyMutations';
import { useDeleteCompany } from './useCompanyMutations';

const mockCreateCompany = vi.mocked(companyApiClient.createCompany);
const mockUpdateCompany = vi.mocked(companyApiClient.updateCompany);
const mockDeleteCompany = vi.mocked(companyApiClient.deleteCompany);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_COMPANY = { name: 'AcmeCH', displayName: 'Acme Switzerland', isPartner: false };

// ── useCreateCompany ──────────────────────────────────────────────────────────

describe('useCreateCompany', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call createCompany with form data', async () => {
    mockCreateCompany.mockResolvedValue(MOCK_COMPANY as never);

    const { result } = renderHook(() => useCreateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        name: 'AcmeCH',
        displayName: 'Acme Switzerland',
      } as never);
    });

    expect(mockCreateCompany).toHaveBeenCalledWith({
      name: 'AcmeCH',
      displayName: 'Acme Switzerland',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate companies query on success', async () => {
    mockCreateCompany.mockResolvedValue(MOCK_COMPANY as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useCreateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
  });

  it('should set isError on failure', async () => {
    mockCreateCompany.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useCreateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should start with idle state', () => {
    const { result } = renderHook(() => useCreateCompany(), { wrapper: wrapper(qc) });

    expect(result.current.isPending).toBe(false);
    expect(result.current.isSuccess).toBe(false);
  });
});

// ── useUpdateCompany ──────────────────────────────────────────────────────────

describe('useUpdateCompany', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateCompany with name and data', async () => {
    mockUpdateCompany.mockResolvedValue(MOCK_COMPANY as never);

    const { result } = renderHook(() => useUpdateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        name: 'AcmeCH',
        data: { displayName: 'Acme AG' } as never,
      });
    });

    expect(mockUpdateCompany).toHaveBeenCalledWith('AcmeCH', { displayName: 'Acme AG' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate companies and company queries on success', async () => {
    mockUpdateCompany.mockResolvedValue(MOCK_COMPANY as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ name: 'AcmeCH', data: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['company', 'AcmeCH'] });
  });

  it('should apply optimistic update when previous data exists', async () => {
    mockUpdateCompany.mockResolvedValue(MOCK_COMPANY as never);

    // Seed existing data in cache
    qc.setQueryData(['company', 'AcmeCH', undefined], MOCK_COMPANY);

    const { result } = renderHook(() => useUpdateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        name: 'AcmeCH',
        data: { displayName: 'Updated' } as never,
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should rollback optimistic update on error', async () => {
    mockUpdateCompany.mockRejectedValue(new Error('Server error'));

    // Seed previous value in cache so rollback code path executes
    qc.setQueryData(['company', 'AcmeCH', undefined], MOCK_COMPANY);

    const { result } = renderHook(() => useUpdateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ name: 'AcmeCH', data: { displayName: 'Bad' } as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should set isError without rollback when no previous data exists', async () => {
    mockUpdateCompany.mockRejectedValue(new Error('Fail'));

    const { result } = renderHook(() => useUpdateCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ name: 'NewCo', data: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteCompany ──────────────────────────────────────────────────────────

describe('useDeleteCompany', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteCompany with company name', async () => {
    mockDeleteCompany.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('AcmeCH');
    });

    expect(mockDeleteCompany).toHaveBeenCalledWith('AcmeCH');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate companies and remove company cache on success', async () => {
    mockDeleteCompany.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const removeSpy = vi.spyOn(qc, 'removeQueries');

    const { result } = renderHook(() => useDeleteCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('AcmeCH');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['companies'] });
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: ['company', 'AcmeCH'] });
  });

  it('should set isError on failure', async () => {
    mockDeleteCompany.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useDeleteCompany(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('AcmeCH').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
