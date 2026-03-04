/**
 * useMyRegistration Hook
 * Story 10.10: Registration Status Indicator for Logged-in Users (T6, AC1, AC7, AC8)
 *
 * Fetches the authenticated user's registration status for a specific event.
 * - AC7: staleTime 5 minutes; invalidated on successful registration creation
 * - AC8: Returns undefined immediately for unauthenticated users (no API call)
 */

import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/hooks/useAuth';
import { getMyRegistration, type MyRegistrationResponse } from '@/services/registrationService';

export type { MyRegistrationResponse };

export interface UseMyRegistrationResult {
  data: MyRegistrationResponse | null | undefined;
  isLoading: boolean;
}

/**
 * React Query hook for the authenticated user's registration status.
 *
 * Query key: ['my-registration', eventCode]
 * staleTime: 5 minutes (AC7)
 *
 * @param eventCode Event code to check (undefined → no call made)
 * @returns { data, isLoading }
 *   - data: MyRegistrationResponse if registered
 *   - data: null if authenticated but not registered (404)
 *   - data: undefined if not authenticated or eventCode is missing
 */
export const useMyRegistration = (eventCode: string | undefined): UseMyRegistrationResult => {
  const { isAuthenticated } = useAuth();

  const query = useQuery<MyRegistrationResponse | null, Error>({
    queryKey: ['my-registration', eventCode],
    queryFn: () => getMyRegistration(eventCode!),
    enabled: !!eventCode && isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 minutes (AC7)
    // Don't retry 404s (not registered), but allow retry on transient errors (5xx, network)
    retry: (_failureCount, error: unknown) => {
      const status = (error as { response?: { status?: number } })?.response?.status;
      return status !== 404;
    },
  });

  // AC8: Return undefined immediately for unauthenticated users
  if (!isAuthenticated || !eventCode) {
    return { data: undefined, isLoading: false };
  }

  return {
    data: query.data,
    isLoading: query.isLoading,
  };
};
