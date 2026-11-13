/**
 * User API Service
 * Story 1.17: Type-safe API client wrappers for user endpoints
 *
 * Endpoints:
 * - GET   /api/v1/users/me?include=roles,settings
 * - GET   /api/v1/users/me/settings
 * - PATCH /api/v1/users/me/settings (Story 4.9: one settings resource, partial update)
 */

import apiClient from './apiClient';
import type { components as UserApiComponents } from '@/types/generated/user-api.types';

/** The wire user of `GET /users/me` (user-api). */
export type CurrentUserResponse = UserApiComponents['schemas']['UserResponse'];
/** The caller's settings: display, notifications and privacy in one object (Story 4.9). */
export type UserSettingsResponse = UserApiComponents['schemas']['UserSettings'];
/** A partial change of the caller's settings: only the fields present change. */
export type UserSettingsChanges = UserApiComponents['schemas']['UpdateUserSettingsRequest'];
/** The languages a user can store. */
export type UserLanguage = NonNullable<UserSettingsChanges['language']>;

/**
 * Fetch authenticated user profile with optional includes
 * @param includes - Optional fields to include (e.g., ['roles', 'settings'])
 * @returns User profile data
 */
export const getUserProfile = async (includes?: string[]): Promise<CurrentUserResponse> => {
  const params = includes && includes.length > 0 ? { include: includes.join(',') } : {};

  const response = await apiClient.get<CurrentUserResponse>('/users/me', { params });
  return response.data;
};

/**
 * The caller's settings (`GET /users/me/settings`). `skipAuthRedirect` for an optional read on any
 * page (the "What's new" dot): a 401 then fails the query instead of sending the user to /login.
 */
export const getUserSettings = async (
  options: { skipAuthRedirect?: boolean } = {}
): Promise<UserSettingsResponse> => {
  const response = await apiClient.get<UserSettingsResponse>('/users/me/settings', {
    skipAuthRedirect: options.skipAuthRedirect,
  });
  return response.data;
};

/**
 * Change some of the caller's settings (`PATCH /users/me/settings`): only the fields present
 * change, the server answers with the whole settings object.
 */
export const updateUserSettings = async (
  changes: UserSettingsChanges
): Promise<UserSettingsResponse> => {
  const response = await apiClient.patch<UserSettingsResponse>('/users/me/settings', changes);
  return response.data;
};

/**
 * Public projection of a user (CUMS `GET /public/users/{username}`).
 */
export type PublicUserResponse = UserApiComponents['schemas']['PublicUserResponse'];

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
