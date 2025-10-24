/**
 * useEmailVerification Hook (TDD - GREEN Phase)
 * Story 1.2.4: Email Verification Flow - Task 4
 * Handles email verification via AWS Cognito confirmSignUp
 */

import { useMutation } from '@tanstack/react-query';
import {
  verifyEmail,
  resendVerificationCode,
  VerifyEmailInput,
} from '@/services/auth/emailVerificationService';

/**
 * Hook for email verification mutation
 * Returns React Query mutation with success/error handling
 */
export const useEmailVerification = () => {
  return useMutation({
    mutationFn: async (input: VerifyEmailInput) => {
      const result = await verifyEmail(input);

      if (!result.success) {
        // Map error codes to user-friendly error keys
        const errorCode = result.error?.code || 'UNKNOWN_ERROR';
        const errorMap: Record<string, string> = {
          INVALID_CODE: 'invalidCode',
          EXPIRED_CODE: 'expiredCode',
          TOO_MANY_ATTEMPTS: 'tooManyAttempts',
          ALREADY_VERIFIED: 'alreadyVerified',
          USER_NOT_FOUND: 'userNotFound',
        };

        const mappedError = errorMap[errorCode] || 'networkError';
        throw new Error(mappedError);
      }

      return result;
    },
    onError: (error: Error) => {
      // Error is already mapped to user-friendly key
      console.error('[useEmailVerification] Verification failed:', error.message);
    },
  });
};

/**
 * Hook for resending verification code
 * Returns React Query mutation with cooldown handling
 */
export const useResendVerification = () => {
  return useMutation({
    mutationFn: async (email: string) => {
      const result = await resendVerificationCode(email);

      if (!result.success) {
        // Map error codes to user-friendly error keys
        const errorCode = result.error?.code || 'UNKNOWN_ERROR';
        const errorMap: Record<string, string> = {
          RATE_LIMITED: 'rateLimited',
          USER_NOT_FOUND: 'userNotFound',
          INVALID_PARAMETER: 'invalidParameter',
        };

        const mappedError = errorMap[errorCode] || 'networkError';
        throw new Error(mappedError);
      }

      return result;
    },
    onError: (error: Error) => {
      // Error is already mapped to user-friendly key
      console.error('[useResendVerification] Resend failed:', error.message);
    },
  });
};
