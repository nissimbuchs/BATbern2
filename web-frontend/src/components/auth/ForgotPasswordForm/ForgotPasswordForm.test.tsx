/**
 * ForgotPasswordForm Component Tests
 * Story 1.2.2: Implement Forgot Password Flow
 *
 * The real useForgotPassword hook and email-verification service run; only Amplify (Cognito,
 * the process boundary) is replaced: `resetPassword` and `resendSignUpCode` from the global
 * `aws-amplify/auth` mock in src/test/setup.ts.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { resendSignUpCode, resetPassword } from 'aws-amplify/auth';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import i18n from '@/i18n/config';

const mockResetPassword = vi.mocked(resetPassword);
const mockResendSignUpCode = vi.mocked(resendSignUpCode);

/** What Cognito answers through Amplify v6: an Error whose `name` is the exception. */
const cognitoError = (name: string, message = name) => Object.assign(new Error(message), { name });

const VerifyPageProbe = () => {
  const location = useLocation();
  return <div data-testid="verify-page">{`${location.pathname}${location.search}`}</div>;
};

// React reports a render loop ("Maximum update depth exceeded") through console.error and
// goes on; the run ignores uncaught errors (vite.config.ts), so a loop passed every test here
// unnoticed. A render loop in this file fails the test that caused it.
afterEach(() => {
  const loops = vi
    .mocked(console.error)
    .mock.calls.map((args) => args.map(String).join(' '))
    .filter((message) => message.includes('Maximum update depth'));
  expect(loops).toEqual([]);
});

beforeEach(async () => {
  vi.clearAllMocks();
  mockResetPassword.mockReset();
  mockResendSignUpCode.mockReset();
  await i18n.changeLanguage('en');
});

// Create theme for MUI components
const theme = createTheme();

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const renderWithTheme = (component: React.ReactElement) => {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/auth/forgot-password']}>
        <I18nextProvider i18n={i18n}>
          <ThemeProvider theme={theme}>
            <Routes>
              <Route path="/auth/verify-email" element={<VerifyPageProbe />} />
              <Route path="*" element={component} />
            </Routes>
          </ThemeProvider>
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

/** Renders the form, types `email`, leaves the field and sends the form. */
const submitEmail = async (email: string) => {
  const user = userEvent.setup();
  renderWithTheme(<ForgotPasswordForm />);
  await user.type(screen.getByLabelText(/email address/i), email);
  await user.tab();
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /send link/i })).not.toBeDisabled()
  );
  await user.click(screen.getByRole('button', { name: /send link/i }));
  return user;
};

describe('ForgotPasswordForm Component - Rendering Tests', () => {
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
  it('should_requestResetCode_when_submitClicked', async () => {
    mockResetPassword.mockResolvedValue({
      isPasswordReset: false,
      nextStep: {
        resetPasswordStep: 'CONFIRM_RESET_PASSWORD_WITH_CODE',
        codeDeliveryDetails: { deliveryMedium: 'EMAIL' },
      },
    });

    await submitEmail('test@example.com');

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalledWith({ username: 'test@example.com' });
    });
  });

  it('should_notSubmit_when_emailInvalid', async () => {
    const user = userEvent.setup();
    renderWithTheme(<ForgotPasswordForm />);

    await user.type(screen.getByLabelText(/email address/i), 'invalid-email');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /send link/i })).toBeDisabled();
    expect(mockResetPassword).not.toHaveBeenCalled();
  });
});

describe('ForgotPasswordForm Component - Loading State Tests', () => {
  it('should_showLoadingStateAndDisableInput_when_requestInProgress', async () => {
    mockResetPassword.mockReturnValue(new Promise(() => {}));

    await submitEmail('test@example.com');

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sending/i });
      expect(submitButton).toBeDisabled();
      expect(submitButton.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/email address/i)).toBeDisabled();
  });
});

