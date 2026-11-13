/**
 * User Account API Service
 * Story 2.6: User Account Management Frontend
 */

import apiClient from './apiClient';
import type {
  AdditionalEmail,
  User,
  UserPreferencesView,
  UserSettingsView,
  UserActivity,
} from '@/types/userAccount.types';
import type { components } from '@/types/generated/user-api.types';
import { updateUserSettings as patchUserSettings, type UserSettingsChanges } from './userApi';
import { uploadTarget } from '@/services/uploadService';

const USER_API_PATH = '/users';

type WireSettings = components['schemas']['UserSettings'];

/**
 * Get current user profile with consolidated data
 * Uses ?include query parameter for resource expansion. Story 4.9: the wire carries one
 * `settings` object (display, notifications, privacy); the account page shows it as two views.
 */
export const getUserProfile = async (
  include: string[] = ['company', 'roles', 'settings', 'activity']
): Promise<{
  user: User;
  preferences?: UserPreferencesView;
  settings?: UserSettingsView;
  activity?: UserActivity[];
}> => {
  const includeParam = include.join(',');
  const response = await apiClient.get(`${USER_API_PATH}/me?include=${includeParam}`);
  const data = response.data;

  // Backend returns flat structure with user data at root level
  // Transform to expected structure: { user: {...}, preferences: {...}, settings: {...} }
  const { settings, activity, ...userData } = data;

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
            displayName: userData.company.displayName,
            uid: userData.company.uid,
          }
        : undefined,
    },
    preferences: settings ? toPreferencesView(settings) : undefined,
    settings: settings ? toSettingsView(settings) : undefined,
    activity: activity || [],
  };
};

/** The display and notification part of the wire settings, as the account page shows it. */
const toPreferencesView = (settings: WireSettings): UserPreferencesView => ({
  theme: mapTheme(settings.theme),
  timezone: settings.timezone || 'Europe/Zurich',
  notificationChannels: {
    email: settings.emailNotifications,
    inApp: settings.inAppNotifications,
    push: settings.pushNotifications,
  },
  notificationFrequency: mapNotificationFrequency(settings.notificationFrequency),
  qnaNotificationFrequency: mapQnaNotificationFrequency(settings.qnaNotificationFrequency),
});

/** The privacy part of the wire settings, as the account page shows it. */
const toSettingsView = (settings: WireSettings): UserSettingsView => ({
  profileVisibility: mapProfileVisibility(settings.profileVisibility),
  showEmail: settings.showEmail,
  showCompany: settings.showCompany,
  showActivity: settings.showActivityHistory,
  allowMessaging: settings.allowMessaging,
});

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
  return visibility.toUpperCase() as 'PUBLIC' | 'MEMBERS_ONLY' | 'PRIVATE';
};

// Story 15.7 — Q&A notification cadence (defaults to LIVE when absent, matching the backend default)
const mapQnaNotificationFrequency = (freq?: string): 'LIVE' | 'DAILY' | 'OFF' => {
  if (!freq) return 'LIVE';
  return freq.toUpperCase() as 'LIVE' | 'DAILY' | 'OFF';
};

/**
 * Update a user's profile (firstName, lastName, email, bio, companyId, termsAccepted).
 * Story 4.10: one `PATCH /users/{username}` for self-service and organizers; only the fields
 * given change. Pass the username of the hydrated user (`/users/me`), never one from the token.
 * `termsAccepted` is accepted only on the caller's own profile (403 otherwise).
 */
