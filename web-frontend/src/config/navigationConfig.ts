/**
 * Navigation Configuration
 * Story 1.17, Task 6b: Static navigation configuration for role-based menus
 */

import type { UserRole } from '@/types/auth';
import {
  Dashboard,
  Event,
  Handshake,
  BarChart,
  Business,
  ManageAccounts,
  PeopleAltOutlined,
  Public,
  Lightbulb,
  CalendarMonth,
} from '@mui/icons-material';

export interface NavigationItem {
  labelKey: string;
  path: string;
  icon: typeof Dashboard;
  roles: UserRole[];
  description?: string;
  children?: NavigationItem[];
}

/**
 * Static navigation configuration for all roles
 * Follows Swiss design principles: clear, minimal, functional
 */
export const navigationConfig: NavigationItem[] = [
  // Organizer-specific items
  {
    labelKey: 'events:navigation.dashboard',
    path: '/organizer/events',
    icon: Event,
    roles: ['organizer'],
    description: 'Manage all events',
  },
  {
    labelKey: 'navigation.partners',
    path: '/organizer/partners',
    icon: Handshake,
    roles: ['organizer'],
    description: 'Manage partners',
    children: [
      {
        labelKey: 'navigation.partners',
        path: '/organizer/partners',
        icon: Handshake,
        roles: ['organizer'],
        description: 'Manage partners',
      },
      {
        labelKey: 'navigation.partnerTopics',
        path: '/organizer/partner-topics',
        icon: Lightbulb,
        roles: ['organizer'],
        description: 'Review partner topic suggestions',
      },
      {
        labelKey: 'navigation.partnerMeetings',
        path: '/organizer/partner-meetings',
        icon: CalendarMonth,
        roles: ['organizer'],
        description: 'Manage partner meetings',
      },
    ],
  },
  {
    labelKey: 'navigation.companies',
    path: '/organizer/companies',
    icon: Business,
    roles: ['organizer'],
    description: 'Manage companies',
  },
  {
    labelKey: 'navigation.users',
    path: '/organizer/users',
    icon: ManageAccounts,
    roles: ['organizer'],
    description: 'Manage platform users',
  },
  {
    labelKey: 'navigation.newsletterSubscribers',
    path: '/organizer/newsletter-subscribers',
    icon: PeopleAltOutlined,
    roles: ['organizer'],
    description: 'Manage newsletter subscribers',
  },
  {
    labelKey: 'navigation.analytics',
    path: '/organizer/analytics',
    icon: BarChart,
    roles: ['organizer', 'partner'],
    description: 'View event analytics',
  },
  {
    labelKey: 'navigation.publicSite',
    path: '/',
    icon: Public,
    roles: ['organizer'],
    description: 'View public website',
  },

  // 2026-05-20 (Q#3) — SPEAKER nav entries removed from the non-public navigation.
  // Speakers redirect to the public site (see App.tsx routing); they never see this
  // role-based menu. The speaker-relevant links ("My Sessions" → speaker dashboard,
  // "My Profile" → profile update page) now live in PublicNavigation's user dropdown.
  // The previous /speaker/events, /speaker/content, /speaker/profile paths did not
  // resolve to any route — they were dead nav items.

  // Partner-specific items
  {
    labelKey: 'navigation.myCompany',
    path: '/partners/company',
    icon: Business,
    roles: ['partner'],
    description: 'Your company profile',
  },
  {
    labelKey: 'navigation.topics',
    path: '/partners/topics',
    icon: Lightbulb,
    roles: ['partner'],
    description: 'Topic suggestions',
  },
  {
    labelKey: 'navigation.publicSite',
    path: '/',
    icon: Public,
    roles: ['partner'],
    description: 'View public website',
  },

  // 2026-05-20 (Q#3) — ATTENDEE nav entries removed for the same reason as SPEAKER
  // entries above: attendees consume the public website (event browsing, registration,
  // archive), not the non-public role-based admin app. The /attendee/* paths did not
  // resolve to public-styled routes; the items were dead.
];

/**
 * Get navigation items for a specific role
 */
export function getNavigationForRole(role: UserRole): NavigationItem[] {
  return navigationConfig.filter((item) => item.roles.includes(role));
}

/**
 * 2026-05-20 (Q#1b) — set of roles that have any non-public-site entries in this
 * config. Used by AppHeader to filter the RoleSelector chips: a role with no entries
 * (currently 'speaker' and 'attendee' — they live in the public site instead) must
 * not show a chip because clicking it would render an empty NavigationMenu. The
 * "Public Site" pseudo-entry is excluded so a role with only that link does not
 * count as "has entries."
 */
export function getRolesWithNavEntries(): ReadonlySet<UserRole> {
  const set = new Set<UserRole>();
  navigationConfig.forEach((item) => {
    if (item.path === '/') return; // Public Site is not a "real" admin entry.
    item.roles.forEach((r) => set.add(r));
  });
  return set;
}

/**
 * Get navigation items for multiple roles, deduplicating by path.
 * Used for users with more than one role (e.g. organizer + partner).
 */
export function getNavigationForRoles(roles: UserRole[]): NavigationItem[] {
  const seen = new Set<string>();
  return roles
    .flatMap((role) => navigationConfig.filter((item) => item.roles.includes(role)))
    .filter((item) => {
      if (seen.has(item.path)) return false;
      seen.add(item.path);
      return true;
    });
}

/**
 * Story 11.E.3 (cherry-pick 73d94688 / Story 9.5): Get navigation items grouped by role section.
 * Returns an array of role groups, each with a label key (`navigation.section.{role}`) and items.
 * Used by NavigationMenu when the signed-in user has more than one role — renders a section
 * header + divider between each role group.
 *
 * Code review 2026-05-18 (D4): dedup items that appear in more than one of the user's roles
 * (e.g. "Public Site" exists for organizer + speaker + partner + attendee — without dedup a
 * dual-role user would see it twice in their nav). The dedup is *across* the user's groups —
 * the item belongs to the first matching role-section in the iteration order. Within a
 * single role section, items are not re-keyed.
 */
export function getGroupedNavigationForRoles(
  roles: UserRole[]
): { role: UserRole; labelKey: string; items: NavigationItem[] }[] {
  const seen = new Set<string>();
  return roles.map((role) => {
    const items = getNavigationForRole(role).filter((item) => {
      if (seen.has(item.path)) return false;
      seen.add(item.path);
      return true;
    });
    return {
      role,
      labelKey: `navigation.section.${role}`,
      items,
    };
  });
}

/**
 * Check if a path is active (matches current location)
 */
export function isPathActive(path: string, currentPath: string): boolean {
  return currentPath === path || currentPath.startsWith(`${path}/`);
}
