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
import { http, HttpResponse } from 'msw';
import { server } from '@/test/msw/server';
import { api } from '@/test/msw/api';
import type { UserSettingsResponse } from './userApi';
import {
  getUserProfile,
  updateUserProfile,
  updateUserPreferences,
  updateUserSettings,
  removeProfilePicture,
  uploadProfilePicture,
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
  it('PATCHes /users/{username} with only the allowed, defined fields', async () => {
    api.on('patchUser', ({ body }) => ({
      ...user,
      firstName: body?.firstName ?? user.firstName,
    }));

    const result = await updateUserProfile('hong.an', {
      firstName: 'Hông',
      bio: 'Architektin',
      username: 'ignored' as never,
      termsAccepted: true,
    });

    const [call] = api.calls('patchUser');
    expect(call.method).toBe('PATCH');
    expect(call.params).toEqual({ username: 'hong.an' });
    expect(call.body).toEqual({
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
  const S3_PUT = 'https://s3.example.com/upload';

  /** Presign, S3 and confirm answer; returns the Content-Type headers S3 received. */
  const pictureUploadAnswers = (fileUrl: string | null) => {
    const s3Puts: string[] = [];
    server.use(
      http.put(S3_PUT, ({ request }) => {
        s3Puts.push(request.headers.get('Content-Type') ?? '');
        return new HttpResponse(null, { status: 200 });
      })
    );
    api.on(
      'createFileUpload',
      () => ({
        uploadId: 'upload-1',
        uploadUrl: S3_PUT,
        expiresAt: '2026-11-13T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'image/jpeg' },
      }),
      { status: 201 }
    );
    api.on('confirmFileUpload', () => ({
      uploadId: 'upload-1',
      purpose: 'profile-picture' as const,
      status: fileUrl ? ('ASSOCIATED' as const) : ('CONFIRMED' as const),
      fileUrl,
    }));
    return s3Puts;
  };

  it('removes the picture of the given user', async () => {
    await removeProfilePicture('hong.an');

    expect(api.calls('removeProfilePictureForUser')[0].params).toEqual({ username: 'hong.an' });
  });

  it('passes a refusal through (another user, not an organizer)', async () => {
    api.on('removeProfilePictureForUser', () =>
      HttpResponse.json({ status: 403 }, { status: 403 })
    );

    await expect(removeProfilePicture('matthias.keller')).rejects.toMatchObject({
      response: { status: 403 },
    });
  });

  it('uploads the own picture on the upload contract and returns its CDN URL', async () => {
    const s3Puts = pictureUploadAnswers('https://cdn.example.com/photo.jpg');
    const file = new File(['content'], 'Porträt.JPG', { type: 'image/jpeg' });

    const url = await uploadProfilePicture('hong.an', file);

    expect(url).toBe('https://cdn.example.com/photo.jpg');
    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'profile-picture' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'Porträt.JPG',
      contentType: 'image/jpeg',
      size: file.size,
      target: { username: 'hong.an' },
    });
    expect(s3Puts).toEqual(['image/jpeg']);
    expect(api.calls('confirmFileUpload')[0].params).toEqual({
      purpose: 'profile-picture',
      uploadId: 'upload-1',
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { username: 'hong.an' } });
  });

  it('uploads the picture of another user (organizer) with that user as target', async () => {
    pictureUploadAnswers('https://cdn.example.com/pic.jpg');

    await uploadProfilePicture(
      'matthias.keller',
      new File(['x'], 'pic.jpg', { type: 'image/jpeg' })
    );

    expect(api.calls('createFileUpload')[0].body).toMatchObject({
      target: { username: 'matthias.keller' },
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({
      target: { username: 'matthias.keller' },
    });
  });

  it('fails when the confirm did not attach the picture', async () => {
    pictureUploadAnswers(null);

    await expect(
      uploadProfilePicture('hong.an', new File(['x'], 'pic.jpg', { type: 'image/jpeg' }))
    ).rejects.toThrow('not attached');
  });

  it('passes a refusal of the reservation through (another user, not an organizer)', async () => {
    api.on('createFileUpload', () => HttpResponse.json({ status: 403 }, { status: 403 }));

    await expect(
      uploadProfilePicture('matthias.keller', new File(['x'], 'pic.jpg', { type: 'image/jpeg' }))
    ).rejects.toMatchObject({ response: { status: 403 } });
    expect(api.calls('confirmFileUpload')).toHaveLength(0);
  });
});
