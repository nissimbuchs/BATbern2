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
      if (!cancelled) navigate('/', { replace: true });
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
