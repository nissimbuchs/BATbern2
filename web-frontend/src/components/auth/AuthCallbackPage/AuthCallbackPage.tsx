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
import { useOptionalConfig } from '@/contexts/useConfig';
import { authService } from '@/services/auth/authService';
import { BATbernLoader } from '@components/shared/BATbernLoader';

// Story 12.8 F6: one-shot guard for the post-link auto-retry (see effect below).
// sessionStorage so it survives the round-trip to the hosted UI but never leaks across tabs.
const LINK_RETRY_KEY = 'batbern.link-retry';

// Config-race fix (2026-06-05): bounded escape if the runtime config never arrives
// (GET /api/v1/config failed) — don't strand the user on the loader forever.
const CONFIG_WAIT_TIMEOUT_MS = 15000;

export const AuthCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { completeFederatedSignIn } = useAuth();
  /**
   * Config-race fix (2026-06-05, root cause of the unstable federated login): this page
   * raced ConfigProvider's background `GET /api/v1/config`. When this effect won,
   * `ensureAmplifyConfigured()` (inside every authService call) silently NO-OP'd —
   * `setAmplifyRuntimeConfig` hadn't been called yet — so `Amplify.configure()` never ran.
   * In Amplify v6 the configure call is the ONLY trigger of the OAuth listener that
   * exchanges `?code=` for tokens; without it the exchange never happens, the F7 wait
   * times out after 15s, and the user bounces to /login ("unexpected error"). Nothing
   * retried configure once the config landed, so the only rescue was an unrelated code
   * path (e.g. AuthContext session restore for users with leftover tokens — why it
   * "sometimes worked"). Same race class as the session-restore gate in AuthContext.tsx.
   * Fix: gate the whole completion flow (incl. the F6 auto-retry, which also needs a
   * configured Amplify) on the config being loaded; the effect re-runs when it lands.
   */
  const config = useOptionalConfig();
  // Guard against double-invocation (React 18 StrictMode dev double-effect).
  const startedRef = useRef(false);

  // Bounded escape: config never arrives (config endpoint down) → fail over to /login
  // instead of an infinite loader. Cleared as soon as config lands / completion starts.
  useEffect(() => {
    if (config || startedRef.current) return;
    const timer = setTimeout(() => {
      if (!startedRef.current) {
        startedRef.current = true;
        console.error('[AuthCallbackPage] Runtime config never arrived — aborting callback');
        navigate('/login', { replace: true });
      }
    }, CONFIG_WAIT_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [config, navigate]);

  useEffect(() => {
    // Wait for the runtime config — without it Amplify cannot be configured and the
    // code exchange cannot run (see doc comment on `config` above).
    if (!config) return;
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
  }, [config, completeFederatedSignIn, navigate, searchParams]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <BATbernLoader size={96} />
    </div>
  );
};

export default AuthCallbackPage;
