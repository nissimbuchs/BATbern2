/**
 * User Account API Service
 * Story 2.6: User Account Management Frontend
 */

import apiClient from './apiClient';
import type {
  User,
  UserPreferences,
  UserSettings,
  UserActivity,
  LogoUploadRequest,
  LogoUploadResponse,
  LogoConfirmRequest,
  LogoConfirmResponse,
} from '@/types/userAccount.types';

const USER_API_PATH = '/users';

/**
 * Get current user profile with consolidated data
 * Uses ?include query parameter for resource expansion
 */
export const getUserProfile = async (
  include: string[] = ['company', 'roles', 'preferences', 'settings', 'activity']
): Promise<{
  user: User;
  preferences?: UserPreferences;
  settings?: UserSettings;
  activity?: UserActivity[];
}> => {
  const includeParam = include.join(',');
  const response = await apiClient.get(`${USER_API_PATH}/me?include=${includeParam}`);
  const data = response.data;

  // Backend returns flat structure with user data at root level
  // Transform to expected structure: { user: {...}, preferences: {...}, settings: {...} }
  const { preferences, settings, activity, ...userData } = data;

  return {
    user: {
      ...userData,
      username: userData.id, // Backend uses 'id' as username
      memberSince: userData.createdAt,
      // Map backend fields to frontend types
      company: userData.company
        ? {
            id: userData.company.id || userData.companyId,
            name: userData.company.name,
            uid: userData.company.uid,
          }
        : undefined,
    },
    preferences: preferences
      ? {
          theme: mapTheme(preferences.theme),
          timezone: preferences.timezone || 'Europe/Zurich',
          notificationChannels: {
            email: preferences.emailNotifications ?? true,
            inApp: preferences.inAppNotifications ?? true,
            push: preferences.pushNotifications ?? false,
          },
          notificationFrequency: mapNotificationFrequency(preferences.notificationFrequency),
        }
      : undefined,
    settings: settings
      ? {
          profileVisibility: mapProfileVisibility(settings.profileVisibility),
          showEmail: settings.showEmail ?? false,
          showCompany: settings.showCompany ?? true,
          showActivity: settings.showActivityHistory ?? true,
          allowMessaging: settings.allowMessaging ?? true,
        }
      : undefined,
    activity: activity || [],
  };
};

// Helper functions to map backend enums to frontend enums
const mapTheme = (theme?: string): 'LIGHT' | 'DARK' | 'AUTO' => {
  if (!theme) return 'LIGHT';
  return theme.toUpperCase() as 'LIGHT' | 'DARK' | 'AUTO';
};

const mapNotificationFrequency = (
  freq?: string
): 'IMMEDIATE' | 'DAILY_DIGEST' | 'WEEKLY_DIGEST' => {
  if (!freq) return 'IMMEDIATE';
  return freq.toUpperCase() as 'IMMEDIATE' | 'DAILY_DIGEST' | 'WEEKLY_DIGEST';
};

const mapProfileVisibility = (visibility?: string): 'PUBLIC' | 'MEMBERS_ONLY' | 'PRIVATE' => {
  if (!visibility) return 'MEMBERS_ONLY';
  const mapped = visibility.toUpperCase().replace('_', '_');
  return mapped as 'PUBLIC' | 'MEMBERS_ONLY' | 'PRIVATE';
};

/**
 * Update user profile (firstName, lastName, email, bio, companyId)
 * Only sends fields that the backend accepts per UpdateUserRequest schema
 * Note: Backend implements PUT, not PATCH
 */
export const updateUserProfile = async (updates: Partial<User>): Promise<User> => {
  // Backend only accepts: firstName, lastName, email, bio, companyId
  // Filter out any other fields to avoid 500 errors
  const allowedFields: Partial<User> = {};

  if (updates.firstName !== undefined) allowedFields.firstName = updates.firstName;
  if (updates.lastName !== undefined) allowedFields.lastName = updates.lastName;
  if (updates.email !== undefined) allowedFields.email = updates.email;
  if (updates.bio !== undefined) allowedFields.bio = updates.bio;
  if (updates.companyId !== undefined) allowedFields.companyId = updates.companyId;

  const response = await apiClient.put(`${USER_API_PATH}/me`, allowedFields);

  // Transform response back to expected format
  const data = response.data;
  // Destructure to exclude preferences, settings, activity from userData
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { preferences, settings, activity, ...userData } = data;

  return {
    ...userData,
    username: userData.id,
    memberSince: userData.createdAt,
    company: userData.company
      ? {
          id: userData.company.id || userData.companyId,
          name: userData.company.name,
          uid: userData.company.uid,
        }
      : undefined,
  };
};

