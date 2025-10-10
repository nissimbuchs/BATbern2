/**
 * Notification types for BATbern platform
 * Based on Story 1.17 and 1.26 API specifications
 */

export type NotificationType =
  | 'EVENT_STATUS_CHANGED'
  | 'SPEAKER_INVITED'
  | 'SPEAKER_CONFIRMED'
  | 'SPEAKER_DECLINED'
  | 'PARTNER_ANALYSIS_READY'
  | 'NEW_MESSAGE'
  | 'SYSTEM_ANNOUNCEMENT';

export type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  timestamp: string; // ISO 8601 format
  isRead: boolean;
  actionUrl?: string;
  priority: NotificationPriority;
}

export interface NotificationsResponse {
  notifications: Notification[];
  unreadCount: number;
  totalCount: number;
  hasMore: boolean;
}

export interface MarkAsReadRequest {
  notificationIds?: string[];
  markAll?: boolean;
}

export interface MarkAsReadResponse {
  success: boolean;
  markedCount: number;
  updatedAt: string;
}

export interface DeleteNotificationResponse {
  success: boolean;
}
