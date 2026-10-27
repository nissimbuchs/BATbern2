/**
 * RegistrationStep2 Component Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 6 (RED Phase)
 * TDD: Write tests first before implementation
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistrationStep2 } from './RegistrationStep2';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { FormProvider, useForm } from 'react-hook-form';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';

// Issue #1141: the "send new confirmation link" action resends via the email-verification service.
const mockResendVerificationCode = vi.hoisted(() => vi.fn());
vi.mock('@/services/auth/emailVerificationService', () => ({
  verifyEmail: vi.fn(),
  resendVerificationCode: mockResendVerificationCode,
}));

const VerifyPageProbe = () => {
  const location = useLocation();
  return <div data-testid="verify-page">{`${location.pathname}${location.search}`}</div>;
};

// Create theme for MUI components
const theme = createTheme();

// Wrapper component for FormProvider with pre-filled data
const FormWrapper: React.FC<{
  children: React.ReactNode;
  onBack: () => void;
  onSubmit: () => void;
}> = ({ children }) => {
  const methods = useForm({
    defaultValues: {
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: false,
      newsletterOptIn: false,
    },
    mode: 'onBlur',
  });

  const [queryClient] = React.useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/auth/register']}>
        <I18nextProvider i18n={i18n}>
          <ThemeProvider theme={theme}>
            <Routes>
              <Route path="/auth/verify-email" element={<VerifyPageProbe />} />
              <Route path="*" element={<FormProvider {...methods}>{children}</FormProvider>} />
            </Routes>
          </ThemeProvider>
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('RegistrationStep2 Component', () => {
  const mockOnBack = vi.fn();
  const mockOnSubmit = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
  });

  // Test 2.1: should_displaySummary_when_step2Rendered
  it('should_displayNameAndEmailSummary_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/john doe/i)).toBeInTheDocument();
      // Use getAllByText to handle multiple instances (in summary and verification notice)
      const emailElements = screen.getAllByText(/john\.doe@example\.com/i);
      expect(emailElements.length).toBeGreaterThan(0);
    });
  });

  // Test 2.2: should_renderCheckboxes_when_step2Loaded
  it('should_displayTermsAndNewsletterCheckboxes_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(
        screen.getByRole('checkbox', { name: /agree to the terms of service/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole('checkbox', { name: /event updates and newsletters/i })
      ).toBeInTheDocument();
    });
  });

  // Test 2.3: should_renderBackButton_when_step2Loaded
  it('should_displayBackButton_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
    });
  });

  // Test 2.4: should_callOnBack_when_backButtonClicked
  it('should_invokeOnBackCallback_when_backButtonClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    const backButton = screen.getByRole('button', { name: /back/i });

    await act(async () => {
      await user.click(backButton);
    });

    await waitFor(() => {
      expect(mockOnBack).toHaveBeenCalledTimes(1);
    });
  });

  // Test 2.5: should_displayEditLink_when_summaryRendered
  it('should_showEditLinkForChangingInformation_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    });
  });

  // Test 2.6: should_callOnBack_when_editLinkClicked
  it('should_navigateBackToStep1_when_editLinkClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    const editButton = screen.getByRole('button', { name: /edit/i });

    await act(async () => {
      await user.click(editButton);
    });

    await waitFor(() => {
      expect(mockOnBack).toHaveBeenCalledTimes(1);
    });
  });

  // Test 2.7: should_requireTerms_when_submittingWithoutCheckbox
  it('should_showError_when_termsNotAcceptedAndSubmitClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    const submitButton = screen.getByRole('button', { name: /create account/i });

    await act(async () => {
      await user.click(submitButton);
    });

    await waitFor(() => {
      expect(screen.getByText(/you must accept the terms/i)).toBeInTheDocument();
    });

    // Should not call onSubmit
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  // Test 2.8: should_callOnSubmit_when_termsAcceptedAndSubmitClicked
  it('should_invokeOnSubmitCallback_when_termsAcceptedAndSubmitClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    const termsCheckbox = screen.getByRole('checkbox', {
      name: /agree to the terms of service/i,
    });
    const submitButton = screen.getByRole('button', { name: /create account/i });

    await act(async () => {
      await user.click(termsCheckbox);
      await user.click(submitButton);
    });

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    });
  });

  // Test 2.9: should_allowNewsletterOptIn_when_checkboxClicked
  it('should_checkNewsletterOptIn_when_userClicksCheckbox', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    const newsletterCheckbox = screen.getByRole('checkbox', {
      name: /event updates and newsletters/i,
    }) as HTMLInputElement;

    // Initially unchecked
    expect(newsletterCheckbox.checked).toBe(false);

    await act(async () => {
      await user.click(newsletterCheckbox);
    });

    await waitFor(() => {
      expect(newsletterCheckbox.checked).toBe(true);
    });
  });

  // Test 2.10: should_disableSubmitButton_when_isLoadingTrue
  it('should_disableCreateAccountButton_when_submissionInProgress', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={true}
            error={null}
          />
        </FormWrapper>
      );
    });

    const submitButton = screen.getByRole('button', {
      name: /creating account/i,
    }) as HTMLButtonElement;

    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    });
  });

  // Test 2.11: EMAIL_EXISTS (code returned by mapCognitoError for UsernameExistsException)
  it('should_showErrorMessage_when_registrationFails', async () => {
    const cognitoError = new Error('EMAIL_EXISTS');

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={cognitoError}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/an account with this email already exists/i)).toBeInTheDocument();
    });
  });

  // Issue #1141: an UNCONFIRMED user who signs up again gets UsernameExistsException.
  // Keep the message, but offer a fresh confirmation link and point confirmed users to login.
  it('should_offerResendConfirmation_when_emailAlreadyExists', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={new Error('EMAIL_EXISTS')}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/an account with this email already exists/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/forgot password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send new confirmation link/i })).toBeInTheDocument();
  });

  it('should_resendConfirmationAndOpenVerifyPage_when_emailExistsActionClicked', async () => {
    mockResendVerificationCode.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={new Error('UsernameExistsException')}
          />
        </FormWrapper>
      );
    });

    await user.click(screen.getByRole('button', { name: /send new confirmation link/i }));

    expect(mockResendVerificationCode).toHaveBeenCalledWith('john.doe@example.com');
    await waitFor(() => {
      expect(screen.getByTestId('verify-page')).toHaveTextContent(
        '/auth/verify-email?email=john.doe%40example.com'
      );
    });
  });

  it('should_notOfferResendAction_when_passwordWeak', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={new Error('INVALID_PASSWORD')}
          />
        </FormWrapper>
      );
    });

    expect(
      screen.queryByRole('button', { name: /send new confirmation link/i })
    ).not.toBeInTheDocument();
  });

  // Test 2.11b: INVALID_PASSWORD (code returned by mapCognitoError for InvalidPasswordException)
  it('should_showPasswordWeakError_when_passwordTooSimple', async () => {
    const passwordError = new Error('INVALID_PASSWORD');

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={passwordError}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/password does not meet/i)).toBeInTheDocument();
    });
  });

  // Test 2.11c: TOO_MANY_REQUESTS (code returned by mapCognitoError for TooManyRequestsException)
  it('should_showRateLimitError_when_tooManyAttempts', async () => {
    const rateLimitError = new Error('TOO_MANY_REQUESTS');

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={rateLimitError}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/too many attempts/i)).toBeInTheDocument();
    });
  });

  // Test 2.11d: unknown error falls back to generic message
  it('should_showGenericError_when_unknownErrorCode', async () => {
    const unknownError = new Error('SOME_UNKNOWN_CODE');

    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={unknownError}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/account creation failed/i)).toBeInTheDocument();
    });
  });

  // Test 2.12: should_displayVerificationNotice_when_step2Rendered
  it('should_showVerificationEmailNotice_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/you will receive a confirmation email/i)).toBeInTheDocument();
    });
  });

  // Test 2.13: should_displayTermsLink_when_termsCheckboxRendered
  it('should_showLinkToTermsAndConditions_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onBack={mockOnBack} onSubmit={mockOnSubmit}>
          <RegistrationStep2
            onBack={mockOnBack}
            onSubmit={mockOnSubmit}
            isLoading={false}
            error={null}
          />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      const termsLink = screen.getByRole('link', { name: /terms/i });
      expect(termsLink).toBeInTheDocument();
      expect(termsLink).toHaveAttribute('href');
    });
  });
});
