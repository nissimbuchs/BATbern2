/* eslint-disable react-refresh/only-export-components */
/**
 * AuthContext - Global Authentication State Management
 * Provides shared auth state across all components using React Context
 */

import React, { createContext, useState, useEffect, useCallback, useMemo } from 'react';
import { authService } from '@services/auth/authService';
import { AuthenticationState, LoginCredentials, SignUpData, UserRole } from '@/types/auth';
import apiClient from '@/services/api/apiClient';

/**
 * Discriminated outcome of a sign-in attempt. Epic 11 bug fix 2026-05-19 — the
 * previous `Promise<boolean>` return collapsed every non-success path into "false",
 * so the FORCE_CHANGE_PASSWORD challenge emitted by Cognito on first sign-in (with
 * the temp password from `issueInvitationCredentials`) looked identical to a bad
 * credentials error. LoginForm now branches on `kind` and renders the inline
 * "set new password" panel when `requires-new-password` is signalled.
 */
export type SignInOutcome =
  | { kind: 'success' }
  | { kind: 'requires-new-password' }
  | { kind: 'failed' };

interface UseAuthReturn extends AuthenticationState {
  refreshToken: () => Promise<boolean>;
  signIn: (credentials: LoginCredentials) => Promise<SignInOutcome>;
  /**
   * Complete the FORCE_CHANGE_PASSWORD challenge with a user-chosen permanent
   * password. Returns `true` on success (auth state is updated; LoginForm should
   * navigate to the dashboard), `false` on failure (Cognito error surfaced via
   * `error` state).
   */
  confirmNewPassword: (newPassword: string) => Promise<boolean>;
  signOut: () => Promise<void>;
  signUp: (data: SignUpData) => Promise<boolean>;
  clearError: () => void;
  hasRole: (role: UserRole) => boolean;
  hasPermission: (resource: string, action: string) => boolean;
  canAccess: (path: string) => boolean;
  isTokenExpired: (token: string) => boolean;
}

export const AuthContext = createContext<UseAuthReturn | undefined>(undefined);

/**
 * Resolve companyName for partner users when not present in JWT.
 * Calls GET /partners/me which looks up the user's company via partner_contacts table.
 * Silently returns undefined on failure so the partner still lands on the error-alert flow (AC5).
 */
async function resolvePartnerCompanyName(): Promise<string | undefined> {
  try {
    const response = await apiClient.get<{ companyName: string }>('/partners/me');
    return response.data?.companyName;
  } catch {
    console.warn(
      '[AuthProvider] Could not resolve companyName via GET /partners/me — ' +
        'ensure user is registered as a partner contact'
    );
    return undefined;
  }
}

interface AuthProviderProps {
  children: React.ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthenticationState>({
    isAuthenticated: false,
    isLoading: true,
    user: null,
    error: null,
    accessToken: null,
  });

  /**
   * Initialize authentication state on mount
   */
  useEffect(() => {
    const initializeAuth = async () => {
      console.log('[AuthProvider] Initializing auth state...');
      try {
        const user = await authService.getCurrentUser();

        if (user) {
          console.log('[AuthProvider] User found, fetching token...');
          // Get current session tokens
          const tokenResult = await authService.refreshToken();

          // Resolve companyName for partner users if not in JWT via GET /partners/me
          let resolvedCompanyName = user.companyName;
          const isPartner = user.role === 'partner' || user.roles?.includes('partner');
          if (isPartner && !resolvedCompanyName) {
            resolvedCompanyName = await resolvePartnerCompanyName();
          }

          setState({
            isAuthenticated: true,
            isLoading: false,
            user:
              resolvedCompanyName !== user.companyName
                ? { ...user, companyName: resolvedCompanyName }
                : user,
            error: null,
            accessToken: tokenResult.accessToken || null,
          });
          console.log('[AuthProvider] Auth initialized - authenticated as:', user.email);
        } else {
          console.log('[AuthProvider] No authenticated user found');
          setState((prev) => ({
            ...prev,
            isLoading: false,
          }));
        }
      } catch (_error) {
        console.error('[AuthProvider] Failed to initialize auth:', _error);
        setState((prev) => ({
          ...prev,
          isLoading: false,
          error: {
            code: 'INIT_ERROR',
            message: 'Failed to initialize authentication',
          },
        }));
      }
    };

    initializeAuth();
  }, []);

