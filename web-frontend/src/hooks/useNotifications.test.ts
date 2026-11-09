/**
 * Notification Hooks Tests (Story BAT-7)
 *
 * Tests for useNotifications, useUnreadCount, useMarkAsRead,
 * useBatchMarkAsRead, useDeleteNotification, useBatchDeleteNotifications.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import {
  useNotifications,
  useUnreadCount,
  useMarkAsRead,
  useBatchMarkAsRead,
  useDeleteNotification,
  useBatchDeleteNotifications,
} from './useNotifications';

vi.mock('@/services/notificationApiClient', () => ({
  notificationApiClient: {
    getNotifications: vi.fn(),
    getUnreadCount: vi.fn(),
    markAsRead: vi.fn(),
    batchMarkAsRead: vi.fn(),
    deleteNotification: vi.fn(),
    batchDelete: vi.fn(),
  },
}));

import { notificationApiClient } from '@/services/notificationApiClient';
import type { NotificationPage } from '@/types/notification';

const mockClient = vi.mocked(notificationApiClient);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

describe('useNotifications', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  describe('useNotifications', () => {
    it('should fetch notifications when username is provided', async () => {
      const mockResponse: NotificationPage = {
        data: [
          {
            id: 'n-1',
            recipientUsername: 'alice',
            notificationType: 'NEW_MESSAGE',
            channel: 'IN_APP',
            priority: 'NORMAL',
            subject: 'Hallo',
            body: 'Hello',
            status: 'SENT',
            createdAt: '2025-03-01T09:00:00Z',
            updatedAt: '2025-03-01T09:00:00Z',
            isRead: false,
          },
        ],
        pagination: { page: 1, limit: 20, totalItems: 1, totalPages: 1 },
      };
      mockClient.getNotifications.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () => useNotifications({ username: 'alice' }, { page: 1, limit: 20 }),
        { wrapper: createWrapper(qc) }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
    });

    it('should not fetch when username is absent', () => {
      const { result } = renderHook(() => useNotifications({ username: '' }), {
        wrapper: createWrapper(qc),
      });

      expect(result.current.isLoading).toBe(false);
      expect(mockClient.getNotifications).not.toHaveBeenCalled();
    });

    it('should not fetch when enabled=false', () => {
      const { result } = renderHook(
        () => useNotifications({ username: 'alice' }, {}, { enabled: false }),
        { wrapper: createWrapper(qc) }
      );

      expect(result.current.isLoading).toBe(false);
      expect(mockClient.getNotifications).not.toHaveBeenCalled();
    });

    it('should surface errors from the API', async () => {
      mockClient.getNotifications.mockRejectedValue(new Error('fetch failed'));

      const { result } = renderHook(() => useNotifications({ username: 'alice' }), {
        wrapper: createWrapper(qc),
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  describe('useUnreadCount', () => {
    it('should fetch unread count when username provided', async () => {
      mockClient.getUnreadCount.mockResolvedValue(5);

      const { result } = renderHook(() => useUnreadCount('alice'), { wrapper: createWrapper(qc) });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toBe(5);
    });

    it('should not fetch when username is undefined', () => {
      const { result } = renderHook(() => useUnreadCount(undefined), {
        wrapper: createWrapper(qc),
      });

      expect(result.current.isLoading).toBe(false);
      expect(mockClient.getUnreadCount).not.toHaveBeenCalled();
    });

    it('should not fetch when enabled=false', () => {
      renderHook(() => useUnreadCount('alice', { enabled: false }), {
        wrapper: createWrapper(qc),
      });

      expect(mockClient.getUnreadCount).not.toHaveBeenCalled();
    });
  });

  describe('useMarkAsRead', () => {
    it('should call markAsRead and invalidate queries on success', async () => {
      mockClient.markAsRead.mockResolvedValue({ success: true, markedCount: 1 });
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useMarkAsRead(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate('n-1');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] });
    });
  });

  describe('useBatchMarkAsRead', () => {
    it('should call batchMarkAsRead and invalidate queries', async () => {
      mockClient.batchMarkAsRead.mockResolvedValue({ success: true, markedCount: 2 });
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useBatchMarkAsRead(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate(['n-1', 'n-2']);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] });
    });
  });

  describe('useDeleteNotification', () => {
    it('should call deleteNotification and invalidate queries', async () => {
      mockClient.deleteNotification.mockResolvedValue({ success: true });
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteNotification(), { wrapper: createWrapper(qc) });

      await act(async () => {
        result.current.mutate('n-1');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] });
    });
  });

  describe('useBatchDeleteNotifications', () => {
    it('should call batchDelete and invalidate queries', async () => {
      mockClient.batchDelete.mockResolvedValue({ success: true });
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

      const { result } = renderHook(() => useBatchDeleteNotifications(), {
        wrapper: createWrapper(qc),
      });

      await act(async () => {
        result.current.mutate(['n-1', 'n-2']);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] });
    });
  });
});
