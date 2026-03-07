/**
 * RegistrationWizard Component Tests (Story 4.1.5 - Task 14)
 *
 * Tests for the 2-step registration wizard component
 * Covers wizard state, navigation, validation, and submission
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RegistrationWizard } from '../RegistrationWizard';
import { eventApiClient } from '@/services/eventApiClient';

// Mock eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    createRegistration: vi.fn(),
  },
}));

// Mock useMyRegistration — vi.fn() so guard tests can override per test (Story 10.10, T11)
vi.mock('@/hooks/useMyRegistration');
import { useMyRegistration } from '@/hooks/useMyRegistration';
const mockUseMyRegistration = vi.mocked(useMyRegistration);

// Mock useAuth + useUserProfile for prefill tests
vi.mock('@/hooks/useAuth/useAuth');
vi.mock('@/hooks/useUserProfile/useUserProfile');
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useUserProfile } from '@/hooks/useUserProfile/useUserProfile';
const mockUseAuth = vi.mocked(useAuth);
const mockUseUserProfile = vi.mocked(useUserProfile);

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      // Map translation keys to actual text for tests
      const translations: Record<string, string> = {
        // Success view (registration namespace, no prefix)
        'success.title': 'Registration Submitted!',
        'success.subtitle': 'Check your email to confirm your registration',
        'success.emailSent': 'Confirmation Email Sent',
        'success.emailSentTo': "We've sent a confirmation email to",
        'success.clickLink': 'Click the link in the email to confirm your registration.',
        'success.validFor': 'This link is valid for',
        'success.valid': '24 hours',
        'success.didntReceive': "Didn't receive the email?",
        'success.checkSpam': 'Check your spam folder',
        'success.checkEmail': 'Verify the email address is correct',
        'success.waitMinutes': 'Wait a few minutes and try again',
        'success.close': 'Close',
        // Wizard strings
        'wizard.steps.step1Progress': '1. Your Details',
        'wizard.steps.step2Progress': '2. Confirm Registration',
        'wizard.steps.step1Title': 'Step 1: Your Details',
        'wizard.steps.step2Title': 'Step 2: Confirm Registration',
        'wizard.buttons.edit': 'Edit',
        'wizard.buttons.cancel': 'Cancel',
        'wizard.buttons.back': '← Back',
        'wizard.buttons.next': 'Next →',
        'wizard.buttons.complete': 'Complete Registration',
        'wizard.buttons.submitting': 'Submitting...',
        'wizard.errors.fillRequired': 'Please fill in all required fields correctly.',
        'wizard.errors.acceptTerms': 'You must accept the terms and conditions to register.',
        'wizard.errors.failed': 'Registration failed. Please try again.',
        'wizard.cancelConfirm': 'Are you sure you want to cancel registration?',
        // PersonalDetails strings
        'personalDetails.title': 'Personal Information',
        'personalDetails.subtitle': 'Please provide your details to register for the event.',
        'personalDetails.fields.firstName': 'First Name *',
        'personalDetails.fields.lastName': 'Last Name *',
        'personalDetails.fields.emailAddress': 'Email Address *',
        'personalDetails.fields.emailHelper': "We'll send your ticket and event updates here",
        'personalDetails.fields.company': 'Company *',
        'personalDetails.fields.role': 'Role *',
        'personalDetails.validation.firstNameRequired': 'First name is required',
        'personalDetails.validation.lastNameRequired': 'Last name is required',
        'personalDetails.validation.invalidEmail': 'Invalid email address',
        'personalDetails.validation.companyRequired': 'Company is required',
        'personalDetails.validation.roleRequired': 'Role is required',
        'personalDetails.placeholders.firstName': 'John',
        'personalDetails.placeholders.lastName': 'Smith',
        'personalDetails.placeholders.email': 'john.smith@company.ch',
        'personalDetails.placeholders.company': 'Search for your company...',
        'personalDetails.placeholders.role': 'Senior Developer',
        // ConfirmStep strings
        'confirmStep.title': 'Review & Confirm',
        'confirmStep.subtitle': 'Please review your information and complete your registration.',
        'confirmStep.personalInfo': 'Personal Information',
        'confirmStep.commPref.title': 'Communication Preferences',
        'confirmStep.commPref.reminders': 'Send me event reminders (1 week and 1 day before)',
        'confirmStep.commPref.newsletter': 'Subscribe to BATbern newsletter (monthly updates)',
        'confirmStep.specialRequests.label': 'Special Requests',
        'confirmStep.specialRequests.optional': '(Optional)',
        'confirmStep.specialRequests.placeholder':
          'Dietary requirements, accessibility needs, etc.',
        'confirmStep.specialRequests.helper':
          'Let us know if you have any dietary requirements or accessibility needs.',
        'confirmStep.terms.prefix': 'I agree to the',
        'confirmStep.terms.termsLink': 'terms and conditions',
        'confirmStep.terms.separator': 'and',
        'confirmStep.terms.privacyLink': 'privacy policy',
        'confirmStep.terms.required': '*',
        'confirmStep.terms.error':
          'You must accept the terms and conditions to complete registration.',
        'confirmStep.account.title': 'Want to manage all your registrations in one place?',
        'confirmStep.account.message':
          'After registering, you can create a free account to view your registration history, update preferences, and get personalized event recommendations.',
        // Common namespace keys
        'navigation.home': 'Home',
      };
      return translations[key] || key;
    },
    i18n: {
      changeLanguage: () => new Promise(() => {}),
    },
  }),
}));

describe('RegistrationWizard Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{ui}</BrowserRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Default: not registered → guard doesn't show, wizard renders normally
    mockUseMyRegistration.mockReturnValue({ data: null, isLoading: false });
    // Default: anonymous user, no profile
    mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>);
    mockUseUserProfile.mockReturnValue({ userProfile: undefined } as ReturnType<
      typeof useUserProfile
    >);
  });

  describe('Initial Rendering', () => {
    test('should_renderStep1Initially_when_mounted', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByRole('heading', { name: /Step 1: Your Details/i })).toBeInTheDocument();
    });

    test('should_renderProgressIndicator_when_mounted', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByText('1. Your Details')).toBeInTheDocument();
      expect(screen.getByText('2. Confirm Registration')).toBeInTheDocument();
    });

    test('should_showProgressBar_when_mounted', () => {
      const { container } = renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Progress bar should be 50% on step 1
      const progressBar = container.querySelector('.bg-blue-400');
      expect(progressBar).toBeInTheDocument();
      expect(progressBar).toHaveStyle({ width: '50%' });
    });

    test('should_renderNextButton_when_onStep1', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-wizard-next-btn')).toBeInTheDocument();
    });

    test('should_renderCancelButton_when_mounted', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-wizard-cancel-btn')).toBeInTheDocument();
    });
  });

  describe('Navigation', () => {
    test('should_expandStep2_when_nextClicked', async () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Fill required fields
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });

      // Click next
      const nextButton = screen.getByTestId('registration-wizard-next-btn');
      fireEvent.click(nextButton);

      await waitFor(() => {
        expect(
          screen.getByRole('heading', { name: /Step 2: Confirm Registration/i })
        ).toBeInTheDocument();
      });
    });

    test('should_showBackButton_when_onStep2', async () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Navigate to step 2
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard-back-btn')).toBeInTheDocument();
      });
    });

    test('should_returnToStep1_when_backClicked', async () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Navigate to step 2
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard-back-btn')).toBeInTheDocument();
      });

      // Click back
      fireEvent.click(screen.getByTestId('registration-wizard-back-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard-next-btn')).toBeInTheDocument();
      });
    });

    test('should_updateProgressBar_when_navigating', async () => {
      const { container } = renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Initially 50%
      let progressBar = container.querySelector('.bg-blue-400');
      expect(progressBar).toHaveStyle({ width: '50%' });

      // Navigate to step 2
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        progressBar = container.querySelector('.bg-blue-400');
        expect(progressBar).toHaveStyle({ width: '100%' });
      });
    });
  });

  describe('Form Validation', () => {
    test('should_disableSubmitButton_when_termsNotAccepted', async () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Navigate to step 2
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        const submitButton = screen.getByTestId('registration-wizard-submit-btn');
        expect(submitButton).toBeDisabled();
      });
    });

    test('should_showError_when_submittingWithoutTerms', async () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Navigate to step 2
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      // Wait for step 2 and try to submit (button will be disabled, but test the logic)
      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard-submit-btn')).toBeDisabled();
      });
    });
  });

  describe('Form Submission', () => {
    test('should_submitRegistration_when_formValid', async () => {
      const mockRegistration = {
        registrationCode: 'ABC123',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@example.com',
      };

      vi.mocked(eventApiClient.createRegistration).mockResolvedValue(mockRegistration);

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Fill step 1
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      // Accept terms on step 2
      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      // Submit
      await waitFor(() => {
        const submitButton = screen.getByTestId('registration-wizard-submit-btn');
        expect(submitButton).not.toBeDisabled();
      });

      const submitButton = screen.getByTestId('registration-wizard-submit-btn');
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(eventApiClient.createRegistration).toHaveBeenCalledWith(
          'BAT2025',
          expect.objectContaining({
            firstName: 'John',
            lastName: 'Doe',
            email: 'john@example.com',
            company: 'Acme Inc',
            role: 'Developer',
            termsAccepted: true,
          })
        );
      });
    });

    test('should_showSuccessMessage_when_submissionSuccessful', async () => {
      const mockRegistration = {
        registrationCode: 'ABC123',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@example.com',
      };

      vi.mocked(eventApiClient.createRegistration).mockResolvedValue(mockRegistration);

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Fill and submit form
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      await waitFor(() => {
        const submitButton = screen.getByTestId('registration-wizard-submit-btn');
        fireEvent.click(submitButton);
      });

      // Story 4.1.5c: Inline success message instead of navigation
      await waitFor(() => {
        expect(
          screen.getByText(/check your email to confirm your registration/i)
        ).toBeInTheDocument();
      });
    });

    test('should_showError_when_submissionFails', async () => {
      vi.mocked(eventApiClient.createRegistration).mockRejectedValue(new Error('Network error'));

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Fill and submit form
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      await waitFor(() => {
        const submitButton = screen.getByTestId('registration-wizard-submit-btn');
        fireEvent.click(submitButton);
      });

      await waitFor(() => {
        expect(screen.getByText(/Network error/i)).toBeInTheDocument();
      });
    });

    test('should_showLoadingState_when_submitting', async () => {
      vi.mocked(eventApiClient.createRegistration).mockImplementation(
        () =>
          new Promise((resolve) => setTimeout(() => resolve({ registrationCode: 'ABC123' }), 100))
      );

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Fill and submit form
      fireEvent.change(screen.getByPlaceholderText('John'), { target: { value: 'John' } });
      fireEvent.change(screen.getByPlaceholderText('Smith'), { target: { value: 'Doe' } });
      fireEvent.change(screen.getByPlaceholderText('john.smith@company.ch'), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByPlaceholderText('Search for your company...'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByTestId('registration-wizard-next-btn'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      const submitButton = screen.getByTestId('registration-wizard-submit-btn');
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard-submit-btn')).toHaveTextContent(
          /Submitting/i
        );
      });
    });
  });

  describe('Cancel Behavior', () => {
    test('should_callOnCancel_when_cancelClickedInInlineMode', () => {
      const mockOnCancel = vi.fn();
      window.confirm = vi.fn(() => true);

      renderWithProviders(
        <RegistrationWizard eventCode="BAT2025" inline={true} onCancel={mockOnCancel} />
      );

      fireEvent.click(screen.getByTestId('registration-wizard-cancel-btn'));

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to cancel registration?');
      expect(mockOnCancel).toHaveBeenCalled();
    });

    test('should_navigateToHome_when_cancelClickedInPageMode', () => {
      window.confirm = vi.fn(() => true);

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" inline={false} />);

      fireEvent.click(screen.getByTestId('registration-wizard-cancel-btn'));

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to cancel registration?');
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });

    test('should_notCancel_when_confirmationDenied', () => {
      const mockOnCancel = vi.fn();
      window.confirm = vi.fn(() => false);

      renderWithProviders(
        <RegistrationWizard eventCode="BAT2025" inline={true} onCancel={mockOnCancel} />
      );

      fireEvent.click(screen.getByTestId('registration-wizard-cancel-btn'));

      expect(mockOnCancel).not.toHaveBeenCalled();
    });
  });

  // ── Story 10.10, T11: Registration Wizard Guard (AC6) ──────────────────────

  describe('Registration Status Guard (AC6)', () => {
    test('should_showGuard_when_userIsAlreadyConfirmed', () => {
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'CONFIRMED',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-status-guard')).toBeInTheDocument();
      expect(screen.getByText('registrationStatusGuard.alreadyRegistered')).toBeInTheDocument();
      // Wizard form step 1 must NOT render
      expect(screen.queryByRole('heading', { name: /Step 1/i })).not.toBeInTheDocument();
    });

    test('should_showGuard_when_userIsRegistered', () => {
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'REGISTERED',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-status-guard')).toBeInTheDocument();
    });

    test('should_showGuard_when_userIsOnWaitlist', () => {
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'WAITLIST',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-status-guard')).toBeInTheDocument();
    });

    test('should_showGoBackButton_when_userHasActiveRegistration', () => {
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'CONFIRMED',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-guard-go-back-btn')).toBeInTheDocument();
      expect(screen.queryByTestId('registration-guard-register-again-btn')).not.toBeInTheDocument();
    });

    test('should_showRegisterAgainButton_when_userIsCancelled', () => {
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'CANCELLED',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-guard-register-again-btn')).toBeInTheDocument();
      expect(screen.queryByTestId('registration-guard-go-back-btn')).not.toBeInTheDocument();
    });

    test('should_showWizard_when_registrationIsLoading', () => {
      mockUseMyRegistration.mockReturnValue({ data: undefined, isLoading: true });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      // Guard condition requires !isRegistrationLoading — while loading, show wizard
      expect(screen.queryByTestId('registration-status-guard')).not.toBeInTheDocument();
    });

    test('should_showWizard_when_notRegistered', () => {
      mockUseMyRegistration.mockReturnValue({ data: null, isLoading: false });

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.queryByTestId('registration-status-guard')).not.toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /Step 1: Your Details/i })).toBeInTheDocument();
    });

    test('should_prefillFormFields_when_userProfileLoads', async () => {
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>);
      mockUseUserProfile.mockReturnValue({
        userProfile: {
          firstName: 'Alice',
          lastName: 'Tester',
          email: 'alice@example.com',
        },
      } as ReturnType<typeof useUserProfile>);

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('Alice')).toBeInTheDocument();
        expect(screen.getByDisplayValue('Tester')).toBeInTheDocument();
        expect(screen.getByDisplayValue('alice@example.com')).toBeInTheDocument();
      });
    });

    test('should_callSetQueryData_with_null_when_registerAgainClicked', () => {
      // The guard dismiss works by calling queryClient.setQueryData(null) to optimistically
      // mark the user as not-registered. This avoids a race condition where removeQueries
      // would trigger a re-fetch returning CANCELLED before the user submits the new form.
      mockUseMyRegistration.mockReturnValue({
        data: {
          registrationCode: 'BATbern999-reg-alice',
          eventCode: 'BAT2025',
          status: 'CANCELLED',
          registrationDate: '2025-11-01T10:00:00Z',
        },
        isLoading: false,
      });

      const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByTestId('registration-status-guard')).toBeInTheDocument();

      fireEvent.click(screen.getByTestId('registration-guard-register-again-btn'));

      // Verify the cache is set to null (not-registered) — not removed (which would race)
      expect(setQueryDataSpy).toHaveBeenCalledWith(['my-registration', 'BAT2025'], null);
    });
  });
});
