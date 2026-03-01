/**
 * User profile types for BATbern platform
 * Based on Story 1.17 and 1.23 API specifications
 */

import { UserRole } from './auth';

export interface UserPreferencesData {
  language: 'de' | 'en' | 'fr' | 'it' | 'rm' | 'es' | 'fi' | 'nl' | 'ja';
  notifications: {
    emailEnabled: boolean;
    inAppEnabled: boolean;
  };
  theme: 'light' | 'dark';
}

export interface UserProfile {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  currentRole: UserRole;
  availableRoles: UserRole[];
  companyId?: string;
  profilePhotoUrl?: string;
  preferences: UserPreferencesData;
}

export interface UserProfileResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  currentRole: UserRole;
  availableRoles: UserRole[];
  companyId?: string;
  profilePhotoUrl?: string;
  preferences: UserPreferencesData;
}

export interface UpdatePreferencesRequest {
  language?: 'de' | 'en' | 'fr' | 'it' | 'rm' | 'es' | 'fi' | 'nl' | 'ja';
  notifications?: {
    emailEnabled?: boolean;
    inAppEnabled?: boolean;
  };
  theme?: 'light' | 'dark';
}

export interface UpdatePreferencesResponse {
  success: boolean;
  preferences: UserPreferencesData;
}
