/**
 * useUserProfile Hook
 * Story 1.17, Task 5b: React Query hook for user profile data
 *
 * Fetches and caches user profile data with roles and settings.
 * Uses React Query for server state management with 5-minute stale time.
 */

import { useQuery } from '@tanstack/react-query';
import { getUserProfile, type CurrentUserResponse } from '@/services/api/userApi';

interface UseUserProfileOptions {
  enabled?: boolean;
  staleTime?: number;
}

/**
 * Fetch user profile with roles and settings
 */
const fetchUserProfile = (): Promise<CurrentUserResponse> => getUserProfile(['roles', 'settings']);

/**
 * Custom hook to fetch and cache user profile data
 *
 * @param options - Query options
 * @returns React Query result with user profile data
 *
 * @example
 * const { data: profile, isLoading, error } = useUserProfile();
 */
export function useUserProfile(options?: UseUserProfileOptions) {
  const query = useQuery({
    queryKey: ['userProfile'],
    queryFn: fetchUserProfile,
    staleTime: options?.staleTime ?? 1000 * 60 * 5, // 5 minutes default
    enabled: options?.enabled ?? true,
  });

  return {
    ...query,
    // Add custom property for unread count if needed in the future
    userProfile: query.data,
  };
}
