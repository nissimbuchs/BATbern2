/**
 * Notification API Client
 *
 * Story BAT-7 - Notifications API Consolidation
 *
 * HTTP client for Notification Service APIs (Event Management Service)
 * Features:
 * - Fetch the signed-in organizer's notifications (the server scopes every call to the caller,
 *   Story 4.10b: there is no username parameter)
 * - Mark notifications as read (PATCH, status READ)
 * - Delete notifications
 * - Get unread count
 * - notifications created from domain events
 */

import apiClient from '@/services/api/apiClient';
import { isAxiosError } from 'axios';
import type {
  NotificationPage,
  NotificationCountResponse,
  NotificationFilters,
  MarkAsReadResponse,
  DeleteNotificationResponse,
  UpdateNotificationRequest,
  UpdateNotificationsRequest,
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
   * Get a page of the caller's notifications, optionally by status
   */
  async getNotifications(
    filters: NotificationFilters = {},
    pagination: { page?: number; limit?: number } = {}
  ): Promise<NotificationPage> {
    try {
      const params = new URLSearchParams();
      params.append('page', (pagination.page || 1).toString());
      params.append('limit', (pagination.limit || 20).toString());

      if (filters.status) {
        params.append('status', filters.status);
      }

      const response = await apiClient.get<NotificationPage>(
        `${NOTIFICATION_API_PATH}?${params.toString()}`
      );

      // Enhance notifications with computed properties and UI aliases
      const enhancedData = response.data.data.map((notification) => ({
        ...notification,
        isRead: notification.status === 'READ',
        actionUrl: notification.eventCode
          ? `/organizer/events/${notification.eventCode}`
          : undefined,
        // UI aliases for backward compatibility
        title: notification.subject,
        message: notification.body,
        timestamp: notification.createdAt,
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
   * Get the caller's unread notification count
   */
  async getUnreadCount(): Promise<number> {
    try {
      const params = new URLSearchParams();
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
      const response = await apiClient.patch<MarkAsReadResponse>(
        `${NOTIFICATION_API_PATH}/${notificationId}`,
        { status: 'READ' } satisfies UpdateNotificationRequest
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
      const response = await apiClient.patch<MarkAsReadResponse>(NOTIFICATION_API_PATH, {
        notificationIds,
        status: 'READ',
      } satisfies UpdateNotificationsRequest);

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
   * Transform API errors into user-friendly messages
   */
  private transformError(error: unknown): Error {
    if (isAxiosError(error)) {
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
