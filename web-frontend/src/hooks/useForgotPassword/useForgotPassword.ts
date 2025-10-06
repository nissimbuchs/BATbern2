/**
 * useForgotPassword Hook (TDD - GREEN Phase)
 * Story 1.2.2: Implement Forgot Password Flow
 * Task 7: Enhanced error handling with rate limit and retry support
 */

import { useMutation } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';

interface ForgotPasswordRequest {
  email: string;
}

interface ForgotPasswordResponse {
  success: boolean;
  message: string;
}

interface UseForgotPasswordOptions {
  onSuccess?: (data: ForgotPasswordResponse) => void;
  onError?: (error: ExtendedError) => void;
}

// Extended Error type to include rate limit info
export interface ExtendedError extends Error {
  type: 'rateLimitExceeded' | 'networkError' | 'unknownError';
  retryAfter?: number; // seconds until retry is allowed
  statusCode?: number;
}

export const useForgotPassword = (options?: UseForgotPasswordOptions) => {
  return useMutation<ForgotPasswordResponse, ExtendedError, string>({
    mutationFn: async (email: string) => {
      const response = await apiClient.post<ForgotPasswordResponse>('/v1/auth/forgot-password', {
        email,
      } as ForgotPasswordRequest);
      return response.data;
    },
    onSuccess: options?.onSuccess,
    onError: (error: any) => {
      // Handle rate limit error (429)
      if (error.response?.status === 429) {
        const retryAfter = error.response.headers?.['retry-after']
          ? parseInt(error.response.headers['retry-after'])
          : 60; // Default to 60 seconds if no Retry-After header

        const transformedError: ExtendedError = Object.assign(new Error('Rate limit exceeded'), {
          type: 'rateLimitExceeded' as const,
          retryAfter,
          statusCode: 429,
        });

        options?.onError?.(transformedError);
        throw transformedError;
      }

      // Handle network errors (no response from server)
      if (!error.response) {
        const transformedError: ExtendedError = Object.assign(
          new Error('Network connection failed'),
          {
            type: 'networkError' as const,
            statusCode: 0,
          }
        );

        options?.onError?.(transformedError);
        throw transformedError;
      }

      // Handle other errors
      const transformedError: ExtendedError = Object.assign(
        new Error(error.response?.data?.message || 'Unknown error'),
        {
          type: 'unknownError' as const,
          statusCode: error.response?.status || 500,
        }
      );

      options?.onError?.(transformedError);
      throw transformedError;
    },
  });
};
