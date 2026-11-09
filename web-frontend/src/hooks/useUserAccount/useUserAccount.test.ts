/**
 * useUserAccount hooks against the MSW harness (Story 2.6; Story 4.9 moved them off the
 * userAccountApi mock and onto the one settings resource).
 *
 * The real hooks and the real userAccountApi run; the harness checks each request against
 * users-api. Both settings views come from `GET /users/me?include=settings` and both are saved
 * with `PATCH /users/me/settings`, so a save invalidates both views and the profile.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import type { UserSettingsResponse } from '@/services/api/userApi';
import {
  userAccountKeys,
  useUserProfile,
  useUserPreferences,
  useUserSettings,
  useUpdateUserProfile,
  useUpdateUserPreferences,
  useUpdateUserSettings,
  useUploadProfilePicture,
  useRemoveProfilePicture,
} from './useUserAccount';
import { useUserProfile as useUserProfileQuery } from '@/hooks/useUserProfile/useUserProfile';

const settings = (over: Partial<UserSettingsResponse> = {}): UserSettingsResponse => ({
  theme: 'dark',
  language: 'de',
  timezone: 'Europe/Zurich',
  emailNotifications: true,
  inAppNotifications: true,
  pushNotifications: false,
  notificationFrequency: 'immediate',
  qnaNotificationFrequency: 'live',
  quietHoursStart: null,
  quietHoursEnd: null,
  profileVisibility: 'members_only',
  showEmail: false,
  showCompany: true,
  showActivityHistory: true,
  allowMessaging: true,
  allowCalendarSync: false,
  twoFactorEnabled: false,
  ...over,
});

const user = {
  id: 'matthias.keller',
  email: 'matthias.keller@example.com',
  firstName: 'Matthias',
  lastName: 'Keller',
  roles: ['ORGANIZER' as const],
  active: true,
  createdAt: '2024-01-01T10:00:00Z',
  updatedAt: '2025-01-15T10:00:00Z',
  additionalEmails: [],
};

let qc: QueryClient;
const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(QueryClientProvider, { client: qc }, children);

beforeEach(() => {
  qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  api.on('getCurrentUser', () => ({ ...user, settings: settings() }));
});

describe('query keys', () => {
  it('keeps one key per view', () => {
    expect(userAccountKeys.profile('u')).toEqual(['user-profile', 'u']);
    expect(userAccountKeys.preferences('u')).toEqual(['user-preferences', 'u']);
    expect(userAccountKeys.settings('u')).toEqual(['user-settings', 'u']);
  });
});

describe('reading', () => {
  it('useUserProfile reads the profile with its settings', async () => {
    const { result } = renderHook(() => useUserProfile('matthias.keller'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.user.username).toBe('matthias.keller');
    expect(result.current.data?.preferences?.theme).toBe('DARK');
  });

  it('useUserProfile reports a failure', async () => {
    api.on('getCurrentUser', () => HttpResponse.json({}, { status: 500 }));

    const { result } = renderHook(() => useUserProfile('matthias.keller'), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('useUserPreferences and useUserSettings read the one settings include', async () => {
    const prefs = renderHook(() => useUserPreferences('matthias.keller'), { wrapper });
    const privacy = renderHook(() => useUserSettings('matthias.keller'), { wrapper });

    await waitFor(() => expect(prefs.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(privacy.result.current.isSuccess).toBe(true));
    expect(prefs.result.current.data?.timezone).toBe('Europe/Zurich');
    expect(privacy.result.current.data?.profileVisibility).toBe('MEMBERS_ONLY');
    expect(api.calls('getCurrentUser').map((c) => c.query.get('include'))).toEqual([
      'settings',
      'settings',
    ]);
  });
});

describe('saving settings', () => {
  it('useUpdateUserPreferences PATCHes theme and timezone and invalidates both views', async () => {
    api.on('updateUserSettings', ({ body }) => settings({ ...body }));
    const invalidate = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateUserPreferences(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ theme: 'LIGHT', timezone: 'Asia/Tokyo' });
    });

    expect(api.calls('updateUserSettings')[0].body).toEqual({
      theme: 'light',
      timezone: 'Asia/Tokyo',
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-preferences'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-settings'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
  });

  it('useUpdateUserSettings PATCHes the privacy fields and invalidates both views', async () => {
    api.on('updateUserSettings', ({ body }) => settings({ ...body }));
    const invalidate = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateUserSettings(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ showEmail: true });
    });

    expect(api.calls('updateUserSettings')[0].body).toEqual({ showEmail: true });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-preferences'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-settings'] });
  });

  it('refetches the useUserProfile query (include=roles,settings) after a save', async () => {
    let language: 'de' | 'en' = 'de';
    api.on('getCurrentUser', () => ({ ...user, settings: settings({ language }) }));
    api.on('updateUserSettings', ({ body }) => {
      language = 'en';
      return settings({ ...body });
    });
    const profile = renderHook(() => useUserProfileQuery(), { wrapper });
    await waitFor(() => expect(profile.result.current.userProfile?.settings?.language).toBe('de'));
    const { result } = renderHook(() => useUpdateUserPreferences(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ theme: 'DARK' });
    });

    await waitFor(() => expect(profile.result.current.userProfile?.settings?.language).toBe('en'));
    expect(
      api.calls('getCurrentUser').filter((c) => c.query.get('include') === 'roles,settings')
    ).toHaveLength(2);
  });

  it('reports a 400 from CUMS as an error', async () => {
    api.on('updateUserSettings', () => HttpResponse.json({}, { status: 400 }));
    const { result } = renderHook(() => useUpdateUserPreferences(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ timezone: 'Mars/Olympus' }).catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('profile and picture', () => {
  it('useUpdateUserProfile PATCHes /users/me and invalidates profile and user lists', async () => {
    api.on('patchCurrentUser', ({ body }) => ({ ...user, ...body }));
    const invalidate = vi.spyOn(qc, 'invalidateQueries');
    const { result } = renderHook(() => useUpdateUserProfile(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ firstName: 'Matthias' });
    });

    expect(api.calls('patchCurrentUser')[0].body).toEqual({ firstName: 'Matthias' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users'] });
  });

  it('useUploadProfilePicture runs presign, S3 upload and confirm', async () => {
    const s3Puts: string[] = [];
    server.use(
      http.put('https://bucket.example/upload', ({ request }) => {
        s3Puts.push(request.headers.get('Content-Type') ?? '');
        return new HttpResponse(null, { status: 200 });
      })
    );
    api.on('requestProfilePictureUploadUrl', () => ({
      uploadUrl: 'https://bucket.example/upload',
      fileId: 'file-1',
      s3Key: 'users/matthias.keller/p.png',
      fileExtension: 'png',
      expiresInMinutes: 15,
    }));
    api.on('confirmProfilePictureUpload', () => ({
      profilePictureUrl: 'https://cdn.example.com/p.png',
    }));
    const { result } = renderHook(() => useUploadProfilePicture(), { wrapper });

    let url: string | undefined;
    await act(async () => {
      url = await result.current.mutateAsync({
        file: new File(['content'], 'avatar.png', { type: 'image/png' }),
      });
    });

    expect(url).toBe('https://cdn.example.com/p.png');
    expect(s3Puts).toEqual(['image/png']);
    expect(api.calls('confirmProfilePictureUpload')[0].body).toMatchObject({ fileId: 'file-1' });
  });

  it('useRemoveProfilePicture deletes the own picture', async () => {
    const { result } = renderHook(() => useRemoveProfilePicture(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(api.calls('removeOwnProfilePicture')).toHaveLength(1);
  });
});
