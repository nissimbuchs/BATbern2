/**
 * User API Tests
 * Story 1.17: Tests for type-safe user API wrappers
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { getUserProfile, updateUserPreferences } from './userApi';
import { UserProfileResponse, UpdatePreferencesResponse } from '@/types/user';

describe('User API', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mockAxios.restore();
  });

  describe('getUserProfile', () => {
    it('should_fetchUserProfile_when_noIncludesProvided', async () => {
      const mockResponse: UserProfileResponse = {
        userId: 'user-123',
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        currentRole: 'organizer',
        availableRoles: ['organizer', 'speaker'],
        companyId: 'company-456',
        profilePhotoUrl: 'https://cdn.example.com/photo.jpg',
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      mockAxios.onGet('/api/v1/users/me').reply(200, mockResponse);

      const result = await getUserProfile();

      expect(result).toEqual(mockResponse);
    });

    it('should_fetchUserProfileWithIncludes_when_includesProvided', async () => {
      const mockResponse: UserProfileResponse = {
        userId: 'user-123',
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        currentRole: 'organizer',
        availableRoles: ['organizer', 'speaker'],
        companyId: 'company-456',
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      mockAxios
        .onGet('/api/v1/users/me', { params: { include: 'roles,preferences' } })
        .reply((config) => {
          expect(config.params?.include).toBe('roles,preferences');
          return [200, mockResponse];
        });

      const result = await getUserProfile(['roles', 'preferences']);

      expect(result).toEqual(mockResponse);
    });

    it('should_throwError_when_userNotFound', async () => {
      mockAxios.onGet('/api/v1/users/me').reply(404, { message: 'User not found' });

      await expect(getUserProfile()).rejects.toThrow();
    });

    it('should_throwError_when_unauthorized', async () => {
      mockAxios.onGet('/api/v1/users/me').reply(401, { message: 'Unauthorized' });

      await expect(getUserProfile()).rejects.toThrow();
    });
  });

  describe('updateUserPreferences', () => {
    it('should_updateLanguagePreference_when_validRequestProvided', async () => {
      const mockResponse: UpdatePreferencesResponse = {
        success: true,
        preferences: {
          language: 'en',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      mockAxios.onPut('/api/v1/users/me/preferences', { language: 'en' }).reply((config) => {
        expect(JSON.parse(config.data)).toEqual({ language: 'en' });
        return [200, mockResponse];
      });

      const result = await updateUserPreferences({ language: 'en' });

      expect(result).toEqual(mockResponse);
      expect(result.preferences.language).toBe('en');
    });

    it('should_updateNotificationPreferences_when_validRequestProvided', async () => {
      const request = {
        notifications: {
          emailEnabled: false,
          inAppEnabled: true,
        },
      };

      const mockResponse: UpdatePreferencesResponse = {
        success: true,
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: false,
            inAppEnabled: true,
          },
          theme: 'light',
        },
      };

      mockAxios.onPut('/api/v1/users/me/preferences', request).reply(200, mockResponse);

      const result = await updateUserPreferences(request);

      expect(result).toEqual(mockResponse);
      expect(result.preferences.notifications.emailEnabled).toBe(false);
    });

    it('should_updateThemePreference_when_validRequestProvided', async () => {
      const mockResponse: UpdatePreferencesResponse = {
        success: true,
        preferences: {
          language: 'de',
          notifications: {
            emailEnabled: true,
            inAppEnabled: true,
          },
          theme: 'dark',
        },
      };

      mockAxios.onPut('/api/v1/users/me/preferences', { theme: 'dark' }).reply(200, mockResponse);

      const result = await updateUserPreferences({ theme: 'dark' });

      expect(result).toEqual(mockResponse);
      expect(result.preferences.theme).toBe('dark');
    });

    it('should_throwError_when_invalidLanguageProvided', async () => {
      mockAxios.onPut('/api/v1/users/me/preferences').reply(400, {
        message: 'Invalid language code',
      });

      await expect(updateUserPreferences({ language: 'en' })).rejects.toThrow();
    });
  });
});
