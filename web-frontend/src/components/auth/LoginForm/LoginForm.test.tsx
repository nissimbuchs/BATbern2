/**
 * LoginForm Component Tests (TDD - Fixed)
 * Story 1.2.1: Frontend Authentication Integration with i18n
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from './LoginForm';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';

// Mock useAuth hook
const mockSignIn = vi.fn();
const mockClearError = vi.fn();

const mockUseAuth = {
  signIn: mockSignIn,
  isLoading: false,
  error: null,
  clearError: mockClearError,
};

vi.mock('@hooks/useAuth', () => ({
  useAuth: () => mockUseAuth,
}));

// Create theme for MUI components
const theme = createTheme();

// Helper function to render with theme and i18n
const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <I18nextProvider i18n={i18n}>
      <ThemeProvider theme={theme}>{component}</ThemeProvider>
    </I18nextProvider>
  );
};

describe('LoginForm Component', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
    // Reset mock state
    Object.assign(mockUseAuth, {
      signIn: mockSignIn,
      isLoading: false,
      error: null,
      clearError: mockClearError,
    });
  });

  it('should_renderLoginFormFields_when_componentMounted', async () => {
    // Test 9.20: should_renderLoginFormFields_when_componentMounted
    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/enter your password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
      expect(screen.getByLabelText(/remember me/i)).toBeInTheDocument();
    });
  });

  it('should_validateEmailFormat_when_invalidEmailEntered', async () => {
    // Test 9.21: should_validateEmailFormat_when_invalidEmailEntered
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, 'invalid-email');
      await user.tab(); // Trigger blur event
    });

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
    });
  });

  it('should_validatePasswordRequired_when_passwordEmpty', async () => {
    // Test 9.22: should_validatePasswordRequired_when_passwordEmpty
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const passwordInput = screen.getByPlaceholderText(/enter your password/i);

    await act(async () => {
      await user.click(passwordInput);
      await user.tab(); // Trigger blur event
    });

    await waitFor(() => {
      expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    });
  });

  it('should_submitCredentials_when_validFormSubmitted', async () => {
    // Test 9.23: should_submitCredentials_when_validFormSubmitted
    const user = userEvent.setup();
    mockSignIn.mockResolvedValue(true); // Mock successful sign in

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByPlaceholderText(/enter your password/i);

    await act(async () => {
      await user.type(emailInput, 'organizer@batbern.ch');
      await user.tab(); // Trigger blur for email validation
      await user.type(passwordInput, 'ValidPassword123!');
      await user.tab(); // Trigger blur for password validation
    });

    // Wait for form to be valid
    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /sign in/i }));
    });

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith({
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: false,
      });
    });
  });

  it('should_handleRememberMeOption_when_checkboxSelected', async () => {
    // Test 9.24: should_handleRememberMeOption_when_checkboxSelected
    const user = userEvent.setup();
    mockSignIn.mockResolvedValue(true); // Mock successful sign in

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await act(async () => {
      await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
      await user.tab(); // Trigger validation
      await user.type(screen.getByPlaceholderText(/enter your password/i), 'password123');
      await user.tab(); // Trigger validation
      await user.click(screen.getByLabelText(/remember me/i));
    });

    // Wait for form to be valid
    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /sign in/i }));
    });

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: true }));
    });
  });

  it('should_displayErrorMessage_when_authenticationFails', async () => {
    // Test 9.25: should_displayErrorMessage_when_authenticationFails
    // Update mock to include error
    Object.assign(mockUseAuth, {
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
  });

  it('should_showLoadingState_when_authenticationInProgress', async () => {
    // Test 9.26: should_showLoadingState_when_authenticationInProgress
    // Update mock to show loading state
    Object.assign(mockUseAuth, {
      isLoading: true,
      error: null,
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).toBeDisabled();
      // Check for loading spinner
      expect(submitButton.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
    });
  });

  it('should_provideForgotPasswordLink_when_userNeedsPasswordReset', async () => {
    // Test 9.27: should_provideForgotPasswordLink_when_userNeedsPasswordReset
    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    expect(screen.getByText(/forgot password/i)).toBeInTheDocument();
  });

  it('should_clearErrorOnInputChange_when_userStartsTyping', async () => {
    // Test 9.28: should_clearErrorOnInputChange_when_userStartsTyping
    const user = userEvent.setup();

    // Set up mock with an error initially
    Object.assign(mockUseAuth, {
      error: { code: 'SOME_ERROR', message: 'Some error' },
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await act(async () => {
      await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
    });

    await waitFor(() => {
      expect(mockClearError).toHaveBeenCalled();
    });
  });
});
