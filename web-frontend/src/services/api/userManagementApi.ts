/**
 * User Management API Service (GREEN Phase)
 *
 * Implementation to make tests pass.
 * Story 2.5.2: User Management Frontend - Task 4b (GREEN Phase)
 *
 * Endpoints:
 * - GET /api/v1/users - List users with filters, pagination, includes
 * - GET /api/v1/users/search - Search users with autocomplete
 * - GET /api/v1/users/{id} - Get user by ID with includes
 * - POST /api/v1/users - Create new user
 * - PUT /api/v1/users/{id}/roles - Update user roles
 * - DELETE /api/v1/users/{id} - Delete user (GDPR compliant)
 */

import apiClient from './apiClient';
import type {
  User,
  UserSearchResponse,
  PaginatedUserResponse,
  CreateUserFormData,
  UserFilters,
  UserPagination,
  Role,
} from '@/types/user.types';

// API base path for user endpoints
// Note: apiClient baseURL is set from runtime config to 'http://localhost:8080/api/v1'
// so we only need '/users' (the /api/v1 prefix is already in the baseURL)
const USER_API_PATH = '/users';

/**
 * List users with filters, pagination, and optional resource expansion
 * AC1: User Management Screen
 */
export const listUsers = async (
  filters: UserFilters,
  pagination: UserPagination,
  includes?: string[]
): Promise<PaginatedUserResponse> => {
  const params: Record<string, string | number | boolean> = {
    page: pagination.page,
    limit: pagination.limit,
  };

  // Add includes parameter
  if (includes && includes.length > 0) {
    params.include = includes.join(',');
  }

  // Add role as separate query parameter (not in filter object)
  // Backend expects ?role=ATTENDEE, not filter={"role":["ATTENDEE"]}
  if (filters.role && filters.role.length > 0) {
    // Take the first role if multiple are selected (backend accepts single role)
    params.role = filters.role[0];
  }

  // Build filter object for JSON filter syntax (for active status only)
  const filterObj: Record<string, string | boolean> = {};

  if (filters.company) {
    filterObj.company = filters.company;
  }

  if (filters.status && filters.status !== 'all') {
    filterObj.active = filters.status === 'active';
  }

  // Add search query parameter if provided (searches name and email)
  if (filters.searchQuery && filters.searchQuery.trim()) {
    params.search = filters.searchQuery;
  }

  // Add filter parameter if we have filters
  if (Object.keys(filterObj).length > 0) {
    params.filter = JSON.stringify(filterObj);
  }

  const response = await apiClient.get<PaginatedUserResponse>(USER_API_PATH, { params });
  return response.data;
};

/**
 * Search users with autocomplete (300ms debouncing should be handled by the hook/component)
 * AC6: Search & Autocomplete
 */
export const searchUsers = async (
  query: string,
  limit: number = 10
): Promise<UserSearchResponse[]> => {
  const response = await apiClient.get<UserSearchResponse[]>(`${USER_API_PATH}/search`, {
    params: {
      query: encodeURIComponent(query),
      limit,
    },
  });
  return response.data;
};

/**
 * Get user by ID with optional resource expansion
 * AC1: User Detail Modal
 */
export const getUserById = async (id: string, includes?: string[]): Promise<User> => {
  const params: Record<string, string> = {};

  if (includes && includes.length > 0) {
    params.include = includes.join(',');
  }

  const response = await apiClient.get<User>(`${USER_API_PATH}/${id}`, { params });
  return response.data;
};

/**
 * Create new user
 * AC4: User Creation
 */
export const createUser = async (data: CreateUserFormData): Promise<User> => {
  const response = await apiClient.post<User>(USER_API_PATH, data);
  return response.data;
};

/**
 * Update user roles with minimum organizers validation
 * AC3: Role Management
 */
export const updateUserRoles = async (id: string, roles: Role[]): Promise<User> => {
  const response = await apiClient.put<User>(`${USER_API_PATH}/${id}/roles`, { roles });
  return response.data;
};

/**
 * Delete user (GDPR compliant cascade deletion)
 * AC5: User Deletion (GDPR)
 */
export const deleteUser = async (id: string): Promise<void> => {
  await apiClient.delete(`${USER_API_PATH}/${id}`);
};

/**
 * Sync Status Response
 * Story 1.2.5: User Reconciliation
 */
export interface SyncStatusResponse {
  cognitoUserCount: number;
  databaseUserCount: number;
  missingInDatabase: number;
  orphanedInDatabase: number;
  missingCognitoIds: string[];
  inSync: boolean;
  message: string;
}

/**
 * Reconciliation Report Response
 * Story 1.2.5: User Reconciliation
 */
export interface ReconciliationReportResponse {
  orphanedUsersDeactivated: number;
  missingUsersCreated: number;
  durationMs: number;
  errors: string[];
  success: boolean;
  message: string;
}

/**
 * Check sync status between Cognito and Database
 * Story 1.2.5: User Reconciliation (Admin only)
 */
export const checkSyncStatus = async (): Promise<SyncStatusResponse> => {
  const response = await apiClient.get<SyncStatusResponse>(`${USER_API_PATH}/admin/sync-status`);
  return response.data;
};

/**
 * Trigger manual user reconciliation from Cognito to Database
 * Story 1.2.5: User Reconciliation (Admin only)
 */
export const reconcileUsers = async (): Promise<ReconciliationReportResponse> => {
  const response = await apiClient.post<ReconciliationReportResponse>(
    `${USER_API_PATH}/admin/reconcile`
  );
  return response.data;
};
