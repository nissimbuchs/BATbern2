/**
 * Auth Store
 * Story 1.17, Task 5b: Zustand auth store implementation
 *
 * Manages authentication state including user, access token, and authentication status.
 * Persists user data to localStorage for session persistence.
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserContext } from '@/types/auth';

interface AuthState {
  user: UserContext | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AuthActions {
  setUser: (user: UserContext | null) => void;
  setAccessToken: (token: string | null) => void;
  setLoading: (loading: boolean) => void;
  logout: () => void;
  reset: () => void;
}

type AuthStore = AuthState & AuthActions;

const initialState: AuthState = {
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isLoading: false,
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      ...initialState,

      setUser: (user) =>
        set(() => ({
          user,
          isAuthenticated: user !== null,
        })),

      setAccessToken: (accessToken) =>
        set(() => ({
          accessToken,
        })),

      setLoading: (isLoading) =>
        set(() => ({
          isLoading,
        })),

      logout: () =>
        set(() => ({
          ...initialState,
        })),

      reset: () =>
        set(() => ({
          ...initialState,
        })),
    }),
    {
      name: 'batbern-auth-user', // localStorage key
      partialize: (state) => ({
        user: state.user,
        // Don't persist accessToken for security reasons
        // Access tokens should be managed by AWS Amplify
      }),
    }
  )
);
