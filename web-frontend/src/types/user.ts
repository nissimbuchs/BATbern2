/**
 * User profile types for BATbern platform
 * Based on Story 1.17 and 1.23 API specifications
 */

import { UserRole } from './auth';
import type { UiLanguage } from '@/i18n/languages';

export interface UserPreferencesData {
  language: UiLanguage;
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
