/**
 * useAuth Hook Tests (TDD - Fixed)
 * Story 1.2: Frontend Authentication Integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useAuth } from './useAuth';
import { AuthProvider } from '@/contexts/AuthContext';
import { LoginCredentials } from '@/types/auth';
import React from 'react';

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

// Wrapper component for tests
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <AuthProvider>{children}</AuthProvider>
);

describe('useAuth Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default mock behavior
    mockAuthService.getCurrentUser.mockResolvedValue(null);
    mockAuthService.signOut.mockResolvedValue();
    mockAuthService.isTokenExpired.mockReturnValue(false);
  });

  it('should_returnAuthenticationState_when_hookInitialized', async () => {
    // Test 9.13: should_returnAuthenticationState_when_hookInitialized
    const { result } = renderHook(() => useAuth(), { wrapper });

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
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const credentials: LoginCredentials = {
      email: 'test@batbern.ch',
      password: 'password123',
    };

    const mockUser = {
      userId: 'user-123',
      email: 'test@batbern.ch',
      emailVerified: true,
      role: 'organizer' as const,
      roles: ['organizer'] as const,
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
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

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
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

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
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

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
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    await waitFor(() => {
      expect(result.current.hasRole).toBeDefined();
      expect(result.current.hasPermission).toBeDefined();
      expect(result.current.canAccess).toBeDefined();
    });
  });

  it('should_clearError_when_clearErrorCalled', async () => {
    // Test 9.19: should_clearError_when_clearErrorCalled
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Wait for initialization to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    await act(async () => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });

  describe('Role and Permission Checks', () => {
    it('should_checkOrganizerPermissions_when_organizerLoggedIn', async () => {
      const mockUser = {
        userId: 'user-123',
        email: 'organizer@batbern.ch',
        emailVerified: true,
        role: 'organizer' as const,
        roles: ['organizer'] as const,
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

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('organizer')).toBe(true);
      expect(result.current.hasPermission('events', 'create')).toBe(true);
      expect(result.current.hasPermission('speakers', 'delete')).toBe(true);
      expect(result.current.hasPermission('partners', 'update')).toBe(true);
      expect(result.current.hasPermission('analytics', 'read')).toBe(true);
    });

    it('should_checkSpeakerPermissions_when_speakerLoggedIn', async () => {
      const mockUser = {
        userId: 'user-456',
        email: 'speaker@batbern.ch',
        emailVerified: true,
        role: 'speaker' as const,
        roles: ['speaker'] as const,
        companyId: 'company-456',
        preferences: {
          language: 'en' as const,
          theme: 'light' as const,
          notifications: { email: true, sms: false, push: true },
          privacy: { showProfile: true, allowMessages: true },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-456',
      };

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('speaker')).toBe(true);
      expect(result.current.hasPermission('events', 'read')).toBe(true);
      expect(result.current.hasPermission('content', 'create')).toBe(true);
      expect(result.current.hasPermission('events', 'create')).toBe(false);
    });

    it('should_checkPartnerPermissions_when_partnerLoggedIn', async () => {
      const mockUser = {
        userId: 'user-789',
        email: 'partner@batbern.ch',
        emailVerified: true,
        role: 'partner' as const,
        roles: ['partner'] as const,
        companyId: 'company-789',
        preferences: {
          language: 'en' as const,
          theme: 'light' as const,
          notifications: { email: true, sms: false, push: true },
          privacy: { showProfile: true, allowMessages: true },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-789',
      };

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('partner')).toBe(true);
      expect(result.current.hasPermission('events', 'read')).toBe(true);
      expect(result.current.hasPermission('analytics', 'read')).toBe(true);
      expect(result.current.hasPermission('events', 'create')).toBe(false);
    });

    it('should_checkAttendeePermissions_when_attendeeLoggedIn', async () => {
      const mockUser = {
        userId: 'user-999',
        email: 'attendee@batbern.ch',
        emailVerified: true,
        role: 'attendee' as const,
        roles: ['attendee'] as const,
        companyId: 'company-999',
        preferences: {
          language: 'en' as const,
          theme: 'light' as const,
          notifications: { email: true, sms: false, push: true },
          privacy: { showProfile: true, allowMessages: true },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-999',
      };

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('attendee')).toBe(true);
      expect(result.current.hasPermission('events', 'read')).toBe(true);
      expect(result.current.hasPermission('content', 'read')).toBe(true);
      expect(result.current.hasPermission('events', 'create')).toBe(false);
    });

    it('should_returnFalse_when_invalidResource', async () => {
      const mockUser = {
        userId: 'user-123',
        email: 'organizer@batbern.ch',
        emailVerified: true,
        role: 'organizer' as const,
        roles: ['organizer'] as const,
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

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasPermission('invalid-resource', 'read')).toBe(false);
    });

    it('should_returnFalse_when_noUser', async () => {
      mockAuthService.getCurrentUser.mockResolvedValue(null);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.hasPermission('events', 'read')).toBe(false);
    });
  });

  describe('Path Access Control', () => {
    it('should_allowPublicPaths_when_notAuthenticated', async () => {
      mockAuthService.getCurrentUser.mockResolvedValue(null);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.canAccess('/')).toBe(true);
      expect(result.current.canAccess('/login')).toBe(true);
      expect(result.current.canAccess('/signup')).toBe(true);
      expect(result.current.canAccess('/forgot-password')).toBe(true);
      expect(result.current.canAccess('/about')).toBe(true);
      expect(result.current.canAccess('/archive')).toBe(true);
    });

    it('should_denyProtectedPaths_when_notAuthenticated', async () => {
      mockAuthService.getCurrentUser.mockResolvedValue(null);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.canAccess('/dashboard')).toBe(false);
      expect(result.current.canAccess('/organizer/events')).toBe(false);
    });

    it('should_allowOrganizerPaths_when_organizerAuthenticated', async () => {
      const mockUser = {
        userId: 'user-123',
        email: 'organizer@batbern.ch',
        emailVerified: true,
        role: 'organizer' as const,
        roles: ['organizer'] as const,
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

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/dashboard')).toBe(true);
      expect(result.current.canAccess('/events')).toBe(true);
      expect(result.current.canAccess('/speakers')).toBe(true);
      expect(result.current.canAccess('/organizer/events')).toBe(true);
      expect(result.current.canAccess('/analytics')).toBe(true);
    });

    it('should_allowSpeakerPaths_when_speakerAuthenticated', async () => {
      const mockUser = {
        userId: 'user-456',
        email: 'speaker@batbern.ch',
        emailVerified: true,
        role: 'speaker' as const,
        roles: ['speaker'] as const,
        companyId: 'company-456',
        preferences: {
          language: 'en' as const,
          theme: 'light' as const,
          notifications: { email: true, sms: false, push: true },
          privacy: { showProfile: true, allowMessages: true },
        },
        issuedAt: Math.floor(Date.now() / 1000),
        expiresAt: Math.floor(Date.now() / 1000) + 3600,
        tokenId: 'token-456',
      };

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.canAccess('/dashboard')).toBe(true);
      expect(result.current.canAccess('/speaker/profile')).toBe(true);
      expect(result.current.canAccess('/materials')).toBe(true);
    });
  });

  describe('Token Expiration', () => {
    it('should_checkTokenExpiration_when_tokenProvided', async () => {
      mockAuthService.getCurrentUser.mockResolvedValue(null);
      mockAuthService.isTokenExpired.mockReturnValue(true);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.isTokenExpired('expired-token')).toBe(true);
    });

    it('should_returnFalse_when_tokenNotExpired', async () => {
      mockAuthService.getCurrentUser.mockResolvedValue(null);
      mockAuthService.isTokenExpired.mockReturnValue(false);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.isTokenExpired('valid-token')).toBe(false);
    });
  });

  describe('Sign Up', () => {
    it('should_signUpSuccessfully_when_validDataProvided', async () => {
      mockAuthService.signUp.mockResolvedValue({
        success: true,
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      let signUpResult: boolean = false;
      await act(async () => {
        signUpResult = await result.current.signUp({
          email: 'newuser@example.com',
          password: 'password123',
          name: 'New User',
        });
      });

      expect(signUpResult).toBe(true);
    });

    it('should_handleSignUpError_when_signUpFails', async () => {
      mockAuthService.signUp.mockRejectedValue(new Error('Sign up failed'));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      let signUpResult: boolean = false;
      await act(async () => {
        signUpResult = await result.current.signUp({
          email: 'newuser@example.com',
          password: 'password123',
          name: 'New User',
        });
      });

      expect(signUpResult).toBe(false);
      expect(result.current.error?.code).toBe('SIGN_UP_ERROR');
    });
  });

  describe('Error Recovery', () => {
    it('should_handleSignInException_when_networkError', async () => {
      mockAuthService.signIn.mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      await act(async () => {
        await result.current.signIn({
          email: 'test@example.com',
          password: 'password123',
        });
      });

      expect(result.current.error?.code).toBe('SIGN_IN_ERROR');
      expect(result.current.error?.message).toContain('Network error');
    });

    it('should_handleSignOutException_when_signOutFails', async () => {
      const mockUser = {
        userId: 'user-123',
        email: 'test@batbern.ch',
        emailVerified: true,
        role: 'organizer' as const,
        roles: ['organizer'] as const,
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

      mockAuthService.getCurrentUser.mockResolvedValue(mockUser);
      mockAuthService.refreshToken.mockResolvedValue({
        success: true,
        accessToken: 'token',
      });
      mockAuthService.signOut.mockRejectedValue(new Error('Sign out failed'));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      await act(async () => {
        await result.current.signOut();
      });

      expect(result.current.error?.code).toBe('SIGN_OUT_ERROR');
    });
  });
});
