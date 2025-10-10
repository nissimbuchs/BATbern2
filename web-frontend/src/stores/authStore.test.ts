/**
 * Auth Store Tests
 * Story 1.17, Task 5a: TDD for Zustand auth store
 */

import { describe, test, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAuthStore } from './authStore';
import type { UserContext } from '@/types/auth';

describe('Auth Store', () => {
  beforeEach(() => {
    // Reset store state before each test
    const { reset } = useAuthStore.getState();
    act(() => {
      reset();
    });
  });

  describe('Initial State', () => {
    test('should_haveNullUser_when_storeInitialized', () => {
      const { result } = renderHook(() => useAuthStore());

      expect(result.current.user).toBeNull();
    });

    test('should_haveNullAccessToken_when_storeInitialized', () => {
      const { result } = renderHook(() => useAuthStore());

      expect(result.current.accessToken).toBeNull();
    });

    test('should_notBeAuthenticated_when_storeInitialized', () => {
      const { result } = renderHook(() => useAuthStore());

      expect(result.current.isAuthenticated).toBe(false);
    });

    test('should_notBeLoading_when_storeInitialized', () => {
      const { result } = renderHook(() => useAuthStore());

      expect(result.current.isLoading).toBe(false);
    });
  });

  describe('setUser Action', () => {
    test('should_updateUser_when_setUserCalled', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockUser: UserContext = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer',
        companyId: 'company-123',
        preferences: {
          language: 'de',
          theme: 'light',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-123',
      };

      act(() => {
        result.current.setUser(mockUser);
      });

      expect(result.current.user).toEqual(mockUser);
    });

    test('should_setIsAuthenticatedTrue_when_userSet', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockUser: UserContext = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer',
        companyId: 'company-123',
        preferences: {
          language: 'de',
          theme: 'light',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-123',
      };

      act(() => {
        result.current.setUser(mockUser);
      });

      expect(result.current.isAuthenticated).toBe(true);
    });
  });

  describe('setAccessToken Action', () => {
    test('should_updateAccessToken_when_setAccessTokenCalled', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...';

      act(() => {
        result.current.setAccessToken(mockToken);
      });

      expect(result.current.accessToken).toBe(mockToken);
    });
  });

  describe('setLoading Action', () => {
    test('should_updateLoadingState_when_setLoadingCalled', () => {
      const { result } = renderHook(() => useAuthStore());

      act(() => {
        result.current.setLoading(true);
      });

      expect(result.current.isLoading).toBe(true);

      act(() => {
        result.current.setLoading(false);
      });

      expect(result.current.isLoading).toBe(false);
    });
  });

  describe('logout Action', () => {
    test('should_clearUser_when_logoutCalled', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockUser: UserContext = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer',
        companyId: 'company-123',
        preferences: {
          language: 'de',
          theme: 'light',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-123',
      };

      act(() => {
        result.current.setUser(mockUser);
        result.current.setAccessToken('token-123');
      });

      act(() => {
        result.current.logout();
      });

      expect(result.current.user).toBeNull();
      expect(result.current.accessToken).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
    });
  });

  describe('State Persistence', () => {
    test('should_persistUserToLocalStorage_when_userSet', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockUser: UserContext = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer',
        companyId: 'company-123',
        preferences: {
          language: 'de',
          theme: 'light',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-123',
      };

      act(() => {
        result.current.setUser(mockUser);
      });

      const storedUser = localStorage.getItem('batbern-auth-user');
      expect(storedUser).toBeTruthy();
      const parsed = JSON.parse(storedUser!);
      // Zustand persist wraps data in { state: {...}, version: 0 }
      expect(parsed.state.user).toEqual(mockUser);
    });

    test('should_clearLocalStorage_when_logoutCalled', () => {
      const { result } = renderHook(() => useAuthStore());

      const mockUser: UserContext = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer',
        companyId: 'company-123',
        preferences: {
          language: 'de',
          theme: 'light',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-123',
      };

      act(() => {
        result.current.setUser(mockUser);
      });

      act(() => {
        result.current.logout();
      });

      const storedUser = localStorage.getItem('batbern-auth-user');
      expect(storedUser).toBeTruthy();
      const parsed = JSON.parse(storedUser!);
      // After logout, user should be null
      expect(parsed.state.user).toBeNull();
    });
  });
});
