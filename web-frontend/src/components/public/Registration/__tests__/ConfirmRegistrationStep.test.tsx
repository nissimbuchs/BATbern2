/**
 * ConfirmRegistrationStep Component Tests (Story 4.1.5 - Task 6)
 *
 * Tests step 2 of registration wizard - review and confirm registration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { ConfirmRegistrationStep } from '../ConfirmRegistrationStep';
import type { CreateRegistrationRequest } from '@/types/event.types';

describe('ConfirmRegistrationStep Component', () => {
  const mockFormData: CreateRegistrationRequest = {
    eventId: 'BAT-2024-002',
    firstName: 'Hans',
    lastName: 'Müller',
    email: 'hans.mueller@example.ch',
    company: 'TechCorp AG',
    role: 'Software Architect',
    termsAccepted: false,
    communicationPreferences: {
      newsletterSubscribed: false,
      eventReminders: true,
    },
    specialRequests: '',
  };

  const defaultProps = {
    formData: mockFormData,
    setFormData: vi.fn(),
    onEdit: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Personal Information Display', () => {
    it('should_displayPersonalInformation_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      expect(screen.getByText('Hans Müller')).toBeInTheDocument();
      expect(screen.getByText('hans.mueller@example.ch')).toBeInTheDocument();
      expect(screen.getByText(/TechCorp AG • Software Architect/)).toBeInTheDocument();
    });

    it('should_displayEditButton_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const editButton = screen.getByRole('button', { name: /edit/i });
      expect(editButton).toBeInTheDocument();
    });

    it('should_callOnEdit_when_editButtonClicked', () => {
      const onEdit = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} onEdit={onEdit} />);

      const editButton = screen.getByRole('button', { name: /edit/i });
      fireEvent.click(editButton);

      expect(onEdit).toHaveBeenCalledTimes(1);
    });
  });

  describe('Communication Preferences', () => {
    it('should_displayEventRemindersChecked_when_eventRemindersTrue', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const checkbox = screen.getByRole('checkbox', { name: /Send me event reminders/i });
      expect(checkbox).toHaveAttribute('data-state', 'checked');
    });

    it('should_displayNewsletterUnchecked_when_newsletterFalse', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const checkbox = screen.getByRole('checkbox', { name: /Subscribe to BATbern newsletter/i });
      expect(checkbox).toHaveAttribute('data-state', 'unchecked');
    });

    it('should_updateEventReminders_when_checkboxToggled', () => {
      const setFormData = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} setFormData={setFormData} />);

      const checkbox = screen.getByLabelText(/Send me event reminders/i);
      fireEvent.click(checkbox);

      expect(setFormData).toHaveBeenCalledTimes(1);
      expect(setFormData).toHaveBeenCalledWith(expect.any(Function));

      // Test the updater function
      const updater = setFormData.mock.calls[0][0];
      const result = updater(mockFormData);
      expect(result.communicationPreferences?.eventReminders).toBe(false);
    });

    it('should_updateNewsletter_when_checkboxToggled', () => {
      const setFormData = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} setFormData={setFormData} />);

      const checkbox = screen.getByLabelText(/Subscribe to BATbern newsletter/i);
      fireEvent.click(checkbox);

      expect(setFormData).toHaveBeenCalledTimes(1);
      expect(setFormData).toHaveBeenCalledWith(expect.any(Function));

      // Test the updater function
      const updater = setFormData.mock.calls[0][0];
      const result = updater(mockFormData);
      expect(result.communicationPreferences?.newsletterSubscribed).toBe(true);
    });

    it('should_preserveOtherPreference_when_oneToggled', () => {
      const setFormData = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} setFormData={setFormData} />);

      // Toggle newsletter
      const newsletterCheckbox = screen.getByLabelText(/Subscribe to BATbern newsletter/i);
      fireEvent.click(newsletterCheckbox);

      const updater = setFormData.mock.calls[0][0];
      const result = updater(mockFormData);

      // Newsletter should be true, event reminders should remain true
      expect(result.communicationPreferences?.newsletterSubscribed).toBe(true);
      expect(result.communicationPreferences?.eventReminders).toBe(true);
    });

    it('should_handleMissingCommunicationPreferences_when_undefined', () => {
      const formDataWithoutPrefs = { ...mockFormData, communicationPreferences: undefined };
      render(<ConfirmRegistrationStep {...defaultProps} formData={formDataWithoutPrefs} />);

      const eventRemindersCheckbox = screen.getByRole('checkbox', {
        name: /Send me event reminders/i,
      });
      const newsletterCheckbox = screen.getByRole('checkbox', {
        name: /Subscribe to BATbern newsletter/i,
      });

      // Should default to true for event reminders, false for newsletter
      expect(eventRemindersCheckbox).toHaveAttribute('data-state', 'checked');
      expect(newsletterCheckbox).toHaveAttribute('data-state', 'unchecked');
    });
  });

  describe('Special Requests', () => {
    it('should_displaySpecialRequestsTextarea_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const textarea = screen.getByPlaceholderText(/Dietary requirements/i);
      expect(textarea).toBeInTheDocument();
    });

    it('should_callSetFormData_when_specialRequestsChanged', () => {
      const setFormData = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} setFormData={setFormData} />);

      const textarea = screen.getByPlaceholderText(/Dietary requirements/i);
      fireEvent.change(textarea, { target: { value: 'Vegetarian meal please' } });

      // Verify setFormData was called with an updater function
      expect(setFormData).toHaveBeenCalledTimes(1);
      expect(setFormData).toHaveBeenCalledWith(expect.any(Function));
    });

    it('should_displayExistingSpecialRequests_when_provided', () => {
      const formDataWithRequests = {
        ...mockFormData,
        specialRequests: 'Need wheelchair access',
      };
      render(<ConfirmRegistrationStep {...defaultProps} formData={formDataWithRequests} />);

      const textarea = screen.getByPlaceholderText(/Dietary requirements/i) as HTMLTextAreaElement;
      expect(textarea.value).toBe('Need wheelchair access');
    });
  });

  describe('Terms and Conditions', () => {
    it('should_displayTermsCheckbox_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const checkbox = screen.getByLabelText(/I agree to the terms and conditions/i);
      expect(checkbox).toBeInTheDocument();
    });

    it('should_displayWarningMessage_when_termsNotAccepted', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const warning = screen.getByText(/You must accept the terms/i);
      expect(warning).toBeInTheDocument();
    });

    it('should_hideWarningMessage_when_termsAccepted', () => {
      const formDataWithTerms = { ...mockFormData, termsAccepted: true };
      render(<ConfirmRegistrationStep {...defaultProps} formData={formDataWithTerms} />);

      const warning = screen.queryByText(/You must accept the terms/i);
      expect(warning).not.toBeInTheDocument();
    });

    it('should_updateTermsAccepted_when_checkboxToggled', () => {
      const setFormData = vi.fn();
      render(<ConfirmRegistrationStep {...defaultProps} setFormData={setFormData} />);

      const checkbox = screen.getByLabelText(/I agree to the terms and conditions/i);
      fireEvent.click(checkbox);

      expect(setFormData).toHaveBeenCalledTimes(1);
      expect(setFormData).toHaveBeenCalledWith(expect.any(Function));

      const updater = setFormData.mock.calls[0][0];
      const result = updater(mockFormData);
      expect(result.termsAccepted).toBe(true);
    });

    it('should_displayTermsLinks_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      const termsLink = screen.getByRole('link', { name: /terms and conditions/i });
      const privacyLink = screen.getByRole('link', { name: /privacy policy/i });

      expect(termsLink).toHaveAttribute('href', '/terms');
      expect(privacyLink).toHaveAttribute('href', '/privacy');
      expect(termsLink).toHaveAttribute('target', '_blank');
      expect(privacyLink).toHaveAttribute('target', '_blank');
    });
  });

  describe('Account Creation CTA', () => {
    it('should_displayAccountCreationPrompt_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      expect(screen.getByText(/Want to manage all your registrations/i)).toBeInTheDocument();
      expect(
        screen.getByText(/After registering, you can create a free account/i)
      ).toBeInTheDocument();
    });
  });

  describe('Header and Instructions', () => {
    it('should_displayStepTitle_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      expect(screen.getByText('Review & Confirm')).toBeInTheDocument();
    });

    it('should_displayInstructions_when_rendered', () => {
      render(<ConfirmRegistrationStep {...defaultProps} />);

      expect(
        screen.getByText(/Please review your information and complete your registration/i)
      ).toBeInTheDocument();
    });
  });
});
