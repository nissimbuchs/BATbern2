/**
 * The role, permission and path rules of the auth state, as pure functions of the user.
 * `AuthProvider` binds them to its state (`hasRole`, `hasPermission`, `canAccess`); the test
 * auth helper (`src/test/auth.tsx`) binds them to a test user, so a test runs the same rules.
 */
import type { UserContext, UserRole } from '@/types/auth';

/** The user's roles; the single `role` for a user without a `roles` array. */
function effectiveRoles(user: UserContext): UserRole[] {
  if (user.roles && user.roles.length > 0) return user.roles;
  return user.role ? [user.role] : [];
}

export function userHasRole(user: UserContext | null, role: UserRole): boolean {
  if (!user) return false;
  return effectiveRoles(user).includes(role);
}

// Role-based permission matrix based on Story 1.2 specifications
const PERMISSIONS: Record<UserRole, Record<string, string[]>> = {
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

/** Whether the user's primary role may do `action` on `resource`. */
export function userHasPermission(
  user: UserContext | null,
  resource: string,
  action: string
): boolean {
  if (!user) return false;
  return PERMISSIONS[user.role]?.[resource]?.includes(action) ?? false;
}

// Public paths accessible to everyone (authenticated and unauthenticated)
const PUBLIC_PATHS = [
  '/login',
  '/signup',
  '/forgot-password',
  '/auth',
  '/about',
  '/why-an-account', // story "Visitors see what an account gives them": linked from the mail
  '/archive',
  '/register',
];

/**
 * Pages every signed-in role may open, whatever its other paths: the dashboard redirect, the
 * role-neutral profile (also the onboarding-gate target, Story 12.11), the account, the
 * attendee dashboard with its "What's new" card, and the claim link from the claim mail (#1217).
 */
const EVERY_ROLE_PATHS = ['/dashboard', '/profile', '/account', '/attendee', '/claim-profile'];

// Role-based path access, on top of EVERY_ROLE_PATHS
const PATH_ACCESS: Record<UserRole, string[]> = {
  // '/speakers' is gone: it only redirects now (#1223 item 8); speakers are under /organizer/users.
  organizer: ['/events', '/partners', '/analytics', '/organizer'],
  speaker: ['/events', '/materials', '/speaker', '/speaker-portal'],
  partner: ['/analytics', '/organizer/analytics', '/events', '/partner'],
  attendee: ['/events', '/content', '/search'],
};

/**
 * Whether a user may open `path`.
 *
 * 2026-05-20 (Q#2c) — this previously only consulted `user.role` (the primary role). For a
 * user with roles [organizer, speaker] the primary is 'organizer', so `/speaker-portal/*`
 * failed the check, bounced through ProtectedRoute → /dashboard → /organizer/events —
 * landing the user on the organizer dashboard instead of the speaker portal. Every role
 * counts: the path is allowed if ANY role allows it.
 */
export function canAccessPath(
  isAuthenticated: boolean,
  user: UserContext | null,
  path: string
): boolean {
  // Homepage is always accessible
  if (path === '/' || PUBLIC_PATHS.some((publicPath) => path.startsWith(publicPath))) {
    return true;
  }
  if (!isAuthenticated || !user) return false;
  const roles = effectiveRoles(user);
  if (roles.length === 0) return false;
  const allowedPaths = [...EVERY_ROLE_PATHS, ...roles.flatMap((r) => PATH_ACCESS[r] ?? [])];
  return allowedPaths.some((allowedPath) => path.startsWith(allowedPath));
}
