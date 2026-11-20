/**
 * Where the login sends the user afterwards: back to the page that asked for it (ProtectedRoute
 * and the Q&A login link put `{ from: location }` into the router state), else the dashboard.
 * Before this, every login went to /dashboard, so the profile-claim link from a mail lost its
 * token for anyone not yet logged in (#1183 D).
 */
const FALLBACK = '/dashboard';
const AUTH_PAGES = ['/login', '/logout', '/auth/'];

export function loginTarget(state: unknown): string {
  const from = (state as { from?: { pathname?: unknown; search?: unknown; hash?: unknown } } | null)
    ?.from;
  const pathname = from?.pathname;
  // only a path on this site: "//host" and "/\host" are read as another host by browsers
  if (typeof pathname !== 'string' || !/^\/(?![/\\])/.test(pathname)) {
    return FALLBACK;
  }
  if (
    AUTH_PAGES.some((p) => pathname === p || pathname.startsWith(p.endsWith('/') ? p : `${p}/`))
  ) {
    return FALLBACK;
  }
  const search = typeof from?.search === 'string' ? from.search : '';
  const hash = typeof from?.hash === 'string' ? from.hash : '';
  return `${pathname}${search}${hash}`;
}

/**
 * The router state does not survive a trip out of the app: "Continue with Google" returns through
 * the Cognito hosted UI to /auth/callback, and sign-up goes through registration and e-mail
 * verification before the login. The target is kept for that trip in this tab, for an hour, and
 * checked again by `loginTarget` when it is read, so a tampered value cannot leave the site.
 */
const REMEMBER_KEY = 'batbern.loginReturn';
const REMEMBER_MS = 60 * 60 * 1000;

export function rememberLoginTarget(state: unknown, now: number = Date.now()): void {
  if (loginTarget(state) === FALLBACK) {
    return;
  }
  const from = (state as { from: unknown }).from;
  try {
    sessionStorage.setItem(REMEMBER_KEY, JSON.stringify({ from, at: now }));
  } catch {
    // storage blocked (private mode): the login falls back to the dashboard as before
  }
}

export function takeRememberedLoginTarget(now: number = Date.now()): string | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(REMEMBER_KEY);
    sessionStorage.removeItem(REMEMBER_KEY);
  } catch {
    return null;
  }
  if (!raw) {
    return null;
  }
  try {
    const { from, at } = JSON.parse(raw) as { from?: unknown; at?: unknown };
    if (typeof at !== 'number' || now - at > REMEMBER_MS) {
      return null;
    }
    const target = loginTarget({ from });
    return target === FALLBACK ? null : target;
  } catch {
    return null;
  }
}

/** After a password sign-in: the router state first, else the target remembered for the trip. */
export function resolveLoginTarget(state: unknown, now: number = Date.now()): string {
  const fromState = loginTarget(state);
  const remembered = takeRememberedLoginTarget(now);
  return fromState !== FALLBACK ? fromState : (remembered ?? FALLBACK);
}
