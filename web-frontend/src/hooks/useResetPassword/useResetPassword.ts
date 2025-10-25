/**
 * useResetPassword Hook - Amplify v6 Direct Integration
 * Story 1.2.2a: Reset Password Confirmation
 *
 * GREEN Phase: Full implementation with Cognito error mapping
 */

import { useMutation } from '@tanstack/react-query';
import { confirmResetPassword } from 'aws-amplify/auth';

interface ResetPasswordInput {
  email: string;
  code: string;
  newPassword: string;
}

interface UseResetPasswordOptions {
  onSuccess?: () => void;
  onError?: (error: ResetPasswordError) => void;
}

export interface ResetPasswordError extends Error {
  type: 'invalidCode' | 'expiredCode' | 'invalidPassword' | 'rateLimitExceeded' | 'unknownError';
  statusCode?: number;
}

export const useResetPassword = (options?: UseResetPasswordOptions) => {
  return useMutation<void, ResetPasswordError, ResetPasswordInput>({
    mutationFn: async ({ email, code, newPassword }: ResetPasswordInput) => {
      try {
        // Amplify v6: confirmResetPassword updates password with code
        await confirmResetPassword({
          username: email,
          confirmationCode: code,
          newPassword: newPassword,
        });
      } catch (error: unknown) {
        console.error('[useResetPassword] Password reset failed:', error);

        const errorCode =
          (error as { code?: string; name?: string }).code ||
          (error as { code?: string; name?: string }).name ||
          'UNKNOWN_ERROR';

        // Map Cognito errors to app errors
        switch (errorCode) {
          case 'CodeMismatchException':
            throw Object.assign(new Error('Invalid verification code'), {
              type: 'invalidCode' as const,
              statusCode: 400,
            });
          case 'ExpiredCodeException':
            throw Object.assign(new Error('Verification code has expired'), {
              type: 'expiredCode' as const,
              statusCode: 400,
            });
          case 'InvalidPasswordException':
            throw Object.assign(new Error('Password does not meet requirements'), {
              type: 'invalidPassword' as const,
              statusCode: 400,
            });
          case 'LimitExceededException':
            throw Object.assign(new Error('Too many password reset attempts'), {
              type: 'rateLimitExceeded' as const,
              statusCode: 429,
            });
          default:
            throw Object.assign(
              new Error((error as { message?: string }).message || 'Password reset failed'),
              {
                type: 'unknownError' as const,
                statusCode: 500,
              }
            );
        }
      }
    },
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};
