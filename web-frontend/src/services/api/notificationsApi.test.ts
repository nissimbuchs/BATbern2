/**
 * Notifications API Tests
 * Story 1.17: Tests for type-safe notifications API wrappers
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { getNotifications, markNotificationsAsRead, deleteNotification } from './notificationsApi';
import { NotificationsResponse, MarkAsReadResponse } from '@/types/notification';

describe('Notifications API', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mockAxios.restore();
  });

  describe('getNotifications', () => {
    it('should_fetchUnreadNotifications_when_defaultParametersUsed', async () => {
      const mockResponse: NotificationsResponse = {
        notifications: [
          {
            id: 'notif-1',
            type: 'EVENT_STATUS_CHANGED',
            title: 'Event status changed',
            message: "Event 'BATbern 2025' is now Published",
            timestamp: '2024-03-15T14:30:00Z',
            isRead: false,
            actionUrl: '/events/uuid',
            priority: 'NORMAL',
          },
        ],
        unreadCount: 3,
        totalCount: 47,
        hasMore: true,
      };

      mockAxios
        .onGet('/api/v1/notifications', { params: { status: 'unread', limit: 10 } })
        .reply((config) => {
          expect(config.params?.status).toBe('unread');
          expect(config.params?.limit).toBe(10);
          return [200, mockResponse];
        });

      const result = await getNotifications();

      expect(result).toEqual(mockResponse);
      expect(result.unreadCount).toBe(3);
    });

    it('should_fetchAllNotifications_when_statusIsAll', async () => {
      const mockResponse: NotificationsResponse = {
        notifications: [],
        unreadCount: 0,
        totalCount: 50,
        hasMore: false,
      };

      mockAxios
        .onGet('/api/v1/notifications', { params: { status: 'all', limit: 20 } })
        .reply((config) => {
          expect(config.params?.status).toBe('all');
          expect(config.params?.limit).toBe(20);
          return [200, mockResponse];
        });

      const result = await getNotifications('all', 20);

      expect(result).toEqual(mockResponse);
      expect(result.totalCount).toBe(50);
    });

    it('should_respectLimitParameter_when_customLimitProvided', async () => {
      const mockResponse: NotificationsResponse = {
        notifications: [],
        unreadCount: 5,
        totalCount: 100,
        hasMore: true,
      };

      mockAxios.onGet('/api/v1/notifications').reply((config) => {
        expect(config.params?.limit).toBe(50);
        return [200, mockResponse];
      });

      const result = await getNotifications('unread', 50);

      expect(result.hasMore).toBe(true);
    });
  });

  describe('markNotificationsAsRead', () => {
    it('should_markSingleNotification_when_notificationIdProvided', async () => {
      const mockResponse: MarkAsReadResponse = {
        success: true,
        markedCount: 1,
        updatedAt: '2024-03-15T14:35:00Z',
      };

      mockAxios
        .onPut('/api/v1/notifications/read', { notificationIds: ['notif-1'] })
        .reply((config) => {
          const data = JSON.parse(config.data);
          expect(data.notificationIds).toEqual(['notif-1']);
          return [200, mockResponse];
        });

      const result = await markNotificationsAsRead({ notificationIds: ['notif-1'] });

      expect(result).toEqual(mockResponse);
      expect(result.markedCount).toBe(1);
    });

    it('should_markMultipleNotifications_when_multipleIdsProvided', async () => {
      const mockResponse: MarkAsReadResponse = {
        success: true,
        markedCount: 3,
        updatedAt: '2024-03-15T14:35:00Z',
      };

      mockAxios
        .onPut('/api/v1/notifications/read', {
          notificationIds: ['notif-1', 'notif-2', 'notif-3'],
        })
        .reply(200, mockResponse);

      const result = await markNotificationsAsRead({
        notificationIds: ['notif-1', 'notif-2', 'notif-3'],
      });

      expect(result.markedCount).toBe(3);
    });

    it('should_markAllNotifications_when_markAllFlagIsTrue', async () => {
      const mockResponse: MarkAsReadResponse = {
        success: true,
        markedCount: 10,
        updatedAt: '2024-03-15T14:35:00Z',
      };

      mockAxios.onPut('/api/v1/notifications/read', { markAll: true }).reply((config) => {
        const data = JSON.parse(config.data);
        expect(data.markAll).toBe(true);
        return [200, mockResponse];
      });

      const result = await markNotificationsAsRead({ markAll: true });

      expect(result.markedCount).toBe(10);
    });

    it('should_throwError_when_invalidNotificationIdsProvided', async () => {
      mockAxios.onPut('/api/v1/notifications/read').reply(400, {
        message: 'Invalid notification IDs',
      });

      await expect(markNotificationsAsRead({ notificationIds: ['invalid-id'] })).rejects.toThrow();
    });
  });

  describe('deleteNotification', () => {
    it('should_deleteNotification_when_validIdProvided', async () => {
      const mockResponse = { success: true };

      mockAxios.onDelete('/api/v1/notifications/notif-1').reply(200, mockResponse);

      const result = await deleteNotification('notif-1');

      expect(result).toEqual(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should_throwError_when_notificationNotFound', async () => {
      mockAxios.onDelete('/api/v1/notifications/invalid-id').reply(404, {
        message: 'Notification not found',
      });

      await expect(deleteNotification('invalid-id')).rejects.toThrow();
    });

    it('should_throwError_when_unauthorized', async () => {
      mockAxios.onDelete('/api/v1/notifications/notif-1').reply(401, {
        message: 'Unauthorized',
      });

      await expect(deleteNotification('notif-1')).rejects.toThrow();
    });
  });
});
