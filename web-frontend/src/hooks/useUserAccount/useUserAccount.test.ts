/**
 * useUserAccount Hooks Tests
 *
 * Coverage for:
 * - useUserProfile: query
 * - useUserPreferences: query
 * - useUserSettings: query
 * - useUserActivity: query with limit
 * - useUpdateUserProfile: mutation + cache invalidation
 * - useUpdateUserPreferences: mutation + cache invalidation
 * - useUpdateUserSettings: mutation + cache invalidation
 * - useUploadProfilePicture: mutation + cache invalidation
 * - useRemoveProfilePicture: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/userAccountApi', () => ({
  getUserProfile: vi.fn(),
  getUserActivity: vi.fn(),
  updateUserProfile: vi.fn(),
  updateUserPreferences: vi.fn(),
  updateUserSettings: vi.fn(),
  uploadProfilePicture: vi.fn(),
  removeProfilePicture: vi.fn(),
}));

import * as userAccountApi from '@/services/api/userAccountApi';
import {
  useUserProfile,
  useUserPreferences,
  useUserSettings,
  useUserActivity,
  useUpdateUserProfile,
  useUpdateUserPreferences,
  useUpdateUserSettings,
  useUploadProfilePicture,
  useRemoveProfilePicture,
} from './useUserAccount';

const mockGetUserProfile = vi.mocked(userAccountApi.getUserProfile);
const mockGetUserActivity = vi.mocked(userAccountApi.getUserActivity);
const mockUpdateUserProfile = vi.mocked(userAccountApi.updateUserProfile);
const mockUpdateUserPreferences = vi.mocked(userAccountApi.updateUserPreferences);
const mockUpdateUserSettings = vi.mocked(userAccountApi.updateUserSettings);
const mockUploadProfilePicture = vi.mocked(userAccountApi.uploadProfilePicture);
const mockRemoveProfilePicture = vi.mocked(userAccountApi.removeProfilePicture);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_USER = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@batbern.ch',
  preferences: { language: 'en', notifications: true },
  settings: { theme: 'dark', timezone: 'Europe/Zurich' },
};

// ── useUserProfile ────────────────────────────────────────────────────────────

describe('useUserProfile', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user profile', async () => {
    mockGetUserProfile.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUserProfile('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserProfile).toHaveBeenCalled();
    expect(result.current.data).toEqual(MOCK_USER);
  });

  it('should set isError on failure', async () => {
    mockGetUserProfile.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useUserProfile('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUserPreferences ────────────────────────────────────────────────────────

describe('useUserPreferences', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user preferences via getUserProfile', async () => {
    mockGetUserProfile.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUserPreferences('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserProfile).toHaveBeenCalledWith(['preferences']);
    expect(result.current.data).toEqual(MOCK_USER.preferences);
  });

  it('should set isError on failure', async () => {
    mockGetUserProfile.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useUserPreferences('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUserSettings ───────────────────────────────────────────────────────────

describe('useUserSettings', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user settings via getUserProfile', async () => {
    mockGetUserProfile.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUserSettings('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserProfile).toHaveBeenCalledWith(['settings']);
    expect(result.current.data).toEqual(MOCK_USER.settings);
  });
});

// ── useUserActivity ───────────────────────────────────────────────────────────

describe('useUserActivity', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch user activity', async () => {
    const activity = [{ action: 'LOGIN', timestamp: '2025-01-01T10:00:00Z' }];
    mockGetUserActivity.mockResolvedValue(activity as never);

    const { result } = renderHook(() => useUserActivity('user-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserActivity).toHaveBeenCalledWith(undefined);
    expect(result.current.data).toEqual(activity);
  });

  it('should pass limit parameter', async () => {
    mockGetUserActivity.mockResolvedValue([] as never);

    const { result } = renderHook(() => useUserActivity('user-1', 5), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetUserActivity).toHaveBeenCalledWith(5);
  });
});

// ── useUpdateUserProfile ──────────────────────────────────────────────────────

describe('useUpdateUserProfile', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateUserProfile with updates', async () => {
    mockUpdateUserProfile.mockResolvedValue(MOCK_USER as never);

    const { result } = renderHook(() => useUpdateUserProfile(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ email: 'newemail@batbern.ch' } as never);
    });

    expect(mockUpdateUserProfile).toHaveBeenCalledWith({ email: 'newemail@batbern.ch' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate user-profile and users caches on success', async () => {
    mockUpdateUserProfile.mockResolvedValue(MOCK_USER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserProfile(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
  });

  it('should set isError on failure', async () => {
    mockUpdateUserProfile.mockRejectedValue(new Error('Validation error'));

    const { result } = renderHook(() => useUpdateUserProfile(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateUserPreferences ──────────────────────────────────────────────────

describe('useUpdateUserPreferences', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateUserPreferences with partial preferences', async () => {
    mockUpdateUserPreferences.mockResolvedValue({ language: 'de' } as never);

    const { result } = renderHook(() => useUpdateUserPreferences(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ language: 'de' } as never);
    });

    expect(mockUpdateUserPreferences).toHaveBeenCalledWith({ language: 'de' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate user-preferences cache on success', async () => {
    mockUpdateUserPreferences.mockResolvedValue({} as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserPreferences(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-preferences'] });
  });
});

// ── useUpdateUserSettings ─────────────────────────────────────────────────────

describe('useUpdateUserSettings', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateUserSettings with partial settings', async () => {
    mockUpdateUserSettings.mockResolvedValue({ theme: 'light' } as never);

    const { result } = renderHook(() => useUpdateUserSettings(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ theme: 'light' } as never);
    });

    expect(mockUpdateUserSettings).toHaveBeenCalledWith({ theme: 'light' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate user-settings cache on success', async () => {
    mockUpdateUserSettings.mockResolvedValue({} as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateUserSettings(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-settings'] });
  });
});

// ── useUploadProfilePicture ───────────────────────────────────────────────────

describe('useUploadProfilePicture', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call uploadProfilePicture with file', async () => {
    mockUploadProfilePicture.mockResolvedValue('https://cdn.example.com/pic.jpg' as never);

    const { result } = renderHook(() => useUploadProfilePicture(), { wrapper: wrapper(qc) });

    const file = new File(['content'], 'avatar.png', { type: 'image/png' });

    await act(async () => {
      await result.current.mutateAsync({ file });
    });

    expect(mockUploadProfilePicture).toHaveBeenCalledWith(file, undefined);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate user-profile and users caches on success', async () => {
    mockUploadProfilePicture.mockResolvedValue('url' as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUploadProfilePicture(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ file: new File([], 'f.png') });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
  });
});

// ── useRemoveProfilePicture ───────────────────────────────────────────────────

describe('useRemoveProfilePicture', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call removeProfilePicture', async () => {
    mockRemoveProfilePicture.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useRemoveProfilePicture(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(mockRemoveProfilePicture).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate user-profile and users caches on success', async () => {
    mockRemoveProfilePicture.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useRemoveProfilePicture(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
  });

  it('should set isError on failure', async () => {
    mockRemoveProfilePicture.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useRemoveProfilePicture(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
