/**
 * userAccountApi against the MSW harness (Story 2.6; Story 4.9 moved it off the apiClient mock
 * and onto the one settings resource).
 *
 * The real apiClient sends each call; the harness checks it against users-api and answers with
 * the spec example or a typed override. The account page shows the wire `settings` object as two
 * views (display and notifications, privacy); both are saved with `PATCH /users/me/settings`,
 * which changes only the fields sent.
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { api } from '@/test/msw/api';
import type { UserSettingsResponse } from './userApi';
import {
  getUserProfile,
  updateUserProfile,
  updateUserPreferences,
  updateUserSettings,
  removeProfilePicture,
  requestPresignedUrl,
  confirmUpload,
  requestPresignedUrlForUser,
  confirmUploadForUser,
} from './userAccountApi';
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

const user = aUser({
  id: 'hong.an',
  email: 'hong.an@example.com',
  firstName: 'Hông',
  lastName: 'Ân',
  roles: ['SPEAKER' as const],
  active: true,
  createdAt: '2023-06-15T10:00:00Z',
  updatedAt: '2025-01-15T10:00:00Z',
  additionalEmails: [],
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('getUserProfile', () => {
  it('asks for company, roles, settings and activity by default', async () => {
    await getUserProfile();

    expect(api.calls('getCurrentUser')[0].query.get('include')).toBe(
      'company,roles,settings,activity'
    );
  });

  it('maps id to username and createdAt to memberSince, and the company', async () => {
    api.on('getCurrentUser', () => ({
      ...user,
      companyId: 'AcmeAG',
      company: {
        id: 'AcmeAG',
        name: 'AcmeAG',
        displayName: 'Acme AG',
        industry: null,
        website: null,
      },
    }));

    const result = await getUserProfile(['company']);

    expect(result.user.username).toBe('hong.an');
    expect(result.user.memberSince).toBe('2023-06-15T10:00:00Z');
    expect(result.user.company).toMatchObject({ id: 'AcmeAG', name: 'AcmeAG' });
    expect(api.calls('getCurrentUser')[0].query.get('include')).toBe('company');
  });

  it('splits the one settings object into the two views of the account page', async () => {
    api.on('getCurrentUser', () => ({
      ...user,
      settings: settings({
        theme: 'dark',
        timezone: 'America/New_York',
        notificationFrequency: 'daily_digest',
        qnaNotificationFrequency: 'off',
        emailNotifications: false,
        profileVisibility: 'members_only',
        showActivityHistory: false,
      }),
    }));

    const result = await getUserProfile();

    expect(result.preferences).toEqual({
      theme: 'DARK',
      timezone: 'America/New_York',
      notificationChannels: { email: false, inApp: true, push: false },
      notificationFrequency: 'DAILY_DIGEST',
      qnaNotificationFrequency: 'OFF',
    });
    expect(result.settings).toEqual({
      profileVisibility: 'MEMBERS_ONLY',
      showEmail: false,
      showCompany: true,
      showActivity: false,
      allowMessaging: true,
    });
  });

  it('leaves both views undefined without settings', async () => {
    api.on('getCurrentUser', () => ({ ...user }));

    const result = await getUserProfile(['company']);

    expect(result.preferences).toBeUndefined();
    expect(result.settings).toBeUndefined();
    expect(result.activity).toEqual([]);
  });
});

describe('updateUserProfile', () => {
  it('PATCHes /users/me with only the allowed, defined fields', async () => {
    api.on('patchCurrentUser', ({ body }) => ({
      ...user,
      firstName: body?.firstName ?? user.firstName,
    }));

    const result = await updateUserProfile({
      firstName: 'Hông',
      bio: 'Architektin',
      username: 'ignored' as never,
      termsAccepted: true,
    });

    expect(api.calls('patchCurrentUser')[0].body).toEqual({
      firstName: 'Hông',
      bio: 'Architektin',
      termsAccepted: true,
    });
    expect(result.username).toBe('hong.an');
  });
});

describe('updateUserPreferences (PATCH /users/me/settings)', () => {
  it('sends theme and timezone with lower-case enums, so the timezone is stored', async () => {
    api.on('updateUserSettings', ({ body }) => settings({ ...body }));

    const result = await updateUserPreferences({ theme: 'DARK', timezone: 'Europe/Berlin' });

    const [call] = api.calls('updateUserSettings');
    expect(call.method).toBe('PATCH');
    expect(call.body).toEqual({ theme: 'dark', timezone: 'Europe/Berlin' });
    expect(result.theme).toBe('DARK');
    expect(result.timezone).toBe('Europe/Berlin');
  });

  it('sends the notification channels and frequencies', async () => {
    api.on('updateUserSettings', ({ body }) => settings({ ...body }));

    const result = await updateUserPreferences({
      notificationChannels: { email: false, inApp: true, push: true },
      notificationFrequency: 'WEEKLY_DIGEST',
      qnaNotificationFrequency: 'DAILY',
    });

    expect(api.calls('updateUserSettings')[0].body).toEqual({
      emailNotifications: false,
      inAppNotifications: true,
      pushNotifications: true,
      notificationFrequency: 'weekly_digest',
      qnaNotificationFrequency: 'daily',
    });
    expect(result.notificationFrequency).toBe('WEEKLY_DIGEST');
    expect(result.qnaNotificationFrequency).toBe('DAILY');
  });
});

describe('updateUserSettings (privacy, PATCH /users/me/settings)', () => {
  it('maps showActivity to showActivityHistory and back', async () => {
    api.on('updateUserSettings', ({ body }) => settings({ ...body }));

    const result = await updateUserSettings({
      profileVisibility: 'PRIVATE',
      showActivity: false,
      allowMessaging: false,
    });

    expect(api.calls('updateUserSettings')[0].body).toEqual({
      profileVisibility: 'private',
      showActivityHistory: false,
      allowMessaging: false,
    });
    expect(result).toMatchObject({
      profileVisibility: 'PRIVATE',
      showActivity: false,
      allowMessaging: false,
    });
  });
});

describe('profile picture', () => {
  const upload = {
    uploadUrl: 'https://s3.example.com/upload',
    fileId: 'file-abc',
    s3Key: 'users/hong.an/picture.jpg',
    fileExtension: 'jpg',
    expiresInMinutes: 15,
    requiredHeaders: { 'Content-Type': 'image/png' },
  };

  it('removes the own picture', async () => {
    await removeProfilePicture();

    expect(api.calls('removeOwnProfilePicture')).toHaveLength(1);
  });

  it('requests a presigned URL and maps fileId and uploadUrl', async () => {
    const now = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(now);
    api.on('requestProfilePictureUploadUrl', () => upload);

    const result = await requestPresignedUrl({
      fileName: 'photo.jpg',
      fileSize: 1024,
      mimeType: 'image/jpeg',
    });

    expect(api.calls('requestProfilePictureUploadUrl')[0].body).toEqual({
      fileName: 'photo.jpg',
      fileSize: 1024,
      mimeType: 'image/jpeg',
    });
    expect(result).toEqual({
      uploadId: 'file-abc',
      presignedUrl: 'https://s3.example.com/upload',
      s3Key: 'users/hong.an/picture.jpg',
      expiresAt: new Date(now + 15 * 60 * 1000).toISOString(),
    });
  });

  it('confirms the upload and maps profilePictureUrl to cloudFrontUrl', async () => {
    api.on('confirmProfilePictureUpload', () => ({
      profilePictureUrl: 'https://cdn.example.com/photo.jpg',
    }));

    const result = await confirmUpload('upload-1', { fileExtension: 'jpg', checksum: '' });

    expect(api.calls('confirmProfilePictureUpload')[0].body).toEqual({
      fileId: 'upload-1',
      fileExtension: 'jpg',
      checksum: '',
    });
    expect(result).toEqual({
      uploadId: 'upload-1',
      status: 'CONFIRMED',
      cloudFrontUrl: 'https://cdn.example.com/photo.jpg',
    });
  });

  it('requests and confirms a picture for another user (admin)', async () => {
    api.on('requestProfilePictureUploadUrlForUser', () => upload);
    api.on('confirmProfilePictureUploadForUser', () => ({
      profilePictureUrl: 'https://cdn.example.com/pic.jpg',
    }));

    const requested = await requestPresignedUrlForUser('matthias.keller', {
      fileName: 'pic.png',
      fileSize: 2048,
      mimeType: 'image/png',
    });
    const confirmed = await confirmUploadForUser('matthias.keller', 'upload-99', {
      fileExtension: 'png',
      checksum: 'abc123',
    });

    expect(api.calls('requestProfilePictureUploadUrlForUser')[0].params).toEqual({
      username: 'matthias.keller',
    });
    expect(requested.uploadId).toBe('file-abc');
    expect(api.calls('confirmProfilePictureUploadForUser')[0].body).toEqual({
      fileId: 'upload-99',
      fileExtension: 'png',
      checksum: 'abc123',
    });
    expect(confirmed.cloudFrontUrl).toBe('https://cdn.example.com/pic.jpg');
  });
});
