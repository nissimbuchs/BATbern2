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
} from '@mui/icons-material';

export interface NavigationItem {
  labelKey: string;
  path: string;
  icon: typeof Dashboard;
  roles: UserRole[];
  description?: string;
}

/**
 * Static navigation configuration for all roles
 * Follows Swiss design principles: clear, minimal, functional
 */
export const navigationConfig: NavigationItem[] = [
  // Organizer-specific items
  {
    labelKey: 'navigation.events',
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
  },
  {
    labelKey: 'navigation.companies',
    path: '/organizer/companies',
    icon: Business,
    roles: ['organizer'],
    description: 'Manage companies',
  },
  {
    labelKey: 'navigation.analytics',
    path: '/organizer/analytics',
    icon: BarChart,
    roles: ['organizer'],
    description: 'View event analytics',
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

  // Partner-specific items
  {
    labelKey: 'navigation.dashboard',
    path: '/partner/dashboard',
    icon: Dashboard,
    roles: ['partner'],
    description: 'Partner dashboard',
  },
  {
    labelKey: 'navigation.events',
    path: '/partner/events',
    icon: Event,
    roles: ['partner'],
    description: 'View events',
  },
  {
    labelKey: 'navigation.analytics',
    path: '/partner/analytics',
    icon: BarChart,
    roles: ['partner'],
    description: 'View analytics',
  },
  {
    labelKey: 'navigation.profile',
    path: '/partner/profile',
    icon: Person,
    roles: ['partner'],
    description: 'Your partner profile',
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
];

/**
 * Get navigation items for a specific role
 */
export function getNavigationForRole(role: UserRole): NavigationItem[] {
  return navigationConfig.filter((item) => item.roles.includes(role));
}

/**
 * Check if a path is active (matches current location)
 */
export function isPathActive(path: string, currentPath: string): boolean {
  return currentPath === path || currentPath.startsWith(`${path}/`);
}
