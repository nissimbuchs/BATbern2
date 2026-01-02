/**
 * Notification React Query Hooks
 *
 * Story BAT-7 - Notifications API Consolidation
 *
 * React Query hooks for Notification feature:
 * - useNotifications: List query with filters, pagination (cache 2min)
 * - useUnreadCount: Unread count query (cache 1min)
 * - useMarkAsRead: Mark notification as read mutation
 * - useBatchMarkAsRead: Batch mark as read mutation
 * - useDeleteNotification: Delete notification mutation
 *
 * EventBridge Integration: Automatically fetches notifications triggered by domain events
 */

import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import { notificationApiClient } from '@/services/notificationApiClient';
import type {
  NotificationsResponse,
  NotificationFilters,
  MarkAsReadResponse,
  DeleteNotificationResponse,
} from '@/types/notification';

// ============================================================================
// Query Hooks
// ============================================================================

/**
 * useNotifications - Fetch paginated list of notifications
 * Cache: 2 minutes (notifications are volatile)
 *
 * @param filters - Username (required) and optional status/eventCode filters
 * @param pagination - Page number and limit
 * @param options - React Query options
 */
export const useNotifications = (
  filters: NotificationFilters,
  pagination: { page?: number; limit?: number } = {},
  options?: { enabled?: boolean }
): UseQueryResult<NotificationsResponse, Error> => {
  return useQuery({
    queryKey: ['notifications', filters, pagination],
    queryFn: () => notificationApiClient.getNotifications(filters, pagination),
    staleTime: 2 * 60 * 1000, // 2 minutes
    enabled: options?.enabled !== false && !!filters.username,
  });
};

/**
 * useUnreadCount - Fetch unread notification count
 * Cache: 1 minute (count changes frequently)
 *
 * @param username - Username of the user
 * @param options - React Query options
 */
export const useUnreadCount = (
  username: string | undefined,
  options?: { enabled?: boolean }
): UseQueryResult<number, Error> => {
  return useQuery({
    queryKey: ['notifications', 'unread-count', username],
    queryFn: () => notificationApiClient.getUnreadCount(username!),
    staleTime: 1 * 60 * 1000, // 1 minute
    enabled: options?.enabled !== false && !!username,
  });
};

// ============================================================================
// Mutation Hooks
// ============================================================================

/**
 * useMarkAsRead - Mark a single notification as read
 *
 * Automatically invalidates notification queries on success
 */
export const useMarkAsRead = (): UseMutationResult<MarkAsReadResponse, Error, string, unknown> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationId: string) => notificationApiClient.markAsRead(notificationId),
    onSuccess: () => {
      // Invalidate all notification queries to refetch with updated data
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
};

/**
 * useBatchMarkAsRead - Mark multiple notifications as read
 *
 * Automatically invalidates notification queries on success
 */
export const useBatchMarkAsRead = (): UseMutationResult<
  MarkAsReadResponse,
  Error,
  string[],
  unknown
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationIds: string[]) =>
      notificationApiClient.batchMarkAsRead(notificationIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
};

/**
 * useDeleteNotification - Delete a single notification
 *
 * Automatically invalidates notification queries on success
 */
export const useDeleteNotification = (): UseMutationResult<
  DeleteNotificationResponse,
  Error,
  string,
  unknown
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationId: string) =>
      notificationApiClient.deleteNotification(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
};

/**
 * useBatchDeleteNotifications - Delete multiple notifications
 *
 * Automatically invalidates notification queries on success
 */
export const useBatchDeleteNotifications = (): UseMutationResult<
  DeleteNotificationResponse,
  Error,
  string[],
  unknown
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationIds: string[]) => notificationApiClient.batchDelete(notificationIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
};
