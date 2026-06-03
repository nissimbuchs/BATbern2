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
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@hooks/useAuth';
import { BATbernLoader } from '@components/shared/BATbernLoader';

export const AuthCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const { completeFederatedSignIn } = useAuth();
  // Guard against double-invocation (React 18 StrictMode dev double-effect).
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    let cancelled = false;
    (async () => {
      const outcome = await completeFederatedSignIn();
      if (cancelled) return;
      // hydrateUserFromDb is awaited inside completeFederatedSignIn, so isAuthenticated
      // and preferences.language are already in state before we navigate.
      navigate(outcome.kind === 'success' ? '/dashboard' : '/login', { replace: true });
    })();

    return () => {
      cancelled = true;
    };
  }, [completeFederatedSignIn, navigate]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <BATbernLoader size={96} />
    </div>
  );
};

export default AuthCallbackPage;
