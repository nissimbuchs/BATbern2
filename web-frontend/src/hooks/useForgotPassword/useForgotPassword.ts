/**
 * useForgotPassword Hook - Amplify v6 Direct Integration
 * Story 1.2.2: Implement Forgot Password Flow (UPDATED October 2025)
 *
 * ARCHITECTURE CHANGE: Migrated from backend API to Amplify v6 direct integration
 * OLD: Frontend → Backend API → Cognito
 * NEW: Frontend → Amplify v6 → Cognito (direct)
 */

import { useMutation } from '@tanstack/react-query';
import { resetPassword } from 'aws-amplify/auth';

interface UseForgotPasswordOptions {
  onSuccess?: () => void;
  onError?: (error: ForgotPasswordError) => void;
}

// Extended Error type to include rate limit info
export interface ForgotPasswordError extends Error {
  type: 'rateLimitExceeded' | 'userNotFound' | 'invalidParameter' | 'unknownError' | 'networkError';
  statusCode?: number;
}

export const useForgotPassword = (options?: UseForgotPasswordOptions) => {
  return useMutation<void, ForgotPasswordError, string>({
    mutationFn: async (email: string) => {
      try {
        // Amplify v6: resetPassword sends code to user's email automatically
        const result = await resetPassword({ username: email });

        console.log('[useForgotPassword] Password reset code sent:', result);

        // Cognito automatically sends email with 6-digit code
        // User will enter code in Story 1.2.2a confirmation screen
      } catch (error: unknown) {
        console.error('[useForgotPassword] Password reset failed:', error);

        const errorCode =
          (error as { code?: string; name?: string }).code ||
          (error as { code?: string; name?: string }).name ||
          'UNKNOWN_ERROR';

        // Map Cognito errors to app errors
        switch (errorCode) {
          case 'LimitExceededException':
            throw Object.assign(new Error('Too many password reset attempts'), {
              type: 'rateLimitExceeded' as const,
              statusCode: 429,
            });
          case 'UserNotFoundException':
            // Email enumeration prevention: Don't throw, return success
            console.log('[useForgotPassword] User not found (suppressed for security)');
            return; // Success (enumeration prevention)
          case 'InvalidParameterException':
            throw Object.assign(new Error('Invalid email address'), {
              type: 'invalidParameter' as const,
              statusCode: 400,
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
