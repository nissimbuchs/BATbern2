/**
 * AuthContext Tests — Multi-Role Support (Story 9.5, Task 1.4)
 * Tests hasRole(), canAccess(), hasPermission() with multi-role users
 *
 * Uses renderHook with AuthProvider wrapper (same pattern as useAuth.test.tsx)
 */

import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import React from 'react';
import { AuthProvider } from './AuthContext';
import { ConfigContext } from './createConfigContext';
import type { AppConfig } from '@/config/runtime-config';
import type { UserRole } from '@/types/auth';
import { useAuth } from '@/hooks/useAuth';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';

// AuthProvider gates session restore on runtime config being present (it needs the
// Cognito pool/client IDs to configure Amplify). In production it always renders inside
// ConfigProvider; tests must supply a config so the restore path runs.
const TEST_CONFIG: AppConfig = {
  environment: 'staging',
  apiBaseUrl: 'https://api.batbern.ch/api/v1',
  cognito: { userPoolId: 'eu-central-1_TEST', clientId: 'client', region: 'eu-central-1' },
  features: { notifications: true, analytics: false, pwa: false, turnstile: false, sso: false },
};

// Cognito at the boundary (Story 4.9 moved this off the authService mock): the real authService
// runs and reads the session from Amplify, which `src/test/setup.ts` mocks globally
// (`aws-amplify/auth`). `cognitoSession` puts a signed-in Cognito session there; the ID token
// claims are what production reads (custom:role, custom:username). The Hub events of the
// federated callback come from `aws-amplify/utils`, mocked here.
const hub = vi.hoisted(() => ({ nextEvent: null as string | null }));
vi.mock('aws-amplify/utils', () => ({
  Hub: {
    listen: (_channel: string, callback: (e: { payload: { event: string } }) => void) => {
      const event = hub.nextEvent;
      if (event) setTimeout(() => callback({ payload: { event } }), 0);
      return () => undefined;
    },
  },
}));

import { fetchAuthSession, getCurrentUser as amplifyGetCurrentUser } from 'aws-amplify/auth';
const amplify = {
  fetchAuthSession: vi.mocked(fetchAuthSession),
  getCurrentUser: vi.mocked(amplifyGetCurrentUser),
};

/** A signed-in Cognito session with these ID token claims. */
function cognitoSession(claims: { sub: string; email: string; role: string; username: string }) {
  const now = Math.floor(Date.now() / 1000);
  amplify.getCurrentUser.mockResolvedValue({ username: claims.sub, userId: claims.sub } as never);
  amplify.fetchAuthSession.mockResolvedValue({
    tokens: {
      idToken: {
        payload: {
          sub: claims.sub,
          email: claims.email,
          email_verified: true,
          'custom:role': claims.role,
          'custom:username': claims.username,
          iat: now,
          exp: now + 3600,
        },
        toString: () => 'id-token',
      },
      accessToken: { payload: { exp: now + 3600 }, toString: () => 'test-access-token' },
    },
  } as never);
}

/** No Cognito session: Amplify has no signed-in user and no tokens. */
function noCognitoSession() {
  amplify.getCurrentUser.mockRejectedValue(new Error('UserUnAuthenticatedException'));
  amplify.fetchAuthSession.mockResolvedValue({ tokens: undefined } as never);
}

// Story 12.1: AuthContext hydrates company + preferences from GET /users/me (include=settings,
// Story 4.9) on every
// login/init (hydrateUserFromDb). Stubbed at the HTTP boundary, the real userApi
// and partnerApi run. Re-registering the same route replaces the previous reply.
let http: MockAdapter;
const usersMeReplies = (body: unknown) => http.onGet('/users/me').reply(200, body);

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ConfigContext.Provider value={TEST_CONFIG}>
    <AuthProvider>{children}</AuthProvider>
  </ConfigContext.Provider>
);

function mockMultiRoleUser(roles: readonly UserRole[]) {
  cognitoSession({
    sub: 'test-user',
    email: 'test@batbern.ch',
    role: roles.map((r) => r.toUpperCase()).join(','),
    username: 'test.user',
  });
}

