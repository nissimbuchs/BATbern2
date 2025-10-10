/**
 * useAuth Hook Tests (TDD - Fixed)
 * Story 1.2: Frontend Authentication Integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useAuth } from './useAuth';
import { LoginCredentials } from '@/types/auth';

// Mock auth service
vi.mock('@services/auth/authService', () => ({
  authService: {
    signIn: vi.fn(),
    signOut: vi.fn(),
    signUp: vi.fn(),
    getCurrentUser: vi.fn(),
    refreshToken: vi.fn(),
    isTokenExpired: vi.fn(),
  },
}));

// Import the mocked module
import { authService } from '@services/auth/authService';

const mockAuthService = vi.mocked(authService);

describe('useAuth Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default mock behavior
    mockAuthService.getCurrentUser.mockResolvedValue(null);
  });

  it('should_returnAuthenticationState_when_hookInitialized', async () => {
    // Test 9.13: should_returnAuthenticationState_when_hookInitialized
    const { result } = renderHook(() => useAuth());

    await waitFor(() => {
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.error).toBeNull();
    });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
  });

  it('should_updateAuthenticationState_when_userSignsIn', async () => {
    // Test 9.14: should_updateAuthenticationState_when_userSignsIn
    const { result } = renderHook(() => useAuth());

    const credentials: LoginCredentials = {
      email: 'test@batbern.ch',
      password: 'password123',
    };

    const mockUser = {
      userId: 'user-123',
      email: 'test@batbern.ch',
      emailVerified: true,
      role: 'organizer' as const,
      companyId: 'company-123',
      preferences: {
        language: 'en' as const,
        theme: 'light' as const,
        notifications: { email: true, sms: false, push: true },
        privacy: { showProfile: true, allowMessages: true },
      },
      issuedAt: Math.floor(Date.now() / 1000),
      expiresAt: Math.floor(Date.now() / 1000) + 3600,
      tokenId: 'token-123',
    };

    // Mock successful sign in
    mockAuthService.signIn.mockResolvedValue({
      success: true,
      user: mockUser,
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
    });

    await act(async () => {
      await result.current.signIn(credentials);
    });

    await waitFor(() => {
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.user).toBeDefined();
      expect(result.current.isLoading).toBe(false);
    });
  });

  it('should_clearAuthenticationState_when_userSignsOut', async () => {
    // Test 9.15: should_clearAuthenticationState_when_userSignsOut
    const { result } = renderHook(() => useAuth());

    await act(async () => {
      await result.current.signOut();
    });

    await waitFor(() => {
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.accessToken).toBeNull();
    });
  });

  it('should_handleAuthenticationError_when_signInFails', async () => {
    // Test 9.16: should_handleAuthenticationError_when_signInFails
    const { result } = renderHook(() => useAuth());

    const invalidCredentials: LoginCredentials = {
      email: 'invalid@example.com',
      password: 'wrongpassword',
    };

    // Mock failed sign in
    mockAuthService.signIn.mockResolvedValue({
      success: false,
      error: {
        code: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password',
      },
    });

    await act(async () => {
      await result.current.signIn(invalidCredentials);
    });

    await waitFor(() => {
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.error).toBeDefined();
      expect(result.current.error?.code).toBe('INVALID_CREDENTIALS');
    });
  });

  it('should_automaticallyRefreshToken_when_tokenNearExpiration', async () => {
    // Test 9.17: should_automaticallyRefreshToken_when_tokenNearExpiration
    const { result } = renderHook(() => useAuth());

    // Mock successful token refresh
    mockAuthService.refreshToken.mockResolvedValue({
      success: true,
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      expiresIn: 3600,
    });

    await act(async () => {
      await result.current.refreshToken();
    });

    await waitFor(() => {
      expect(mockAuthService.refreshToken).toHaveBeenCalled();
    });
  });

  it('should_provideUserRoleAccess_when_userAuthenticated', async () => {
    // Test 9.18: should_provideUserRoleAccess_when_userAuthenticated
    const { result } = renderHook(() => useAuth());

    await waitFor(() => {
      expect(result.current.hasRole).toBeDefined();
      expect(result.current.hasPermission).toBeDefined();
      expect(result.current.canAccess).toBeDefined();
    });
  });

  it('should_clearError_when_clearErrorCalled', async () => {
    // Test 9.19: should_clearError_when_clearErrorCalled
    const { result } = renderHook(() => useAuth());

    await act(async () => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});
