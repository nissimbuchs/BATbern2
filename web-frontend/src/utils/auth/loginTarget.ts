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
