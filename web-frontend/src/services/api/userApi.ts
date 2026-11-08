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

  const response = await apiClient.get<UserProfileResponse>('/users/me', { params });
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
    '/users/me/preferences',
    preferences
  );
  return response.data;
};

/**
 * Public projection of a user (CUMS `GET /public/users/{username}`).
 */
export interface PublicUserResponse {
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  profilePictureUrl?: string | null;
}

/**
 * Fetch the public projection of a user.
 *
 * Skip-Auth: public endpoint, does NOT attach the Cognito token. A stale ID token would
 * otherwise produce a 401 and the apiClient interceptor's `/login` redirect.
 */
export const getPublicUser = async (username: string): Promise<PublicUserResponse> => {
  const response = await apiClient.get<PublicUserResponse>(`/public/users/${username}`, {
    headers: { 'Skip-Auth': 'true' },
  });
  return response.data;
};

/**
 * Admin: the server fetches an image from `url`, stores it as the user's profile picture and
 * returns its CDN URL.
 */
export const uploadProfilePictureFromUrl = async (
  username: string,
  url: string,
  filename: string
): Promise<{ profilePictureUrl?: string }> => {
  const response = await apiClient.post<{ profilePictureUrl?: string }>(
    `/users/${encodeURIComponent(username)}/profile-picture/upload-from-url`,
    { url, filename }
  );
  return response.data;
};
