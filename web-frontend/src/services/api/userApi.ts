/**
 * User API Service
 * Story 1.17: Type-safe API client wrappers for user endpoints
 *
 * Endpoints:
 * - GET /api/v1/users/me?include=roles,preferences
 * - PUT /api/v1/users/me/preferences
 */

import apiClient from './apiClient';
import {
  UserProfileResponse,
  UpdatePreferencesRequest,
  UpdatePreferencesResponse,
} from '@/types/user';

/**
 * Fetch authenticated user profile with optional includes
 * @param includes - Optional fields to include (e.g., ['roles', 'preferences'])
 * @returns User profile data
 */
export const getUserProfile = async (includes?: string[]): Promise<UserProfileResponse> => {
  const params = includes && includes.length > 0 ? { include: includes.join(',') } : {};

  const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me', { params });
  return response.data;
};

/**
 * Update user preferences (partial update supported)
 * @param preferences - Preferences to update
 * @returns Updated preferences
 */
export const updateUserPreferences = async (
  preferences: UpdatePreferencesRequest
): Promise<UpdatePreferencesResponse> => {
  const response = await apiClient.put<UpdatePreferencesResponse>(
    '/api/v1/users/me/preferences',
    preferences
  );
  return response.data;
};
