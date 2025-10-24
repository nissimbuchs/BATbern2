/**
 * useResetPassword Hook Tests (TDD - RED Phase)
 * Story 1.2.2a: Reset Password Confirmation
 *
 * Tests for Amplify v6 confirmResetPassword integration with React Query
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useResetPassword, type ResetPasswordError } from './useResetPassword';

// Mock Amplify v6 auth module
vi.mock('aws-amplify/auth', () => ({
  confirmResetPassword: vi.fn(),
}));

import { confirmResetPassword } from 'aws-amplify/auth';

describe('useResetPassword Hook', () => {
  let queryClient: QueryClient;
  let wrapper: ({ children }: { children: ReactNode }) => JSX.Element;

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
        mutations: {
          retry: false,
        },
      },
    });

    wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    return wrapper;
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  /**
   * Test Group 4: Reset Confirmation (AC 14, 15, 16)
   * Tests the password reset confirmation flow with Amplify v6
   */
  describe('Reset Confirmation Flow', () => {
    it('should_callAmplifyAPI_when_formSubmitted', async () => {
      // Test 4.1: Call confirmResetPassword with correct parameters
      vi.mocked(confirmResetPassword).mockResolvedValue(undefined);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      const resetInput = {
        email: 'user@example.com',
        code: '123456',
        newPassword: 'NewPassword123!',
      };

      await act(async () => {
        result.current.mutate(resetInput);
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(confirmResetPassword).toHaveBeenCalledWith({
        username: 'user@example.com',
        confirmationCode: '123456',
        newPassword: 'NewPassword123!',
      });
    });

    it('should_returnSuccess_when_resetSuccessful', async () => {
      // Test 4.3: Display success message after successful reset
      vi.mocked(confirmResetPassword).mockResolvedValue(undefined);

      const onSuccessMock = vi.fn();

      const { result } = renderHook(() => useResetPassword({ onSuccess: onSuccessMock }), {
        wrapper: createWrapper(),
      });

      const resetInput = {
        email: 'user@example.com',
        code: '123456',
        newPassword: 'NewPassword123!',
      };

      await act(async () => {
        result.current.mutate(resetInput);
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(onSuccessMock).toHaveBeenCalled();
      });
    });
  });

  /**
   * Test Group 5: Error Handling (AC 17-22)
   * Tests all error scenarios with proper error type mapping
   */
  describe('Error Handling', () => {
    it('should_displayError_when_codeInvalid', async () => {
      // Test 5.1: Invalid code error (CodeMismatchException)
      const cognitoError = {
        name: 'CodeMismatchException',
        message: 'Invalid verification code provided',
      };

      vi.mocked(confirmResetPassword).mockRejectedValue(cognitoError);

      const onErrorMock = vi.fn();

      const { result } = renderHook(() => useResetPassword({ onError: onErrorMock }), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '999999',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('invalidCode');
      expect(error.statusCode).toBe(400);
      expect(onErrorMock).toHaveBeenCalled();
      // Verify first argument (error) matches expected error
      const calledError = onErrorMock.mock.calls[0][0] as ResetPasswordError;
      expect(calledError.type).toBe('invalidCode');
      expect(calledError.statusCode).toBe(400);
    });

    it('should_displayError_when_codeExpired', async () => {
      // Test 5.2: Expired code error (ExpiredCodeException)
      const cognitoError = {
        name: 'ExpiredCodeException',
        message: 'Verification code has expired',
      };

      vi.mocked(confirmResetPassword).mockRejectedValue(cognitoError);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('expiredCode');
      expect(error.statusCode).toBe(400);
    });

    it('should_displayError_when_passwordInvalid', async () => {
      // Test 5.4: Password doesn't meet requirements (InvalidPasswordException)
      const cognitoError = {
        name: 'InvalidPasswordException',
        message: 'Password does not meet requirements',
      };

      vi.mocked(confirmResetPassword).mockRejectedValue(cognitoError);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'weak',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('invalidPassword');
      expect(error.statusCode).toBe(400);
    });

    it('should_displayError_when_rateLimitExceeded', async () => {
      // Test 5.5: Too many attempts (LimitExceededException)
      const cognitoError = {
        name: 'LimitExceededException',
        message: 'Too many password reset attempts',
      };

      vi.mocked(confirmResetPassword).mockRejectedValue(cognitoError);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('rateLimitExceeded');
      expect(error.statusCode).toBe(429);
    });

    it('should_displayGenericError_when_unknownErrorOccurs', async () => {
      // Test: Generic error handling for unexpected errors
      const unknownError = {
        name: 'UnknownError',
        message: 'Something went wrong',
      };

      vi.mocked(confirmResetPassword).mockRejectedValue(unknownError);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('unknownError');
      expect(error.statusCode).toBe(500);
    });

    it('should_handleNetworkError_when_requestFails', async () => {
      // Test: Network error handling
      const networkError = new Error('Network request failed');

      vi.mocked(confirmResetPassword).mockRejectedValue(networkError);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      const error = result.current.error as ResetPasswordError;
      expect(error.type).toBe('unknownError');
    });
  });

  /**
   * Loading States Tests
   * Tests mutation loading states
   */
  describe('Loading States', () => {
    it('should_showLoadingState_when_mutationInProgress', async () => {
      // Test: Loading state during API call
      vi.mocked(confirmResetPassword).mockReturnValue(
        new Promise(() => {}) // Never resolves
      );

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isPending).toBe(true);
      });
    });

    it('should_clearLoadingState_when_mutationCompletes', async () => {
      // Test: Loading state clears after success
      vi.mocked(confirmResetPassword).mockResolvedValue(undefined);

      const { result } = renderHook(() => useResetPassword(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        result.current.mutate({
          email: 'user@example.com',
          code: '123456',
          newPassword: 'NewPassword123!',
        });
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
        expect(result.current.isPending).toBe(false);
      });
    });
  });
});
