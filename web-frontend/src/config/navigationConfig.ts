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
} from '@mui/icons-material';

export interface NavigationItem {
  label: string;
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
    label: 'Events',
    path: '/organizer/events',
    icon: Event,
    roles: ['organizer'],
    description: 'Manage all events',
  },
  {
    label: 'Speakers',
    path: '/organizer/speakers',
    icon: People,
    roles: ['organizer'],
    description: 'Manage speakers',
  },
  {
    label: 'Partners',
    path: '/organizer/partners',
    icon: Handshake,
    roles: ['organizer'],
    description: 'Manage partners',
  },
  {
    label: 'Analytics',
    path: '/organizer/analytics',
    icon: BarChart,
    roles: ['organizer'],
    description: 'View event analytics',
  },

  // Speaker-specific items
  {
    label: 'Dashboard',
    path: '/speaker/dashboard',
    icon: Dashboard,
    roles: ['speaker'],
    description: 'Speaker dashboard',
  },
  {
    label: 'My Events',
    path: '/speaker/events',
    icon: Event,
    roles: ['speaker'],
    description: 'Your assigned events',
  },
  {
    label: 'My Content',
    path: '/speaker/content',
    icon: ContentPaste,
    roles: ['speaker'],
    description: 'Manage your content',
  },
  {
    label: 'Profile',
    path: '/speaker/profile',
    icon: Person,
    roles: ['speaker'],
    description: 'Your speaker profile',
  },

  // Partner-specific items
  {
    label: 'Dashboard',
    path: '/partner/dashboard',
    icon: Dashboard,
    roles: ['partner'],
    description: 'Partner dashboard',
  },
  {
    label: 'Events',
    path: '/partner/events',
    icon: Event,
    roles: ['partner'],
    description: 'View events',
  },
  {
    label: 'Analytics',
    path: '/partner/analytics',
    icon: BarChart,
    roles: ['partner'],
    description: 'View analytics',
  },
  {
    label: 'Profile',
    path: '/partner/profile',
    icon: Person,
    roles: ['partner'],
    description: 'Your partner profile',
  },

  // Attendee-specific items
  {
    label: 'Events',
    path: '/attendee/events',
    icon: Event,
    roles: ['attendee'],
    description: 'Browse events',
  },
  {
    label: 'Speakers',
    path: '/attendee/speakers',
    icon: People,
    roles: ['attendee'],
    description: 'Browse speakers',
  },
  {
    label: 'My Registrations',
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
