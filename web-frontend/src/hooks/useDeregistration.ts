/**
 * useDeregistration Hook (Story 10.12)
 *
 * React Query hooks for self-service deregistration.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import * as deregistrationService from '@/services/deregistrationService';
import type { DeregistrationVerifyResponse } from '@/services/deregistrationService';

/** Verify a deregistration token. Enabled only when token is non-null. */
export function useVerifyDeregistrationToken(
  token: string | null
): UseQueryResult<DeregistrationVerifyResponse, Error> {
  return useQuery({
    queryKey: ['deregistration', 'verify-token', token],
    queryFn: () => deregistrationService.verifyDeregistrationToken(token!),
    enabled: !!token,
    retry: false,
  });
}

/** Cancel registration by deregistration token. Invalidates my-registration cache on success. */
export function useDeregisterByToken(eventCode?: string): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deregistrationService.deregisterByToken,
    onSuccess: () => {
      if (eventCode) {
        queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] });
      }
    },
  });
}

/** Request deregistration link by email. Always shows success (anti-enumeration). */
export function useDeregistrationByEmail(): UseMutationResult<
  void,
  Error,
  { email: string; eventCode: string }
> {
  return useMutation({
    mutationFn: ({ email, eventCode }) => deregistrationService.deregisterByEmail(email, eventCode),
  });
}
