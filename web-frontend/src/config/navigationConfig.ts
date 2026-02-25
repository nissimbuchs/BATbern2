/**
 * Navigation Configuration
 * Story 1.17, Task 6b: Static navigation configuration for role-based menus
 */

import type { UserRole } from '@/types/auth';
import {
  Dashboard,
  Event,
  People,
  Handshake,
  BarChart,
  Person,
  ContentPaste,
  EventAvailable,
  Business,
  ManageAccounts,
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
    labelKey: 'navigation.speakers',
    path: '/organizer/speakers',
    icon: People,
    roles: ['organizer'],
    description: 'Manage speakers',
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

  // Speaker-specific items
  {
    labelKey: 'navigation.dashboard',
    path: '/speaker/dashboard',
    icon: Dashboard,
    roles: ['speaker'],
    description: 'Speaker dashboard',
  },
  {
    labelKey: 'navigation.myEvents',
    path: '/speaker/events',
    icon: Event,
    roles: ['speaker'],
    description: 'Your assigned events',
  },
  {
    labelKey: 'navigation.myContent',
    path: '/speaker/content',
    icon: ContentPaste,
    roles: ['speaker'],
    description: 'Manage your content',
  },
  {
    labelKey: 'navigation.myCompany',
    path: '/speaker/company',
    icon: Business,
    roles: ['speaker'],
    description: 'Your company profile',
  },
  {
    labelKey: 'navigation.profile',
    path: '/speaker/profile',
    icon: Person,
    roles: ['speaker'],
    description: 'Your speaker profile',
  },
  {
    labelKey: 'navigation.publicSite',
    path: '/',
    icon: Public,
    roles: ['speaker'],
    description: 'View public website',
  },

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

  // Attendee-specific items
  {
    labelKey: 'navigation.events',
    path: '/attendee/events',
    icon: Event,
    roles: ['attendee'],
    description: 'Browse events',
  },
  {
    labelKey: 'navigation.speakers',
    path: '/attendee/speakers',
    icon: People,
    roles: ['attendee'],
    description: 'Browse speakers',
  },
  {
    labelKey: 'navigation.myRegistrations',
    path: '/attendee/registrations',
    icon: EventAvailable,
    roles: ['attendee'],
    description: 'Your event registrations',
  },
  {
    labelKey: 'navigation.publicSite',
    path: '/',
    icon: Public,
    roles: ['attendee'],
    description: 'View public website',
  },
];

/**
 * Get navigation items for a specific role
 */
export function getNavigationForRole(role: UserRole): NavigationItem[] {
  return navigationConfig.filter((item) => item.roles.includes(role));
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
 * Check if a path is active (matches current location)
 */
export function isPathActive(path: string, currentPath: string): boolean {
  return currentPath === path || currentPath.startsWith(`${path}/`);
}
