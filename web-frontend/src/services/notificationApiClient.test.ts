/**
 * Notification API Client Tests
 *
 * Comprehensive tests for notificationApiClient HTTP methods
 * Tests all API methods with mocked responses
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { notificationApiClient } from './notificationApiClient';
import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';

// Mock the apiClient module
vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('notificationApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getNotifications', () => {
    it('should fetch notifications with default pagination', async () => {
      const mockResponse = {
        data: [
          {
            id: 'notif-1',
            subject: 'Event Updated',
            body: 'Event BAT2025 has been updated',
            status: 'UNREAD',
            eventCode: 'BAT2025',
            createdAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.getNotifications({ username: 'test-user' });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('/notifications?'));
      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('username=test-user'));
      expect(result.data[0]).toHaveProperty('isRead', false);
      expect(result.data[0]).toHaveProperty('title', 'Event Updated');
      expect(result.data[0]).toHaveProperty('actionUrl', '/organizer/events/BAT2025');
    });

    it('should fetch notifications with custom pagination', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 2,
          limit: 50,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: true,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      await notificationApiClient.getNotifications(
        { username: 'test-user' },
        { page: 2, limit: 50 }
      );

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('page=2'));
      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('limit=50'));
    });

    it('should fetch notifications with status filter', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      await notificationApiClient.getNotifications({
        username: 'test-user',
        status: 'READ',
      });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('status=READ'));
    });

    it('should fetch notifications with eventCode filter', async () => {
      const mockResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      await notificationApiClient.getNotifications({
        username: 'test-user',
        eventCode: 'BAT2025',
      });

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('eventCode=BAT2025'));
    });

    it('should enhance notifications with computed properties', async () => {
      const mockResponse = {
        data: [
          {
            id: 'notif-1',
            subject: 'Test Subject',
            body: 'Test Body',
            status: 'READ',
            eventCode: 'BAT2025',
            createdAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.getNotifications({ username: 'test-user' });

      expect(result.data[0]).toHaveProperty('isRead', true);
      expect(result.data[0]).toHaveProperty('title', 'Test Subject');
      expect(result.data[0]).toHaveProperty('message', 'Test Body');
      expect(result.data[0]).toHaveProperty('timestamp', '2025-01-15T10:00:00Z');
    });

    it('should set actionUrl to undefined when eventCode is missing', async () => {
      const mockResponse = {
        data: [
          {
            id: 'notif-1',
            subject: 'General Notification',
            body: 'No event associated',
            status: 'UNREAD',
            createdAt: '2025-01-15T10:00:00Z',
          },
        ],
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.getNotifications({ username: 'test-user' });

      expect(result.data[0]).toHaveProperty('actionUrl', undefined);
    });

    it('should handle errors gracefully', async () => {
      const error = new AxiosError('Network error');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow();
    });
  });

  describe('getUnreadCount', () => {
    it('should fetch unread notification count', async () => {
      const mockResponse = {
        count: 5,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.getUnreadCount('test-user');

      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('/notifications/count'));
      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('username=test-user'));
      expect(apiClient.get).toHaveBeenCalledWith(expect.stringContaining('status=UNREAD'));
      expect(result).toBe(5);
    });

    it('should return zero when no unread notifications', async () => {
      const mockResponse = {
        count: 0,
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.getUnreadCount('test-user');

      expect(result).toBe(0);
    });

    it('should handle errors gracefully', async () => {
      const error = new AxiosError('Network error');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(notificationApiClient.getUnreadCount('test-user')).rejects.toThrow();
    });
  });

  describe('markAsRead', () => {
    it('should mark single notification as read', async () => {
      const mockResponse = {
        success: true,
        message: 'Notification marked as read',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.markAsRead('notif-1');

      expect(apiClient.put).toHaveBeenCalledWith('/notifications/notif-1/read');
      expect(result).toEqual(mockResponse);
    });

    it('should handle errors when marking as read', async () => {
      const error = new AxiosError('Notification not found');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(notificationApiClient.markAsRead('invalid-id')).rejects.toThrow();
    });
  });

  describe('batchMarkAsRead', () => {
    it('should mark multiple notifications as read', async () => {
      const mockResponse = {
        success: true,
        message: '3 notifications marked as read',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.batchMarkAsRead(['notif-1', 'notif-2', 'notif-3']);

      expect(apiClient.put).toHaveBeenCalledWith('/notifications/batch-read', {
        notificationIds: ['notif-1', 'notif-2', 'notif-3'],
      });
      expect(result).toEqual(mockResponse);
    });

    it('should handle empty array', async () => {
      const mockResponse = {
        success: true,
        message: '0 notifications marked as read',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.batchMarkAsRead([]);

      expect(apiClient.put).toHaveBeenCalledWith('/notifications/batch-read', {
        notificationIds: [],
      });
      expect(result).toEqual(mockResponse);
    });

    it('should handle errors in batch operation', async () => {
      const error = new AxiosError('Batch operation failed');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(notificationApiClient.batchMarkAsRead(['notif-1', 'notif-2'])).rejects.toThrow();
    });
  });

  describe('deleteNotification', () => {
    it('should delete single notification', async () => {
      const mockResponse = {
        success: true,
        message: 'Notification deleted',
      };

      vi.mocked(apiClient.delete).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.deleteNotification('notif-1');

      expect(apiClient.delete).toHaveBeenCalledWith('/notifications/notif-1');
      expect(result).toEqual(mockResponse);
    });

    it('should handle deletion errors', async () => {
      const error = new AxiosError('Notification not found');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(notificationApiClient.deleteNotification('invalid-id')).rejects.toThrow();
    });
  });

  describe('batchDelete', () => {
    it('should delete multiple notifications', async () => {
      const mockResponse = {
        success: true,
        message: '3 notifications deleted',
      };

      vi.mocked(apiClient.delete).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.batchDelete(['notif-1', 'notif-2', 'notif-3']);

      expect(apiClient.delete).toHaveBeenCalledWith('/notifications/batch-delete', {
        data: { notificationIds: ['notif-1', 'notif-2', 'notif-3'] },
      });
      expect(result).toEqual(mockResponse);
    });

    it('should handle empty array in batch delete', async () => {
      const mockResponse = {
        success: true,
        message: '0 notifications deleted',
      };

      vi.mocked(apiClient.delete).mockResolvedValue({ data: mockResponse });

      const result = await notificationApiClient.batchDelete([]);

      expect(apiClient.delete).toHaveBeenCalledWith('/notifications/batch-delete', {
        data: { notificationIds: [] },
      });
      expect(result).toEqual(mockResponse);
    });

    it('should handle errors in batch delete', async () => {
      const error = new AxiosError('Batch delete failed');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(notificationApiClient.batchDelete(['notif-1', 'notif-2'])).rejects.toThrow();
    });
  });

  describe('Error Transformation', () => {
    it('should transform AxiosError with response message', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { message: 'Custom error message' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValue(axiosError);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow('Custom error message');
    });

    it('should include correlation ID when present', async () => {
      const axiosError = new AxiosError('Request failed');
      axiosError.response = {
        data: { message: 'Error occurred' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: { 'x-correlation-id': 'abc-123-xyz' },
        config: {} as any,
      };

      vi.mocked(apiClient.get).mockRejectedValue(axiosError);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow('Error occurred (Correlation ID: abc-123-xyz)');
    });

    it('should use error message when no response data', async () => {
      const axiosError = new AxiosError('Network timeout');

      vi.mocked(apiClient.get).mockRejectedValue(axiosError);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow('Network timeout');
    });

    it('should handle non-AxiosError errors', async () => {
      const genericError = new Error('Generic error');

      vi.mocked(apiClient.get).mockRejectedValue(genericError);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow('Generic error');
    });

    it('should handle unknown error types', async () => {
      const unknownError = 'String error';

      vi.mocked(apiClient.get).mockRejectedValue(unknownError);

      await expect(
        notificationApiClient.getNotifications({ username: 'test-user' })
      ).rejects.toThrow('Unknown error occurred');
    });
  });

  describe('Client Singleton', () => {
    it('should export a singleton instance', () => {
      expect(notificationApiClient).toBeDefined();
      expect(typeof notificationApiClient.getNotifications).toBe('function');
      expect(typeof notificationApiClient.getUnreadCount).toBe('function');
      expect(typeof notificationApiClient.markAsRead).toBe('function');
      expect(typeof notificationApiClient.batchMarkAsRead).toBe('function');
      expect(typeof notificationApiClient.deleteNotification).toBe('function');
      expect(typeof notificationApiClient.batchDelete).toBe('function');
    });
  });
});
