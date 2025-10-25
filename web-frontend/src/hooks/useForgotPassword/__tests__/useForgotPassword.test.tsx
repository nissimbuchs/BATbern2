import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useForgotPassword } from '../useForgotPassword';
import { resetPassword } from 'aws-amplify/auth';
import React, { type ReactNode } from 'react';

vi.mock('aws-amplify/auth', () => ({
  resetPassword: vi.fn(),
}));

describe('useForgotPassword', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('should_sendResetCode_when_validEmailProvided', async () => {
    vi.mocked(resetPassword).mockResolvedValue({
      isPasswordReset: false,
      nextStep: { resetPasswordStep: 'CONFIRM_RESET_PASSWORD_WITH_CODE' },
    });

    const { result } = renderHook(() => useForgotPassword(), { wrapper });

    result.current.mutate('test@example.com');

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(resetPassword).toHaveBeenCalledWith({ username: 'test@example.com' });
  });

  it('should_throwRateLimitError_when_tooManyAttempts', async () => {
    const limitError = Object.assign(new Error('Too many attempts'), {
      code: 'LimitExceededException',
    });
    vi.mocked(resetPassword).mockRejectedValue(limitError);

    const { result } = renderHook(() => useForgotPassword(), { wrapper });

    result.current.mutate('test@example.com');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.type).toBe('rateLimitExceeded');
    expect(result.current.error?.statusCode).toBe(429);
  });

  it('should_returnSuccess_when_userNotFound', async () => {
    const notFoundError = Object.assign(new Error('User not found'), {
      code: 'UserNotFoundException',
    });
    vi.mocked(resetPassword).mockRejectedValue(notFoundError);

    const { result } = renderHook(() => useForgotPassword(), { wrapper });

    result.current.mutate('nonexistent@example.com');

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });
  });

  it('should_throwInvalidParameterError_when_invalidEmail', async () => {
    const invalidError = Object.assign(new Error('Invalid parameter'), {
      code: 'InvalidParameterException',
    });
    vi.mocked(resetPassword).mockRejectedValue(invalidError);

    const { result } = renderHook(() => useForgotPassword(), { wrapper });

    result.current.mutate('invalid-email');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.type).toBe('invalidParameter');
    expect(result.current.error?.statusCode).toBe(400);
  });

  it('should_throwUnknownError_when_unexpectedError', async () => {
    const unknownError = new Error('Something went wrong');
    vi.mocked(resetPassword).mockRejectedValue(unknownError);

    const { result } = renderHook(() => useForgotPassword(), { wrapper });

    result.current.mutate('test@example.com');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.type).toBe('unknownError');
    expect(result.current.error?.statusCode).toBe(500);
  });

  it('should_callOnSuccess_when_resetSucceeds', async () => {
    vi.mocked(resetPassword).mockResolvedValue({
      isPasswordReset: false,
      nextStep: { resetPasswordStep: 'CONFIRM_RESET_PASSWORD_WITH_CODE' },
    });

    const onSuccess = vi.fn();
    const { result } = renderHook(() => useForgotPassword({ onSuccess }), { wrapper });

    result.current.mutate('test@example.com');

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('should_callOnError_when_resetFails', async () => {
    const limitError = Object.assign(new Error('Too many attempts'), {
      code: 'LimitExceededException',
    });
    vi.mocked(resetPassword).mockRejectedValue(limitError);

    const onError = vi.fn();
    const { result } = renderHook(() => useForgotPassword({ onError }), { wrapper });

    result.current.mutate('test@example.com');

    await waitFor(() => {
      expect(onError).toHaveBeenCalled();
    });

    const error = onError.mock.calls[0][0];
    expect(error.type).toBe('rateLimitExceeded');
    expect(onError.mock.calls[0][1]).toBe('test@example.com');
  });
});
