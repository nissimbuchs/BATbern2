/**
 * Unit Tests for User Account Hooks
 * Story 2.6: User Account Management Frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
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
  userAccountKeys,
} from './useUserAccount';

// Mock the API module
vi.mock('@/services/api/userAccountApi');

describe('User Account Hooks', () => {
  let queryClient: QueryClient;

  const createWrapper = () => {
    return ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };

  const mockUserProfile = {
    id: 'user-123',
    username: 'anna.mueller',
    email: 'anna.mueller@techcorp.ch',
    firstName: 'Anna',
    lastName: 'Müller',
    bio: 'Test bio',
    profilePictureUrl: 'https://cdn.example.com/profile.jpg',
    emailVerified: true,
    roles: ['ORGANIZER'],
    memberSince: '2020-01-15T00:00:00Z',
    preferences: {
      language: 'en',
      timezone: 'Europe/Zurich',
      emailNotifications: true,
      eventReminders: true,
    },
    settings: {
      theme: 'light' as const,
      compactView: false,
      showProfileInDirectory: true,
    },
  };

  const mockActivity = [
    {
      id: 'activity-1',
      type: 'login' as const,
      timestamp: '2024-01-15T10:00:00Z',
      details: { ip: '192.168.1.1' },
    },
  ];

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
      },
    });
    vi.clearAllMocks();
  });

  describe('Query Key Generation', () => {
    it('should_generateCorrectProfileKey_when_called', () => {
      const key = userAccountKeys.profile('user-123');
      expect(key).toEqual(['user-profile', 'user-123']);
    });

    it('should_generateCorrectPreferencesKey_when_called', () => {
      const key = userAccountKeys.preferences('user-123');
      expect(key).toEqual(['user-preferences', 'user-123']);
    });

    it('should_generateCorrectSettingsKey_when_called', () => {
      const key = userAccountKeys.settings('user-123');
      expect(key).toEqual(['user-settings', 'user-123']);
    });

    it('should_generateCorrectActivityKey_when_called', () => {
      const key = userAccountKeys.activity('user-123', 10);
      expect(key).toEqual(['user-activity', 'user-123', { limit: 10 }]);
    });
  });

  describe('useUserProfile', () => {
    it('should_fetchProfile_when_hookMounts', async () => {
      vi.mocked(userAccountApi.getUserProfile).mockResolvedValueOnce(mockUserProfile);

      const { result } = renderHook(() => useUserProfile('user-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockUserProfile);
      expect(userAccountApi.getUserProfile).toHaveBeenCalledTimes(1);
    });

    it('should_handleError_when_fetchFails', async () => {
      vi.mocked(userAccountApi.getUserProfile).mockRejectedValueOnce(new Error('Network error'));

      const { result } = renderHook(() => useUserProfile('user-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toBeDefined();
    });
  });

  describe('useUserPreferences', () => {
    it('should_fetchPreferences_when_hookMounts', async () => {
      vi.mocked(userAccountApi.getUserProfile).mockResolvedValueOnce(mockUserProfile);

      const { result } = renderHook(() => useUserPreferences('user-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockUserProfile.preferences);
      expect(userAccountApi.getUserProfile).toHaveBeenCalledWith(['preferences']);
    });
  });

  describe('useUserSettings', () => {
    it('should_fetchSettings_when_hookMounts', async () => {
      vi.mocked(userAccountApi.getUserProfile).mockResolvedValueOnce(mockUserProfile);

      const { result } = renderHook(() => useUserSettings('user-123'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockUserProfile.settings);
      expect(userAccountApi.getUserProfile).toHaveBeenCalledWith(['settings']);
    });
  });

  describe('useUserActivity', () => {
    it('should_fetchActivity_when_hookMounts', async () => {
      vi.mocked(userAccountApi.getUserActivity).mockResolvedValueOnce(mockActivity);

      const { result } = renderHook(() => useUserActivity('user-123', 10), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockActivity);
      expect(userAccountApi.getUserActivity).toHaveBeenCalledWith(10);
    });
  });

  describe('useUpdateUserProfile', () => {
    it('should_updateProfile_when_mutationCalled', async () => {
      const updatedProfile = { ...mockUserProfile, bio: 'Updated bio' };
      vi.mocked(userAccountApi.updateUserProfile).mockResolvedValueOnce(updatedProfile);

      const { result } = renderHook(() => useUpdateUserProfile(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ bio: 'Updated bio' });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.updateUserProfile).toHaveBeenCalledWith({ bio: 'Updated bio' });
    });

    it('should_invalidateQueries_when_updateSucceeds', async () => {
      const updatedProfile = { ...mockUserProfile, bio: 'Updated bio' };
      vi.mocked(userAccountApi.updateUserProfile).mockResolvedValueOnce(updatedProfile);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateUserProfile(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ bio: 'Updated bio' });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
    });
  });

  describe('useUpdateUserPreferences', () => {
    it('should_updatePreferences_when_mutationCalled', async () => {
      vi.mocked(userAccountApi.updateUserPreferences).mockResolvedValueOnce(
        mockUserProfile.preferences
      );

      const { result } = renderHook(() => useUpdateUserPreferences(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ language: 'de' });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.updateUserPreferences).toHaveBeenCalledWith({ language: 'de' });
    });

    it('should_invalidateQueries_when_updateSucceeds', async () => {
      vi.mocked(userAccountApi.updateUserPreferences).mockResolvedValueOnce(
        mockUserProfile.preferences
      );

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateUserPreferences(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ language: 'de' });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-preferences'] });
    });
  });

  describe('useUpdateUserSettings', () => {
    it('should_updateSettings_when_mutationCalled', async () => {
      vi.mocked(userAccountApi.updateUserSettings).mockResolvedValueOnce(mockUserProfile.settings);

      const { result } = renderHook(() => useUpdateUserSettings(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ theme: 'dark' as const });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.updateUserSettings).toHaveBeenCalledWith({ theme: 'dark' });
    });

    it('should_invalidateQueries_when_updateSucceeds', async () => {
      vi.mocked(userAccountApi.updateUserSettings).mockResolvedValueOnce(mockUserProfile.settings);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateUserSettings(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ theme: 'dark' as const });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-settings'] });
    });
  });

  describe('useUploadProfilePicture', () => {
    it('should_uploadPicture_when_mutationCalled', async () => {
      const newUrl = 'https://cdn.example.com/new-profile.jpg';
      vi.mocked(userAccountApi.uploadProfilePicture).mockResolvedValueOnce(newUrl);

      const { result } = renderHook(() => useUploadProfilePicture(), {
        wrapper: createWrapper(),
      });

      const testFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
      result.current.mutate({ file: testFile });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.uploadProfilePicture).toHaveBeenCalledWith(testFile, undefined);
    });

    it('should_passProgressCallback_when_provided', async () => {
      const newUrl = 'https://cdn.example.com/new-profile.jpg';
      vi.mocked(userAccountApi.uploadProfilePicture).mockResolvedValueOnce(newUrl);

      const { result } = renderHook(() => useUploadProfilePicture(), {
        wrapper: createWrapper(),
      });

      const testFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
      const onProgress = vi.fn();
      result.current.mutate({ file: testFile, onProgress });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.uploadProfilePicture).toHaveBeenCalledWith(testFile, onProgress);
    });

    it('should_invalidateQueries_when_uploadSucceeds', async () => {
      const newUrl = 'https://cdn.example.com/new-profile.jpg';
      vi.mocked(userAccountApi.uploadProfilePicture).mockResolvedValueOnce(newUrl);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUploadProfilePicture(), {
        wrapper: createWrapper(),
      });

      const testFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
      result.current.mutate({ file: testFile });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
    });
  });

  describe('useRemoveProfilePicture', () => {
    it('should_removePicture_when_mutationCalled', async () => {
      vi.mocked(userAccountApi.removeProfilePicture).mockResolvedValueOnce(undefined);

      const { result } = renderHook(() => useRemoveProfilePicture(), {
        wrapper: createWrapper(),
      });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(userAccountApi.removeProfilePicture).toHaveBeenCalled();
    });

    it('should_invalidateQueries_when_removeSucceeds', async () => {
      vi.mocked(userAccountApi.removeProfilePicture).mockResolvedValueOnce(undefined);

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useRemoveProfilePicture(), {
        wrapper: createWrapper(),
      });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user-profile'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
    });
  });
});