describe('ForgotPasswordForm Component - View Transition Tests', () => {
  it('should_showConfirmationWithMaskedEmail_when_requestSuccessful', async () => {
    mockResetPassword.mockResolvedValue({
      isPasswordReset: false,
      nextStep: {
        resetPasswordStep: 'CONFIRM_RESET_PASSWORD_WITH_CODE',
        codeDeliveryDetails: { deliveryMedium: 'EMAIL' },
      },
    });

    await submitEmail('test@example.com');

    await waitFor(() => {
      expect(screen.getByText(/check your email/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/t\*+@example\.com/i)).toBeInTheDocument();
  });

  it('should_showConfirmation_when_accountUnknown', async () => {
    // Anti-enumeration: Cognito's UserNotFoundException looks like a success.
    mockResetPassword.mockRejectedValue(cognitoError('UserNotFoundException'));

    await submitEmail('nobody@example.com');

    await waitFor(() => {
      expect(screen.getByText(/check your email/i)).toBeInTheDocument();
    });
  });
});

describe('ForgotPasswordForm Component - Error Handling Tests', () => {
  it('should_displayRateLimitErrorAndCountdown_when_limitExceeded', async () => {
    mockResetPassword.mockRejectedValue(cognitoError('LimitExceededException'));

    await submitEmail('test@example.com');

    await waitFor(() => {
      expect(screen.getByText(/too many requests/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /wait/i })).toBeDisabled();
  });

  it('should_displayConnectionErrorAndRetry_when_cognitoUnreachable', async () => {
    mockResetPassword.mockRejectedValueOnce(cognitoError('NetworkError', 'Network error'));
    mockResetPassword.mockResolvedValueOnce({
      isPasswordReset: false,
      nextStep: {
        resetPasswordStep: 'CONFIRM_RESET_PASSWORD_WITH_CODE',
        codeDeliveryDetails: { deliveryMedium: 'EMAIL' },
      },
    });

    const user = await submitEmail('test@example.com');

    await waitFor(() => {
      expect(screen.getByText(/connection error/i)).toBeInTheDocument();
    });
    await user.click(screen.getByTestId('retry-button'));

    await waitFor(() => {
      expect(screen.getByText(/check your email/i)).toBeInTheDocument();
    });
    expect(mockResetPassword).toHaveBeenCalledTimes(2);
  });

  it('should_displayUnknownError_when_cognitoFailsOtherwise', async () => {
    mockResetPassword.mockRejectedValue(cognitoError('InternalErrorException'));

    await submitEmail('test@example.com');

    await waitFor(() => {
      expect(screen.getByText(/an unknown error occurred/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId('retry-button')).not.toBeInTheDocument();
  });
});

// Issue #1141: Cognito answers resetPassword for an UNCONFIRMED user (no verified email)
// with InvalidParameterException. The old text claimed the account did not exist.
describe('ForgotPasswordForm Component - Unconfirmed Account', () => {
  it('should_explainUnconfirmedAccount_when_invalidParameter', async () => {
    mockResetPassword.mockRejectedValue(cognitoError('InvalidParameterException'));

    await submitEmail('pending@example.com');

    await waitFor(() => {
      expect(screen.getByText(/not been confirmed yet/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/no account yet/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send new confirmation link/i })).toBeInTheDocument();
  });

  it('should_resendConfirmationAndOpenVerifyPage_when_actionClicked', async () => {
    mockResetPassword.mockRejectedValue(cognitoError('InvalidParameterException'));
    mockResendSignUpCode.mockResolvedValue({
      destination: 'p***@example.com',
      deliveryMedium: 'EMAIL',
      attributeName: 'email',
    });

    const user = await submitEmail('pending@example.com');
    await user.click(await screen.findByRole('button', { name: /send new confirmation link/i }));

    expect(mockResendSignUpCode).toHaveBeenCalledWith({ username: 'pending@example.com' });
    await waitFor(() => {
      expect(screen.getByTestId('verify-page')).toHaveTextContent(
        '/auth/verify-email?email=pending%40example.com'
      );
    });
  });

  it('should_resendToTheEditedAddress_when_userCorrectsTheEmail', async () => {
    // The action follows the field as the user types (useWatch), not the submitted value.
    mockResetPassword.mockRejectedValue(cognitoError('InvalidParameterException'));
    mockResendSignUpCode.mockResolvedValue({
      destination: 'p***@example.com',
      deliveryMedium: 'EMAIL',
      attributeName: 'email',
    });

    const user = await submitEmail('pending@example.com');
    const action = await screen.findByRole('button', { name: /send new confirmation link/i });
    await user.clear(screen.getByLabelText(/email address/i));
    await user.type(screen.getByLabelText(/email address/i), 'Pending.Two@example.com');
    await user.click(action);

    expect(mockResendSignUpCode).toHaveBeenCalledWith({ username: 'Pending.Two@example.com' });
  });

  it('should_notOfferResendAction_when_networkError', async () => {
    mockResetPassword.mockRejectedValue(cognitoError('NetworkError'));

    await submitEmail('pending@example.com');

    // Positive control: the error is shown, only the resend action is absent.
    await waitFor(() => {
      expect(screen.getByText(/connection error/i)).toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', { name: /send new confirmation link/i })
    ).not.toBeInTheDocument();
  });
});

describe('ForgotPasswordForm Component - Navigation Tests', () => {
  it('should_provideBackToLoginLink_when_formRendered', async () => {
    await act(async () => {
      renderWithTheme(<ForgotPasswordForm />);
    });

    const backLink = screen.getByText(/back to login/i);
    expect(backLink.closest('a')).toHaveAttribute('href', '/login');
  });
});
