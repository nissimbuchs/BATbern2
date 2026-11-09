/* eslint-disable react-refresh/only-export-components -- test helper, never hot-reloaded */
/**
 * Auth state for tests (Story 17.0c2).
 *
 * `useAuth` is `useContext(AuthContext)`. The real `AuthProvider` sits on Amplify (a boundary,
 * mocked globally in `setup.ts`) and `GET /users/me`; a screen test does not need the sign-in
 * flow, only a signed-in (or signed-out) state. This helper supplies that state through the real
 * `AuthContext.Provider`, so `useAuth` and every consumer run for real. Never
 * `vi.mock('@/hooks/useAuth')`.
 *
 *   renderWithAuth(<EventManagementDashboard />, { auth: anAuthState({ role: 'organizer' }) });
 *   renderWithAuth(<ProtectedRoute>...</ProtectedRoute>, { auth: signedOut() });
 *
 * `hasRole`, `hasPermission` and `canAccess` are the provider's own rules (`authRules.ts`) bound
 * to the test user. The actions (`signIn`, `signOut`, ...) are `vi.fn()`s that resolve like a
 * success; override any field through the `overrides` argument.
 */
import type { ReactElement, ReactNode } from 'react';
import { vi } from 'vitest';
import { AuthContext } from '@/contexts/AuthContext';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import { canAccessPath, userHasPermission, userHasRole } from '@/contexts/authRules';
import type { UserContext, UserRole } from '@/types/auth';
import { renderWithProviders } from './test-utils';

type RenderOptions = NonNullable<Parameters<typeof renderWithProviders>[1]>;

/** A signed-in user; `role` sets the primary role and, unless given, `roles`. */
export function anAuthUser(overrides: Partial<UserContext> = {}): UserContext {
  const role: UserRole = overrides.role ?? 'organizer';
  return {
    userId: 'a1b2c3d4-0000-4000-8000-000000000001',
    username: 'anna.keller',
    email: 'Anna.Keller@example.com',
    emailVerified: true,
    role,
    roles: [role],
    preferences: {
      language: 'en',
      theme: 'light',
      notifications: { email: true, sms: false, push: false },
      privacy: { showProfile: true, allowMessages: true },
    },
    termsAcceptedAt: '2026-01-15T09:00:00Z',
    issuedAt: Math.floor(Date.now() / 1000),
    expiresAt: Math.floor(Date.now() / 1000) + 3600,
    tokenId: 'test-token-id',
    ...overrides,
  };
}

/** The full value `useAuth()` returns, for `user` (null: signed out). */
export function anAuthState(
  user: UserContext | null = anAuthUser(),
  overrides: Partial<UseAuthReturn> = {}
): UseAuthReturn {
  // An overridden `user` is the user the rules below are bound to.
  const subject = 'user' in overrides ? (overrides.user ?? null) : user;
  const isAuthenticated = overrides.isAuthenticated ?? subject !== null;
  return {
    isAuthenticated,
    isLoading: false,
    user: subject,
    error: null,
    accessToken: subject ? 'test-access-token' : null,
    refreshToken: vi.fn().mockResolvedValue(true),
    signIn: vi.fn().mockResolvedValue({ kind: 'success' }),
    confirmNewPassword: vi.fn().mockResolvedValue(true),
    completeFederatedSignIn: vi.fn().mockResolvedValue({ kind: 'success' }),
    signOut: vi.fn().mockResolvedValue(undefined),
    signUp: vi.fn().mockResolvedValue(true),
    refreshUser: vi.fn().mockResolvedValue(undefined),
    clearError: vi.fn(),
    hasRole: (role) => userHasRole(subject, role),
    hasPermission: (resource, action) => userHasPermission(subject, resource, action),
    canAccess: (path) => canAccessPath(isAuthenticated, subject, path),
    isTokenExpired: () => false,
    ...overrides,
  };
}

/** Shorthand: the state of a signed-in user with `role`. */
export function signedInAs(role: UserRole, user: Partial<UserContext> = {}): UseAuthReturn {
  return anAuthState(anAuthUser({ role, ...user }));
}

/** Shorthand: nobody signed in. */
export function signedOut(overrides: Partial<UseAuthReturn> = {}): UseAuthReturn {
  return anAuthState(null, overrides);
}

/** Provides `auth` to everything below it through the real `AuthContext`. */
export function AuthTestProvider({
  auth,
  children,
}: {
  auth: UseAuthReturn;
  children: ReactNode;
}): ReactElement {
  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>;
}

/**
 * `renderWithProviders` (QueryClient + MemoryRouter) with `auth` (default: a signed-in
 * organizer) inside. Returns what it returns, plus `rerenderWithAuth` to render again with
 * another auth state (the providers and the query cache stay).
 */
export function renderWithAuth(
  ui: ReactElement,
  { auth = signedInAs('organizer'), ...options }: RenderOptions & { auth?: UseAuthReturn } = {}
) {
  const result = renderWithProviders(
    <AuthTestProvider auth={auth}>{ui}</AuthTestProvider>,
    options
  );
  return {
    ...result,
    rerenderWithAuth: (next: ReactElement, nextAuth: UseAuthReturn = auth) =>
      result.rerender(<AuthTestProvider auth={nextAuth}>{next}</AuthTestProvider>),
  };
}
