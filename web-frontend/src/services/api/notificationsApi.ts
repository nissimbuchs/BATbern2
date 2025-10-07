/**
 * Notifications API Service
 * Story 1.17 & 1.26: Type-safe API client wrappers for notification endpoints
 *
 * Endpoints:
 * - GET /api/v1/notifications?status=unread&limit=10
 * - PUT /api/v1/notifications/read
 * - DELETE /api/v1/notifications/{id}
 */

import apiClient from './apiClient';
import {
  NotificationsResponse,
  MarkAsReadRequest,
  MarkAsReadResponse,
  DeleteNotificationResponse,
} from '@/types/notification';

/**
 * Fetch notifications with optional filters
 * @param status - Filter by status ('unread' or 'all')
 * @param limit - Maximum number of notifications to fetch
 * @returns Notifications response with unread count
 */
export const getNotifications = async (
  status: 'unread' | 'all' = 'unread',
  limit: number = 10
): Promise<NotificationsResponse> => {
  const params = { status, limit };

  const response = await apiClient.get<NotificationsResponse>('/api/v1/notifications', { params });
  return response.data;
};

/**
 * Mark notification(s) as read
 * @param request - Notification IDs or markAll flag
 * @returns Mark as read response with count
 */
export const markNotificationsAsRead = async (
  request: MarkAsReadRequest
): Promise<MarkAsReadResponse> => {
  const response = await apiClient.put<MarkAsReadResponse>('/api/v1/notifications/read', request);
  return response.data;
};

/**
 * Delete a notification
 * @param notificationId - Notification ID to delete
 * @returns Delete confirmation
 */
export const deleteNotification = async (
  notificationId: string
): Promise<DeleteNotificationResponse> => {
  const response = await apiClient.delete<DeleteNotificationResponse>(
    `/api/v1/notifications/${notificationId}`
  );
  return response.data;
};
