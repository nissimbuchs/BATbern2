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
  '/archive',
  '/register',
];

// Role-based path access
const PATH_ACCESS: Record<UserRole, string[]> = {
  organizer: [
    '/dashboard',
    '/profile', // Story 12.11: role-neutral profile page
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
  // Story 12.11: '/profile' for every role — the role-neutral profile page is
  // also the onboarding-gate target, so every authenticated role must reach it.
  attendee: ['/dashboard', '/profile', '/events', '/content', '/search', '/attendee', '/account'],
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
  const allowedPaths = effectiveRoles(user).flatMap((r) => PATH_ACCESS[r] ?? []);
  return allowedPaths.some((allowedPath) => path.startsWith(allowedPath));
}
