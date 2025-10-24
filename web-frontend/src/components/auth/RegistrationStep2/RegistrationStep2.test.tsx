/**
 * RegistrationStep2 Component Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 6 (RED Phase)
 * TDD: Write tests first before implementation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistrationStep2 } from './RegistrationStep2';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { FormProvider, useForm } from 'react-hook-form';
import i18n from '@/i18n/config';

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
      fullName: 'John Doe',
      email: 'john.doe@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
      agreedToTerms: false,
      newsletterOptIn: false,
    },
    mode: 'onBlur',
  });

  return (
    <I18nextProvider i18n={i18n}>
      <ThemeProvider theme={theme}>
        <FormProvider {...methods}>{children}</FormProvider>
      </ThemeProvider>
    </I18nextProvider>
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

  // Test 2.11: should_displayError_when_errorPropProvided
  it('should_showErrorMessage_when_registrationFails', async () => {
    // Use Cognito error code that gets mapped to localized message
    const cognitoError = new Error('UsernameExistsException');

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
      // Check for the translated error message
      expect(screen.getByText(/an account with this email already exists/i)).toBeInTheDocument();
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
