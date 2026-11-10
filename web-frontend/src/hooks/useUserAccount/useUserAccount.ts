/**
 * React Query Hooks for User Account Management
 * Story 2.6: User Account Management Frontend
 */

import { useQuery, useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { User, UserPreferencesView, UserSettingsView } from '@/types/userAccount.types';
import * as userAccountApi from '@/services/api/userAccountApi';

// Query keys
export const userAccountKeys = {
  profile: (userId: string) => ['user-profile', userId] as const,
  preferences: (userId: string) => ['user-preferences', userId] as const,
  settings: (userId: string) => ['user-settings', userId] as const,
};

/**
 * Both views and both profile queries read the one settings resource (Story 4.9): the account
 * profile (`['user-profile', …]`) and `useUserProfile` (`['userProfile']`, include=roles,settings).
 */
const invalidateSettings = (queryClient: QueryClient) => {
  queryClient.invalidateQueries({ queryKey: ['user-preferences'] });
  queryClient.invalidateQueries({ queryKey: ['user-settings'] });
  queryClient.invalidateQueries({ queryKey: ['user-profile'] });
  queryClient.invalidateQueries({ queryKey: ['userProfile'] });
};

/**
 * Hook to fetch user profile with consolidated data
 */
export const useUserProfile = (userId: string) => {
  return useQuery({
    queryKey: userAccountKeys.profile(userId),
    queryFn: () => userAccountApi.getUserProfile(),
    staleTime: 1000 * 60 * 5, // 5 minutes
    gcTime: 1000 * 60 * 30, // 30 minutes (formerly cacheTime)
  });
};

/**
 * Hook to fetch user preferences
 */
export const useUserPreferences = (userId: string) => {
  return useQuery({
    queryKey: userAccountKeys.preferences(userId),
    queryFn: async () => {
      // Story 4.9: display and notification settings are part of the one settings resource
      const data = await userAccountApi.getUserProfile(['settings']);
      return data.preferences;
    },
    staleTime: 1000 * 60 * 10, // 10 minutes
    gcTime: 1000 * 60 * 60, // 1 hour
  });
};

/**
 * Hook to fetch user settings
 */
export const useUserSettings = (userId: string) => {
  return useQuery({
    queryKey: userAccountKeys.settings(userId),
    queryFn: async () => {
      const data = await userAccountApi.getUserProfile(['settings']);
      return data.settings;
    },
    staleTime: 1000 * 60 * 10, // 10 minutes
    gcTime: 1000 * 60 * 60, // 1 hour
  });
};

/**
 * Hook to update a user's profile: `PATCH /users/{username}` (Story 4.10). `username` is the
 * hydrated user's (`/users/me`), the own one on the account page.
 */
export const useUpdateUserProfile = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ username, updates }: { username: string; updates: Partial<User> }) =>
      userAccountApi.updateUserProfile(username, updates),
    onSuccess: () => {
      // Invalidate and refetch user profile
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
      // Also invalidate user list queries so updates are visible in admin screens
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
};

/**
 * Hook to update user preferences
 */
export const useUpdateUserPreferences = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (preferences: Partial<UserPreferencesView>) =>
      userAccountApi.updateUserPreferences(preferences),
    onSuccess: () => {
      // One settings resource (Story 4.9): both views and the profile read it
      invalidateSettings(queryClient);
    },
  });
};

/**
 * Hook to update user settings
 */
export const useUpdateUserSettings = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (settings: Partial<UserSettingsView>) =>
      userAccountApi.updateUserSettings(settings),
    onSuccess: () => {
      // One settings resource (Story 4.9): both views and the profile read it
      invalidateSettings(queryClient);
    },
  });
};

/**
 * Hook to upload profile picture
 */
export const useUploadProfilePicture = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      username,
      file,
      onProgress,
    }: {
      username: string;
      file: File;
      onProgress?: (progress: number) => void;
    }) => userAccountApi.uploadProfilePicture(username, file, onProgress),
    onSuccess: () => {
      // Invalidate and refetch user profile to get new picture URL
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
      // Also invalidate user list queries so updates are visible in admin screens
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
};

/**
 * Hook to remove profile picture
 */
export const useRemoveProfilePicture = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (username: string) => userAccountApi.removeProfilePicture(username),
    onSuccess: () => {
      // Invalidate and refetch user profile
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
      // Also invalidate user list queries so updates are visible in admin screens
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
};

/**
 * Story 10.32 — register an additional email on the current user's profile.
 * Invalidates the user-profile cache so the UI shows the new row immediately.
 */
export const useAddAdditionalEmail = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: userAccountApi.AddAdditionalEmailPayload) =>
      userAccountApi.addAdditionalEmail(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};

/**
 * Story 10.32 — remove an additional email from the current user's profile.
 */
export const useDeleteAdditionalEmail = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) => userAccountApi.deleteAdditionalEmail(email),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};

/**
 * #1244 — switch the copy of event mails for one of the current user's additional addresses.
 */
export const useUpdateAdditionalEmail = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, receiveEventMail }: { email: string; receiveEventMail: boolean }) =>
      userAccountApi.updateAdditionalEmail(email, { receiveEventMail }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};

/**
 * Additional-email verification (v2) — resend the verification email for one of
 * the caller's own unverified additional emails. Invalidates the user-profile
 * cache so any status changes are reflected.
 */
export const useResendVerification = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) => userAccountApi.resendAdditionalEmailVerification(email),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};