export const updateUserProfile = async (
  username: string,
  updates: Partial<User> & {
    /** Story 12.11: write-once ToS/Privacy consent — server stamps the timestamp. */
    termsAccepted?: boolean;
  }
): Promise<User> => {
  // Backend only accepts: firstName, lastName, email, bio, companyId, termsAccepted
  // (UpdateUserRequest); other fields of the view model are not sent.
  const allowedFields: Partial<User> & { termsAccepted?: boolean } = {};

  if (updates.firstName !== undefined) allowedFields.firstName = updates.firstName;
  if (updates.lastName !== undefined) allowedFields.lastName = updates.lastName;
  if (updates.email !== undefined) allowedFields.email = updates.email;
  if (updates.bio !== undefined) allowedFields.bio = updates.bio;
  if (updates.companyId !== undefined) allowedFields.companyId = updates.companyId;
  // Story 12.11: consent flag — without this allowlist entry the field would be
  // silently dropped and consent could never be recorded.
  if (updates.termsAccepted !== undefined) allowedFields.termsAccepted = updates.termsAccepted;

  const response = await apiClient.patch(
    `${USER_API_PATH}/${encodeURIComponent(username)}`,
    allowedFields
  );

  // Transform response back to expected format
  const data = response.data;
  // Destructure to exclude preferences, settings, activity from userData
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { settings, activity, ...userData } = data;

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
 * Update the display and notification settings (theme, timezone, notifications).
 * Story 4.9: one `PATCH /users/me/settings`; only the fields given change. The timezone is part
 * of that resource now and is stored (it used to go to `/users/me/preferences`, which dropped it).
 */
export const updateUserPreferences = async (
  preferences: Partial<UserPreferencesView>
): Promise<UserPreferencesView> => {
  const changes: UserSettingsChanges = {
    theme: lower(preferences.theme),
    timezone: preferences.timezone,
    emailNotifications: preferences.notificationChannels?.email,
    inAppNotifications: preferences.notificationChannels?.inApp,
    pushNotifications: preferences.notificationChannels?.push,
    notificationFrequency: lower(preferences.notificationFrequency),
    qnaNotificationFrequency: lower(preferences.qnaNotificationFrequency),
  };
  return toPreferencesView(await patchUserSettings(changes));
};

/**
 * Update the privacy settings. Story 4.9: the same `PATCH /users/me/settings`.
 */
export const updateUserSettings = async (
  settings: Partial<UserSettingsView>
): Promise<UserSettingsView> => {
  const changes: UserSettingsChanges = {
    profileVisibility: lower(settings.profileVisibility),
    showEmail: settings.showEmail,
    showCompany: settings.showCompany,
    showActivityHistory: settings.showActivity,
    allowMessaging: settings.allowMessaging,
  };
  return toSettingsView(await patchUserSettings(changes));
};

/** The UI's upper-case enum value as the wire's lower-case one; undefined stays undefined. */
const lower = <T extends string>(value: T | undefined): Lowercase<T> | undefined =>
  value === undefined ? undefined : (value.toLowerCase() as Lowercase<T>);

// Profile picture: the upload contract, purpose `profile-picture` with target `username`
// (Story 17.11a); the user themself or an organizer. The removal stays on `/users/{username}/picture`.

/**
 * Remove a user's profile picture (the user themself or an organizer).
 */
export const removeProfilePicture = async (username: string): Promise<void> => {
  await apiClient.delete(`${USER_API_PATH}/${encodeURIComponent(username)}/picture`);
};

/**
 * PUT the file straight to S3 with the presigned URL and its signed headers. XMLHttpRequest (not
 * the API client) for the progress events and so that no auth header is sent to S3.
 */
export const uploadToS3 = async (
  presignedUrl: string,
  file: File,
  onProgress?: (progress: number) => void,
  requiredHeaders: Record<string, string> = {}
): Promise<void> => {
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
    const headers = { 'Content-Type': file.type, ...requiredHeaders };
    Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
    xhr.send(file);
  });
};

/**
 * Upload a user's profile picture: reserve (`POST /uploads/profile-picture` with the target),
 * PUT to S3, confirm with the target, which attaches it. Returns the picture's CDN URL.
 */
export const uploadProfilePicture = async (
  username: string,
  file: File,
  onProgress?: (progress: number) => void
): Promise<string> => {
  const target = uploadTarget('profile-picture', { username });
  const slot = await target.presign({
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type,
  });
  await uploadToS3(slot.uploadUrl, file, onProgress, slot.requiredHeaders);
  const confirmed = await target.confirm(slot.fileId, {
    fileId: slot.fileId,
    fileExtension: file.name.split('.').pop() ?? '',
    checksum: '',
  });
  if (!confirmed?.fileUrl) {
    throw new Error('The profile picture was not attached');
  }
  return confirmed.fileUrl;
};

// =====================================================================
// Story 10.32 — Additional emails per user profile
// =====================================================================

export type { AdditionalEmail };

export interface AddAdditionalEmailPayload {
  email: string;
  label?: string;
}

export interface AdditionalEmailErrorResponse {
  errorCode?: string;
  message?: string;
}

/**
 * Story 10.32 — register an additional email on the caller's profile.
 * Throws on non-2xx; surface the error code via the Axios error shape.
 */
export const addAdditionalEmail = async (
  payload: AddAdditionalEmailPayload
): Promise<AdditionalEmail> => {
  const response = await apiClient.post<AdditionalEmail>(
    `${USER_API_PATH}/me/additional-emails`,
    payload
  );
  return response.data;
};

/**
 * Story 10.32 — remove an additional email from the caller's profile. Story 4.10d: by its `id`
 * (`AdditionalEmail.id`), the address never goes in the path.
 */
export const deleteAdditionalEmail = async (emailId: string): Promise<void> => {
  await apiClient.delete(`${USER_API_PATH}/me/additional-emails/${emailId}`);
};

/**
 * #1244 — switch the copy of event mails for one of the caller's additional addresses.
 * 409 `NOT_VERIFIED` when switching it on for an unconfirmed address.
 */
export const updateAdditionalEmail = async (
  emailId: string,
  payload: { receiveEventMail: boolean }
): Promise<AdditionalEmail> => {
  const response = await apiClient.patch<AdditionalEmail>(
    `${USER_API_PATH}/me/additional-emails/${emailId}`,
    payload
  );
  return response.data;
};

// =====================================================================
// Additional-email verification (v2)
// =====================================================================

export interface AdditionalEmailVerificationCheck {
  /** Masked email (e.g. in***@example.com). */
  email: string;
  verified: boolean;
}

export interface AdditionalEmailVerificationConfirm {
  email: string;
  verified: boolean;
  alreadyVerified: boolean;
}

