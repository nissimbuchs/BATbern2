/**
 * ForgotPasswordForm Component Tests (TDD - RED Phase)
 * Story 1.2.2: Implement Forgot Password Flow
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';

// Mock useForgotPassword hook
const mockForgotPassword = vi.fn();

const mockUseForgotPassword = {
  mutate: mockForgotPassword,
  isPending: false,
  isSuccess: false,
  isError: false,
  error: null,
  data: null,
};

vi.mock('@hooks/useForgotPassword', () => ({
  useForgotPassword: () => mockUseForgotPassword,
}));

// Create theme for MUI components
const theme = createTheme();

// Create a new QueryClient for each test
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

// Helper function to render with theme and i18n
const renderWithTheme = (component: React.ReactElement) => {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <ThemeProvider theme={theme}>{component}</ThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>
  );
};

describe('ForgotPasswordForm Component - Rendering Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
    // Reset mock state
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_renderForgotPasswordForm_when_pageLoads', async () => {
    // Test 1.1: should_renderForgotPasswordForm_when_pageLoads
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/reset password/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /send link/i })).toBeInTheDocument();
      expect(screen.getByText(/back to login/i)).toBeInTheDocument();
    });
  });

  it('should_displayGermanText_when_languageIsGerman', async () => {
    // Test 1.2: should_displayGermanText_when_languageIsGerman
    await i18n.changeLanguage('de');

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/passwort zurücksetzen/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/e-mail-adresse/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /link senden/i })).toBeInTheDocument();
    });
  });

  it('should_displayEnglishText_when_languageIsEnglish', async () => {
    // Test 1.3: should_displayEnglishText_when_languageIsEnglish
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/reset password/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });
  });

  it('should_displaySubtitle_when_formRendered', async () => {
    // Test: should_displaySubtitle_when_formRendered
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(
        screen.getByText(/enter your email and we'll send you a reset link/i)
      ).toBeInTheDocument();
    });
  });

  it('should_displayExpirationNotice_when_formRendered', async () => {
    // Test: should_displayExpirationNotice_when_formRendered
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/link will expire in 1 hour/i)).toBeInTheDocument();
    });
  });
});

describe('ForgotPasswordForm Component - Email Validation Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_validateEmail_when_invalidFormat', async () => {
    // Test 1.4: should_validateEmail_when_invalidFormat
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
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

  it('should_showRequiredError_when_emailEmpty', async () => {
    // Test: should_showRequiredError_when_emailEmpty
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.click(emailInput);
      await user.tab(); // Trigger blur event
    });

    await waitFor(() => {
      expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    });
  });

  it('should_showMaxLengthError_when_emailTooLong', async () => {
    // Test: should_showMaxLengthError_when_emailTooLong
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);
    const longEmail = 'a'.repeat(250) + '@example.com'; // Over 255 characters

    await act(async () => {
      await user.type(emailInput, longEmail);
      await user.tab(); // Trigger blur event
    });

    await waitFor(() => {
      expect(screen.getByText(/email too long/i)).toBeInTheDocument();
    });
  });

  it('should_enableSubmitButton_when_emailValid', async () => {
    // Test 1.5: should_enableSubmitButton_when_emailValid
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, 'valid@example.com');
      await user.tab();
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /send link/i });
      expect(submitButton).not.toBeDisabled();
    });
  });

  it('should_disableSubmitButton_when_emailEmpty', async () => {
    // Test 1.6: should_disableSubmitButton_when_emailEmpty
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const submitButton = screen.getByRole('button', { name: /send link/i });
    expect(submitButton).toBeDisabled();
  });
});

describe('ForgotPasswordForm Component - Form Submission Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_callForgotPasswordAPI_when_submitClicked', async () => {
    // Test 2.1: should_callForgotPasswordAPI_when_submitClicked
    const user = userEvent.setup();
    const testEmail = 'test@example.com';

    // Mock successful response
    mockForgotPassword.mockImplementation((email, { onSuccess }) => {
      onSuccess?.({ success: true, message: 'Email sent' });
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, testEmail);
      await user.tab();
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /send link/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /send link/i }));
    });

    await waitFor(() => {
      expect(mockForgotPassword).toHaveBeenCalledWith(
        testEmail,
        expect.objectContaining({
          onSuccess: expect.any(Function),
        })
      );
    });
  });

  it('should_notSubmit_when_emailInvalid', async () => {
    // Test: should_notSubmit_when_emailInvalid
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, 'invalid-email');
      await user.tab();
    });

    // Button should be disabled or form should not submit
    await waitFor(() => {
      expect(mockForgotPassword).not.toHaveBeenCalled();
    });
  });
});

describe('ForgotPasswordForm Component - Loading State Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
  });

  it('should_showLoadingState_when_requestInProgress', async () => {
    // Test 2.2: should_showLoadingState_when_requestInProgress
    // Update mock to show loading state
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: true,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sending/i });
      expect(submitButton).toBeDisabled();
      // Check for loading spinner
      expect(submitButton.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
    });
  });

  it('should_disableInputs_when_requestInProgress', async () => {
    // Test: should_disableInputs_when_requestInProgress
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: true,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      const emailInput = screen.getByLabelText(/email address/i);
      expect(emailInput).toBeDisabled();
    });
  });
});

describe('ForgotPasswordForm Component - View Transition Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_transitionToConfirmation_when_requestSuccessful', async () => {
    // Test 2.3: should_transitionToConfirmation_when_requestSuccessful
    const user = userEvent.setup();
    const testEmail = 'test@example.com';

    // Mock successful response
    mockForgotPassword.mockImplementation((email, { onSuccess }) => {
      onSuccess?.({ success: true, message: 'Email sent' });
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, testEmail);
      await user.tab();
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /send link/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /send link/i }));
    });

    // Should transition to confirmation view
    await waitFor(() => {
      expect(screen.getByText(/check your email/i)).toBeInTheDocument();
    });
  });

  it('should_displayEmailAddress_when_confirmationShown', async () => {
    // Test 2.4: should_displayEmailAddress_when_confirmationShown
    const user = userEvent.setup();
    const testEmail = 'test@example.com';

    // Mock successful response
    mockForgotPassword.mockImplementation((email, { onSuccess }) => {
      onSuccess?.({ success: true, message: 'Email sent' });
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, testEmail);
      await user.tab();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /send link/i }));
    });

    // Should display masked email address
    await waitFor(() => {
      expect(screen.getByText(/t\*+@example\.com/i)).toBeInTheDocument();
    });
  });
});

describe('ForgotPasswordForm Component - Error Handling Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_displayError_when_rateLimitExceeded', async () => {
    // Test 4.2: should_displayRateLimitError_when_429Response
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: true,
      error: {
        type: 'rateLimitExceeded',
        message: 'Rate limit exceeded',
        retryAfter: 60,
        statusCode: 429,
      },
      data: null,
      reset: vi.fn(),
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/too many requests/i)).toBeInTheDocument();
    });
  });

  it('should_displayError_when_networkError', async () => {
    // Test 4.3: should_displayNetworkError_when_requestFails
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: true,
      error: {
        type: 'networkError',
        message: 'Network connection failed',
        statusCode: 0,
      },
      data: null,
      reset: vi.fn(),
    });

    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/connection error/i)).toBeInTheDocument();
    });
  });
});

describe('ForgotPasswordForm Component - Navigation Tests', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
    Object.assign(mockUseForgotPassword, {
      mutate: mockForgotPassword,
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
      data: null,
    });
  });

  it('should_provideBackToLoginLink_when_formRendered', async () => {
    // Test: should_provideBackToLoginLink_when_formRendered
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    await waitFor(() => {
      const backLink = screen.getByText(/back to login/i);
      expect(backLink).toBeInTheDocument();
      expect(backLink.closest('a')).toHaveAttribute('href', '/login');
    });
  });
});
