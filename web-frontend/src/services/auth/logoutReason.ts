/**
 * Logout-reason hand-off (Story 12.8 finding F5, 2026-06-04).
 *
 * When the gateway returns 403 ACCOUNT_DEACTIVATED, the apiClient interceptor forces a
 * sign-out and routes to `/login?reason=account_deactivated`. For a FEDERATED session,
 * however, Amplify `signOut()` performs a full-page redirect to the Cognito hosted-UI
 * `/logout` endpoint — clobbering that in-app navigation (and its query param). This tiny
 * sessionStorage hand-off carries the reason across the Cognito logout round-trip
 * (sessionStorage is per-tab and survives cross-origin navigation away and back):
 *
 *   apiClient (403) ── setLogoutReason ──▶ Cognito /logout ──▶ /logout (LogoutPage)
 *       │                                     consumeLogoutReason → /login?reason=…
 *       └─▶ in-app /login (native sessions / no redirect) — LoginForm consumeLogoutReason
 *
 * Both LoginForm and LogoutPage consume (whichever lands first wins); the flag is only
 * ever set by the deactivated-account path, so a consumed-but-unused flag is harmless.
 */

const KEY = 'batbern.logout-reason';

export type LogoutReason = 'account_deactivated';

export function setLogoutReason(reason: LogoutReason): void {
  try {
    sessionStorage.setItem(KEY, reason);
  } catch {
    // Storage unavailable (private mode / quota) — non-fatal, the in-app
    // `/login?reason=…` navigation still covers the non-redirect path.
  }
}

export function consumeLogoutReason(): LogoutReason | null {
  try {
    const value = sessionStorage.getItem(KEY);
    if (value !== null) {
      sessionStorage.removeItem(KEY);
    }
    return value as LogoutReason | null;
  } catch {
    return null;
  }
}
