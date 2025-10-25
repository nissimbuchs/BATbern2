/**
 * useUserSync Hook
 *
 * React Query hooks for Cognito-Database user synchronization.
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * Provides:
 * - Sync status checking (Cognito vs Database)
 * - Manual reconciliation trigger
 * - Real-time sync state management
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkSyncStatus, reconcileUsers } from '@/services/api/userManagementApi';

/**
 * Check sync status between Cognito and Database
 * Polls every 30 seconds when enabled
 */
export const useSyncStatus = (enabled: boolean = false) => {
  return useQuery({
    queryKey: ['users', 'sync-status'],
    queryFn: checkSyncStatus,
    staleTime: 30 * 1000, // 30 seconds
    gcTime: 5 * 60 * 1000, // 5 minutes
    enabled,
    refetchInterval: enabled ? 30 * 1000 : false, // Poll every 30 seconds when enabled
  });
};

/**
 * Trigger manual user reconciliation
 * Invalidates sync status after success
 */
export const useReconcileUsers = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: reconcileUsers,
    onSuccess: () => {
      // Invalidate sync status to refresh
      queryClient.invalidateQueries({ queryKey: ['users', 'sync-status'] });
      // Invalidate user list to show newly synced users
      queryClient.invalidateQueries({ queryKey: ['users', 'list'] });
    },
  });
};

/**
 * Combined hook for user sync management
 * Provides both status checking and reconciliation
 */
export const useUserSync = () => {
  const syncStatus = useSyncStatus(true);
  const reconcileMutation = useReconcileUsers();

  return {
    // Sync status query
    syncStatus: syncStatus.data,
    isSyncStatusLoading: syncStatus.isLoading,
    isSyncStatusError: syncStatus.isError,
    syncStatusError: syncStatus.error,
    refetchSyncStatus: syncStatus.refetch,

    // Reconciliation mutation
    reconcile: reconcileMutation.mutate,
    reconcileAsync: reconcileMutation.mutateAsync,
    reconciliationResult: reconcileMutation.data,
    isReconciling: reconcileMutation.isPending,
    isReconcileSuccess: reconcileMutation.isSuccess,
    isReconcileError: reconcileMutation.isError,
    reconcileError: reconcileMutation.error,
    resetReconciliation: reconcileMutation.reset,

    // Computed values
    isInSync: syncStatus.data?.inSync ?? true,
    needsSync: syncStatus.data ? !syncStatus.data.inSync : false,
    missingCount: syncStatus.data?.missingInDatabase ?? 0,
    orphanedCount: syncStatus.data?.orphanedInDatabase ?? 0,
  };
};
