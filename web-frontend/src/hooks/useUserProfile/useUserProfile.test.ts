/**
 * useUserProfile Hook Tests
 * Story 1.17, Task 5a: TDD for React Query user profile hook
 */

import { describe, test, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserProfile } from './useUserProfile';
import apiClient, { setNavigationCallback } from '@/services/api/apiClient';
import React, { type ReactNode } from 'react';
import MockAdapter from 'axios-mock-adapter';

// HTTP is stubbed at the boundary: the real userApi and apiClient run.
const PROFILE_URL = '/users/me';

describe('useUserProfile Hook', () => {
  let queryClient: QueryClient;
  let http: MockAdapter;
  const profileCalls = () => http.history.get.filter((r) => r.url === PROFILE_URL);
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

    http = new MockAdapter(apiClient);
  });

  afterEach(() => {
    http.restore();
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

      http.onGet(PROFILE_URL).replyOnce(200, mockUserProfile);

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockUserProfile);
      expect(profileCalls()).toHaveLength(1);
      expect(profileCalls()[0].params).toEqual({ include: 'roles,preferences' });
    });

    test('should_showLoading_when_queryInProgress', () => {
      http.onGet(PROFILE_URL).reply(() => new Promise(() => {})); // Never resolves

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

      http.onGet(PROFILE_URL).replyOnce(200, mockUserProfile);

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // Second call should use cached data (no new API call)
      const { result: result2 } = renderHook(() => useUserProfile(), { wrapper });

      expect(result2.current.data).toEqual(mockUserProfile);
      expect(profileCalls()).toHaveLength(1); // Only called once
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

      http.onGet(PROFILE_URL).reply(200, mockUserProfile);

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
      http.onGet(PROFILE_URL).replyOnce(500, { message: 'Failed to fetch user profile' });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeTruthy();
    });

    test('should_handle401Error_when_unauthorized', async () => {
      const navigate = vi.fn();
      setNavigationCallback(navigate);
      http.onGet(PROFILE_URL).replyOnce(401, { message: 'Unauthorized' });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeTruthy();
      expect(navigate).toHaveBeenCalledWith('/login');
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

      http.onGet(PROFILE_URL).reply(200, mockUserProfile);

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const callCountBefore = profileCalls().length;

      // Trigger refetch
      await result.current.refetch();

      expect(profileCalls().length).toBeGreaterThan(callCountBefore);
    });
  });
});
