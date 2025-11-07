/**
 * RegistrationStep1 Component Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 4 (RED Phase)
 * TDD: Write tests first before implementation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistrationStep1 } from './RegistrationStep1';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { FormProvider, useForm } from 'react-hook-form';
import i18n from '@/i18n/config';

// Create theme for MUI components
const theme = createTheme();

// Wrapper component for FormProvider
const FormWrapper: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const methods = useForm({
    defaultValues: {
      fullName: '',
      email: '',
      password: '',
      confirmPassword: '',
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

describe('RegistrationStep1 Component', () => {
  const mockOnContinue = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
  });

  // Test 1.1: should_renderStep1_when_pageLoads
  it('should_renderStep1Fields_when_componentMounted', async () => {
    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/^email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
    });
  });

  // Test 1.2: should_validateFullName_when_invalidInput
  it('should_showError_when_fullNameTooShort', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const nameInput = screen.getByLabelText(/full name/i);

    await act(async () => {
      await user.type(nameInput, 'A');
      await user.tab();
    });

    await waitFor(() => {
      expect(screen.getByText(/name must be at least 2 characters/i)).toBeInTheDocument();
    });
  });

  // Test 1.2b: should_validateFullName_when_invalidCharacters
  it('should_showError_when_fullNameHasInvalidCharacters', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const nameInput = screen.getByLabelText(/full name/i);

    await act(async () => {
      await user.type(nameInput, 'John123');
      await user.tab();
    });

    await waitFor(() => {
      expect(screen.getByText(/name contains invalid characters/i)).toBeInTheDocument();
    });
  });

  // Test 1.3: should_validateEmail_when_invalidFormat
  it('should_showError_when_emailInvalidFormat', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const emailInput = screen.getByLabelText(/^email/i);

    await act(async () => {
      await user.type(emailInput, 'invalid-email');
      await user.tab();
    });

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
    });
  });

  // Test 1.4: should_showPasswordRequirements_when_typing
  it('should_displayPasswordRequirements_when_passwordFieldFocused', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i);

    await act(async () => {
      await user.type(passwordInput, 'P');
    });

    await waitFor(() => {
      expect(screen.getByText(/password requirements:/i)).toBeInTheDocument();
      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
      expect(screen.getByText(/uppercase and lowercase/i)).toBeInTheDocument();
      expect(screen.getByText(/at least one number/i)).toBeInTheDocument();
    });
  });

  // Test 1.5: should_updateStrengthIndicator_when_passwordChanges
  it('should_showWeakStrength_when_passwordSimple', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i);

    await act(async () => {
      await user.type(passwordInput, 'password');
    });

    await waitFor(() => {
      expect(screen.getByText(/weak/i)).toBeInTheDocument();
    });
  });

  it('should_showMediumStrength_when_passwordMeetsRequirements', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i);

    await act(async () => {
      await user.type(passwordInput, 'Password123');
    });

    await waitFor(() => {
      expect(screen.getByText(/medium/i)).toBeInTheDocument();
    });
  });

  it('should_showStrongStrength_when_passwordComplex', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i);

    await act(async () => {
      await user.type(passwordInput, 'Password123!');
    });

    await waitFor(() => {
      expect(screen.getByText(/strong/i)).toBeInTheDocument();
    });
  });

  // Test 1.6: should_validatePasswordMatch_when_confirmTyping
  it('should_showError_when_passwordsDoNotMatch', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);

    await act(async () => {
      await user.type(passwordInput, 'Password123');
      await user.type(confirmInput, 'Different123');
      await user.tab();
    });

    await waitFor(() => {
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    });
  });

  // Test 1.7: should_enableContinueButton_when_allFieldsValid
  it('should_callOnContinue_when_allFieldsValidAndButtonClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await act(async () => {
      await user.type(nameInput, 'John Doe');
      await user.type(emailInput, 'john.doe@example.com');
      await user.type(passwordInput, 'Password123');
      await user.type(confirmInput, 'Password123');
      await user.click(continueButton);
    });

    await waitFor(() => {
      expect(mockOnContinue).toHaveBeenCalled();
    });
  });

  // Test 1.8: should_disableContinueButton_when_invalidData
  it('should_notCallOnContinue_when_fieldsInvalidAndButtonClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const continueButton = screen.getByRole('button', { name: /continue/i });

    await act(async () => {
      await user.click(continueButton);
    });

    // Should not call onContinue with empty fields
    expect(mockOnContinue).not.toHaveBeenCalled();
  });

  // Password visibility toggle tests
  it('should_togglePasswordVisibility_when_iconClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <FormWrapper onContinue={mockOnContinue}>
          <RegistrationStep1 onContinue={mockOnContinue} />
        </FormWrapper>
      );
    });

    const passwordInput = screen.getByLabelText(/^password$/i) as HTMLInputElement;

    // Initially should be type="password"
    expect(passwordInput.type).toBe('password');

    // Get the first toggle button (for password field, not confirm password)
    const toggleButtons = screen.getAllByLabelText(/show password/i);
    const toggleButton = toggleButtons[0];

    await act(async () => {
      await user.click(toggleButton);
    });

    await waitFor(() => {
      expect(passwordInput.type).toBe('text');
    });
  });
});
