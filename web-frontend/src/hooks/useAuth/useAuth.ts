/**
 * useAuth Hook Implementation
 * Story 1.2: Authentication State Management
 */

import { useState, useEffect, useCallback } from 'react';
import { authService } from '@services/auth/authService';
import { AuthenticationState, LoginCredentials, SignUpData, UserRole } from '@/types/auth';

interface UseAuthReturn extends Omit<AuthenticationState, 'refreshToken'> {
  refreshToken: () => Promise<boolean>;
  signIn: (credentials: LoginCredentials) => Promise<boolean>;
  signOut: () => Promise<void>;
  signUp: (data: SignUpData) => Promise<boolean>;
  clearError: () => void;
  hasRole: (role: UserRole) => boolean;
  hasPermission: (resource: string, action: string) => boolean;
  canAccess: (path: string) => boolean;
  isTokenExpired: (token: string) => boolean;
}

export function useAuth(): UseAuthReturn {
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
      try {
        const user = await authService.getCurrentUser();

        if (user) {
          // Get current session tokens
          const tokenResult = await authService.refreshToken();

          setState({
            isAuthenticated: true,
            isLoading: false,
            user,
            error: null,
            accessToken: tokenResult.accessToken || null,
          });
        } else {
          setState((prev) => ({
            ...prev,
            isLoading: false,
          }));
        }
      } catch (error) {
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
  const signIn = useCallback(async (credentials: LoginCredentials): Promise<boolean> => {
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      const result = await authService.signIn(credentials);

      if (result.success && result.user) {
        setState({
          isAuthenticated: true,
          isLoading: false,
          user: result.user,
          error: null,
          accessToken: result.accessToken || null,
        });
        return true;
      } else {
        setState((prev) => ({
          ...prev,
          isLoading: false,
          error: result.error || {
            code: 'SIGN_IN_FAILED',
            message: 'Sign in failed',
          },
        }));
        return false;
      }
    } catch (error: any) {
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_IN_ERROR',
          message: error.message || 'An error occurred during sign in',
        },
      }));
      return false;
    }
  }, []);

  /**
   * Sign out user
   */
  const signOut = useCallback(async (): Promise<void> => {
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
    } catch (error: any) {
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_OUT_ERROR',
          message: error.message || 'An error occurred during sign out',
        },
      }));
    }
  }, []);

  /**
   * Sign up new user
   */
  const signUp = useCallback(async (data: SignUpData): Promise<boolean> => {
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      const result = await authService.signUp(data);

      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: result.error || null,
      }));

      return result.success;
    } catch (error: any) {
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'SIGN_UP_ERROR',
          message: error.message || 'An error occurred during sign up',
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
    } catch (error) {
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
   * Check if user has specific role
   */
  const hasRole = useCallback(
    (role: UserRole): boolean => {
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
      if (!state.isAuthenticated) {
        // Public paths
        const publicPaths = ['/login', '/signup', '/forgot-password', '/'];
        return publicPaths.some((publicPath) => path.startsWith(publicPath));
      }

      if (!state.user) return false;

      const { role } = state.user;

      // Role-based path access
      const pathAccess: Record<UserRole, string[]> = {
        organizer: ['/dashboard', '/events', '/speakers', '/partners', '/analytics'],
        speaker: ['/dashboard', '/profile', '/events', '/materials'],
        partner: ['/dashboard', '/profile', '/analytics', '/events'],
        attendee: ['/dashboard', '/events', '/content', '/search'],
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

  return {
    ...state,
    signIn,
    signOut,
    signUp,
    refreshToken,
    clearError,
    hasRole,
    hasPermission,
    canAccess,
    isTokenExpired,
  };
}
