/* eslint-disable react-refresh/only-export-components */
/**
 * AuthContext - Global Authentication State Management
 * Provides shared auth state across all components using React Context
 */

import React, { createContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { authService } from '@services/auth/authService';
import { useOptionalConfig } from './useConfig';
import {
  AuthenticationState,
  LoginCredentials,
  SignUpData,
  UserContext,
  UserRole,
} from '@/types/auth';
import apiClient from '@/services/api/apiClient';
import { getUserProfile } from '@/services/api/userApi';
import { hasCognitoSession } from '@/utils/auth/cognitoSession';

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
  /**
   * Story 12.7 (SSO Phase 4): complete a federated (Google) sign-in after the
   * Amplify hosted-UI redirect has settled. Reads the current session, then runs
   * the SAME success branch as password `signIn` (`hydrateUserFromDb` →
   * partner-companyName resolve → `setState({ isAuthenticated: true })`), so a
   * federated session lands an identical `UserContext`. Awaited by the
   * `/auth/callback` handler BEFORE it navigates to `/dashboard`.
   */
  completeFederatedSignIn: () => Promise<SignInOutcome>;
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
 * Hydrate the authenticated user from `GET /users/me` — company, preferences, and
 * (when the JWT carries none) roles.
 *
 * Story 12.1 (ADR-001 "minimal target footprint"): `companyId` and `preferences` are
 * business data owned by `user_profiles`, not identity/authorization, so they no longer
 * ride in the token (`custom:companyId` is gone; `custom:preferences` is no longer read
 * by `extractUserContextFromToken`). They are sourced here from the DB on every
 * login/init. The regression guard in AC1 is satisfied structurally: this runs and is
 * `await`-ed BEFORE `setState({ isAuthenticated: true })`, so `preferences.language` is
 * on the user the moment any auth-gated effect (e.g. `LanguageSync`, App.tsx) fires.
 *
 * Roles: the JWT `custom:role` claim is authoritative when present — the
 * PreTokenGeneration Lambda projects it fresh from `role_assignments` in the staging
 * DB. In local development a CUMS-provisioned speaker has a Cognito user in staging but
 * a `user_profiles` row only in the LOCAL DB, so the Lambda finds nothing and the JWT
 * comes back empty; this falls back to the DB roles so the user isn't stranded on a
 * blank dashboard (Pattern 3b, Epic 11.E.7 — see `JwtRolesConverter` for the backend
 * twin). When the JWT already carries roles, DB roles are NOT used to override them.
 *
 * Silently no-ops on any failure, preserving the token-derived user as-is.
 */
/**
 * Coerce a backend locale to the frontend `UserPreferences.language` union. The backend
 * `UserPreferences` enum carries more locales (rm/es/fi/nl/ja/…) than the frontend
 * 4-locale union, so a blind cast would put an out-of-union value into a typed field.
 * Values outside the union fall back to the prior value, then 'en'.
 */
const FE_LANGUAGES: readonly UserContext['preferences']['language'][] = ['en', 'de', 'fr', 'it'];
function normalizeLanguage(
  lang: string | undefined,
  fallback: UserContext['preferences']['language'] = 'en'
): UserContext['preferences']['language'] {
  return (FE_LANGUAGES as readonly string[]).includes(lang ?? '')
    ? (lang as UserContext['preferences']['language'])
    : fallback;
}

async function hydrateUserFromDb(user: UserContext): Promise<UserContext> {
  try {
    const profile = await getUserProfile(['roles', 'company', 'preferences']);
    // The backend `/users/me` response (UserResponse, see user-api.types.ts) returns
    // `roles: ('ORGANIZER' | …)[]` (UPPERCASE), `companyId` (the meaningful company name
    // per ADR-003), and `preferences` (canonical UserPreferences). The declared
    // `UserProfileResponse` return type in userApi.ts does not match the runtime shape,
    // so we cast through `unknown` and read the real fields.
    const raw = profile as unknown as {
      roles?: string[];
      currentRole?: string;
      companyId?: string;
      preferences?: {
        language?: string;
        theme?: string;
        emailNotifications?: boolean;
        pushNotifications?: boolean;
      };
    };

    let hydrated = user;

    // Story 12.1: company sourced from the DB (was custom:companyId).
    if (raw.companyId) {
      hydrated = { ...hydrated, companyId: raw.companyId };
    }

    // Story 12.1: preferences sourced from the DB (was custom:preferences). Map the
    // canonical backend shape onto the frontend UserPreferences contract; `language` is
    // the regression-critical field for locale selection.
    if (raw.preferences) {
      const p = raw.preferences;
      hydrated = {
        ...hydrated,
        preferences: {
          language: normalizeLanguage(p.language, hydrated.preferences?.language ?? 'en'),
          theme: p.theme === 'dark' ? 'dark' : 'light',
          notifications: {
            email: p.emailNotifications ?? true,
            sms: false,
            push: p.pushNotifications ?? true,
          },
          privacy: {
            showProfile: true,
            allowMessages: true,
          },
        },
      };
    }

    // Roles: only fall back to DB roles when the JWT carried none (local-dev Pattern 3b).
    if (!user.roles || user.roles.length === 0) {
      const fetchedRoles: UserRole[] = (raw.roles ?? [])
        .map((r) => r.toLowerCase())
        .filter(
          (r): r is UserRole =>
            r === 'organizer' || r === 'speaker' || r === 'partner' || r === 'attendee'
        );
      if (fetchedRoles.length > 0) {
        const primary: UserRole =
          raw.currentRole && fetchedRoles.includes(raw.currentRole.toLowerCase() as UserRole)
            ? (raw.currentRole.toLowerCase() as UserRole)
            : fetchedRoles[0];
        console.log(
          '[AuthProvider] Hydrated roles from /users/me (JWT custom:role was empty) —',
          'roles=',
          fetchedRoles,
          'primary=',
          primary
        );
        hydrated = { ...hydrated, role: primary, roles: fetchedRoles };
      }
    }

    return hydrated;
  } catch (error) {
    console.warn(
      '[AuthProvider] Could not hydrate user via GET /users/me — leaving user as-is',
      error
    );
    return user;
  }
}

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
   * Runtime config (Cognito user-pool + client IDs) is loaded asynchronously by
   * ConfigProvider AFTER first paint — the config gate was decoupled from bootstrap in
   * perf/public-homepage-followup #2 (see ConfigContext / main.tsx). Amplify cannot be
   * configured until that config is stashed (`ensureAmplifyConfigured()` no-ops while it
   * is null), and a stored Cognito session cannot be restored without a configured
   * Amplify. Session restore below is therefore gated on `config`: without the gate the
   * restore raced the `GET /api/v1/config` round-trip, ran against an unconfigured
   * Amplify, silently resolved to "no user", and bounced authenticated users (incl. the
   * Playwright @gate suite) to /login. Anonymous visitors short-circuit BEFORE the gate
   * so aws-amplify is still never pulled onto the public homepage.
   */
  const config = useOptionalConfig();

  // Session restore must run at most once, but the effect re-runs when `config`
  // transitions null → loaded; this ref guards against a second restore.
  const restoreStartedRef = useRef(false);

  /**
   * Initialize authentication state on mount (once runtime config is available)
   */
  useEffect(() => {
    if (restoreStartedRef.current) return;

    // Anonymous visitors have no Cognito tokens in storage. Resolve immediately —
    // independent of runtime config — so aws-amplify (~426 KB) is never loaded on the
    // public homepage (authService methods dynamically import it). A returning
    // authenticated user has tokens in storage and falls through to the restore path.
    if (!hasCognitoSession()) {
      restoreStartedRef.current = true;
      console.log('[AuthProvider] No Cognito session in storage — skipping restore');
      setState((prev) => ({ ...prev, isLoading: false }));
      return;
    }

    // A session exists, but Amplify needs the Cognito config from runtime config to
    // restore it. Wait for ConfigProvider to load it (this effect re-runs when `config`
    // becomes non-null). Stay in the loading state meanwhile — do NOT settle to
    // not-authenticated, which would bounce the user to /login mid-bootstrap.
    if (!config) return;

    restoreStartedRef.current = true;

    const initializeAuth = async () => {
      console.log('[AuthProvider] Initializing auth state...');

      try {
        const user = await authService.getCurrentUser();

        if (user) {
          console.log('[AuthProvider] User found, fetching token...');
          // Get current session tokens
          const tokenResult = await authService.refreshToken();

          // Epic 11.E.7: hydrate roles from /users/me when JWT carries none
          // (local-dev path; in staging the JWT always has custom:role and this no-ops).
          const hydratedUser = await hydrateUserFromDb(user);

          // Resolve companyName for partner users if not in JWT via GET /partners/me
          let resolvedCompanyName = hydratedUser.companyName;
          const isPartner =
            hydratedUser.role === 'partner' || hydratedUser.roles?.includes('partner');
          if (isPartner && !resolvedCompanyName) {
            resolvedCompanyName = await resolvePartnerCompanyName();
          }

          setState({
            isAuthenticated: true,
            isLoading: false,
            user:
              resolvedCompanyName !== hydratedUser.companyName
                ? { ...hydratedUser, companyName: resolvedCompanyName }
                : hydratedUser,
            error: null,
            accessToken: tokenResult.accessToken || null,
          });
          console.log('[AuthProvider] Auth initialized - authenticated as:', hydratedUser.email);
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
  }, [config]);

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

        // Epic 11.E.7: hydrate roles from /users/me when JWT carries none
        // (local-dev path; in staging the JWT always has custom:role and this no-ops).
        let signedInUser = await hydrateUserFromDb(result.user);

        // Resolve companyName for partner users if not in JWT via GET /partners/me
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
        // Epic 11.E.7: hydrate roles from /users/me when JWT carries none
        // (local-dev path; in staging the JWT always has custom:role and this no-ops).
        let signedInUser = await hydrateUserFromDb(result.user);
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
   * Complete federated (Google) sign-in (Story 12.7, SSO Phase 4).
   *
   * The Amplify hosted-UI redirect has returned and Amplify has processed the
   * `?code=` on `/auth/callback`; the session is now resolvable. This mirrors the
   * `signIn` success branch (and `initializeAuth`): get the user from the settled
   * session, fetch the access token, `hydrateUserFromDb` (so `preferences.language`
   * is present before any auth-gated effect — the Story 12.1 regression guard holds
   * for federated logins too), resolve partner companyName, then flip auth state.
   * No SSO-specific token branching (ADR-010 D1: same JWT shape as password login).
   */
  const completeFederatedSignIn = useCallback(async (): Promise<SignInOutcome> => {
    console.log('[AuthProvider] completeFederatedSignIn called');
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      // Story 12.8 F7: Amplify v6 runs the ?code= → token exchange asynchronously — a
      // single immediate session check races it, and the resulting /login navigation
      // CANCELS the in-flight exchange (prod symptom after the auth.batbern.ch switch:
      // every federated login silently bounced to /login while provisioning succeeded).
      // Wait (bounded; Hub event or token-poll) for the exchange to settle first.
      const exchangeSettled = await authService.waitForFederatedSession();
      if (!exchangeSettled) {
        console.warn('[AuthProvider] completeFederatedSignIn: token exchange did not settle');
      }

      const user = await authService.getCurrentUser();
      if (!user) {
        console.warn('[AuthProvider] completeFederatedSignIn: no session resolved');
        setState((prev) => ({
          ...prev,
          isLoading: false,
          error: { code: 'FEDERATED_SIGN_IN_FAILED', message: 'No federated session found' },
        }));
        return { kind: 'failed' };
      }

      const tokenResult = await authService.refreshToken();

      let signedInUser = await hydrateUserFromDb(user);

      const isPartner = signedInUser.role === 'partner' || signedInUser.roles?.includes('partner');
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
        accessToken: tokenResult.accessToken || null,
      });
      console.log(
        '[AuthProvider] Federated sign-in complete - authenticated as:',
        signedInUser.email
      );
      return { kind: 'success' };
    } catch (error: unknown) {
      console.error('[AuthProvider] Exception during completeFederatedSignIn:', error);
      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: {
          code: 'FEDERATED_SIGN_IN_ERROR',
          message:
            error instanceof Error ? error.message : 'An error occurred during federated sign in',
        },
      }));
      return { kind: 'failed' };
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

      // 2026-05-20 (Q#2c) — canAccess previously only consulted `state.user.role` (the
      // primary role). For a user with roles [organizer, speaker] the primary is
      // 'organizer', so `/speaker-portal/*` failed the check, bounced through
      // ProtectedRoute → /dashboard → /organizer/events — landing the user on the
      // organizer dashboard instead of the speaker portal. Iterate over the full
      // `roles` array (falling back to the singular `role` for legacy callers) and
      // accept the path if ANY role allows it.
      const effectiveRoles: UserRole[] =
        state.user.roles && state.user.roles.length > 0
          ? state.user.roles
          : state.user.role
            ? [state.user.role]
            : [];

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
        speaker: [
          '/dashboard',
          '/profile',
          '/events',
          '/materials',
          '/speaker',
          '/speaker-portal',
          '/account',
        ],
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

      const allowedPaths = effectiveRoles.flatMap((r) => pathAccess[r] ?? []);
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
      completeFederatedSignIn,
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
      completeFederatedSignIn,
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
