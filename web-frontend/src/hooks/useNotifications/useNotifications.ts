/**
 * useNotifications Hook
 * Story 1.17, Task 5b: React Query hook for notifications
 *
 * Fetches and caches notifications with mutations for marking as read/delete.
 * Uses React Query for server state management with 1-minute stale time.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';

interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  timestamp: string;
  isRead: boolean;
  actionUrl: string;
  priority: string;
}

interface NotificationsResponse {
  notifications: Notification[];
  unreadCount: number;
  totalCount: number;
  hasMore: boolean;
}

interface UseNotificationsOptions {
  enabled?: boolean;
  staleTime?: number;
}

/**
 * Fetch unread notifications
 */
const fetchNotifications = async (): Promise<NotificationsResponse> => {
  const response = await apiClient.get('/notifications?status=unread&limit=10');
  return response.data;
};

/**
 * Mark notification(s) as read
 */
const markAsReadMutation = async (notificationIds: string[]) => {
  const response = await apiClient.put('/notifications/read', {
    notificationIds,
  });
  return response.data;
};

/**
 * Mark all notifications as read
 */
const markAllAsReadMutation = async () => {
  const response = await apiClient.put('/notifications/read', {
    markAll: true,
  });
  return response.data;
};

/**
 * Delete a notification
 */
const deleteNotificationMutation = async (id: string) => {
  const response = await apiClient.delete(`/notifications/${id}`);
  return response.data;
};

/**
 * Custom hook to fetch and manage notifications
 *
 * @param options - Query options
 * @returns React Query result with notifications data and mutation functions
 *
 * @example
 * const { data, unreadCount, markAsRead, markAllAsRead, deleteNotification } = useNotifications();
 */
export function useNotifications(options?: UseNotificationsOptions) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: fetchNotifications,
    staleTime: options?.staleTime ?? 1000 * 60, // 1 minute default
    enabled: options?.enabled ?? true,
  });

  // Mutation for marking single notification as read
  const markAsReadMut = useMutation({
    mutationFn: (notificationId: string) => markAsReadMutation([notificationId]),
    onSuccess: () => {
      // Invalidate and refetch notifications after marking as read
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Mutation for marking all notifications as read
  const markAllAsReadMut = useMutation({
    mutationFn: markAllAsReadMutation,
    onSuccess: () => {
      // Invalidate and refetch notifications after marking all as read
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Mutation for deleting a notification
  const deleteNotificationMut = useMutation({
    mutationFn: deleteNotificationMutation,
    onSuccess: () => {
      // Invalidate and refetch notifications after deletion
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return {
    ...query,
    unreadCount: query.data?.unreadCount ?? 0,
    markAsRead: (id: string) => markAsReadMut.mutateAsync(id),
    markAllAsRead: () => markAllAsReadMut.mutateAsync(),
    deleteNotification: (id: string) => deleteNotificationMut.mutateAsync(id),
  };
}
