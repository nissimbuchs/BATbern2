/**
 * useRegistration Hook Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 10
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useRegistration } from './useRegistration';
import { authService } from '@/services/auth/authService';
import React from 'react';

// Mock authService
vi.mock('@/services/auth/authService', () => ({
  authService: {
    signUp: vi.fn(),
  },
}));

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: {
      language: 'en',
    },
  }),
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

describe('useRegistration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 1: should_callAuthServiceSignUp_when_mutationInvoked
  it('should_callAuthServiceSignUp_when_mutationInvoked', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'John Doe',
      email: 'john.doe@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith({
        email: 'john.doe@example.com',
        password: 'Password123',
        confirmPassword: 'Password123',
        firstName: 'John',
        lastName: 'Doe',
        role: 'ATTENDEE',
        companyId: '',
        acceptTerms: true,
        language: 'en',
        newsletterOptIn: false,
      });
    });
  });

  // Test 2: should_splitFullName_when_nameHasMultipleWords
  it('should_splitFullName_when_nameHasMultipleWords', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'John Michael Doe',
      email: 'john.doe@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: true,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith(
        expect.objectContaining({
          firstName: 'John',
          lastName: 'Michael Doe',
        })
      );
    });
  });

  // Test 3: should_setRoleToAttendee_when_userRegisters (FR22)
  it('should_setRoleToAttendee_when_userRegisters', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Jane Smith',
      email: 'jane@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith(
        expect.objectContaining({
          role: 'ATTENDEE', // FR22: Always ATTENDEE for self-registration
        })
      );
    });
  });

  // Test 4: should_includeLanguagePreference_when_registered
  it('should_includeLanguagePreference_when_registered', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Test User',
      email: 'test@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith(
        expect.objectContaining({
          language: 'en',
        })
      );
    });
  });

  // Test 5: should_includeNewsletterOptIn_when_checkboxChecked
  it('should_includeNewsletterOptIn_when_checkboxChecked', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Newsletter User',
      email: 'newsletter@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: true,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith(
        expect.objectContaining({
          newsletterOptIn: true,
        })
      );
    });
  });

  // Test 6: should_throwError_when_signUpFails
  it('should_throwError_when_signUpFails', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: false,
      requiresConfirmation: false,
      error: {
        code: 'UsernameExistsException',
        message: 'User already exists',
      },
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Existing User',
      email: 'existing@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error).toBeDefined();
    });
  });

  // Test 7: should_returnEmail_when_registrationSuccessful
  it('should_returnEmail_when_registrationSuccessful', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Success User',
      email: 'success@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    let registrationResult;
    result.current.mutate(testData, {
      onSuccess: (data) => {
        registrationResult = data;
      },
    });

    await waitFor(() => {
      expect(registrationResult).toEqual({
        email: 'success@example.com',
        requiresConfirmation: true,
      });
    });
  });

  // Test 8: should_handleSingleWordName_when_providedwhen_providedwhen_provided
  it('should_handleSingleWordName_when_provided', async () => {
    const mockSignUp = vi.mocked(authService.signUp);
    mockSignUp.mockResolvedValue({
      success: true,
      requiresConfirmation: true,
    });

    const { result } = renderHook(() => useRegistration(), {
      wrapper: createWrapper(),
    });

    const testData = {
      fullName: 'Madonna',
      email: 'madonna@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: true,
      newsletterOptIn: false,
    };

    result.current.mutate(testData);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith(
        expect.objectContaining({
          firstName: 'Madonna',
          lastName: 'Madonna', // Falls back to first name if no last name
        })
      );
    });
  });
});
