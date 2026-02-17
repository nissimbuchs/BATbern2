/**
 * AuthContext Tests — Multi-Role Support (Story 9.5, Task 1.4)
 * Tests hasRole(), canAccess(), hasPermission() with multi-role users
 *
 * Uses renderHook with AuthProvider wrapper (same pattern as useAuth.test.tsx)
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import React from 'react';
import { AuthProvider } from './AuthContext';
import { useAuth } from '@/hooks/useAuth';

// Mock authService
vi.mock('@services/auth/authService', () => ({
  authService: {
    getCurrentUser: vi.fn().mockResolvedValue(null),
    refreshToken: vi.fn().mockResolvedValue({ success: false }),
    signIn: vi.fn(),
    signOut: vi.fn(),
    isTokenExpired: vi.fn(() => false),
  },
}));

// Import the mocked module
import { authService } from '@services/auth/authService';
const mockAuthService = vi.mocked(authService);

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <AuthProvider>{children}</AuthProvider>
);

function mockMultiRoleUser(roles: readonly string[]) {
  const primaryRole = roles[0] || 'attendee';
  const mockUser = {
    userId: 'test-user',
    username: 'test.user',
    email: 'test@batbern.ch',
    emailVerified: true,
    role: primaryRole,
    roles: [...roles],
    companyId: 'company-123',
    preferences: {
      language: 'en' as const,
      theme: 'light' as const,
      notifications: { email: true, sms: false, push: true },
      privacy: { showProfile: true, allowMessages: true },
    },
    issuedAt: Math.floor(Date.now() / 1000),
    expiresAt: Math.floor(Date.now() / 1000) + 3600,
    tokenId: 'test-token',
  };

  mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
  mockAuthService.refreshToken.mockResolvedValue({
    success: true,
    accessToken: 'test-access-token',
  } as any);
}

describe('AuthContext — Multi-Role Support (Story 9.5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthService.getCurrentUser.mockResolvedValue(null);
    mockAuthService.isTokenExpired.mockReturnValue(false);
  });

  describe('hasRole() — checks user.roles[] not user.role', () => {
    test('should return true for primary role', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('organizer')).toBe(true);
    });

    test('should return true for secondary role', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('speaker')).toBe(true);
    });

    test('should return false for unassigned role', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('partner')).toBe(false);
    });

    test('should work with single-role user', async () => {
      mockMultiRoleUser(['speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('speaker')).toBe(true);
      expect(result.current.hasRole('organizer')).toBe(false);
    });
  });

  describe('canAccess() — aggregates paths from ALL roles', () => {
    test('should allow organizer paths for organizer+speaker user', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/organizer/events')).toBe(true);
      expect(result.current.canAccess('/events')).toBe(true);
      expect(result.current.canAccess('/dashboard')).toBe(true);
    });

    test('should allow speaker paths for organizer+speaker user', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/speaker/dashboard')).toBe(true);
      expect(result.current.canAccess('/speaker/events')).toBe(true);
    });

    test('should deny partner paths for organizer+speaker user', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/partner/dashboard')).toBe(false);
    });

    test('should allow public paths without authentication', async () => {
      mockMultiRoleUser(['speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/login')).toBe(true);
      expect(result.current.canAccess('/')).toBe(true);
    });

    test('should allow /speaker-portal as public path', async () => {
      mockMultiRoleUser(['speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/speaker-portal/login')).toBe(true);
    });
  });

  describe('hasPermission() — merges permissions from ALL roles', () => {
    test('should grant organizer permissions for organizer+speaker user', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasPermission('events', 'create')).toBe(true);
      expect(result.current.hasPermission('speakers', 'delete')).toBe(true);
    });

    test('should grant speaker permissions for organizer+speaker user', async () => {
      mockMultiRoleUser(['organizer', 'speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasPermission('content', 'create')).toBe(true);
    });

    test('should deny permissions not in any role', async () => {
      mockMultiRoleUser(['speaker']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasPermission('events', 'create')).toBe(false);
      expect(result.current.hasPermission('speakers', 'delete')).toBe(false);
    });

    test('should merge across all roles', async () => {
      mockMultiRoleUser(['speaker', 'attendee']);
      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasPermission('content', 'update')).toBe(true);
      expect(result.current.hasPermission('events', 'read')).toBe(true);
      expect(result.current.hasPermission('events', 'create')).toBe(false);
    });
  });
});
