/**
 * Email Verification Service Tests (Story 1.2.4)
 *
 * Tests for verifyEmail and resendVerificationCode,
 * including all Cognito error code mappings.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { verifyEmail, resendVerificationCode } from './emailVerificationService';

vi.mock('aws-amplify/auth', () => ({
  confirmSignUp: vi.fn(),
  resendSignUpCode: vi.fn(),
}));

import * as amplifyAuth from 'aws-amplify/auth';

const mockConfirmSignUp = vi.mocked(amplifyAuth.confirmSignUp);
const mockResendSignUpCode = vi.mocked(amplifyAuth.resendSignUpCode);

describe('emailVerificationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── verifyEmail ──────────────────────────────────────────────────────────────

  describe('verifyEmail', () => {
    it('should return success on valid code', async () => {
      mockConfirmSignUp.mockResolvedValue({
        isSignUpComplete: true,
        nextStep: { signUpStep: 'DONE' },
      });

      const result = await verifyEmail({ email: 'user@example.com', code: '123456' });

      expect(mockConfirmSignUp).toHaveBeenCalledWith({
        username: 'user@example.com',
        confirmationCode: '123456',
      });
      expect(result).toEqual({ success: true });
    });

    it('should return INVALID_CODE for CodeMismatchException', async () => {
      const err = Object.assign(new Error('code mismatch'), { name: 'CodeMismatchException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: 'wrong' });

      expect(result).toEqual({
        success: false,
        error: { code: 'INVALID_CODE', message: 'Invalid verification code' },
      });
    });

    it('should return EXPIRED_CODE for ExpiredCodeException', async () => {
      const err = Object.assign(new Error('expired'), { name: 'ExpiredCodeException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: 'old' });

      expect(result.error?.code).toBe('EXPIRED_CODE');
    });

    it('should return TOO_MANY_ATTEMPTS for TooManyFailedAttemptsException', async () => {
      const err = Object.assign(new Error('too many'), { name: 'TooManyFailedAttemptsException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: '000000' });

      expect(result.error?.code).toBe('TOO_MANY_ATTEMPTS');
    });

    it('should return TOO_MANY_ATTEMPTS for LimitExceededException', async () => {
      const err = Object.assign(new Error('limit'), { name: 'LimitExceededException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: '000000' });

      expect(result.error?.code).toBe('TOO_MANY_ATTEMPTS');
    });

    it('should return ALREADY_VERIFIED for NotAuthorizedException', async () => {
      const err = Object.assign(new Error('already verified'), { name: 'NotAuthorizedException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: '123456' });

      expect(result.error?.code).toBe('ALREADY_VERIFIED');
    });

    it('should return USER_NOT_FOUND for UserNotFoundException', async () => {
      const err = Object.assign(new Error('not found'), { name: 'UserNotFoundException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'nobody@example.com', code: '123456' });

      expect(result.error?.code).toBe('USER_NOT_FOUND');
    });

    it('should return unknown error code for unrecognised exceptions', async () => {
      const err = Object.assign(new Error('weird failure'), { name: 'SomeOtherException' });
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: '123456' });

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('SomeOtherException');
      expect(result.error?.message).toBe('weird failure');
    });

    it('should use code field when name is absent', async () => {
      const err = { code: 'CodeMismatchException', message: 'mismatch' };
      mockConfirmSignUp.mockRejectedValue(err);

      const result = await verifyEmail({ email: 'user@example.com', code: '000' });

      expect(result.error?.code).toBe('INVALID_CODE');
    });
  });

  // ── resendVerificationCode ───────────────────────────────────────────────────

  describe('resendVerificationCode', () => {
    it('should return success when code is resent', async () => {
      mockResendSignUpCode.mockResolvedValue({
        destination: 'user@example.com',
        deliveryMedium: 'EMAIL',
        attributeName: 'email',
      });

      const result = await resendVerificationCode('user@example.com');

      expect(mockResendSignUpCode).toHaveBeenCalledWith({ username: 'user@example.com' });
      expect(result).toEqual({ success: true });
    });

    it('should return RATE_LIMITED for LimitExceededException', async () => {
      const err = Object.assign(new Error('limit'), { name: 'LimitExceededException' });
      mockResendSignUpCode.mockRejectedValue(err);

      const result = await resendVerificationCode('user@example.com');

      expect(result.error?.code).toBe('RATE_LIMITED');
    });

    it('should return RATE_LIMITED for TooManyRequestsException', async () => {
      const err = Object.assign(new Error('rate limit'), { name: 'TooManyRequestsException' });
      mockResendSignUpCode.mockRejectedValue(err);

      const result = await resendVerificationCode('user@example.com');

      expect(result.error?.code).toBe('RATE_LIMITED');
    });

    it('should return USER_NOT_FOUND for UserNotFoundException', async () => {
      const err = Object.assign(new Error('not found'), { name: 'UserNotFoundException' });
      mockResendSignUpCode.mockRejectedValue(err);

      const result = await resendVerificationCode('nobody@example.com');

      expect(result.error?.code).toBe('USER_NOT_FOUND');
    });

    it('should return INVALID_PARAMETER for InvalidParameterException', async () => {
      const err = Object.assign(new Error('bad param'), { name: 'InvalidParameterException' });
      mockResendSignUpCode.mockRejectedValue(err);

      const result = await resendVerificationCode('not-an-email');

      expect(result.error?.code).toBe('INVALID_PARAMETER');
    });

    it('should return raw error code for unknown exceptions', async () => {
      const err = Object.assign(new Error('mystery'), { name: 'MysteryException' });
      mockResendSignUpCode.mockRejectedValue(err);

      const result = await resendVerificationCode('user@example.com');

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('MysteryException');
      expect(result.error?.message).toBe('mystery');
    });

    it('should use UNKNOWN_ERROR when no code or name available', async () => {
      mockResendSignUpCode.mockRejectedValue({ message: 'something' });

      const result = await resendVerificationCode('user@example.com');

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('UNKNOWN_ERROR');
    });
  });
});
