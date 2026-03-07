/**
 * useUserSync Hooks Tests
 *
 * Coverage for:
 * - useSyncStatus: query with enabled guard
 * - useReconcileUsers: mutation + cache invalidation
 * - useUserSync: combined hook exposing computed values
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  checkSyncStatus: vi.fn(),
  reconcileUsers: vi.fn(),
  listUsers: vi.fn(),
  getUserById: vi.fn(),
  searchUsers: vi.fn(),
  updateUserRoles: vi.fn(),
  createUser: vi.fn(),
  deleteUser: vi.fn(),
  updateUser: vi.fn(),
}));

import { checkSyncStatus, reconcileUsers } from '@/services/api/userManagementApi';
import { useSyncStatus, useReconcileUsers, useUserSync } from './useUserSync';

const mockCheckSyncStatus = vi.mocked(checkSyncStatus);
const mockReconcileUsers = vi.mocked(reconcileUsers);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_SYNC_STATUS = {
  inSync: true,
  missingInDatabase: 0,
  orphanedInDatabase: 0,
  lastCheckedAt: '2025-12-01T10:00:00Z',
};

const OUT_OF_SYNC = {
  inSync: false,
  missingInDatabase: 3,
  orphanedInDatabase: 1,
  lastCheckedAt: '2025-12-01T10:00:00Z',
};

// ── useSyncStatus ─────────────────────────────────────────────────────────────

describe('useSyncStatus', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should not fetch when enabled is false (default)', () => {
    const { result } = renderHook(() => useSyncStatus(false), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockCheckSyncStatus).not.toHaveBeenCalled();
  });

  it('should fetch when enabled is true', async () => {
    mockCheckSyncStatus.mockResolvedValue(MOCK_SYNC_STATUS as never);

    const { result } = renderHook(() => useSyncStatus(true), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockCheckSyncStatus).toHaveBeenCalled();
    expect(result.current.data).toEqual(MOCK_SYNC_STATUS);
  });

  it('should set isError on failure', async () => {
    mockCheckSyncStatus.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useSyncStatus(true), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useReconcileUsers ─────────────────────────────────────────────────────────

describe('useReconcileUsers', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call reconcileUsers on mutate', async () => {
    const reconcileResult = { synced: 3, skipped: 0 };
    mockReconcileUsers.mockResolvedValue(reconcileResult as never);

    const { result } = renderHook(() => useReconcileUsers(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync(undefined as never);
    });

    expect(mockReconcileUsers).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(reconcileResult);
  });

  it('should invalidate sync-status and users list on success', async () => {
    mockReconcileUsers.mockResolvedValue({} as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useReconcileUsers(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync(undefined as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'sync-status'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
  });

  it('should set isError on failure', async () => {
    mockReconcileUsers.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => useReconcileUsers(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync(undefined as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUserSync (combined) ────────────────────────────────────────────────────

describe('useUserSync', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should expose isInSync=true when status says in sync', async () => {
    mockCheckSyncStatus.mockResolvedValue(MOCK_SYNC_STATUS as never);

    const { result } = renderHook(() => useUserSync(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isSyncStatusLoading).toBe(false));

    expect(result.current.isInSync).toBe(true);
    expect(result.current.needsSync).toBe(false);
    expect(result.current.missingCount).toBe(0);
    expect(result.current.orphanedCount).toBe(0);
  });

  it('should expose isInSync=false and counts when out of sync', async () => {
    mockCheckSyncStatus.mockResolvedValue(OUT_OF_SYNC as never);

    const { result } = renderHook(() => useUserSync(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isSyncStatusLoading).toBe(false));

    expect(result.current.isInSync).toBe(false);
    expect(result.current.needsSync).toBe(true);
    expect(result.current.missingCount).toBe(3);
    expect(result.current.orphanedCount).toBe(1);
  });

  it('should default isInSync=true when data is undefined', () => {
    mockCheckSyncStatus.mockResolvedValue(MOCK_SYNC_STATUS as never);

    const { result } = renderHook(() => useUserSync(), { wrapper: wrapper(qc) });

    // Before data loads, defaults should be safe
    expect(result.current.isInSync).toBe(true);
    expect(result.current.needsSync).toBe(false);
    expect(result.current.missingCount).toBe(0);
  });

  it('should expose reconcile functions', () => {
    mockCheckSyncStatus.mockResolvedValue(MOCK_SYNC_STATUS as never);

    const { result } = renderHook(() => useUserSync(), { wrapper: wrapper(qc) });

    expect(typeof result.current.reconcile).toBe('function');
    expect(typeof result.current.reconcileAsync).toBe('function');
    expect(typeof result.current.resetReconciliation).toBe('function');
  });

  it('should start with isReconciling=false', () => {
    mockCheckSyncStatus.mockResolvedValue(MOCK_SYNC_STATUS as never);

    const { result } = renderHook(() => useUserSync(), { wrapper: wrapper(qc) });

    expect(result.current.isReconciling).toBe(false);
    expect(result.current.isReconcileSuccess).toBe(false);
    expect(result.current.isReconcileError).toBe(false);
  });
});
