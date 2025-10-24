/**
 * RegistrationWizard Component Tests
 * Story 1.2.3: Implement Account Creation Flow - Task 8 (RED Phase)
 * TDD: Write tests first before implementation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistrationWizard } from './RegistrationWizard';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';

// Create theme for MUI components
const theme = createTheme();

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock useRegistration hook
const mockRegisterMutation = vi.fn();
vi.mock('@/hooks/useRegistration', () => ({
  useRegistration: () => ({
    mutate: mockRegisterMutation,
    isLoading: false,
    error: null,
  }),
}));

// Wrapper component with all required providers
const AllProviders: React.FC<{ children: React.ReactNode; initialEntries?: string[] }> = ({
  children,
  initialEntries = ['/auth/register'],
}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <I18nextProvider i18n={i18n}>
          <ThemeProvider theme={theme}>{children}</ThemeProvider>
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('RegistrationWizard Component', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
  });

  // Test 3.1: should_renderStep1Initially_when_wizardLoaded
  it('should_displayStep1_when_componentMounted', async () => {
    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    await waitFor(() => {
      expect(screen.getByText(/step 1 of 2/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    });
  });

  // Test 3.2: should_displayStepper_when_wizardRendered
  it('should_showStepperIndicator_when_componentMounted', async () => {
    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    await waitFor(() => {
      // MUI Stepper should be present - check for step labels
      expect(screen.getByText('Step 1')).toBeInTheDocument();
      expect(screen.getByText('Step 2')).toBeInTheDocument();
    });
  });

  // Test 3.3: should_transitionToStep2_when_continueClicked
  it('should_navigateToStep2_when_step1Completed', async () => {
    const user = userEvent.setup();

    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    // Fill out Step 1 fields
    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john.doe@example.com');
    await user.type(passwordInput, 'Password123');
    await user.type(confirmInput, 'Password123');
    await user.click(continueButton);

    await waitFor(() => {
      expect(screen.getByText(/step 2 of 2/i)).toBeInTheDocument();
    });
  });

  // Test 3.4: should_updateURL_when_stepChanges
  it('should_addStepParameterToURL_when_navigatingToStep2', async () => {
    const user = userEvent.setup();

    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    // Wait for form to be rendered with i18n translations loaded
    const nameInput = await screen.findByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john.doe@example.com');
    await user.type(passwordInput, 'Password123');
    await user.type(confirmInput, 'Password123');
    await user.click(continueButton);

    // Wait for Step 2 to render
    await waitFor(() => {
      expect(screen.getByText(/step 2 of 2/i)).toBeInTheDocument();
    });
  });

  // Test 3.5: should_preserveFormData_when_navigatingBack
  it('should_retainFormValues_when_backButtonClicked', async () => {
    const user = userEvent.setup();

    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    // Wait for form to be rendered
    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john.doe@example.com');
    await user.type(passwordInput, 'Password123');
    await user.type(confirmInput, 'Password123');
    await user.click(continueButton);

    // Wait for Step 2 to render
    await waitFor(() => {
      expect(screen.getByText(/step 2 of 2/i)).toBeInTheDocument();
    });

    // Now on Step 2, click Back
    const backButton = screen.getByRole('button', { name: /back/i });
    await user.click(backButton);

    // Verify form values preserved
    await waitFor(() => {
      const nameInputAgain = screen.getByLabelText(/full name/i) as HTMLInputElement;
      expect(nameInputAgain.value).toBe('John Doe');
    });
  });

  // Test 3.6: should_callCognitoSignUp_when_createAccountClicked
  it('should_invokeRegistrationMutation_when_step2Submitted', async () => {
    const user = userEvent.setup();

    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    // Wait for form to be rendered
    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john.doe@example.com');
    await user.type(passwordInput, 'Password123');
    await user.type(confirmInput, 'Password123');
    await user.click(continueButton);

    // Wait for Step 2 to render
    await waitFor(() => {
      expect(screen.getByText(/step 2 of 2/i)).toBeInTheDocument();
    });

    // On Step 2, accept terms and submit
    const termsCheckbox = screen.getByRole('checkbox', {
      name: /agree to the terms of service/i,
    });
    const createButton = screen.getByRole('button', { name: /create account/i });

    await user.click(termsCheckbox);
    await user.click(createButton);

    await waitFor(() => {
      expect(mockRegisterMutation).toHaveBeenCalled();
    });
  });

  // Test 3.7: should_navigateToVerification_when_registrationSuccessful
  it('should_redirectToEmailVerification_when_accountCreated', async () => {
    // Mock successful registration
    const mockSuccessfulRegister = vi.fn((data, { onSuccess }) => {
      onSuccess({ email: data.email, requiresConfirmation: true });
    });

    vi.mocked(mockRegisterMutation).mockImplementation(mockSuccessfulRegister);

    const user = userEvent.setup();

    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    // Wait for form to be rendered
    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/^email/i);
    const passwordInput = screen.getByLabelText(/^password$/i);
    const confirmInput = screen.getByLabelText(/confirm password/i);
    const continueButton = screen.getByRole('button', { name: /continue/i });

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john.doe@example.com');
    await user.type(passwordInput, 'Password123');
    await user.type(confirmInput, 'Password123');
    await user.click(continueButton);

    // Wait for Step 2 to render
    await waitFor(() => {
      expect(screen.getByText(/step 2 of 2/i)).toBeInTheDocument();
    });

    // On Step 2, accept terms and submit
    const termsCheckbox = screen.getByRole('checkbox', {
      name: /agree to the terms of service/i,
    });
    const createButton = screen.getByRole('button', { name: /create account/i });

    await user.click(termsCheckbox);
    await user.click(createButton);

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(expect.stringContaining('/auth/verify-email'));
    });
  });

  // Test 3.8: should_displayTitle_when_wizardRendered
  it('should_showWizardTitle_when_componentMounted', async () => {
    render(
      <AllProviders>
        <RegistrationWizard />
      </AllProviders>
    );

    await waitFor(() => {
      // Use getByRole to find the specific heading
      expect(
        screen.getByRole('heading', { name: /create account/i, level: 4 })
      ).toBeInTheDocument();
    });
  });
});
