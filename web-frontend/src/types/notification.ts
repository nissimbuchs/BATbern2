/**
 * Notification types for BATbern platform
 * Based on Story BAT-7 (Notification System) and EventBridge integration
 */

export type NotificationType =
  // Event notifications
  | 'EVENT_PUBLISHED'
  | 'EVENT_STATUS_CHANGED'
  // Speaker workflow notifications
  | 'SPEAKER_INVITED'
  | 'SPEAKER_ACCEPTED'
  | 'SPEAKER_DECLINED'
  | 'SPEAKER_CONFIRMED'
  | 'CONTENT_SUBMITTED'
  | 'QUALITY_REVIEW_PENDING'
  | 'QUALITY_REVIEW_APPROVED'
  | 'QUALITY_REVIEW_REQUIRES_CHANGES'
  | 'SLOT_ASSIGNED'
  // Task notifications
  | 'TASK_ASSIGNED'
  | 'TASK_DEADLINE_WARNING'
  | 'DEADLINE_WARNING'
  // Other notifications
  | 'OVERFLOW_DETECTED'
  | 'VOTING_REQUIRED'
  | 'PARTNER_ANALYSIS_READY'
  | 'NEW_MESSAGE'
  | 'SYSTEM_ANNOUNCEMENT';

export type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'READ' | 'UNREAD';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'WEBHOOK' | 'IN_APP';

export interface Notification {
  id: string;
  recipientUsername: string;
  eventCode?: string; // Nullable for non-event notifications
  notificationType: NotificationType;
  channel: NotificationChannel;
  priority: NotificationPriority;
  subject: string;
  body: string;
  status: NotificationStatus;
  sentAt?: string; // ISO 8601 format
  readAt?: string; // ISO 8601 format
  failedAt?: string; // ISO 8601 format
  failureReason?: string;
  metadata?: Record<string, unknown>;
  createdAt: string; // ISO 8601 format
  updatedAt: string; // ISO 8601 format
  // UI helper properties (aliases for backward compatibility)
  isRead?: boolean; // Computed from status === 'READ'
  actionUrl?: string; // Computed from eventCode/metadata
  title?: string; // Alias for subject (for UI components)
  message?: string; // Alias for body (for UI components)
  timestamp?: string; // Alias for createdAt (for UI components)
}

export interface NotificationsResponse {
  data: Notification[];
  pagination: {
    page: number;
    limit: number;
    totalItems: number;
    totalPages: number;
  };
  unreadCount?: number; // Total unread notifications (for in-app notifications)
}

export interface NotificationCountResponse {
  count: number;
}

export interface MarkAsReadRequest {
  notificationIds?: string[];
}

export interface MarkAsReadResponse {
  success: boolean;
  markedCount: number;
  updatedAt: string;
}

export interface DeleteNotificationResponse {
  success: boolean;
}

export interface NotificationFilters {
  username: string;
  status?: 'PENDING' | 'SENT' | 'READ' | 'FAILED' | 'UNREAD';
  eventCode?: string;
  notificationType?: NotificationType;
}
