/**
 * User Account Types
 * Story 2.6: User Account Management Frontend
 */

export interface User {
  id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  firstName: string;
  lastName: string;
  bio?: string;
  profilePictureUrl?: string;
  company?: {
    id: string;
    name: string;
    uid?: string;
  };
  roles: UserRole[];
  memberSince: string;
}

export type UserRole = 'ORGANIZER' | 'SPEAKER' | 'PARTNER' | 'ATTENDEE';

export interface UserPreferences {
  theme: 'LIGHT' | 'DARK' | 'AUTO';
  timezone: string;
  notificationChannels: {
    email: boolean;
    inApp: boolean;
    push: boolean;
  };
  notificationFrequency: 'IMMEDIATE' | 'DAILY_DIGEST' | 'WEEKLY_DIGEST';
}

export interface UserSettings {
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
  preferences: UserPreferences;
  settings: UserSettings;
  activity: UserActivity[];
}

// Logo upload types (ADR-002)
export interface LogoUploadRequest {
  fileName: string;
  fileSize: number;
  mimeType: string;
}

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
