/**
 * User Account Types
 * Story 2.6: User Account Management Frontend
 */

import type { components } from '@/types/generated/user-api.types';

export interface User {
  id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  firstName: string;
  lastName: string;
  bio?: string;
  profilePictureUrl?: string;
  companyId?: string;
  company?: {
    id: string;
    name: string;
    displayName?: string;
    uid?: string;
  };
  roles: UserRole[];
  memberSince: string;
  /**
   * Story 12.11: moment the user accepted the ToS + Privacy Policy. `null`/absent =
   * consent not on record (the backend omits null fields — non_null inclusion), which
   * makes the onboarding gate fire. Set server-side only (write-once).
   */
  termsAcceptedAt?: string | null;
  /**
   * Story 10.32: additional email addresses the user has declared on their
   * profile. Always present (may be empty) after the user-profile endpoint
   * upgrade lands.
   */
  additionalEmails?: AdditionalEmail[];
}

/**
 * Story 10.32 — one additional email registered on a user profile. `receiveEventMail` (#1244):
 * this address gets a copy of event mails (only while confirmed).
 */
export type AdditionalEmail = components['schemas']['AdditionalEmail'];

export type UserRole = 'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE';

/**
 * The UI's view of the display and notification part of the wire `UserSettings` (user-api):
 * upper-case enums, nested channels. `userAccountApi` maps between the two. Story 4.9 merged
 * the former preferences into the one settings resource; the account page keeps two views.
 */
export interface UserPreferencesView {
  theme: 'LIGHT' | 'DARK' | 'AUTO';
  timezone: string;
  notificationChannels: {
    email: boolean;
    inApp: boolean;
    push: boolean;
  };
  notificationFrequency: 'IMMEDIATE' | 'DAILY_DIGEST' | 'WEEKLY_DIGEST';
  /**
   * Story 15.7 — how often the user is emailed about new questions in the Q&A of sessions where
   * they are speaker / co-speaker / moderator. LIVE = bundled digest during the session,
   * DAILY = at most once per 24h, OFF = never.
   */
  qnaNotificationFrequency: 'LIVE' | 'DAILY' | 'OFF';
}

/** The UI's view of the privacy part of the wire `UserSettings` (user-api), mapped in `userAccountApi`. */
export interface UserSettingsView {
  profileVisibility: 'PUBLIC' | 'MEMBERS_ONLY' | 'PRIVATE';
  showEmail: boolean;
  showCompany: boolean;
  showActivity: boolean;
  allowMessaging: boolean;
}

export interface UserActivity {
  id: string;
  type: string;
  description: string;
  timestamp: string;
}

export interface UserProfileData {
  user: User;
  preferences: UserPreferencesView;
  settings: UserSettingsView;
  activity: UserActivity[];
}

// Profile picture upload types (ADR-002); the request body is the generated
// ProfilePictureUploadRequest (user-api).
export interface LogoUploadResponse {
  uploadId: string;
  presignedUrl: string;
  expiresAt: string;
  s3Key: string;
}

export interface LogoConfirmRequest {
  fileExtension: string;
  checksum: string;
}

export interface LogoConfirmResponse {
  uploadId: string;
  status: 'CONFIRMED';
  cloudFrontUrl: string;
}