/**
 * Update user preferences (theme, timezone, notifications)
 * Converts frontend enums (uppercase) to backend format (lowercase)
 */
export const updateUserPreferences = async (
  preferences: Partial<UserPreferences>
): Promise<UserPreferences> => {
  // Transform frontend preferences to backend format (lowercase enums)
  const backendPreferences = {
    theme: preferences.theme?.toLowerCase(),
    language: preferences.timezone ? 'de' : undefined, // TODO: Get from i18n
    timezone: preferences.timezone,
    emailNotifications: preferences.notificationChannels?.email,
    inAppNotifications: preferences.notificationChannels?.inApp,
    pushNotifications: preferences.notificationChannels?.push,
    notificationFrequency: preferences.notificationFrequency?.toLowerCase().replace('_', '_'),
    quietHoursStart: undefined, // TODO: Add to frontend type
    quietHoursEnd: undefined, // TODO: Add to frontend type
  };

  const response = await apiClient.put(`${USER_API_PATH}/me/preferences`, backendPreferences);

  // Transform backend response back to frontend format (uppercase enums)
  return {
    theme: mapTheme(response.data.theme),
    timezone: response.data.timezone || 'Europe/Zurich',
    notificationChannels: {
      email: response.data.emailNotifications ?? true,
      inApp: response.data.inAppNotifications ?? true,
      push: response.data.pushNotifications ?? false,
    },
    notificationFrequency: mapNotificationFrequency(response.data.notificationFrequency),
  };
};

/**
 * Update user settings (privacy, visibility)
 * Converts frontend enums (uppercase) to backend format (lowercase)
 */
export const updateUserSettings = async (
  settings: Partial<UserSettings>
): Promise<UserSettings> => {
  // Transform frontend settings to backend format (lowercase enums, snake_case)
  const backendSettings = {
    profileVisibility: settings.profileVisibility?.toLowerCase().replace('_', '_'),
    showEmail: settings.showEmail,
    showCompany: settings.showCompany,
    showActivityHistory: settings.showActivity,
    allowMessaging: settings.allowMessaging,
    allowCalendarSync: undefined, // TODO: Add to frontend type
    timezone: 'Europe/Zurich', // TODO: Get from preferences
    twoFactorEnabled: false, // TODO: Add to frontend type
  };

  const response = await apiClient.put(`${USER_API_PATH}/me/settings`, backendSettings);

  // Transform backend response back to frontend format (uppercase enums)
  return {
    profileVisibility: mapProfileVisibility(response.data.profileVisibility),
    showEmail: response.data.showEmail ?? false,
    showCompany: response.data.showCompany ?? true,
    showActivity: response.data.showActivityHistory ?? true,
    allowMessaging: response.data.allowMessaging ?? true,
  };
};

/**
 * Get user activity history
 */
export const getUserActivity = async (limit?: number): Promise<UserActivity[]> => {
  const params = limit ? `?limit=${limit}` : '';
  const response = await apiClient.get(`${USER_API_PATH}/me/activity${params}`);
  return response.data;
};

/**
 * Remove profile picture
 */
export const removeProfilePicture = async (): Promise<void> => {
  await apiClient.delete(`${USER_API_PATH}/me/picture`);
};

// Profile Picture Upload Service (ADR-002 3-phase pattern)

/**
 * Phase 1: Request presigned URL for S3 upload (profile picture)
 */
export const requestPresignedUrl = async (
  request: LogoUploadRequest
): Promise<LogoUploadResponse> => {
  // For profile pictures, use /users/me/picture/presigned-url
  const response = await apiClient.post(`${USER_API_PATH}/me/picture/presigned-url`, request);
  const data = response.data;

  // Map backend response fields to frontend type
  // Backend returns: uploadUrl, fileId, s3Key, fileExtension, expiresInMinutes
  // Frontend expects: presignedUrl, uploadId, s3Key, expiresAt
  return {
    uploadId: data.fileId,
    presignedUrl: data.uploadUrl,
    s3Key: data.s3Key,
    expiresAt: new Date(Date.now() + (data.expiresInMinutes || 15) * 60 * 1000).toISOString(),
  };
};

/**
 * Phase 2: Upload file directly to S3 using presigned URL
 */
