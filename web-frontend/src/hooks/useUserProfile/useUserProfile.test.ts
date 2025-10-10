/**
 * useUserProfile Hook Tests
 * Story 1.17, Task 5a: TDD for React Query user profile hook
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserProfile } from './useUserProfile';
import apiClient from '@/services/api/apiClient';
import React, { type ReactNode } from 'react';

// Mock API client
vi.mock('@/services/api/apiClient');

describe('useUserProfile Hook', () => {
  let queryClient: QueryClient;
  let wrapper: React.ComponentType<{ children: ReactNode }>;

  beforeEach(() => {
    // Create fresh query client for each test
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries in tests
        },
      },
    });

    // Create wrapper with the queryClient
    wrapper = ({ children }: { children: ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);

    // Clear all mocks
    vi.clearAllMocks();
  });

  describe('Successful Query', () => {
    test('should_fetchUserProfile_when_hookCalled', async () => {
      const mockUserProfile = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        firstName: 'Test',
        lastName: 'User',
        currentRole: 'organizer',
        availableRoles: ['organizer', 'speaker'],
        companyId: 'company-123',
        profilePhotoUrl: 'https://example.com/photo.jpg',
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockUserProfile });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockUserProfile);
      expect(apiClient.get).toHaveBeenCalledWith('/users/me?include=roles,preferences');
    });

    test('should_showLoading_when_queryInProgress', () => {
      vi.mocked(apiClient.get).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });
  });

  describe('Caching Behavior', () => {
    test('should_cacheData_when_querySucceeds', async () => {
      const mockUserProfile = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        firstName: 'Test',
        lastName: 'User',
        currentRole: 'organizer',
        availableRoles: ['organizer'],
        companyId: 'company-123',
        profilePhotoUrl: null,
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockUserProfile });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Second call should use cached data (no new API call)
      const { result: result2 } = renderHook(() => useUserProfile(), { wrapper });

      expect(result2.current.data).toEqual(mockUserProfile);
      expect(apiClient.get).toHaveBeenCalledTimes(1); // Only called once
    });

    test('should_haveStaleTime_when_configured', async () => {
      const mockUserProfile = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        firstName: 'Test',
        lastName: 'User',
        currentRole: 'organizer',
        availableRoles: ['organizer'],
        companyId: 'company-123',
        profilePhotoUrl: null,
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockUserProfile });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Data should be considered fresh for staleTime duration (5 minutes)
      expect(result.current.isStale).toBe(false);
    });
  });

  describe('Error Handling', () => {
    test('should_handleError_when_apiFails', async () => {
      const mockError = new Error('Failed to fetch user profile');
      vi.mocked(apiClient.get).mockRejectedValueOnce(mockError);

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeTruthy();
    });

    test('should_handle401Error_when_unauthorized', async () => {
      const mockError = {
        response: {
          status: 401,
          data: { message: 'Unauthorized' },
        },
      };

      vi.mocked(apiClient.get).mockRejectedValueOnce(mockError);

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeTruthy();
    });
  });

  describe('Refetch', () => {
    test('should_refetchData_when_refetchCalled', async () => {
      const mockUserProfile = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        firstName: 'Test',
        lastName: 'User',
        currentRole: 'organizer',
        availableRoles: ['organizer'],
        companyId: 'company-123',
        profilePhotoUrl: null,
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockUserProfile });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const callCountBefore = vi.mocked(apiClient.get).mock.calls.length;

      // Trigger refetch
      await result.current.refetch();

      expect(vi.mocked(apiClient.get).mock.calls.length).toBeGreaterThan(callCountBefore);
    });
  });
});
