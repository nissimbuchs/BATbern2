/**
 * ResetPasswordForm Component Tests (TDD - RED Phase)
 * Story 1.2.2a: Reset Password Confirmation
 *
 * Tests following the pattern established in Story 1.2.1 (i18n Foundation)
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { ResetPasswordForm } from './ResetPasswordForm';

// Mock useResetPassword hook
vi.mock('@/hooks/useResetPassword', () => ({
  useResetPassword: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
    error: null,
    reset: vi.fn(),
  })),
}));

// Mock useNavigate and useSearchParams
const mockNavigate = vi.fn();
const mockSearchParams = new URLSearchParams('?email=user@example.com');
const mockSetSearchParams = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: () => [mockSearchParams, mockSetSearchParams],
  };
});

import { useResetPassword, type ResetPasswordError } from '@/hooks/useResetPassword';

type MockedUseResetPassword = ReturnType<typeof useResetPassword>;

describe('ResetPasswordForm Component', () => {
  let queryClient: QueryClient;

  const renderComponent = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    return render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nextProvider i18n={i18n}>
            <ResetPasswordForm />
          </I18nextProvider>
        </BrowserRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockClear();
    vi.useRealTimers(); // Ensure real timers for each test
  });

  /**
   * Test Group 1: Form Rendering (AC 1-8)
   * Tests that all form fields and UI elements render correctly
   */
  describe('Form Rendering', () => {
    it('should_renderAllFormFields_when_componentMounted', () => {
      // Test 1.1: Render all form fields (AC 1-8)
      renderComponent();

      expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/6-digit.*code/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/new password/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /reset password/i })).toBeInTheDocument();
      expect(screen.getByText(/back to login/i)).toBeInTheDocument();
    });

    it('should_displayEmailFromURLParam_when_provided', () => {
      // Test 1.2: Display email from URL parameter (AC 1)
      renderComponent();

      const emailField = screen.getByLabelText(/email/i) as HTMLInputElement;
      expect(emailField.value).toBe('user@example.com');
      expect(emailField).toBeDisabled(); // Email should be read-only
    });

    it('should_showPasswordStrengthIndicator_when_typingPassword', async () => {
      // Test 1.3: Password strength indicator (AC 5)
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      await user.type(newPasswordField, 'weak');

      // Should show password strength indicator
      await waitFor(() => {
        expect(screen.getByText(/weak|medium|strong/i)).toBeInTheDocument();
      });
    });

    it('should_displayPasswordRequirements_when_componentMounted', () => {
      // Test 1.4: Password requirements display (AC 6)
      renderComponent();

      expect(
        screen.getByText(/at least 8 characters.*uppercase.*lowercase.*number/i)
      ).toBeInTheDocument();
    });
  });

  /**
   * Test Group 2: Form Validation (AC 9-13)
   * Tests client-side validation with real-time feedback
   */
  describe('Form Validation', () => {
    it('should_validateCodeLength_when_codeEntered', async () => {
      // Test 2.1: Code must be exactly 6 digits (AC 9)
      const user = userEvent.setup();
      renderComponent();

      const codeField = screen.getByLabelText(/6-digit.*code/i);
      await user.type(codeField, '123'); // Too short
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/invalid.*code/i)).toBeInTheDocument();
      });
    });

    it('should_validatePasswordRequirements_when_passwordEntered', async () => {
      // Test 2.2: Password must meet requirements (AC 10)
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      await user.type(newPasswordField, 'short'); // Too short, no uppercase, no number
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/does not meet requirements/i)).toBeInTheDocument();
      });
    });

    it('should_validatePasswordMatch_when_confirmPasswordEntered', async () => {
      // Test 2.3: Confirm password must match (AC 11)
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      const confirmPasswordField = screen.getByLabelText(/confirm password/i);

      await user.type(newPasswordField, 'ValidPassword123');
      await user.type(confirmPasswordField, 'DifferentPassword123');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
      });
    });

    it('should_disableSubmitButton_when_formInvalid', async () => {
      // Test 2.4: Submit button disabled when form invalid (AC 12)
      const user = userEvent.setup();
      renderComponent();

      const submitButton = screen.getByRole('button', { name: /reset password/i });
      expect(submitButton).toBeDisabled();

      // Fill partial form
      const codeField = screen.getByLabelText(/6-digit.*code/i);
      await user.type(codeField, '123456');

      // Still disabled because passwords not filled
      expect(submitButton).toBeDisabled();
    });

    it('should_enableSubmitButton_when_formValid', async () => {
      // Test 2.5: Submit button enabled when form valid
      const user = userEvent.setup();
      renderComponent();

      const codeField = screen.getByLabelText(/6-digit.*code/i);
      const newPasswordField = screen.getByLabelText(/new password/i);
      const confirmPasswordField = screen.getByLabelText(/confirm password/i);

      await user.type(codeField, '123456');
      await user.type(newPasswordField, 'ValidPassword123');
      await user.type(confirmPasswordField, 'ValidPassword123');

      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /reset password/i });
        expect(submitButton).toBeEnabled();
      });
    });
  });

  /**
   * Test Group 3: Password Strength Indicator (AC 5)
   * Tests real-time password strength feedback
   */
  describe('Password Strength', () => {
    it('should_showWeakStrength_when_simplePasswordEntered', async () => {
      // Test 3.1: Weak password indicator
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      await user.type(newPasswordField, 'password1'); // weak - all lowercase + number

      await waitFor(() => {
        expect(screen.getByText(/weak/i)).toBeInTheDocument();
      });
    });

    it('should_showMediumStrength_when_moderatePasswordEntered', async () => {
      // Test 3.2: Medium password indicator
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      await user.type(newPasswordField, 'Password1'); // medium - upper + lower + number

      await waitFor(() => {
        expect(screen.getByText(/medium/i)).toBeInTheDocument();
      });
    });

    it('should_showStrongStrength_when_complexPasswordEntered', async () => {
      // Test 3.3: Strong password indicator
      const user = userEvent.setup();
      renderComponent();

      const newPasswordField = screen.getByLabelText(/new password/i);
      await user.type(newPasswordField, 'MyStr0ng!Pass'); // strong - upper + lower + number + special

      await waitFor(() => {
        expect(screen.getByText(/strong/i)).toBeInTheDocument();
      });
    });
  });

  /**
   * Test Group 4: Reset Confirmation (AC 14-16)
   * Tests the password reset confirmation flow
   */
  describe('Reset Confirmation', () => {
    it('should_callAmplifyAPI_when_formSubmitted', async () => {
      // Test 4.1: Call useResetPassword hook on submit (AC 14)
      const mockMutate = vi.fn();
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: mockMutate,
        isPending: false,
        isError: false,
        isSuccess: false,
        error: null,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      const user = userEvent.setup();
      renderComponent();

      const codeField = screen.getByLabelText(/6-digit.*code/i);
      const newPasswordField = screen.getByLabelText(/new password/i);
      const confirmPasswordField = screen.getByLabelText(/confirm password/i);
      const submitButton = screen.getByRole('button', { name: /reset password/i });

      await user.type(codeField, '123456');
      await user.type(newPasswordField, 'NewPassword123');
      await user.type(confirmPasswordField, 'NewPassword123');
      await user.click(submitButton);

      await waitFor(() => {
        expect(mockMutate).toHaveBeenCalledWith(
          expect.objectContaining({
            email: 'user@example.com',
            code: '123456',
            newPassword: 'NewPassword123',
          }),
          expect.objectContaining({
            onSuccess: expect.any(Function),
          })
        );
      });
    });

    it('should_redirectToLogin_when_resetSuccessful', async () => {
      // Test 4.2: Redirect to login after success (AC 15)
      const mockMutate = vi.fn((_, options) => {
        options?.onSuccess?.();
      });

      vi.mocked(useResetPassword).mockReturnValue({
        mutate: mockMutate,
        isPending: false,
        isError: false,
        isSuccess: false,
        error: null,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      const user = userEvent.setup();
      renderComponent();

      const codeField = screen.getByLabelText(/6-digit.*code/i);
      const newPasswordField = screen.getByLabelText(/new password/i);
      const confirmPasswordField = screen.getByLabelText(/confirm password/i);
      const submitButton = screen.getByRole('button', { name: /reset password/i });

      await user.type(codeField, '123456');
      await user.type(newPasswordField, 'NewPassword123');
      await user.type(confirmPasswordField, 'NewPassword123');
      await user.click(submitButton);

      // Wait for the 2-second setTimeout to trigger navigation
      await waitFor(
        () => {
          expect(mockNavigate).toHaveBeenCalledWith('/login', expect.any(Object));
        },
        { timeout: 3000 }
      );
    }, 15000); // Test timeout set to 15 seconds

    it('should_displaySuccessMessage_when_resetSuccessful', async () => {
      // Test 4.3: Show success message (AC 15)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: false,
        isSuccess: true,
        error: null,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      await waitFor(() => {
        expect(screen.getByText(/successfully reset/i)).toBeInTheDocument();
      });
    });
  });

  /**
   * Test Group 5: Error Handling (AC 17-22)
   * Tests all error scenarios with proper error messages
   */
  describe('Error Handling', () => {
    it('should_displayError_when_codeInvalid', () => {
      // Test 5.1: Invalid code error (AC 17)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        isSuccess: false,
        error: { type: 'invalidCode', message: 'Invalid code' } as ResetPasswordError,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      expect(screen.getByText(/invalid.*code/i)).toBeInTheDocument();
    });

    it('should_displayError_when_codeExpired', () => {
      // Test 5.2: Expired code error (AC 18)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        isSuccess: false,
        error: { type: 'expiredCode', message: 'Code expired' } as ResetPasswordError,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      expect(screen.getByText(/expired.*request.*new/i)).toBeInTheDocument();
    });

    it('should_linkToForgotPassword_when_codeExpired', () => {
      // Test 5.3: Link to forgot password when code expired (AC 18)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        isSuccess: false,
        error: { type: 'expiredCode', message: 'Code expired' } as ResetPasswordError,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      const requestNewCodeButton = screen.getByRole('button', { name: /request new code/i });
      expect(requestNewCodeButton).toBeInTheDocument();
    });

    it('should_displayError_when_passwordInvalid', () => {
      // Test 5.4: Invalid password error (AC 19)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        isSuccess: false,
        error: { type: 'invalidPassword', message: 'Password invalid' } as ResetPasswordError,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      expect(screen.getByText(/does not meet requirements/i)).toBeInTheDocument();
    });

    it('should_displayError_when_rateLimitExceeded', () => {
      // Test 5.5: Rate limit error (AC 20)
      vi.mocked(useResetPassword).mockReturnValue({
        mutate: vi.fn(),
        isPending: false,
        isError: true,
        isSuccess: false,
        error: { type: 'rateLimitExceeded', message: 'Too many attempts' } as ResetPasswordError,
        reset: vi.fn(),
      } as unknown as MockedUseResetPassword);

      renderComponent();

      expect(screen.getByText(/too many attempts/i)).toBeInTheDocument();
    });
  });

  /**
   * Accessibility Tests
   * Ensures WCAG 2.1 AA compliance
   */
  describe('Accessibility', () => {
    it('should_haveProperLabels_when_rendered', () => {
      renderComponent();

      expect(screen.getByLabelText(/email/i)).toHaveAccessibleName();
      expect(screen.getByLabelText(/6-digit.*code/i)).toHaveAccessibleName();
      expect(screen.getByLabelText(/new password/i)).toHaveAccessibleName();
      expect(screen.getByLabelText(/confirm password/i)).toHaveAccessibleName();
    });

    it('should_supportKeyboardNavigation_when_tabPressed', async () => {
      const user = userEvent.setup();
      renderComponent();

      // First tab goes to "Back to Login" link
      await user.tab();
      expect(screen.getByText(/back to login/i)).toHaveFocus();

      // Second tab goes to code field
      await user.tab();
      expect(screen.getByLabelText(/6-digit.*code/i)).toHaveFocus();

      // Third tab goes to new password field
      await user.tab();
      expect(screen.getByLabelText(/new password/i)).toHaveFocus();
    });
  });
});
