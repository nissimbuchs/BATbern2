/**
 * LogoutPage — `/logout` route handler (Story 12.7, SSO Phase 4)
 *
 * Exists so the Cognito hosted-UI `redirectSignOut` target (registered in
 * cognito-stack.ts and configured at amplify.ts) lands on a real route rather than
 * the catch-all `*` → `/`. Signs out via the existing `useAuth().signOut()` (→ Amplify
 * signOut) then navigates to `/` (matching the Amplify `redirectSignOut` target).
 */

import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@hooks/useAuth';
import { consumeLogoutReason } from '@/services/auth/logoutReason';
import { BATbernLoader } from '@components/shared/BATbernLoader';

export const LogoutPage: React.FC = () => {
  const navigate = useNavigate();
  const { signOut } = useAuth();
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    let cancelled = false;
    (async () => {
      await signOut();
      if (!cancelled) {
        // Story 12.8 F5: when the sign-out was FORCED by a 403 ACCOUNT_DEACTIVATED, the
        // apiClient stored the reason before Amplify's hosted-UI logout redirect landed us
        // here. Forward it to the login surface so the user sees WHY they were signed out
        // instead of silently arriving on the homepage.
        const reason = consumeLogoutReason();
        navigate(reason === 'account_deactivated' ? '/login?reason=account_deactivated' : '/', {
          replace: true,
        });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [signOut, navigate]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <BATbernLoader size={96} />
    </div>
  );
};

export default LogoutPage;
