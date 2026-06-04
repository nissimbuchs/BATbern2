/**
 * AuthCallbackPage — SSO federated-login callback handler (Story 12.7, SSO Phase 4)
 *
 * The Cognito hosted UI redirects back to `/auth/callback?code=...` after a Google
 * sign-in. Amplify processes the code; this component completes the session through
 * the SAME hydration path as password login (`AuthContext.completeFederatedSignIn`),
 * then routes the user on. It touches NO `fetch`/`axios` directly (project rule) —
 * it drives everything through the context layer. Text-free (BATbernLoader only), so
 * no i18n keys are required (AC8).
 */

import React, { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@hooks/useAuth';
import { authService } from '@/services/auth/authService';
import { BATbernLoader } from '@components/shared/BATbernLoader';

// Story 12.8 F6: one-shot guard for the post-link auto-retry (see effect below).
// sessionStorage so it survives the round-trip to the hosted UI but never leaks across tabs.
const LINK_RETRY_KEY = 'batbern.link-retry';

export const AuthCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { completeFederatedSignIn } = useAuth();
  // Guard against double-invocation (React 18 StrictMode dev double-effect).
  const startedRef = useRef(false);

  useEffect(() => {
    // `startedRef` already guards against the React 18 StrictMode dev double-effect,
    // so the federated completion runs exactly once. We deliberately do NOT add a
    // `cancelled` cleanup flag here: under StrictMode the fake unmount would set it on
    // the only in-flight run, and the remount early-returns (startedRef is set) without
    // restarting — so a `cancelled` check would skip navigation entirely in dev, leaving
    // the user stuck on the loader. This is a transient redirect page, so navigating
    // after a (real) unmount is a harmless no-op.
    if (startedRef.current) return;
    startedRef.current = true;

    // Story 12.8 F6 (verified live 2026-06-04): when the account-linking PreSignUp trigger
    // merges a FIRST-TIME Google sign-in into an existing native user
    // (AdminLinkProviderForUser, Story 12.6), Cognito ABORTS that in-flight sign-in by
    // design — the hosted UI bounces back here with
    // `?error_description=Already found an entry for username …`. The identity IS linked
    // at that point; a second sign-in goes straight through. Auto-retry once (one-shot
    // sessionStorage guard against loops) so the user's first click still ends signed-in
    // instead of stranding them logged-out on the login page.
    const errorDescription = searchParams.get('error_description') ?? '';
    if (/already found an entry for username/i.test(errorDescription)) {
      if (!sessionStorage.getItem(LINK_RETRY_KEY)) {
        sessionStorage.setItem(LINK_RETRY_KEY, '1');
        void authService.signInWithFederated('Google');
        return; // the browser navigates to the hosted UI — nothing further to do here
      }
      // Retry already attempted and the abort recurred (unexpected) — clear the guard and
      // fall through to the normal failure path below; no retry loop.
      sessionStorage.removeItem(LINK_RETRY_KEY);
    } else {
      // Any non-abort outcome clears the one-shot guard.
      sessionStorage.removeItem(LINK_RETRY_KEY);
    }

    (async () => {
      const outcome = await completeFederatedSignIn();
      // hydrateUserFromDb is awaited inside completeFederatedSignIn, so isAuthenticated
      // and preferences.language are already in state before we navigate.
      navigate(outcome.kind === 'success' ? '/dashboard' : '/login', { replace: true });
    })();
  }, [completeFederatedSignIn, navigate, searchParams]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <BATbernLoader size={96} />
    </div>
  );
};

export default AuthCallbackPage;
