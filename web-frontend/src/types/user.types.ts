/**
 * User Management Types
 *
 * Application-specific types derived from OpenAPI-generated types.
 * Story 2.5.2: User Management Frontend
 */

import type { components } from './generated/user-api.types';

// Core User Types from OpenAPI
export type UserResponse = components['schemas']['UserResponse'];
export type UserSearchResponse = components['schemas']['UserSearchResponse'];
export type UpdateUserRequest = components['schemas']['UpdateUserRequest'];
export type GetOrCreateUserRequest = components['schemas']['GetOrCreateUserRequest'];
export type GetOrCreateUserResponse = components['schemas']['GetOrCreateUserResponse'];
export type PaginatedUserResponse = components['schemas']['PaginatedUserResponse'];
export type UserPreferences = components['schemas']['UserPreferences'];
export type UserSettings = components['schemas']['UserSettings'];
export type UpdateRolesRequest = components['schemas']['UpdateRolesRequest'];
export type ActivityEntry = components['schemas']['ActivityEntry'];
export type Company = components['schemas']['Company'];

// Simplified User type for most use cases
export type User = UserResponse;

// Role enum
export type Role = 'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE';

// Role labels for display (i18n keys)
export const ROLE_LABELS: Record<Role, string> = {
  ORGANIZER: 'userManagement.roles.organizer',
  SPEAKER: 'userManagement.roles.speaker',
  PARTNER: 'userManagement.roles.partner',
  ATTENDEE: 'userManagement.roles.attendee',
};

// Role icons/emojis for display
export const ROLE_ICONS: Record<Role, string> = {
  ORGANIZER: '🎯',
  SPEAKER: '🎤',
  PARTNER: '🏢',
  ATTENDEE: '👤',
};

// Frontend-specific types (not in OpenAPI)
export interface UserFilters {
  role?: Role[];
  company?: string;
  status?: 'active' | 'inactive' | 'all';
  searchQuery?: string;
}

export interface UserPagination {
  page: number;
  limit: number;
  totalItems?: number;
  totalPages?: number;
  hasNext?: boolean;
  hasPrev?: boolean;
}

export interface CreateUserFormData {
  firstName: string;
  lastName: string;
  email: string;
  companyId?: string;
  roles: Role[];
}

export interface UserListState {
  filters: UserFilters;
  pagination: UserPagination;
  selectedUser: User | null;
  searchQuery: string;
}

// Error types
export interface UserManagementError {
  code: string;
  message: string;
  field?: string;
}

// Minimum organizers validation
export const MIN_ORGANIZERS = 2;

// API include options
export type UserIncludeOption = 'company' | 'roles' | 'preferences' | 'settings' | 'activity';

// Default pagination
export const DEFAULT_PAGINATION: UserPagination = {
  page: 1,
  limit: 20,
};

// Status filter options
export type UserStatusFilter = 'active' | 'inactive' | 'all';
