/**
 * useEmailVerification Hook Tests (TDD - RED Phase)
 * Story 1.2.4: Email Verification Flow - Task 3
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEmailVerification, useResendVerification } from './useEmailVerification';
import * as emailVerificationService from '@/services/auth/emailVerificationService';
import React from 'react';

// Mock emailVerificationService
vi.mock('@/services/auth/emailVerificationService', () => ({
  verifyEmail: vi.fn(),
  resendVerificationCode: vi.fn(),
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe('useEmailVerification', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 1: should_callVerifyEmail_when_mutationInvoked
  it('should_callVerifyEmail_when_mutationInvoked', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: true,
    });

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '123456',
    };

    result.current.mutate(testInput);

    await waitFor(() => {
      expect(mockVerifyEmail).toHaveBeenCalledWith(testInput);
    });
  });

  // Test 2: should_returnSuccess_when_verificationSucceeds
  it('should_returnSuccess_when_verificationSucceeds', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: true,
    });

    const onSuccess = vi.fn();

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '123456',
    };

    result.current.mutate(testInput, { onSuccess });

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
      expect(result.current.isSuccess).toBe(true);
    });
  });

  // Test 3: should_returnError_when_verificationFails
  it('should_returnError_when_verificationFails', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: false,
      error: {
        code: 'INVALID_CODE',
        message: 'Invalid verification code',
      },
    });

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '999999',
    };

    result.current.mutate(testInput);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe('invalidCode');
    });
  });

  // Test 4: should_returnExpiredCodeError_when_codeExpired
  it('should_returnExpiredCodeError_when_codeExpired', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: false,
      error: {
        code: 'EXPIRED_CODE',
        message: 'Verification code has expired',
      },
    });

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '123456',
    };

    result.current.mutate(testInput);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe('expiredCode');
    });
  });

  // Test 5: should_returnTooManyAttemptsError_when_limitExceeded
  it('should_returnTooManyAttemptsError_when_limitExceeded', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: false,
      error: {
        code: 'TOO_MANY_ATTEMPTS',
        message: 'Too many failed attempts',
      },
    });

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '123456',
    };

    result.current.mutate(testInput);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe('tooManyAttempts');
    });
  });

  // Test 6: should_returnAlreadyVerifiedError_when_userAlreadyVerified
  it('should_returnAlreadyVerifiedError_when_userAlreadyVerified', async () => {
    const mockVerifyEmail = vi.mocked(emailVerificationService.verifyEmail);
    mockVerifyEmail.mockResolvedValue({
      success: false,
      error: {
        code: 'ALREADY_VERIFIED',
        message: 'Email is already verified',
      },
    });

    const { result } = renderHook(() => useEmailVerification(), {
      wrapper: createWrapper(),
    });

    const testInput = {
      email: 'john.doe@example.com',
      code: '123456',
    };

    result.current.mutate(testInput);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe('alreadyVerified');
    });
  });
});

describe('useResendVerification', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 7: should_callResendVerificationCode_when_mutationInvoked
  it('should_callResendVerificationCode_when_mutationInvoked', async () => {
    const mockResend = vi.mocked(emailVerificationService.resendVerificationCode);
    mockResend.mockResolvedValue({
      success: true,
    });

    const { result } = renderHook(() => useResendVerification(), {
      wrapper: createWrapper(),
    });

    const testEmail = 'john.doe@example.com';

    result.current.mutate(testEmail);

    await waitFor(() => {
      expect(mockResend).toHaveBeenCalledWith(testEmail);
    });
  });

  // Test 8: should_returnSuccess_when_resendSucceeds
  it('should_returnSuccess_when_resendSucceeds', async () => {
    const mockResend = vi.mocked(emailVerificationService.resendVerificationCode);
    mockResend.mockResolvedValue({
      success: true,
    });

    const onSuccess = vi.fn();

    const { result } = renderHook(() => useResendVerification(), {
      wrapper: createWrapper(),
    });

    const testEmail = 'john.doe@example.com';

    result.current.mutate(testEmail, { onSuccess });

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
      expect(result.current.isSuccess).toBe(true);
    });
  });

  // Test 9: should_returnError_when_resendFails
  it('should_returnError_when_resendFails', async () => {
    const mockResend = vi.mocked(emailVerificationService.resendVerificationCode);
    mockResend.mockResolvedValue({
      success: false,
      error: {
        code: 'RATE_LIMITED',
        message: 'Too many resend attempts',
      },
    });

    const { result } = renderHook(() => useResendVerification(), {
      wrapper: createWrapper(),
    });

    const testEmail = 'john.doe@example.com';

    result.current.mutate(testEmail);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe('rateLimited');
    });
  });
});