/**
 * Additional-email verification (v2) — GET-check a token (no mutation). Public:
 * the token IS the credential. Throws on invalid/expired (surface errorCode via
 * the Axios error shape).
 */
export const checkAdditionalEmailVerification = async (
  token: string
): Promise<AdditionalEmailVerificationCheck> => {
  const response = await apiClient.get<AdditionalEmailVerificationCheck>(
    `${USER_API_PATH}/additional-emails/verify`,
    { params: { token } }
  );
  return response.data;
};

/**
 * Additional-email verification (v2) — POST-confirm a token (mutates). Public.
 */
export const confirmAdditionalEmailVerification = async (
  token: string
): Promise<AdditionalEmailVerificationConfirm> => {
  const response = await apiClient.post<AdditionalEmailVerificationConfirm>(
    `${USER_API_PATH}/additional-emails/verify`,
    { token }
  );
  return response.data;
};

/**
 * Additional-email verification (v2) — resend the verification email for one of
 * the caller's own (unverified) additional emails. 204 on success.
 */
export const resendAdditionalEmailVerification = async (emailId: string): Promise<void> => {
  await apiClient.post(`${USER_API_PATH}/me/additional-emails/${emailId}/resend-verification`);
};

// =====================================================================
// #1183 D — claim an earlier profile under the caller's name
// =====================================================================

export type ProfileClaimCandidate = components['schemas']['ProfileClaimCandidate'];
export type ProfileClaimConfirmResponse = components['schemas']['ProfileClaimConfirmResponse'];

/**
 * Earlier profiles without a login under the caller's name (masked address only). An optional
 * read (the dashboard offer, the "What's new" merge entry, also in the header on public pages):
 * a 401 fails the query (nothing shown) instead of sending the user to /login.
 */
export const listProfileClaimCandidates = async (): Promise<ProfileClaimCandidate[]> => {
  const response = await apiClient.get<ProfileClaimCandidate[]>(
    `${USER_API_PATH}/me/profile-claims`,
    { skipAuthRedirect: true }
  );
  return response.data;
};

/** Sends a confirmation link to the earlier profile's address. 202. */
export const requestProfileClaim = async (candidateId: string): Promise<void> => {
  await apiClient.post(`${USER_API_PATH}/me/profile-claims`, { candidateId });
};

/** Confirms the link from the mail while logged in as the claimer; merges the earlier profile. */
export const confirmProfileClaim = async (token: string): Promise<ProfileClaimConfirmResponse> => {
  const response = await apiClient.post<ProfileClaimConfirmResponse>(
    `${USER_API_PATH}/me/profile-claims/confirm`,
    { token }
  );
  return response.data;
};

/**
 * #1237: no access to the earlier profile's address any more. Asks the organizers to merge it; they
 * decide in the Mail Center. 409 MERGE_REQUEST_OPEN / MERGE_REQUEST_DECLINED.
 */
export const requestProfileMerge = async (candidateId: string, note?: string): Promise<string> => {
  const response = await apiClient.post<{ requestId: string }>(
    `${USER_API_PATH}/me/profile-claims/merge-requests`,
    { candidateId, note: note?.trim() ? note.trim() : undefined }
  );
  return response.data.requestId;
};

/**
 * #1244 step 2: a confirmed additional address becomes the primary (login) address; the old primary
 * stays as a confirmed additional address under a new `id` (re-read `GET /users/me`).
 * 409 `NOT_VERIFIED` / `EMAIL_HAS_OTHER_LOGIN`.
 */
export const makeAdditionalEmailPrimary = async (emailId: string): Promise<void> => {
  await apiClient.post(`${USER_API_PATH}/me/additional-emails/${emailId}/make-primary`);
};

/**
 * #1244 step 3: the address belongs to another account of the caller with its own login. A link
 * goes to that address; confirming it (`/claim-profile?token=`) merges the accounts. 404 when the
 * address is no other login.
 */
export const requestAccountMerge = async (email: string): Promise<void> => {
  await apiClient.post(`${USER_API_PATH}/me/account-merges`, { email });
};

export type CompanyLogoRequestView = components['schemas']['CompanyLogoRequestView'];

/**
 * #1246: asks the organizers for a new logo of the caller's company. `logoUploadId` comes from a
 * confirmed upload (useFileUpload). 409 `NO_COMPANY` / `LOGO_REQUEST_OPEN` / `LOGO_NOT_READY`.
 */
export const createCompanyLogoRequest = async (
  logoUploadId: string,
  note?: string
): Promise<CompanyLogoRequestView> => {
  const response = await apiClient.post<CompanyLogoRequestView>(
    `${USER_API_PATH}/me/company-logo-requests`,
    { logoUploadId, note: note?.trim() ? note.trim() : undefined }
  );
  return response.data;
};

/** #1246: the caller's open logo request for their company, or null. */
export const getMyOpenCompanyLogoRequest = async (): Promise<CompanyLogoRequestView | null> => {
  const response = await apiClient.get<CompanyLogoRequestView>(
    `${USER_API_PATH}/me/company-logo-requests/open`
  );
  return response.status === 204 || !response.data ? null : response.data;
};
