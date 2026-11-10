/**
 * userApi against the MSW harness (Story 1.17; Story 4.9 moved it off axios-mock-adapter and
 * onto the one settings resource).
 *
 * The real apiClient sends each call; the harness checks it against users-api and answers with
 * the spec example or a typed override. Settings are one resource: `PATCH /users/me/settings`
 * with only the fields that change; `/users/me/preferences` no longer exists.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import apiClient from './apiClient';
import {
  getPublicUser,
  getUserProfile,
  updateUserSettings,
  uploadProfilePictureFromUrl,
  type UserSettingsResponse,
} from './userApi';
import { aUser } from '@/test/fixtures';

const settings = (over: Partial<UserSettingsResponse> = {}): UserSettingsResponse => ({
  theme: 'auto',
  language: 'de',
  timezone: 'Europe/Zurich',
  emailNotifications: true,
  inAppNotifications: true,
  pushNotifications: false,
  notificationFrequency: 'immediate',
  qnaNotificationFrequency: 'live',
  quietHoursStart: null,
  quietHoursEnd: null,
  profileVisibility: 'public',
  showEmail: false,
  showCompany: true,
  showActivityHistory: true,
  allowMessaging: true,
  allowCalendarSync: false,
  twoFactorEnabled: false,
  ...over,
});

describe('userApi', () => {
  describe('getUserProfile', () => {
    it('reads /users/me without an include by default', async () => {
      const user = await getUserProfile();

      const [call] = api.calls('getCurrentUser');
      expect(call.query.has('include')).toBe(false);
      expect(user.id).toBeTruthy();
    });

    it('asks for roles and the merged settings with include', async () => {
      api.on('getCurrentUser', () =>
        aUser({
          id: 'anna.meier',
          email: 'anna.meier@example.com',
          firstName: 'Anna',
          lastName: 'Meier',
          roles: ['SPEAKER'],
          createdAt: '2025-01-15T10:00:00Z',
          updatedAt: '2025-01-15T10:00:00Z',
          settings: settings({ language: 'fr' }),
        })
      );

      const user = await getUserProfile(['roles', 'settings']);

      expect(api.calls('getCurrentUser')[0].query.get('include')).toBe('roles,settings');
      expect(user.settings?.language).toBe('fr');
    });

    it('rejects a 401', async () => {
      api.on('getCurrentUser', () =>
        HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
      );

      await expect(getUserProfile()).rejects.toMatchObject({ response: { status: 401 } });
    });
  });

  describe('updateUserSettings (PATCH /users/me/settings)', () => {
    it('sends only the fields that change and returns the whole object', async () => {
      api.on('updateUserSettings', ({ body }) => settings({ ...body }));

      const result = await updateUserSettings({ language: 'en' });

      const [call] = api.calls('updateUserSettings');
      expect(call.method).toBe('PATCH');
      expect(call.body).toEqual({ language: 'en' });
      expect(result.language).toBe('en');
      expect(result.timezone).toBe('Europe/Zurich');
    });

    it('stores theme and timezone in one call', async () => {
      api.on('updateUserSettings', ({ body }) => settings({ ...body }));

      const result = await updateUserSettings({ theme: 'dark', timezone: 'America/New_York' });

      expect(api.calls('updateUserSettings')[0].body).toEqual({
        theme: 'dark',
        timezone: 'America/New_York',
      });
      expect(result).toMatchObject({ theme: 'dark', timezone: 'America/New_York' });
    });

    it('rejects with the 400 CUMS answers for an invalid value', async () => {
      api.on('updateUserSettings', () =>
        HttpResponse.json({ message: 'Not an IANA time zone id' }, { status: 400 })
      );

      await expect(updateUserSettings({ timezone: 'Mars/Olympus' })).rejects.toMatchObject({
        response: { status: 400 },
      });
    });
  });

  describe('getPublicUser', () => {
    it('reads the public projection of a user without the login token', async () => {
      // The request interceptor strips the Skip-Auth marker before sending, so record it with an
      // interceptor that runs first (the last-added runs first).
      const skipAuth: string[] = [];
      const id = apiClient.interceptors.request.use((config) => {
        if (config.headers?.['Skip-Auth'] === 'true') skipAuth.push(config.url ?? '');
        return config;
      });
      api.on('getPublicUserByUsername', ({ params }) => ({
        username: params.username,
        firstName: 'Alice',
        lastName: 'Brünnimann',
        profilePictureUrl: null,
      }));

      try {
        await expect(getPublicUser('alice.bruennimann')).resolves.toMatchObject({
          username: 'alice.bruennimann',
        });
        expect(skipAuth).toEqual(['/public/users/alice.bruennimann']);
      } finally {
        apiClient.interceptors.request.eject(id);
      }
    });

    it('passes a 404 through', async () => {
      api.on('getPublicUserByUsername', () => HttpResponse.json({}, { status: 404 }));

      await expect(getPublicUser('ghost.user')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });

  describe('uploadProfilePictureFromUrl', () => {
    it('posts the URL and filename for the user', async () => {
      await uploadProfilePictureFromUrl(
        'jane.doe',
        'https://img.example.com/p.jpg',
        'Jane-portrait'
      );

      const [call] = api.calls('uploadProfilePictureFromUrl');
      expect(call.params).toEqual({ username: 'jane.doe' });
      expect(call.body).toEqual({
        url: 'https://img.example.com/p.jpg',
        filename: 'Jane-portrait',
      });
    });
  });
});