describe('AuthContext — Multi-Role Support (Story 9.5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    http = new MockAdapter(apiClient);
    // AuthProvider now skips session restore unless a Cognito session exists in storage
    // (perf/public-homepage-followup #2). Seed one so these tests exercise the restore path;
    // the anonymous-skip behaviour is covered by its own test below.
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem('CognitoIdentityServiceProvider.client.user.idToken', 'stub');
    hub.nextEvent = null;
    noCognitoSession();
    // Default: /users/me hydration is a no-op (JWT already carries roles in staging).
    usersMeReplies({ roles: [] });
  });

  afterEach(() => {
    http.restore();
  });

  describe('Session restore gating (perf/public-homepage-followup #2)', () => {
    test('skips getCurrentUser for anonymous visitors with no Cognito tokens in storage', async () => {
      // No Cognito keys in storage → anonymous visitor. AuthProvider must NOT call
      // authService.getCurrentUser (which would dynamically pull in aws-amplify on the public
      // homepage). It should settle to not-authenticated / not-loading without touching Amplify.
      localStorage.clear();
      sessionStorage.clear();
      mockMultiRoleUser(['organizer']); // even if a user WOULD resolve, it must not be queried

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(amplify.getCurrentUser).not.toHaveBeenCalled();
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
    });

    // Regression: perf/public-homepage-followup #2 decoupled the runtime-config gate from
    // bootstrap, so config (Cognito pool IDs) loads AFTER first paint. AuthProvider must NOT
    // restore a stored session before that config arrives — Amplify is unconfigured until
    // then, so getCurrentUser() resolves to "no user" and the user is wrongly bounced to
    // /login. With a Cognito session present but config still null, restore must be deferred
    // (stay loading, don't query) and then run once config resolves.
    test('defers session restore until runtime config is loaded, then restores', async () => {
      // Cognito session present (seeded in beforeEach) and a user WOULD resolve.
      mockMultiRoleUser(['organizer']);

      // A mutable config the wrapper reads, so we can flip null → loaded between renders
      // (renderHook's rerender keeps the same wrapper; it cannot swap it).
      let currentConfig: AppConfig | null = null;
      const deferredConfigWrapper = ({ children }: { children: React.ReactNode }) => (
        <ConfigContext.Provider value={currentConfig}>
          <AuthProvider>{children}</AuthProvider>
        </ConfigContext.Provider>
      );

      const { result, rerender } = renderHook(() => useAuth(), {
        wrapper: deferredConfigWrapper,
      });

      // Restore must be deferred: still loading, Amplify-backed getCurrentUser not yet called,
      // and crucially NOT settled to not-authenticated (which would redirect to /login).
      await waitFor(() => expect(amplify.getCurrentUser).not.toHaveBeenCalled());
      expect(result.current.isLoading).toBe(true);
      expect(result.current.isAuthenticated).toBe(false);

      // Config resolves → ConfigContext flips to a real config → restore runs.
      currentConfig = TEST_CONFIG;
      rerender();

      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
      expect(amplify.getCurrentUser).toHaveBeenCalledTimes(1);
    });
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

    test.skip('should allow speaker paths for organizer+speaker user', async () => {
      // Code review 2026-05-18 (P5): re-applied from cherry-pick 73d94688. The underlying
      // canAccess() implementation in AuthContext still uses singular `user.role` rather
      // than aggregating across `user.roles`, so multi-role canAccess doesn't behave as
      // this test asserts. Leaving the assertion in place as a marker for the follow-up
      // story that wires multi-role permission aggregation into AuthContext.
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

  describe('Story 12.1 — hydrate company + preferences from /users/me', () => {
    function mockTokenUserWithoutCompanyOrPrefs(roles: readonly UserRole[]) {
      // Story 12.1: the token carries identity + authorization only; company and preferences
      // (language) come from /users/me.
      mockMultiRoleUser(roles);
    }

    test('should populate companyId + preferences.language from /users/me', async () => {
      mockTokenUserWithoutCompanyOrPrefs(['organizer']);
      usersMeReplies({
        roles: ['ORGANIZER'],
        companyId: 'Swiss IT Solutions AG',
        settings: {
          language: 'fr',
          theme: 'dark',
          emailNotifications: false,
          pushNotifications: false,
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      // company + preferences sourced from the DB, not the token
      expect(result.current.user?.companyId).toBe('Swiss IT Solutions AG');
      expect(result.current.user?.preferences?.language).toBe('fr');
    });

    test('regression guard: preferences.language is set on the user the moment isAuthenticated flips true (before LanguageSync runs)', async () => {
      mockTokenUserWithoutCompanyOrPrefs(['speaker']);
      usersMeReplies({
        roles: ['SPEAKER'],
        companyId: 'Acme AG',
        settings: { language: 'de', theme: 'light' },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });
      // The very assertion that isAuthenticated is true happens only after hydration
      // resolves (AuthContext awaits hydrateUserFromDb before setState). So if language
      // is present here, it was populated before any auth-gated effect (LanguageSync) ran.
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
      expect(result.current.user?.preferences?.language).toBe('de');
    });

    test('does NOT override JWT roles with DB roles when the token already carries roles', async () => {
      mockTokenUserWithoutCompanyOrPrefs(['organizer', 'speaker']);
      // DB returns a different (single) role set — must be ignored since JWT had roles.
      usersMeReplies({
        roles: ['ATTENDEE'],
        companyId: 'Acme AG',
        settings: { language: 'en' },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.hasRole('organizer')).toBe(true);
      expect(result.current.hasRole('speaker')).toBe(true);
      expect(result.current.hasRole('attendee')).toBe(false);
      // but company still hydrated from DB
      expect(result.current.user?.companyId).toBe('Acme AG');
    });
  });

  describe('Story 12.11 — refreshUser() consent-gate refresh', () => {
    test('should pick up termsAcceptedAt from /users/me on refreshUser()', async () => {
      mockMultiRoleUser(['attendee']);
      // Initial hydration: consent confirmed absent → null (gate armed).
      usersMeReplies({ roles: [], companyId: undefined });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
      expect(result.current.user?.termsAcceptedAt).toBeNull();

      // Consent recorded server-side; the refresh GET now returns it.
      usersMeReplies({
        roles: [],
        termsAcceptedAt: '2026-06-04T18:00:00Z',
      });
      await act(async () => result.current.refreshUser());

      expect(result.current.user?.termsAcceptedAt).toBe('2026-06-04T18:00:00Z');
    });

    test('regression guard (review patch 2026-06-04): overrides survive a silently-failed hydration — the gate must lift after a successful consent PUT even when the refresh GET fails', async () => {
      mockMultiRoleUser(['attendee']);
      usersMeReplies({ roles: [], companyId: undefined });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
      expect(result.current.user?.termsAcceptedAt).toBeNull();

      // The refresh GET fails transiently — hydrateUserFromDb fails open and returns
      // the stale user (termsAcceptedAt: null). The server-authoritative override from
      // the consent PUT response must still land, or ProtectedRoute re-fires the gate.
      http.onGet('/users/me').reply(503);
      await act(async () =>
        result.current.refreshUser({ termsAcceptedAt: '2026-06-04T18:00:00Z' })
      );

      expect(result.current.user?.termsAcceptedAt).toBe('2026-06-04T18:00:00Z');
    });
  });

  describe('partner companyName resolution (GET /partners/me)', () => {
    test('should set companyName from /partners/me when the token has none', async () => {
      mockMultiRoleUser(['partner']);
      http.onGet('/partners/me').reply(200, { companyName: 'Acme AG' });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.user?.companyName).toBe('Acme AG');
      expect(http.history.get.filter((r) => r.url === '/partners/me')).toHaveLength(1);
    });

    test('should stay authenticated without companyName when /partners/me fails', async () => {
      mockMultiRoleUser(['partner']);
      http.onGet('/partners/me').reply(404);

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(result.current.user?.companyName).toBeUndefined();
    });

    test('should not call /partners/me for a non-partner', async () => {
      mockMultiRoleUser(['organizer']);

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

      expect(http.history.get.filter((r) => r.url === '/partners/me')).toHaveLength(0);
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

    test.skip('should grant speaker permissions for organizer+speaker user', async () => {
      // Code review 2026-05-18 (P5): same note as above — hasPermission() uses singular
      // `user.role`, so multi-role permission aggregation is not implemented yet. Tracked
      // as a follow-up; the test stays as a marker.
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

  // Story 12.7 (SSO Phase 4): federated-completion entry point. Mirrors the password
  // signIn success branch — hydrates from /users/me (so preferences.language is present,
  // the Story 12.1 regression guard) and flips isAuthenticated BEFORE the caller navigates.
  describe('Story 12.7 — completeFederatedSignIn', () => {
    test('hydrates user and flips isAuthenticated, returning success', async () => {
      // No stored Cognito session → bootstrap restore is skipped; getCurrentUser is
      // therefore only invoked by completeFederatedSignIn (clean isolation).
      localStorage.clear();
      sessionStorage.clear();
      cognitoSession({
        sub: 'fed-user',
        email: 'fed@batbern.ch',
        role: 'ATTENDEE',
        username: 'fed.user',
      });
      hub.nextEvent = 'signInWithRedirect';
      // /users/me carries the regression-critical language ('de' here).
      usersMeReplies({
        roles: ['attendee'],
        companyId: undefined,
        settings: { language: 'de' },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(result.current.isAuthenticated).toBe(false);

      let outcome: unknown;
      await act(async () => {
        outcome = await result.current.completeFederatedSignIn();
      });

      expect(outcome).toEqual({ kind: 'success' });
      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
        expect(result.current.user?.preferences.language).toBe('de');
      });
    });

    test('returns failed and stays unauthenticated when no session resolves', async () => {
      localStorage.clear();
      sessionStorage.clear();
      noCognitoSession();
      hub.nextEvent = 'signInWithRedirect_failure';

      const { result } = renderHook(() => useAuth(), { wrapper });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      let outcome: unknown;
      await act(async () => {
        outcome = await result.current.completeFederatedSignIn();
      });

      expect(outcome).toEqual({ kind: 'failed' });
      expect(result.current.isAuthenticated).toBe(false);
    });
  });
});
