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
 * Fetches the signed-in organizer's notifications that in-process domain events created. The
 * server scopes every call to the caller (Story 4.10b), so the hooks send no username. The
 * signed-in username is part of the query keys, so one user's cache is never shown to the next
 * user in the same tab (sign-out also clears the cache, AuthProvider); without a signed-in user
 * nothing is fetched.
 */

/** The signed-in username, or undefined outside an AuthProvider or when signed out. */
const useSignedInUsername = (): string | undefined => useContext(AuthContext)?.user?.username;

import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import { useContext } from 'react';
import { AuthContext } from '@/contexts/AuthContext';
import { notificationApiClient } from '@/services/notificationApiClient';
import type {
  NotificationPage,
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
 * @param filters - Optional status filter
 * @param pagination - Page number and limit
 * @param options - React Query options (`enabled: false` while nobody is signed in)
 */
export const useNotifications = (
  filters: NotificationFilters = {},
  pagination: { page?: number; limit?: number } = {},
  options?: { enabled?: boolean }
): UseQueryResult<NotificationPage, Error> => {
  const username = useSignedInUsername();
  return useQuery({
    queryKey: ['notifications', username, filters, pagination],
    queryFn: () => notificationApiClient.getNotifications(filters, pagination),
    staleTime: 2 * 60 * 1000, // 2 minutes
    enabled: options?.enabled !== false && !!username,
  });
};

/**
 * useUnreadCount - Fetch unread notification count
 * Cache: 1 minute (count changes frequently)
 *
 * @param options - React Query options (`enabled: false` while nobody is signed in)
 */
export const useUnreadCount = (options?: { enabled?: boolean }): UseQueryResult<number, Error> => {
  const username = useSignedInUsername();
  return useQuery({
    queryKey: ['notifications', 'unread-count', username],
    queryFn: () => notificationApiClient.getUnreadCount(),
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
