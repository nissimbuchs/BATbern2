/**
 * useNotifications Hook Tests
 * Story 1.17, Task 5a: TDD for React Query notifications hook
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useNotifications } from './useNotifications';
import apiClient from '@/services/api/apiClient';
import React, { type ReactNode } from 'react';

// Mock API client
vi.mock('@/services/api/apiClient');

describe('useNotifications Hook', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    // Create fresh query client for each test
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries in tests
        },
      },
    });

    // Clear all mocks
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);

  describe('Successful Query', () => {
    test('should_fetchNotifications_when_hookCalled', async () => {
      const mockNotifications = {
        notifications: [
          {
            id: 'notif-1',
            type: 'EVENT_STATUS_CHANGED',
            title: 'Event status changed',
            message: 'Event "BATbern 2025" is now Published',
            timestamp: '2024-03-15T14:30:00Z',
            isRead: false,
            actionUrl: '/events/event-123',
            priority: 'NORMAL',
          },
          {
            id: 'notif-2',
            type: 'NEW_SPEAKER_INVITED',
            title: 'New speaker invited',
            message: 'John Doe was invited to speak',
            timestamp: '2024-03-15T12:00:00Z',
            isRead: true,
            actionUrl: '/speakers/speaker-123',
            priority: 'LOW',
          },
        ],
        unreadCount: 1,
        totalCount: 2,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockNotifications);
      expect(apiClient.get).toHaveBeenCalledWith('/notifications?status=unread&limit=10');
    });

    test('should_extractUnreadCount_when_dataLoaded', async () => {
      const mockNotifications = {
        notifications: [],
        unreadCount: 5,
        totalCount: 10,
        hasMore: true,
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.unreadCount).toBe(5);
    });
  });

  describe('Manual Refresh', () => {
    test('should_provideRefetchFunction_when_hookCalled', async () => {
      const mockNotifications = {
        notifications: [],
        unreadCount: 0,
        totalCount: 0,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.refetch).toBeDefined();
      expect(typeof result.current.refetch).toBe('function');
    });

    test('should_refetchData_when_refetchCalled', async () => {
      const mockNotifications = {
        notifications: [],
        unreadCount: 0,
        totalCount: 0,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const callCountBefore = vi.mocked(apiClient.get).mock.calls.length;

      // Trigger manual refresh
      await act(async () => {
        await result.current.refetch();
      });

      expect(vi.mocked(apiClient.get).mock.calls.length).toBeGreaterThan(callCountBefore);
    });
  });

  describe('Mark as Read', () => {
    test('should_markNotificationAsRead_when_markAsReadCalled', async () => {
      const mockNotifications = {
        notifications: [
          {
            id: 'notif-1',
            type: 'EVENT_STATUS_CHANGED',
            title: 'Event status changed',
            message: 'Event updated',
            timestamp: '2024-03-15T14:30:00Z',
            isRead: false,
            actionUrl: '/events/event-123',
            priority: 'NORMAL',
          },
        ],
        unreadCount: 1,
        totalCount: 1,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockNotifications });
      vi.mocked(apiClient.put).mockResolvedValueOnce({
        data: { success: true, markedCount: 1 },
      });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      await act(async () => {
        await result.current.markAsRead('notif-1');
      });

      expect(apiClient.put).toHaveBeenCalledWith('/notifications/read', {
        notificationIds: ['notif-1'],
      });
    });

    test('should_markAllAsRead_when_markAllAsReadCalled', async () => {
      const mockNotifications = {
        notifications: [],
        unreadCount: 3,
        totalCount: 3,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockNotifications });
      vi.mocked(apiClient.put).mockResolvedValueOnce({
        data: { success: true, markedCount: 3 },
      });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      await act(async () => {
        await result.current.markAllAsRead();
      });

      expect(apiClient.put).toHaveBeenCalledWith('/notifications/read', {
        markAll: true,
      });
    });
  });

  describe('Delete Notification', () => {
    test('should_deleteNotification_when_deleteNotificationCalled', async () => {
      const mockNotifications = {
        notifications: [
          {
            id: 'notif-1',
            type: 'EVENT_STATUS_CHANGED',
            title: 'Event status changed',
            message: 'Event updated',
            timestamp: '2024-03-15T14:30:00Z',
            isRead: false,
            actionUrl: '/events/event-123',
            priority: 'NORMAL',
          },
        ],
        unreadCount: 1,
        totalCount: 1,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockNotifications });
      vi.mocked(apiClient.delete).mockResolvedValueOnce({
        data: { success: true },
      });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      await act(async () => {
        await result.current.deleteNotification('notif-1');
      });

      expect(apiClient.delete).toHaveBeenCalledWith('/notifications/notif-1');
    });
  });

  describe('Error Handling', () => {
    test('should_handleError_when_apiFails', async () => {
      const mockError = new Error('Failed to fetch notifications');
      vi.mocked(apiClient.get).mockRejectedValueOnce(mockError);

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeTruthy();
    });
  });

  describe('Caching Behavior', () => {
    test('should_cacheData_when_querySucceeds', async () => {
      const mockNotifications = {
        notifications: [],
        unreadCount: 0,
        totalCount: 0,
        hasMore: false,
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Second call should use cached data (no new API call)
      const { result: result2 } = renderHook(() => useNotifications(), { wrapper });

      expect(result2.current.data).toEqual(mockNotifications);
      expect(apiClient.get).toHaveBeenCalledTimes(1); // Only called once
    });
  });
});
