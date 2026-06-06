/**
 * Additional-email verification (v2) hooks.
 *
 * React Query hooks for the public /verify-email landing page: GET-check the
 * token (no mutation) and POST-confirm it. Mirrors the useNewsletter
 * verify/unsubscribe pattern.
 */

import {
  useMutation,
  useQuery,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import * as userAccountApi from '@/services/api/userAccountApi';
import type {
  AdditionalEmailVerificationCheck,
  AdditionalEmailVerificationConfirm,
} from '@/services/api/userAccountApi';

/** GET-check the verification token (no mutation) — returns masked email + status. */
export function useCheckAdditionalEmailVerification(
  token: string | null
): UseQueryResult<AdditionalEmailVerificationCheck, Error> {
  return useQuery({
    queryKey: ['additional-email-verify', 'check', token],
    queryFn: () => userAccountApi.checkAdditionalEmailVerification(token!),
    enabled: !!token,
    retry: false,
  });
}

/** POST-confirm the verification token (mutates). */
export function useConfirmAdditionalEmailVerification(): UseMutationResult<
  AdditionalEmailVerificationConfirm,
  Error,
  string
> {
  return useMutation({
    mutationFn: userAccountApi.confirmAdditionalEmailVerification,
  });
}
