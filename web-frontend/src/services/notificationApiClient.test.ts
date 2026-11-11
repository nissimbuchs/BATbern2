/**
 * notificationApiClient on the MSW harness (Story 4.10b, Epic 17 test fidelity): the real
 * apiClient sends every request through the spec check, so a removed path (`PUT …/read`,
 * `batch-read`, `batch-delete`) or a `username` parameter the spec no longer has fails the test.
 * The server scopes every call to the signed-in organizer; the client sends no username.
 * Error mapping: `notificationApiClient.errors.test.ts`.
 */

import { describe, it, expect } from 'vitest';
import { api } from '@/test/msw/api';
import type { components } from '@/types/generated/event-notifications-api.types';
import { notificationApiClient } from './notificationApiClient';

type NotificationResponse = components['schemas']['NotificationResponse'];

const ID = '3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f';
const ID_2 = '8d1e2f3a-4b5c-4d6e-8f70-112233445566';

const notification = (overrides: Partial<NotificationResponse> = {}): NotificationResponse => ({
  id: ID,
  recipientUsername: 'hongan.nguyen',
  eventCode: 'BATbern57',
  notificationType: 'EVENT_PUBLISHED',
  channel: 'IN_APP',
  priority: 'NORMAL',
  subject: 'Programm BATbern57 veröffentlicht',
  body: 'Das Programm von Brünnimann’s Abend ist online',
  status: 'UNREAD',
  sentAt: null,
  readAt: null,
  failedAt: null,
  failureReason: null,
  metadata: null,
  createdAt: '2026-11-10T08:00:00Z',
  updatedAt: '2026-11-10T08:00:00Z',
  ...overrides,
});

describe('notificationApiClient', () => {
  describe('getNotifications', () => {
    it('should_sendPageAndStatusOnly_when_listingTheCallersInbox', async () => {
      api.on('listNotifications', () => ({
        data: [notification()],
        pagination: { page: 2, limit: 50, totalItems: 51, totalPages: 2 },
      }));

      const result = await notificationApiClient.getNotifications(
        { status: 'UNREAD' },
        { page: 2, limit: 50 }
      );

      const [call] = api.calls('listNotifications');
      expect(call.query.get('page')).toBe('2');
      expect(call.query.get('limit')).toBe('50');
      expect(call.query.get('status')).toBe('UNREAD');
      expect(call.query.has('username')).toBe(false);
      expect(result.pagination.totalItems).toBe(51);
    });

    it('should_addUiFields_when_mappingTheWireNotification', async () => {
      api.on('listNotifications', () => ({
        data: [notification(), notification({ id: ID_2, status: 'READ', eventCode: null })],
        pagination: { page: 1, limit: 20, totalItems: 2, totalPages: 1 },
      }));

      const { data } = await notificationApiClient.getNotifications();

      expect(api.calls('listNotifications')[0].query.get('page')).toBe('1');
      expect(api.calls('listNotifications')[0].query.get('limit')).toBe('20');
      expect(data[0]).toMatchObject({
        isRead: false,
        title: 'Programm BATbern57 veröffentlicht',
        message: 'Das Programm von Brünnimann’s Abend ist online',
        timestamp: '2026-11-10T08:00:00Z',
        actionUrl: '/organizer/events/BATbern57',
      });
      expect(data[1].isRead).toBe(true);
      expect(data[1].actionUrl).toBeUndefined();
    });
  });

  it('should_askForUnreadWithoutUsername_when_counting', async () => {
    api.on('getUnreadCount', () => ({ count: 7 }));

    await expect(notificationApiClient.getUnreadCount()).resolves.toBe(7);

    const [call] = api.calls('getUnreadCount');
    expect(call.query.get('status')).toBe('UNREAD');
    expect(call.query.has('username')).toBe(false);
  });

  it('should_patchStatusRead_when_markingOneRead', async () => {
    api.on('updateNotification', () => ({
      success: true,
      markedCount: 1,
      updatedAt: '2026-11-10T09:00:00Z',
    }));

    const result = await notificationApiClient.markAsRead(ID);

    const [call] = api.calls('updateNotification');
    expect(call.method).toBe('PATCH');
    expect(call.params.id).toBe(ID);
    expect(call.body).toEqual({ status: 'READ' });
    expect(result.markedCount).toBe(1);
  });

  it('should_patchCollectionWithIds_when_markingManyRead', async () => {
    api.on('updateNotifications', () => ({
      success: true,
      markedCount: 2,
      updatedAt: '2026-11-10T09:00:00Z',
    }));

    const result = await notificationApiClient.batchMarkAsRead([ID, ID_2]);

    const [call] = api.calls('updateNotifications');
    expect(call.method).toBe('PATCH');
    expect(call.body).toEqual({ notificationIds: [ID, ID_2], status: 'READ' });
    expect(result.markedCount).toBe(2);
  });

  it('should_deleteById_when_deletingOne', async () => {
    api.on('deleteNotification', () => ({ success: true }));

    await expect(notificationApiClient.deleteNotification(ID)).resolves.toEqual({ success: true });

    expect(api.calls('deleteNotification')[0].params.id).toBe(ID);
  });
});