  /**
   * Sign in user
   */
  const signIn = useCallback(async (credentials: LoginCredentials): Promise<SignInOutcome> => {
    console.log('[AuthProvider] signIn called');
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      console.log('[AuthProvider] Calling authService.signIn');
      const result = await authService.signIn(credentials);
      console.log('[AuthProvider] authService.signIn result:', {
        success: result.success,
        hasUser: !!result.user,
        pendingChallenge: result.pendingChallenge,
        error: result.error,
      });

      // Epic 11 bug fix 2026-05-19 — FORCE_CHANGE_PASSWORD: surface the challenge
      // to LoginForm so it can render the inline "set new password" panel.
      // isLoading flips back to false so the panel is interactive.
      if (result.pendingChallenge === 'CONFIRM_SIGN_IN_WITH_NEW_PASSWORD_REQUIRED') {
        setState((prev) => ({ ...prev, isLoading: false, error: null }));
        return { kind: 'requires-new-password' };
      }

      if (result.success && result.user) {
        console.log('[AuthProvider] Sign in successful, updating global state');

        // Resolve companyName for partner users if not in JWT via GET /partners/me
        let signedInUser = result.user;
        const isPartner =
          signedInUser.role === 'partner' || signedInUser.roles?.includes('partner');
        if (isPartner && !signedInUser.companyName) {
          const resolved = await resolvePartnerCompanyName();
          if (resolved) {
            signedInUser = { ...signedInUser, companyName: resolved };
          }
        }

        setState({
          isAuthenticated: true,
          isLoading: false,
          user: signedInUser,
          error: null,
          accessToken: result.accessToken || null,
        });
        console.log('[AuthProvider] Global auth state updated - isAuthenticated: true');
        return { kind: 'success' };
      }

      console.log('[AuthProvider] Sign in failed:', result.error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: result.error || {
          code: 'SIGN_IN_FAILED',
          message: 'Sign in failed',
        },
      }));
      return { kind: 'failed' };
    } catch (error: unknown) {
      console.error('[AuthProvider] Exception during sign in:', error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_IN_ERROR',
          message: error instanceof Error ? error.message : 'An error occurred during sign in',
        },
      }));
      return { kind: 'failed' };
    }
  }, []);

  /**
   * Complete the FORCE_CHANGE_PASSWORD challenge (Epic 11 bug fix 2026-05-19).
   * Submits the new permanent password to Cognito; on success, populates the
   * global auth state so the user can proceed to the dashboard. Mirrors the
   * success path of `signIn` (including the partner companyName resolution).
   */
  const confirmNewPassword = useCallback(async (newPassword: string): Promise<boolean> => {
    console.log('[AuthProvider] confirmNewPassword called');
    setState((prev) => ({ ...prev, isLoading: true, error: null }));
    try {
      const result = await authService.confirmNewPassword(newPassword);
      if (result.success && result.user) {
        let signedInUser = result.user;
        const isPartner =
          signedInUser.role === 'partner' || signedInUser.roles?.includes('partner');
        if (isPartner && !signedInUser.companyName) {
          const resolved = await resolvePartnerCompanyName();
          if (resolved) {
            signedInUser = { ...signedInUser, companyName: resolved };
          }
        }
        setState({
          isAuthenticated: true,
          isLoading: false,
          user: signedInUser,
          error: null,
          accessToken: result.accessToken || null,
        });
        return true;
      }
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: result.error || {
          code: 'CONFIRM_NEW_PASSWORD_FAILED',
          message: 'Could not confirm new password',
        },
      }));
      return false;
    } catch (error: unknown) {
      console.error('[AuthProvider] Exception during confirmNewPassword:', error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'CONFIRM_NEW_PASSWORD_ERROR',
          message:
            error instanceof Error
              ? error.message
              : 'An error occurred while confirming the new password',
        },
      }));
      return false;
    }
  }, []);

  /**
   * Sign out user
   */
  const signOut = useCallback(async (): Promise<void> => {
    console.log('[AuthProvider] signOut called');
    setState((prev) => ({ ...prev, isLoading: true }));

    try {
      await authService.signOut();
      setState({
        isAuthenticated: false,
        isLoading: false,
        user: null,
        error: null,
        accessToken: null,
      });
      console.log('[AuthProvider] Sign out successful');
    } catch (error: unknown) {
      console.error('[AuthProvider] Sign out failed:', error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_OUT_ERROR',
          message: error instanceof Error ? error.message : 'An error occurred during sign out',
        },
      }));
    }
  }, []);

  /**
   * Sign up new user
   */
  const signUp = useCallback(async (data: SignUpData): Promise<boolean> => {
    console.log('[AuthProvider] signUp called');
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      const result = await authService.signUp(data);

      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: result.error || null,
      }));

      return result.success;
    } catch (error: unknown) {
      console.error('[AuthProvider] Sign up failed:', error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_UP_ERROR',
          message: error instanceof Error ? error.message : 'An error occurred during sign up',
        },
      }));
      return false;
    }
  }, []);

  /**
   * Refresh authentication token
   */
  const refreshToken = useCallback(async (): Promise<boolean> => {
    try {
      const result = await authService.refreshToken();

      if (result.success) {
        setState((prev) => ({
          ...prev,
          accessToken: result.accessToken || null,
        }));
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, []);

  /**
   * Clear authentication error
   */
  const clearError = useCallback(() => {
    setState((prev) => ({ ...prev, error: null }));
  }, []);

  /**
   * Check if user has specific role (supports multi-role users)
   */
  const hasRole = useCallback(
    (role: UserRole): boolean => {
      const roles = state.user?.roles;
      if (roles && roles.length > 0) return roles.includes(role);
      return state.user?.role === role;
    },
    [state.user]
  );

  /**
   * Check if user has permission for resource/action
   */
  const hasPermission = useCallback(
    (resource: string, action: string): boolean => {
      if (!state.user) return false;

      const { role } = state.user;

      // Role-based permission matrix based on Story 1.2 specifications
      const permissions: Record<UserRole, Record<string, string[]>> = {
        organizer: {
          events: ['create', 'read', 'update', 'delete'],
          speakers: ['create', 'read', 'update', 'delete'],
          partners: ['create', 'read', 'update', 'delete'],
          content: ['read'],
          analytics: ['read'],
        },
        speaker: {
          events: ['read'],
          speakers: ['read', 'update'], // own profile only
          content: ['create', 'read', 'update'], // own content only
          analytics: ['read'], // own analytics only
        },
        partner: {
          events: ['read'],
          partners: ['read', 'update'], // own profile only
          analytics: ['read'], // own analytics only
          content: ['read'],
        },
        attendee: {
          events: ['read'],
          content: ['read'],
          speakers: ['read'],
        },
      };

      const rolePermissions = permissions[role];
      if (!rolePermissions) return false;

      const resourcePermissions = rolePermissions[resource];
      if (!resourcePermissions) return false;

      return resourcePermissions.includes(action);
    },
    [state.user]
  );

  /**
   * Check if user can access specific path
   */
  const canAccess = useCallback(
    (path: string): boolean => {
      // Public paths accessible to everyone (authenticated and unauthenticated)
      const publicPaths = [
        '/login',
        '/signup',
        '/forgot-password',
        '/auth',
        '/about',
        '/archive',
        '/register',
      ];
      const isPublicPath = publicPaths.some((publicPath) => path.startsWith(publicPath));

      // Homepage is always accessible
      if (path === '/' || isPublicPath) {
        return true;
      }

      if (!state.isAuthenticated) {
        return false;
      }

      if (!state.user) return false;

      const { role } = state.user;

      // Role-based path access
      const pathAccess: Record<UserRole, string[]> = {
        organizer: [
          '/dashboard',
          '/events',
          '/speakers',
          '/partners',
          '/analytics',
          '/organizer',
          '/account',
        ],
        speaker: ['/dashboard', '/profile', '/events', '/materials', '/speaker', '/account'],
        partner: [
          '/dashboard',
          '/profile',
          '/analytics',
          '/organizer/analytics',
          '/events',
          '/partner',
          '/account',
        ],
        attendee: ['/dashboard', '/events', '/content', '/search', '/attendee', '/account'],
      };

      const allowedPaths = pathAccess[role] || [];
      return allowedPaths.some((allowedPath) => path.startsWith(allowedPath));
    },
    [state.isAuthenticated, state.user]
  );

  /**
   * Check if token is expired
   */
  const isTokenExpired = useCallback((token: string): boolean => {
    return authService.isTokenExpired(token);
  }, []);

  // Memoize the context value to prevent unnecessary re-renders
  const contextValue = useMemo(
    () => ({
      ...state,
      signIn,
      confirmNewPassword,
      signOut,
      signUp,
      refreshToken,
      clearError,
      hasRole,
      hasPermission,
      canAccess,
      isTokenExpired,
    }),
    [
      state,
      signIn,
      confirmNewPassword,
      signOut,
      signUp,
      refreshToken,
      clearError,
      hasRole,
      hasPermission,
      canAccess,
      isTokenExpired,
    ]
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};
