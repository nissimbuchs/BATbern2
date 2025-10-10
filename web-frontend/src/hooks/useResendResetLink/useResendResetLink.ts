/**
 * useResendResetLink Hook (TDD - GREEN Phase)
 * Story 1.2.2: Implement Forgot Password Flow
 * Task 7: Enhanced error handling with rate limit and retry support
 */

import { useMutation } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';
import { ExtendedError } from '../useForgotPassword/useForgotPassword';

interface ResendResetLinkRequest {
  email: string;
}

interface ResendResetLinkResponse {
  success: boolean;
  message: string;
}

interface UseResendResetLinkOptions {
  onSuccess?: (data: ResendResetLinkResponse) => void;
  onError?: (error: ExtendedError) => void;
}

export const useResendResetLink = (options?: UseResendResetLinkOptions) => {
  return useMutation<ResendResetLinkResponse, ExtendedError, string>({
    mutationFn: async (email: string) => {
      const response = await apiClient.post<ResendResetLinkResponse>('/v1/auth/resend-reset-link', {
        email,
      } as ResendResetLinkRequest);
      return response.data;
    },
    onSuccess: options?.onSuccess,
    onError: (error: unknown) => {
      // Handle rate limit error (429)
      if ((error as { response?: { status?: number } }).response?.status === 429) {
        const axiosError = error as {
          response: {
            status: number;
            headers?: { 'retry-after'?: string };
            data?: { message?: string };
          };
        };
        const retryAfter = axiosError.response.headers?.['retry-after']
          ? parseInt(axiosError.response.headers['retry-after'])
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
      const errorWithResponse = error as {
        response?: { status?: number; data?: { message?: string } };
      };
      if (!errorWithResponse.response) {
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
        new Error(errorWithResponse.response?.data?.message || 'Unknown error'),
        {
          type: 'unknownError' as const,
          statusCode: errorWithResponse.response?.status || 500,
        }
      );

      options?.onError?.(transformedError);
      throw transformedError;
    },
  });
};
