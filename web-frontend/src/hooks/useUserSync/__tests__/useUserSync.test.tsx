import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSyncStatus, useReconcileUsers, useUserSync } from '../useUserSync';
import { checkSyncStatus, reconcileUsers } from '@/services/api/userManagementApi';
import React, { type ReactNode } from 'react';

vi.mock('@/services/api/userManagementApi', () => ({
  checkSyncStatus: vi.fn(),
  reconcileUsers: vi.fn(),
}));

describe('useUserSync', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  describe('useSyncStatus', () => {
    it('should_returnSyncStatus_when_enabled', async () => {
      const mockStatus = {
        inSync: false,
        missingInDatabase: 5,
        orphanedInDatabase: 2,
      };
      vi.mocked(checkSyncStatus).mockResolvedValue(mockStatus);

      const { result } = renderHook(() => useSyncStatus(true), { wrapper });

      await waitFor(() => {
        expect(result.current.data).toEqual(mockStatus);
      });
    });

    it('should_notFetch_when_disabled', () => {
      const { result } = renderHook(() => useSyncStatus(false), { wrapper });

      expect(result.current.fetchStatus).toBe('idle');
      expect(checkSyncStatus).not.toHaveBeenCalled();
    });
  });

  describe('useReconcileUsers', () => {
    it('should_reconcileUsers_when_mutationCalled', async () => {
      const mockResult = {
        usersCreated: 5,
        usersDeleted: 2,
        errors: [],
      };
      vi.mocked(reconcileUsers).mockResolvedValue(mockResult);

      const { result } = renderHook(() => useReconcileUsers(), { wrapper });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockResult);
    });

    it('should_invalidateQueries_when_reconcileSucceeds', async () => {
      const mockResult = {
        usersCreated: 3,
        usersDeleted: 1,
        errors: [],
      };
      vi.mocked(reconcileUsers).mockResolvedValue(mockResult);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useReconcileUsers(), { wrapper });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'sync-status'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'list'] });
    });
  });

  describe('useUserSync', () => {
    it('should_returnCombinedData_when_syncStatusAvailable', async () => {
      const mockStatus = {
        inSync: false,
        missingInDatabase: 5,
        orphanedInDatabase: 2,
      };
      vi.mocked(checkSyncStatus).mockResolvedValue(mockStatus);

      const { result } = renderHook(() => useUserSync(), { wrapper });

      await waitFor(() => {
        expect(result.current.syncStatus).toEqual(mockStatus);
      });

      expect(result.current.isInSync).toBe(false);
      expect(result.current.needsSync).toBe(true);
      expect(result.current.missingCount).toBe(5);
      expect(result.current.orphanedCount).toBe(2);
    });

    it('should_defaultToInSync_when_noDataAvailable', () => {
      vi.mocked(checkSyncStatus).mockResolvedValue({
        inSync: true,
        missingInDatabase: 0,
        orphanedInDatabase: 0,
      });

      const { result } = renderHook(() => useUserSync(), { wrapper });

      expect(result.current.isInSync).toBe(true);
      expect(result.current.needsSync).toBe(false);
      expect(result.current.missingCount).toBe(0);
      expect(result.current.orphanedCount).toBe(0);
    });

    it('should_provideReconcileFunction_when_rendered', async () => {
      const mockResult = {
        usersCreated: 3,
        usersDeleted: 1,
        errors: [],
      };
      vi.mocked(reconcileUsers).mockResolvedValue(mockResult);
      vi.mocked(checkSyncStatus).mockResolvedValue({
        inSync: true,
        missingInDatabase: 0,
        orphanedInDatabase: 0,
      });

      const { result } = renderHook(() => useUserSync(), { wrapper });

      result.current.reconcile();

      await waitFor(() => {
        expect(result.current.isReconcileSuccess).toBe(true);
      });

      expect(result.current.reconciliationResult).toEqual(mockResult);
    });

    it('should_exposeLoadingStates_when_reconciling', async () => {
      vi.mocked(reconcileUsers).mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve({ usersCreated: 0, usersDeleted: 0, errors: [] }), 100)
          )
      );
      vi.mocked(checkSyncStatus).mockResolvedValue({
        inSync: true,
        missingInDatabase: 0,
        orphanedInDatabase: 0,
      });

      const { result } = renderHook(() => useUserSync(), { wrapper });

      result.current.reconcile();

      // Wait for the loading state to become true
      await waitFor(() => {
        expect(result.current.isReconciling).toBe(true);
      });

      // Then wait for it to become false when complete
      await waitFor(() => {
        expect(result.current.isReconciling).toBe(false);
      });
    });
  });
});
