/**
 * userAccountApi Tests
 *
 * Coverage for:
 * - getUserProfile: GET /users/me, data transformation (username, memberSince, company, preferences, settings)
 * - updateUserProfile: PUT /users/me with field filtering
 * - updateUserPreferences: PUT /users/me/preferences (uppercase→lowercase enum conversion)
 * - updateUserSettings: PUT /users/me/settings (uppercase→lowercase, showActivity→showActivityHistory)
 * - getUserActivity: GET /users/me/activity with/without limit
 * - removeProfilePicture: DELETE /users/me/picture
 * - requestPresignedUrl: POST presigned URL (fileId→uploadId, uploadUrl→presignedUrl mapping)
 * - confirmUpload: POST confirm (uploadId→fileId, profilePictureUrl→cloudFrontUrl)
 * - requestPresignedUrlForUser / confirmUploadForUser: admin endpoints
 * - mapTheme / mapNotificationFrequency / mapProfileVisibility helpers (via API responses)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from './apiClient';
import {
  getUserProfile,
  updateUserProfile,
  updateUserPreferences,
  updateUserSettings,
  getUserActivity,
  removeProfilePicture,
  requestPresignedUrl,
  confirmUpload,
  requestPresignedUrlForUser,
  confirmUploadForUser,
  associateProfilePicture,
} from './userAccountApi';

const mockGet = vi.mocked(apiClient.get);
const mockPut = vi.mocked(apiClient.put);
const mockPost = vi.mocked(apiClient.post);
const mockDelete = vi.mocked(apiClient.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

// ── getUserProfile ────────────────────────────────────────────────────────────

describe('getUserProfile', () => {
  it('should GET /users/me with default include params', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'user-1',
        firstName: 'Alice',
        lastName: 'Smith',
        email: 'alice@example.com',
        createdAt: '2024-01-01',
      },
    });

    await getUserProfile();

    expect(mockGet).toHaveBeenCalledWith(
      '/users/me?include=company,roles,preferences,settings,activity'
    );
  });

  it('should map id to username and createdAt to memberSince', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'user-42',
        firstName: 'Bob',
        createdAt: '2023-06-15',
      },
    });

    const result = await getUserProfile();

    expect(result.user.username).toBe('user-42');
    expect(result.user.memberSince).toBe('2023-06-15');
  });

  it('should map nested company fields', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'user-1',
        createdAt: '2024-01-01',
        company: { id: 'comp-1', name: 'Acme', uid: 'CHE-123' },
        companyId: 'comp-1',
      },
    });

    const result = await getUserProfile();

    expect(result.user.company).toEqual({ id: 'comp-1', name: 'Acme', uid: 'CHE-123' });
  });

  it('should leave company undefined when not present', async () => {
    mockGet.mockResolvedValue({ data: { id: 'u1', createdAt: '2024-01-01' } });

    const result = await getUserProfile();

    expect(result.user.company).toBeUndefined();
  });

  it('should transform preferences with mapTheme and mapNotificationFrequency', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'u1',
        createdAt: '2024-01-01',
        preferences: {
          theme: 'dark',
          timezone: 'UTC',
          emailNotifications: false,
          inAppNotifications: true,
          pushNotifications: true,
          notificationFrequency: 'daily_digest',
        },
      },
    });

    const result = await getUserProfile();

    expect(result.preferences?.theme).toBe('DARK');
    expect(result.preferences?.notificationFrequency).toBe('DAILY_DIGEST');
    expect(result.preferences?.notificationChannels).toEqual({
      email: false,
      inApp: true,
      push: true,
    });
    expect(result.preferences?.timezone).toBe('UTC');
  });

  it('should default theme to LIGHT when preferences.theme is absent', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'u1',
        createdAt: '2024-01-01',
        preferences: { timezone: 'UTC' },
      },
    });

    const result = await getUserProfile();

    expect(result.preferences?.theme).toBe('LIGHT');
  });

  it('should default notificationFrequency to IMMEDIATE when absent', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'u1',
        createdAt: '2024-01-01',
        preferences: {},
      },
    });

    const result = await getUserProfile();

    expect(result.preferences?.notificationFrequency).toBe('IMMEDIATE');
  });

  it('should transform settings with mapProfileVisibility', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'u1',
        createdAt: '2024-01-01',
        settings: {
          profileVisibility: 'public',
          showEmail: true,
          showCompany: false,
          showActivityHistory: false,
          allowMessaging: false,
        },
      },
    });

    const result = await getUserProfile();

    expect(result.settings?.profileVisibility).toBe('PUBLIC');
    expect(result.settings?.showEmail).toBe(true);
    expect(result.settings?.showCompany).toBe(false);
    expect(result.settings?.showActivity).toBe(false);
    expect(result.settings?.allowMessaging).toBe(false);
  });

  it('should default profileVisibility to MEMBERS_ONLY when settings.profileVisibility is absent', async () => {
    mockGet.mockResolvedValue({
      data: {
        id: 'u1',
        createdAt: '2024-01-01',
        settings: {},
      },
    });

    const result = await getUserProfile();

    expect(result.settings?.profileVisibility).toBe('MEMBERS_ONLY');
  });

  it('should return activity array from response', async () => {
    const activity = [{ type: 'LOGIN', timestamp: '2024-01-01' }];
    mockGet.mockResolvedValue({
      data: { id: 'u1', createdAt: '2024-01-01', activity },
    });

    const result = await getUserProfile();

    expect(result.activity).toEqual(activity);
  });

  it('should return empty activity array when activity is absent', async () => {
    mockGet.mockResolvedValue({ data: { id: 'u1', createdAt: '2024-01-01' } });

    const result = await getUserProfile();

    expect(result.activity).toEqual([]);
  });

  it('should accept custom include list', async () => {
    mockGet.mockResolvedValue({ data: { id: 'u1', createdAt: '2024-01-01' } });

    await getUserProfile(['company', 'roles']);

    expect(mockGet).toHaveBeenCalledWith('/users/me?include=company,roles');
  });
});

// ── updateUserProfile ─────────────────────────────────────────────────────────

describe('updateUserProfile', () => {
  it('should PUT /users/me with only allowed fields', async () => {
    mockPut.mockResolvedValue({
      data: { id: 'u1', createdAt: '2024-01-01', firstName: 'Alice' },
    });

    await updateUserProfile({
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
      bio: 'Dev',
      companyId: 'comp-1',
    });

    expect(mockPut).toHaveBeenCalledWith('/users/me', {
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
      bio: 'Dev',
      companyId: 'comp-1',
    });
  });

  it('should strip disallowed fields from update payload', async () => {
    mockPut.mockResolvedValue({
      data: { id: 'u1', createdAt: '2024-01-01', firstName: 'Alice' },
    });

    await updateUserProfile({
      firstName: 'Alice',
      username: 'should-be-stripped' as never,
      memberSince: 'should-be-stripped' as never,
    } as never);

    const calledWith = mockPut.mock.calls[0][1] as Record<string, unknown>;
    expect(calledWith).not.toHaveProperty('username');
    expect(calledWith).not.toHaveProperty('memberSince');
  });

  it('should transform response: map id→username, createdAt→memberSince', async () => {
    mockPut.mockResolvedValue({
      data: { id: 'user-99', createdAt: '2025-01-01', firstName: 'Bob' },
    });

    const result = await updateUserProfile({ firstName: 'Bob' });

    expect(result.username).toBe('user-99');
    expect(result.memberSince).toBe('2025-01-01');
  });

  it('should only send fields that are defined', async () => {
    mockPut.mockResolvedValue({ data: { id: 'u1', createdAt: '2024-01-01' } });

    await updateUserProfile({ firstName: 'Alice' });

    const calledWith = mockPut.mock.calls[0][1] as Record<string, unknown>;
    expect(Object.keys(calledWith)).toEqual(['firstName']);
  });
});

// ── updateUserPreferences ─────────────────────────────────────────────────────

describe('updateUserPreferences', () => {
  it('should PUT /users/me/preferences with lowercase-converted enums', async () => {
    mockPut.mockResolvedValue({
      data: {
        theme: 'dark',
        timezone: 'UTC',
        emailNotifications: true,
        inAppNotifications: true,
        pushNotifications: false,
        notificationFrequency: 'daily_digest',
      },
    });

    await updateUserPreferences({
      theme: 'DARK',
      notificationFrequency: 'DAILY_DIGEST',
      timezone: 'UTC',
      notificationChannels: { email: true, inApp: true, push: false },
    });

    const payload = mockPut.mock.calls[0][1] as Record<string, unknown>;
    expect(mockPut).toHaveBeenCalledWith('/users/me/preferences', expect.any(Object));
    expect(payload.theme).toBe('dark');
    expect(payload.notificationFrequency).toBe('daily_digest');
    expect(payload.timezone).toBe('UTC');
    expect(payload.emailNotifications).toBe(true);
    expect(payload.inAppNotifications).toBe(true);
    expect(payload.pushNotifications).toBe(false);
  });

  it('should transform response back to uppercase enums', async () => {
    mockPut.mockResolvedValue({
      data: {
        theme: 'auto',
        timezone: 'Europe/Zurich',
        emailNotifications: true,
        inAppNotifications: true,
        pushNotifications: false,
        notificationFrequency: 'weekly_digest',
      },
    });

    const result = await updateUserPreferences({ theme: 'AUTO' });

    expect(result.theme).toBe('AUTO');
    expect(result.notificationFrequency).toBe('WEEKLY_DIGEST');
  });
});

// ── updateUserSettings ────────────────────────────────────────────────────────

describe('updateUserSettings', () => {
  it('should PUT /users/me/settings mapping showActivity→showActivityHistory', async () => {
    mockPut.mockResolvedValue({
      data: {
        profileVisibility: 'members_only',
        showEmail: false,
        showCompany: true,
        showActivityHistory: true,
        allowMessaging: true,
      },
    });

    await updateUserSettings({
      profileVisibility: 'MEMBERS_ONLY',
      showEmail: false,
      showCompany: true,
      showActivity: true,
      allowMessaging: true,
    });

    const payload = mockPut.mock.calls[0][1] as Record<string, unknown>;
    expect(mockPut).toHaveBeenCalledWith('/users/me/settings', expect.any(Object));
    expect(payload.showActivityHistory).toBe(true);
    expect(payload.profileVisibility).toBe('members_only');
  });

  it('should transform response: showActivityHistory→showActivity, uppercase visibility', async () => {
    mockPut.mockResolvedValue({
      data: {
        profileVisibility: 'private',
        showEmail: true,
        showCompany: false,
        showActivityHistory: false,
        allowMessaging: false,
      },
    });

    const result = await updateUserSettings({ profileVisibility: 'PRIVATE' });

    expect(result.profileVisibility).toBe('PRIVATE');
    expect(result.showActivity).toBe(false);
    expect(result.showEmail).toBe(true);
    expect(result.showCompany).toBe(false);
    expect(result.allowMessaging).toBe(false);
  });
});

// ── getUserActivity ───────────────────────────────────────────────────────────

describe('getUserActivity', () => {
  it('should GET /users/me/activity without limit param when not provided', async () => {
    mockGet.mockResolvedValue({ data: [] });

    await getUserActivity();

    expect(mockGet).toHaveBeenCalledWith('/users/me/activity');
  });

  it('should append ?limit= when provided', async () => {
    mockGet.mockResolvedValue({ data: [] });

    await getUserActivity(10);

    expect(mockGet).toHaveBeenCalledWith('/users/me/activity?limit=10');
  });

  it('should return the data array from response', async () => {
    const items = [{ type: 'REGISTRATION', timestamp: '2025-01-01' }];
    mockGet.mockResolvedValue({ data: items });

    const result = await getUserActivity(5);

    expect(result).toEqual(items);
  });
});

// ── removeProfilePicture ──────────────────────────────────────────────────────

describe('removeProfilePicture', () => {
  it('should DELETE /users/me/picture', async () => {
    mockDelete.mockResolvedValue({});

    await removeProfilePicture();

    expect(mockDelete).toHaveBeenCalledWith('/users/me/picture');
  });
});

// ── requestPresignedUrl ───────────────────────────────────────────────────────

describe('requestPresignedUrl', () => {
  it('should POST /users/me/picture/presigned-url and map fields', async () => {
    const now = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(now);

    mockPost.mockResolvedValue({
      data: {
        fileId: 'file-abc',
        uploadUrl: 'https://s3.example.com/upload',
        s3Key: 'users/u1/picture.jpg',
        expiresInMinutes: 15,
      },
    });

    const result = await requestPresignedUrl({
      fileName: 'photo.jpg',
      fileSize: 1024,
      mimeType: 'image/jpeg',
    });

    expect(mockPost).toHaveBeenCalledWith('/users/me/picture/presigned-url', {
      fileName: 'photo.jpg',
      fileSize: 1024,
      mimeType: 'image/jpeg',
    });
    expect(result.uploadId).toBe('file-abc');
    expect(result.presignedUrl).toBe('https://s3.example.com/upload');
    expect(result.s3Key).toBe('users/u1/picture.jpg');
    expect(result.expiresAt).toBe(new Date(now + 15 * 60 * 1000).toISOString());
  });

  it('should default expiresInMinutes to 15 when absent in response', async () => {
    const now = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(now);

    mockPost.mockResolvedValue({
      data: { fileId: 'f1', uploadUrl: 'https://s3.x.com', s3Key: 'k' },
    });

    const result = await requestPresignedUrl({
      fileName: 'x.jpg',
      fileSize: 1,
      mimeType: 'image/jpeg',
    });

    expect(result.expiresAt).toBe(new Date(now + 15 * 60 * 1000).toISOString());
  });
});

// ── confirmUpload ─────────────────────────────────────────────────────────────

describe('confirmUpload', () => {
  it('should POST /users/me/picture/confirm and map profilePictureUrl→cloudFrontUrl', async () => {
    mockPost.mockResolvedValue({
      data: { profilePictureUrl: 'https://cdn.example.com/photo.jpg' },
    });

    const result = await confirmUpload('upload-1', { fileExtension: 'jpg', checksum: '' });

    expect(mockPost).toHaveBeenCalledWith('/users/me/picture/confirm', {
      fileId: 'upload-1',
      fileExtension: 'jpg',
      checksum: '',
    });
    expect(result.cloudFrontUrl).toBe('https://cdn.example.com/photo.jpg');
    expect(result.uploadId).toBe('upload-1');
    expect(result.status).toBe('CONFIRMED');
  });
});

// ── requestPresignedUrlForUser (admin) ────────────────────────────────────────

describe('requestPresignedUrlForUser', () => {
  it('should POST /users/{username}/picture/presigned-url', async () => {
    mockPost.mockResolvedValue({
      data: {
        fileId: 'f2',
        uploadUrl: 'https://s3.x.com/url',
        s3Key: 'users/admin-user/pic.jpg',
        expiresInMinutes: 10,
      },
    });

    const result = await requestPresignedUrlForUser('admin-user', {
      fileName: 'pic.jpg',
      fileSize: 2048,
      mimeType: 'image/png',
    });

    expect(mockPost).toHaveBeenCalledWith('/users/admin-user/picture/presigned-url', {
      fileName: 'pic.jpg',
      fileSize: 2048,
      mimeType: 'image/png',
    });
    expect(result.uploadId).toBe('f2');
    expect(result.presignedUrl).toBe('https://s3.x.com/url');
  });
});

// ── confirmUploadForUser (admin) ──────────────────────────────────────────────

describe('confirmUploadForUser', () => {
  it('should POST /users/{username}/picture/confirm', async () => {
    mockPost.mockResolvedValue({
      data: { profilePictureUrl: 'https://cdn.x.com/pic.jpg' },
    });

    const result = await confirmUploadForUser('target-user', 'upload-99', {
      fileExtension: 'png',
      checksum: 'abc123',
    });

    expect(mockPost).toHaveBeenCalledWith('/users/target-user/picture/confirm', {
      fileId: 'upload-99',
      fileExtension: 'png',
      checksum: 'abc123',
    });
    expect(result.cloudFrontUrl).toBe('https://cdn.x.com/pic.jpg');
    expect(result.status).toBe('CONFIRMED');
  });
});

// ── associateProfilePicture ───────────────────────────────────────────────────

describe('associateProfilePicture', () => {
  it('should PUT /users/me with profilePictureFileId', async () => {
    mockPut.mockResolvedValue({ data: { id: 'u1', createdAt: '2025-01-01' } });

    await associateProfilePicture('upload-55');

    expect(mockPut).toHaveBeenCalledWith('/users/me', { profilePictureFileId: 'upload-55' });
  });
});
