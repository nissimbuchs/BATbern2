/**
 * Notification API Client
 *
 * Story BAT-7 - Notifications API Consolidation
 *
 * HTTP client for Notification Service APIs (Event Management Service)
 * Features:
 * - Fetch notifications for organizers
 * - Mark notifications as read
 * - Delete notifications
 * - Get unread count
 * - EventBridge-triggered notifications
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type {
  NotificationsResponse,
  NotificationCountResponse,
  NotificationFilters,
  MarkAsReadResponse,
  DeleteNotificationResponse,
} from '@/types/notification';

// API base path for notification endpoints
const NOTIFICATION_API_PATH = '/notifications';

/**
 * Notification API Client Class
 *
 * Handles all HTTP requests to the Notification APIs
 */
class NotificationApiClient {
  /**
   * Get paginated list of notifications with optional filters
   */
  async getNotifications(
    filters: NotificationFilters,
    pagination: { page?: number; limit?: number } = {}
  ): Promise<NotificationsResponse> {
    try {
      const params = new URLSearchParams();
      params.append('username', filters.username);
      params.append('page', (pagination.page || 1).toString());
      params.append('limit', (pagination.limit || 20).toString());

      if (filters.status) {
        params.append('status', filters.status);
      }
      if (filters.eventCode) {
        params.append('eventCode', filters.eventCode);
      }

      const response = await apiClient.get<NotificationsResponse>(
        `${NOTIFICATION_API_PATH}?${params.toString()}`
      );

      // Enhance notifications with computed properties
      const enhancedData = response.data.data.map((notification) => ({
        ...notification,
        isRead: notification.status === 'READ',
        actionUrl: notification.eventCode
          ? `/organizer/events/${notification.eventCode}`
          : undefined,
      }));

      return {
        ...response.data,
        data: enhancedData,
      };
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get unread notification count for user
   */
  async getUnreadCount(username: string): Promise<number> {
    try {
      const params = new URLSearchParams();
      params.append('username', username);
      params.append('status', 'UNREAD'); // In-app notifications have status UNREAD

      const response = await apiClient.get<NotificationCountResponse>(
        `${NOTIFICATION_API_PATH}/count?${params.toString()}`
      );

      return response.data.count;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Mark a single notification as read
   */
  async markAsRead(notificationId: string): Promise<MarkAsReadResponse> {
    try {
      const response = await apiClient.put<MarkAsReadResponse>(
        `${NOTIFICATION_API_PATH}/${notificationId}/read`
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Mark multiple notifications as read (batch)
   */
  async batchMarkAsRead(notificationIds: string[]): Promise<MarkAsReadResponse> {
    try {
      const response = await apiClient.put<MarkAsReadResponse>(
        `${NOTIFICATION_API_PATH}/batch-read`,
        { notificationIds }
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete a notification
   */
  async deleteNotification(notificationId: string): Promise<DeleteNotificationResponse> {
    try {
      const response = await apiClient.delete<DeleteNotificationResponse>(
        `${NOTIFICATION_API_PATH}/${notificationId}`
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete multiple notifications (batch)
   */
  async batchDelete(notificationIds: string[]): Promise<DeleteNotificationResponse> {
    try {
      const response = await apiClient.delete<DeleteNotificationResponse>(
        `${NOTIFICATION_API_PATH}/batch-delete`,
        { data: { notificationIds } }
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Transform API errors into user-friendly messages
   */
  private transformError(error: unknown): Error {
    if (error instanceof AxiosError) {
      const message = error.response?.data?.message || error.message;
      const correlationId = error.response?.headers?.['x-correlation-id'];

      return new Error(correlationId ? `${message} (Correlation ID: ${correlationId})` : message);
    }

    return error instanceof Error ? error : new Error('Unknown error occurred');
  }
}

// Export singleton instance
export const notificationApiClient = new NotificationApiClient();
export default notificationApiClient;
