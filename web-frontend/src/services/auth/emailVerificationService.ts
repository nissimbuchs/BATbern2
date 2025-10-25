/**
 * Email Verification Service Implementation
 * Story 1.2.4: Email Verification Flow
 *
 * Uses AWS Amplify v6 for direct Cognito integration (NO backend proxy needed)
 * Follows the same pattern as authService.ts (Story 1.2.3)
 */

import { confirmSignUp, resendSignUpCode } from 'aws-amplify/auth';

export interface VerifyEmailInput {
  email: string;
  code: string;
}

export interface VerifyEmailResult {
  success: boolean;
  error?: {
    code: string;
    message: string;
  };
}

/**
 * Verify user email with 6-digit code
 * Wraps Amplify's confirmSignUp function with error handling
 */
export async function verifyEmail(input: VerifyEmailInput): Promise<VerifyEmailResult> {
  try {
    const result = await confirmSignUp({
      username: input.email,
      confirmationCode: input.code,
    });

    console.log('[emailVerificationService] Email verified:', result);

    return {
      success: true,
    };
  } catch (error: unknown) {
    console.error('[emailVerificationService] Verification failed:', error);

    const errorCode =
      (error as { code?: string; name?: string }).code ||
      (error as { code?: string; name?: string }).name ||
      'UNKNOWN_ERROR';

    // Map Cognito error codes to user-friendly error codes
    switch (errorCode) {
      case 'CodeMismatchException':
        return {
          success: false,
          error: {
            code: 'INVALID_CODE',
            message: 'Invalid verification code',
          },
        };
      case 'ExpiredCodeException':
        return {
          success: false,
          error: {
            code: 'EXPIRED_CODE',
            message: 'Verification code has expired',
          },
        };
      case 'TooManyFailedAttemptsException':
      case 'LimitExceededException':
        return {
          success: false,
          error: {
            code: 'TOO_MANY_ATTEMPTS',
            message: 'Too many failed attempts',
          },
        };
      case 'NotAuthorizedException':
        return {
          success: false,
          error: {
            code: 'ALREADY_VERIFIED',
            message: 'Email is already verified',
          },
        };
      case 'UserNotFoundException':
        return {
          success: false,
          error: {
            code: 'USER_NOT_FOUND',
            message: 'User not found',
          },
        };
      default:
        return {
          success: false,
          error: {
            code: errorCode,
            message: (error as { message?: string }).message || 'Verification failed',
          },
        };
    }
  }
}

/**
 * Resend verification code to user email
 * Wraps Amplify's resendSignUpCode function with error handling
 */
export async function resendVerificationCode(email: string): Promise<VerifyEmailResult> {
  try {
    await resendSignUpCode({
      username: email,
    });

    console.log('[emailVerificationService] Verification code resent to:', email);

    return {
      success: true,
    };
  } catch (error: unknown) {
    console.error('[emailVerificationService] Resend failed:', error);

    const errorCode =
      (error as { code?: string; name?: string }).code ||
      (error as { code?: string; name?: string }).name ||
      'UNKNOWN_ERROR';

    // Map Cognito error codes
    switch (errorCode) {
      case 'LimitExceededException':
      case 'TooManyRequestsException':
        return {
          success: false,
          error: {
            code: 'RATE_LIMITED',
            message: 'Too many resend attempts, please wait',
          },
        };
      case 'UserNotFoundException':
        return {
          success: false,
          error: {
            code: 'USER_NOT_FOUND',
            message: 'User not found',
          },
        };
      case 'InvalidParameterException':
        return {
          success: false,
          error: {
            code: 'INVALID_PARAMETER',
            message: 'Invalid email address',
          },
        };
      default:
        return {
          success: false,
          error: {
            code: errorCode,
            message: (error as { message?: string }).message || 'Failed to resend code',
          },
        };
    }
  }
}
