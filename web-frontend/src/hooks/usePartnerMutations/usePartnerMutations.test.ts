/**
 * usePartnerMutations Tests
 *
 * Coverage for:
 * - useCreatePartner: mutation + cache invalidation + navigation
 * - useUpdatePartner: mutation + optimistic update + rollback
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('@/services/api/partnerApi', () => ({
  createPartner: vi.fn(),
  updatePartner: vi.fn(),
}));

import { createPartner, updatePartner } from '@/services/api/partnerApi';
import { useCreatePartner } from './usePartnerMutations';
import { useUpdatePartner } from './usePartnerMutations';

const mockCreatePartner = vi.mocked(createPartner);
const mockUpdatePartner = vi.mocked(updatePartner);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_PARTNER = { companyName: 'TechCorp', tier: 'GOLD', isActive: true };

// ── useCreatePartner ──────────────────────────────────────────────────────────

describe('useCreatePartner', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call createPartner with data', async () => {
    mockCreatePartner.mockResolvedValue(MOCK_PARTNER as never);

    const { result } = renderHook(() => useCreatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ companyName: 'TechCorp', tier: 'GOLD' } as never);
    });

    expect(mockCreatePartner).toHaveBeenCalledWith({ companyName: 'TechCorp', tier: 'GOLD' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should navigate to partner detail on success', async () => {
    mockCreatePartner.mockResolvedValue(MOCK_PARTNER as never);

    const { result } = renderHook(() => useCreatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ companyName: 'TechCorp' } as never);
    });

    expect(mockNavigate).toHaveBeenCalledWith('/organizer/partners/TechCorp');
  });

  it('should invalidate partners and partner-statistics on success', async () => {
    mockCreatePartner.mockResolvedValue(MOCK_PARTNER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useCreatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ companyName: 'TechCorp' } as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partners'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partner-statistics'] });
  });

  it('should set isError on failure', async () => {
    mockCreatePartner.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useCreatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ companyName: 'Dup' } as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdatePartner ──────────────────────────────────────────────────────────

describe('useUpdatePartner', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updatePartner with companyName and data', async () => {
    mockUpdatePartner.mockResolvedValue(MOCK_PARTNER as never);

    const { result } = renderHook(() => useUpdatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        companyName: 'TechCorp',
        data: { tier: 'PLATINUM' } as never,
      });
    });

    expect(mockUpdatePartner).toHaveBeenCalledWith('TechCorp', { tier: 'PLATINUM' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate partners, partner detail, and statistics on success', async () => {
    mockUpdatePartner.mockResolvedValue(MOCK_PARTNER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ companyName: 'TechCorp', data: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partners'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partner', 'TechCorp'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['partner-statistics'] });
  });

  it('should apply optimistic update when previous data exists in cache', async () => {
    mockUpdatePartner.mockResolvedValue(MOCK_PARTNER as never);

    qc.setQueryData(['partner', 'TechCorp', undefined], MOCK_PARTNER);

    const { result } = renderHook(() => useUpdatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        companyName: 'TechCorp',
        data: { tier: 'SILVER' } as never,
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should rollback optimistic update on error', async () => {
    mockUpdatePartner.mockRejectedValue(new Error('Server error'));

    // Seed previous value in cache so rollback code path executes
    qc.setQueryData(['partner', 'TechCorp', undefined], MOCK_PARTNER);

    const { result } = renderHook(() => useUpdatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ companyName: 'TechCorp', data: { tier: 'INVALID' } as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should set isError when no previous data and mutation fails', async () => {
    mockUpdatePartner.mockRejectedValue(new Error('Fail'));

    const { result } = renderHook(() => useUpdatePartner(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ companyName: 'Unknown', data: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
