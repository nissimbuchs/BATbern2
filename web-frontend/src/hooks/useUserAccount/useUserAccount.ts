/**
 * React Query Hooks for User Account Management
 * Story 2.6: User Account Management Frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { User, UserPreferences, UserSettings } from '@/types/userAccount.types';
import * as userAccountApi from '@/services/api/userAccountApi';

// Query keys
export const userAccountKeys = {
  profile: (userId: string) => ['user-profile', userId] as const,
  preferences: (userId: string) => ['user-preferences', userId] as const,
  settings: (userId: string) => ['user-settings', userId] as const,
  activity: (userId: string, limit?: number) => ['user-activity', userId, { limit }] as const,
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
      const data = await userAccountApi.getUserProfile(['preferences']);
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
 * Hook to fetch user activity
 */
export const useUserActivity = (userId: string, limit?: number) => {
  return useQuery({
    queryKey: userAccountKeys.activity(userId, limit),
    queryFn: () => userAccountApi.getUserActivity(limit),
    staleTime: 1000 * 60, // 1 minute
    gcTime: 1000 * 60 * 10, // 10 minutes
  });
};

/**
 * Hook to update user profile
 */
export const useUpdateUserProfile = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (updates: Partial<User>) => userAccountApi.updateUserProfile(updates),
    onSuccess: () => {
      // Invalidate and refetch user profile
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};

/**
 * Hook to update user preferences
 */
export const useUpdateUserPreferences = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (preferences: Partial<UserPreferences>) =>
      userAccountApi.updateUserPreferences(preferences),
    onSuccess: () => {
      // Invalidate and refetch preferences
      queryClient.invalidateQueries({ queryKey: ['user-preferences'] });
    },
  });
};

/**
 * Hook to update user settings
 */
export const useUpdateUserSettings = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (settings: Partial<UserSettings>) => userAccountApi.updateUserSettings(settings),
    onSuccess: () => {
      // Invalidate and refetch settings
      queryClient.invalidateQueries({ queryKey: ['user-settings'] });
    },
  });
};

/**
 * Hook to upload profile picture
 */
export const useUploadProfilePicture = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ file, onProgress }: { file: File; onProgress?: (progress: number) => void }) =>
      userAccountApi.uploadProfilePicture(file, onProgress),
    onSuccess: () => {
      // Invalidate and refetch user profile to get new picture URL
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};

/**
 * Hook to remove profile picture
 */
export const useRemoveProfilePicture = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => userAccountApi.removeProfilePicture(),
    onSuccess: () => {
      // Invalidate and refetch user profile
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    },
  });
};