export const uploadToS3 = async (
  presignedUrl: string,
  file: File,
  onProgress?: (progress: number) => void
): Promise<void> => {
  // Use native fetch for S3 upload to avoid adding auth headers
  const xhr = new XMLHttpRequest();

  return new Promise((resolve, reject) => {
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        const progress = Math.round((event.loaded / event.total) * 100);
        onProgress(progress);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status === 200) {
        resolve();
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Upload failed'));
    });

    xhr.addEventListener('abort', () => {
      reject(new Error('Upload aborted'));
    });

    xhr.open('PUT', presignedUrl);
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.send(file);
  });
};

/**
 * Phase 3: Confirm upload completion (profile picture)
 */
export const confirmUpload = async (
  uploadId: string,
  request: LogoConfirmRequest
): Promise<LogoConfirmResponse> => {
  // For profile pictures, backend expects: fileId, fileExtension, checksum (optional)
  const response = await apiClient.post(`${USER_API_PATH}/me/picture/confirm`, {
    fileId: uploadId, // Backend uses 'fileId' not 'uploadId'
    fileExtension: request.fileExtension,
    checksum: request.checksum,
  });
  const data = response.data;

  // Map backend response to frontend type
  // Backend returns: profilePictureUrl
  // Frontend expects: cloudFrontUrl
  return {
    uploadId: uploadId,
    status: 'CONFIRMED',
    cloudFrontUrl: data.profilePictureUrl,
  };
};

/**
 * Associate uploaded picture with user profile
 * Note: This is handled automatically by the confirm endpoint
 * Backend associates the picture during confirmation phase
 */
export const associateProfilePicture = async (uploadId: string): Promise<User> => {
  // Backend automatically associates the picture during confirm phase
  // This function is kept for backward compatibility but may not be needed
  const response = await apiClient.put(`${USER_API_PATH}/me`, {
    profilePictureFileId: uploadId,
  });
  return response.data;
};

/**
 * Complete 3-phase upload workflow
 */
export const uploadProfilePicture = async (
  file: File,
  onProgress?: (progress: number) => void
): Promise<string> => {
  // Phase 1: Request presigned URL
  const uploadResponse = await requestPresignedUrl({
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type,
  });

  // Phase 2: Upload to S3
  await uploadToS3(uploadResponse.presignedUrl, file, onProgress);

  // Phase 3: Confirm upload
  // Backend automatically associates the picture with user during confirmation
  const fileExtension = file.name.split('.').pop() || 'jpg';
  const confirmResponse = await confirmUpload(uploadResponse.uploadId, {
    fileExtension,
    checksum: '', // TODO: Implement SHA256 checksum
  });

  return confirmResponse.cloudFrontUrl;
};

// Admin endpoints for uploading profile pictures for other users

/**
 * Phase 1 (Admin): Request presigned URL for S3 upload for a specific user
 */
export const requestPresignedUrlForUser = async (
  username: string,
  request: LogoUploadRequest
): Promise<LogoUploadResponse> => {
  const response = await apiClient.post(
    `${USER_API_PATH}/${username}/picture/presigned-url`,
    request
  );
  const data = response.data;

  return {
    uploadId: data.fileId,
    presignedUrl: data.uploadUrl,
    s3Key: data.s3Key,
    expiresAt: new Date(Date.now() + (data.expiresInMinutes || 15) * 60 * 1000).toISOString(),
  };
};

/**
 * Phase 3 (Admin): Confirm upload completion for a specific user
 */
export const confirmUploadForUser = async (
  username: string,
  uploadId: string,
  request: LogoConfirmRequest
): Promise<LogoConfirmResponse> => {
  const response = await apiClient.post(`${USER_API_PATH}/${username}/picture/confirm`, {
    fileId: uploadId,
    fileExtension: request.fileExtension,
    checksum: request.checksum,
  });
  const data = response.data;

  return {
    uploadId: uploadId,
    status: 'CONFIRMED',
    cloudFrontUrl: data.profilePictureUrl,
  };
};

/**
 * Complete 3-phase upload workflow for a specific user (Admin)
 */
export const uploadProfilePictureForUser = async (
  username: string,
  file: File,
  onProgress?: (progress: number) => void
): Promise<string> => {
  // Phase 1: Request presigned URL
  const uploadResponse = await requestPresignedUrlForUser(username, {
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type,
  });

  // Phase 2: Upload to S3
  await uploadToS3(uploadResponse.presignedUrl, file, onProgress);

  // Phase 3: Confirm upload
  const fileExtension = file.name.split('.').pop() || 'jpg';
  const confirmResponse = await confirmUploadForUser(username, uploadResponse.uploadId, {
    fileExtension,
    checksum: '', // TODO: Implement SHA256 checksum
  });

  return confirmResponse.cloudFrontUrl;
};
