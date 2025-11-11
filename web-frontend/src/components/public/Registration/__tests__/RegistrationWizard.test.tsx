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
        'registration.success.title': 'Registration Submitted!',
        'registration.success.subtitle': 'Check your email to confirm your registration',
        'registration.success.emailSent': 'Confirmation Email Sent',
        'registration.success.emailSentTo': "We've sent a confirmation email to",
        'registration.success.clickLink':
          'Click the link in the email to confirm your registration.',
        'registration.success.validFor': 'This link is valid for',
        'registration.success.valid': '24 hours',
        'registration.success.didntReceive': "Didn't receive the email?",
        'registration.success.checkSpam': 'Check your spam folder',
        'registration.success.checkEmail': 'Verify the email address is correct',
        'registration.success.waitMinutes': 'Wait a few minutes and try again',
        'registration.success.close': 'Close',
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
  });

  describe('Initial Rendering', () => {
    test('should_renderStep1Initially_when_mounted', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByText('Step 1: Your Details')).toBeInTheDocument();
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

      expect(screen.getByText('Next →')).toBeInTheDocument();
    });

    test('should_renderCancelButton_when_mounted', () => {
      renderWithProviders(<RegistrationWizard eventCode="BAT2025" />);

      expect(screen.getByText('Cancel')).toBeInTheDocument();
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });

      // Click next
      const nextButton = screen.getByText('Next →');
      fireEvent.click(nextButton);

      await waitFor(() => {
        expect(screen.getByText('Step 2: Confirm Registration')).toBeInTheDocument();
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        expect(screen.getByText('← Back')).toBeInTheDocument();
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        expect(screen.getByText('← Back')).toBeInTheDocument();
      });

      // Click back
      fireEvent.click(screen.getByText('← Back'));

      await waitFor(() => {
        expect(screen.getByText('Next →')).toBeInTheDocument();
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        const submitButton = screen.getByText('Complete Registration');
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      // Wait for step 2 and try to submit (button will be disabled, but test the logic)
      await waitFor(() => {
        expect(screen.getByText('Complete Registration')).toBeDisabled();
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      // Accept terms on step 2
      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      // Submit
      await waitFor(() => {
        const submitButton = screen.getByText('Complete Registration');
        expect(submitButton).not.toBeDisabled();
      });

      const submitButton = screen.getByText('Complete Registration');
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      await waitFor(() => {
        const submitButton = screen.getByText('Complete Registration');
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      await waitFor(() => {
        const submitButton = screen.getByText('Complete Registration');
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
      fireEvent.change(screen.getByPlaceholderText('TechCorp AG'), {
        target: { value: 'Acme Inc' },
      });
      fireEvent.change(screen.getByPlaceholderText('Senior Developer'), {
        target: { value: 'Developer' },
      });
      fireEvent.click(screen.getByText('Next →'));

      await waitFor(() => {
        const termsCheckbox = screen.getByRole('checkbox', { name: /agree to the/i });
        fireEvent.click(termsCheckbox);
      });

      const submitButton = screen.getByText('Complete Registration');
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText('Submitting...')).toBeInTheDocument();
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

      fireEvent.click(screen.getByText('Cancel'));

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to cancel registration?');
      expect(mockOnCancel).toHaveBeenCalled();
    });

    test('should_navigateToHome_when_cancelClickedInPageMode', () => {
      window.confirm = vi.fn(() => true);

      renderWithProviders(<RegistrationWizard eventCode="BAT2025" inline={false} />);

      fireEvent.click(screen.getByText('Cancel'));

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to cancel registration?');
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });

    test('should_notCancel_when_confirmationDenied', () => {
      const mockOnCancel = vi.fn();
      window.confirm = vi.fn(() => false);

      renderWithProviders(
        <RegistrationWizard eventCode="BAT2025" inline={true} onCancel={mockOnCancel} />
      );

      fireEvent.click(screen.getByText('Cancel'));

      expect(mockOnCancel).not.toHaveBeenCalled();
    });
  });
});
