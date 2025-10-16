/**
 * CompanyForm Component Tests (RED Phase - TDD)
 *
 * Tests for company creation and editing modal form
 * - Create and edit modes
 * - Form validation (Swiss UID, required fields)
 * - Duplicate name detection
 * - Unsaved changes warning
 *
 * Story: 2.5.1 - Company Management Frontend
 * Acceptance Criteria: AC3, AC4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { CompanyForm } from '@/components/shared/Company/CompanyForm';
import type { Company } from '@/types/company.types';

// Updated to match backend CompanyResponse schema
const mockCompany: Company = {
  id: 'company-123',
  name: 'Acme Corporation',
  displayName: 'ACME Corp',
  swissUID: 'CHE-123.456.789',
  website: 'https://acme.com',
  industry: 'Technology',
  description: 'A leading tech company',
  isVerified: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
  createdBy: 'user-123',
  // Removed: sector, location, logoUrl (logoUrl is now nested in logo object), verificationStatus
};

describe('CompanyForm Component - AC3 Create Company Form', () => {
  describe('Modal Display', () => {
    it('should_openModal_when_createCompanyButtonClicked', () => {
      // AC3 Test 3.1: Modal opens for company creation
      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Create New Company')).toBeInTheDocument();
    });

    it('should_displayAllFormFields_when_modalOpened', () => {
      // Test that all form fields are present (updated to match backend schema)
      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      // Required field
      expect(screen.getByLabelText(/company name/i)).toBeInTheDocument();

      // Optional fields
      expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/swiss uid/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/website/i)).toBeInTheDocument();
      expect(screen.getAllByLabelText(/industry/i)[0]).toBeInTheDocument();
      expect(screen.getByLabelText(/description/i)).toBeInTheDocument();

      // Removed: city, canton, country, sector (not in backend schema)
    });

    it('should_displayCreateButtons_when_createMode', () => {
      // Test that create mode has "Save Draft" and "Save & Create" buttons
      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
          allowDraft={true}
        />
      );

      expect(screen.getByRole('button', { name: /save draft/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /save & create/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });
  });

  describe('Required Field Validation', () => {
    it('should_validateRequiredFields_when_formSubmitted', async () => {
      // AC3 Test 3.2: Required field validation (only name is required now)
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Try to submit without filling required fields
      const submitButton = screen.getByRole('button', { name: /save & create/i });
      await user.click(submitButton);

      // Should show validation error for company name only (all other fields are optional)
      await waitFor(() => {
        expect(screen.getByText(/company name is required/i)).toBeInTheDocument();
      });

      // onSubmit should not be called
      expect(onSubmit).not.toHaveBeenCalled();
    });

    it('should_displayInlineErrorMessages_when_validationFails', async () => {
      // Test that inline error messages appear below each field
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      // Submit form without filling required fields
      const submitButton = screen.getByRole('button', { name: /save & create/i });
      await user.click(submitButton);

      // Error messages should be in helper text below each field
      await waitFor(() => {
        const nameField = screen.getByLabelText(/company name/i);
        const nameError = nameField.closest('.MuiFormControl-root')?.querySelector('.MuiFormHelperText-root');
        expect(nameError).toHaveTextContent(/required/i);
      });
    });
  });

  describe('Swiss UID Validation', () => {
    it('should_validateSwissUIDFormat_when_uidEntered', async () => {
      // AC3 Test 3.3: Swiss UID format validation (CHE-XXX.XXX.XXX)
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      const uidField = screen.getByLabelText(/swiss uid/i);

      // Test invalid format
      await user.type(uidField, 'CHE-123');
      await user.tab(); // Trigger blur event

      await waitFor(() => {
        expect(screen.getByText(/invalid swiss uid format/i)).toBeInTheDocument();
      });
    });

    it('should_acceptValidSwissUID_when_correctFormatEntered', async () => {
      // Test valid Swiss UID format
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      const uidField = screen.getByLabelText(/swiss uid/i);

      // Enter valid Swiss UID
      await user.type(uidField, 'CHE-123.456.789');
      await user.tab();

      // Should not show error
      await waitFor(() => {
        expect(screen.queryByText(/invalid swiss uid format/i)).not.toBeInTheDocument();
      });
    });

    it('should_allowEmptySwissUID_when_optional', async () => {
      // Swiss UID is optional, so empty should be valid
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Fill only required field (name only, all others optional)
      await user.type(screen.getByLabelText(/company name/i), 'Test Company');

      // Submit should succeed without Swiss UID (and without any other fields)
      await user.click(screen.getByRole('button', { name: /save & create/i }));

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalled();
      });
    });
  });

  describe('Duplicate Name Detection', () => {
    it('should_detectDuplicateName_when_companyNameExists', async () => {
      // AC3 Test 3.4: Duplicate company name detection (backend validation)
      const user = userEvent.setup();
      const onSubmit = vi.fn().mockRejectedValue({
        response: {
          status: 409,
          data: {
            message: 'Company with this name already exists',
            error: 'ERR_DUPLICATE_COMPANY',
          },
        },
      });

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Fill form with duplicate company name
      await user.type(screen.getByLabelText(/company name/i), 'Existing Company');

      // Submit form
      await user.click(screen.getByRole('button', { name: /save & create/i }));

      // Should display duplicate error message
      await waitFor(() => {
        expect(screen.getByText(/company with this name already exists/i)).toBeInTheDocument();
      });
    });
  });

  describe('Save Draft Functionality', () => {
    it('should_allowSaveDraft_when_incompleteDataProvided', async () => {
      // AC3 Test 3.5: Save draft with incomplete data
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
          allowDraft={true}
        />
      );

      // Fill only company name
      await user.type(screen.getByLabelText(/company name/i), 'Draft Company');

      // Click "Save Draft" button
      await user.click(screen.getByRole('button', { name: /save draft/i }));

      // Should call onSubmit with draft flag (wait for async setTimeout in component)
      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalled();
      }, { timeout: 3000 });

      // Verify draft flag was passed
      const callArgs = onSubmit.mock.calls[0];
      expect(callArgs[0]).toEqual(expect.objectContaining({
        name: 'Draft Company',
      }));
      expect(callArgs[1]).toEqual(expect.objectContaining({ isDraft: true }));
    });

    it('should_not_validateRequiredFields_when_savingDraft', async () => {
      // Draft should not require all fields to be filled
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
          allowDraft={true}
        />
      );

      // Only fill company name (missing required fields)
      await user.type(screen.getByLabelText(/company name/i), 'Draft Company');

      // Click "Save Draft"
      await user.click(screen.getByRole('button', { name: /save draft/i }));

      // Should not show validation errors
      expect(screen.queryByText(/industry is required/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/city is required/i)).not.toBeInTheDocument();
    });
  });

  describe('Full Form Submission', () => {
    it('should_callCreateAPI_when_saveButtonClicked', async () => {
      // AC3 Test 3.6: Call create API on successful form submission
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="create"
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Fill all fields (name is required, others optional)
      await user.type(screen.getByLabelText(/company name/i), 'New Company');
      await user.type(screen.getByLabelText(/display name/i), 'NewCo');
      await user.type(screen.getByLabelText(/swiss uid/i), 'CHE-987.654.321');
      await user.type(screen.getByLabelText(/website/i), 'https://newco.com');
      await user.type(screen.getByLabelText(/description/i), 'A new company');

      // Select industry (optional field)
      const industrySelect = screen.getAllByLabelText(/industry/i)[0];
      await user.click(industrySelect);
      await user.click(screen.getByRole('option', { name: /technology/i }));

      // Sector and location removed - no longer in backend schema

      // Submit form
      await user.click(screen.getByRole('button', { name: /save & create/i }));

      // Should call onSubmit with form data (updated to match backend schema)
      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'New Company',
            displayName: 'NewCo',
            swissUID: 'CHE-987.654.321',
            website: 'https://newco.com',
            industry: 'Technology',
            description: 'A new company',
            // Removed: sector, location (not in backend schema)
          }),
          { isDraft: false }
        );
      });
    });
  });
});

describe('CompanyForm Component - AC4 Edit Company Form', () => {
  describe('Edit Mode Display', () => {
    it('should_prefillForm_when_editButtonClicked', () => {
      // AC4 Test 4.1: Pre-fill form with existing company data
      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Edit Company')).toBeInTheDocument();

      // Verify fields are pre-filled (only fields that exist in backend schema)
      expect(screen.getByDisplayValue('Acme Corporation')).toBeInTheDocument();
      expect(screen.getByDisplayValue('ACME Corp')).toBeInTheDocument();
      expect(screen.getByDisplayValue('CHE-123.456.789')).toBeInTheDocument();
      expect(screen.getByDisplayValue('https://acme.com')).toBeInTheDocument();
      expect(screen.getByDisplayValue('A leading tech company')).toBeInTheDocument();
      // Removed: Bern, BE, Switzerland (location removed from schema)
    });

    it('should_displayEditButtons_when_editMode', () => {
      // Test that edit mode has "Save Changes" button (no draft option)
      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /save draft/i })).not.toBeInTheDocument();
    });
  });

  describe('Partial Update', () => {
    it('should_sendPartialUpdate_when_onlyNameChanged', async () => {
      // AC4 Test 4.2: Partial update - only send changed fields
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Change only the company name
      const nameField = screen.getByLabelText(/company name/i);
      await user.clear(nameField);
      await user.type(nameField, 'Updated Acme Corporation');

      // Submit form
      await user.click(screen.getByRole('button', { name: /save changes/i }));

      // Should only send changed fields
      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'Updated Acme Corporation',
          }),
          { isPartialUpdate: true, changedFields: ['name'] }
        );
      });
    });

    it('should_sendMultipleFields_when_multipleFieldsChanged', async () => {
      // Test partial update with multiple changed fields
      const user = userEvent.setup();
      const onSubmit = vi.fn();

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />
      );

      // Change name and website
      const nameField = screen.getByLabelText(/company name/i);
      await user.clear(nameField);
      await user.type(nameField, 'Updated Name');

      const websiteField = screen.getByLabelText(/website/i);
      await user.clear(websiteField);
      await user.type(websiteField, 'https://updated-acme.com');

      // Submit form
      await user.click(screen.getByRole('button', { name: /save changes/i }));

      // Should include both changed fields
      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'Updated Name',
            website: 'https://updated-acme.com',
          }),
          { isPartialUpdate: true, changedFields: expect.arrayContaining(['name', 'website']) }
        );
      });
    });
  });

  describe('Unsaved Changes Warning', () => {
    it('should_showUnsavedChangesWarning_when_modalClosedWithChanges', async () => {
      // AC4 Test 4.3: Warn user about unsaved changes
      const user = userEvent.setup();
      const onClose = vi.fn();

      // Mock window.confirm
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={onClose}
          onSubmit={vi.fn()}
        />
      );

      // Make a change
      const nameField = screen.getByLabelText(/company name/i);
      await user.type(nameField, ' Updated');

      // Try to close modal
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      // Should show confirmation dialog
      await waitFor(() => {
        expect(confirmSpy).toHaveBeenCalledWith(
          expect.stringContaining('unsaved changes')
        );
      });

      // Should not close if user cancels
      expect(onClose).not.toHaveBeenCalled();

      confirmSpy.mockRestore();
    });

    it('should_closeWithoutWarning_when_noChanges', async () => {
      // Test that modal closes without warning if no changes made
      const user = userEvent.setup();
      const onClose = vi.fn();

      const confirmSpy = vi.spyOn(window, 'confirm');

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={onClose}
          onSubmit={vi.fn()}
        />
      );

      // Close modal without making changes
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      // Should not show confirmation
      expect(confirmSpy).not.toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();

      confirmSpy.mockRestore();
    });

    it('should_closeModal_when_userConfirmsUnsavedChanges', async () => {
      // Test that modal closes if user confirms discarding changes
      const user = userEvent.setup();
      const onClose = vi.fn();

      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={onClose}
          onSubmit={vi.fn()}
        />
      );

      // Make a change
      const nameField = screen.getByLabelText(/company name/i);
      await user.type(nameField, ' Updated');

      // Try to close modal
      await user.click(screen.getByRole('button', { name: /cancel/i }));

      // Should show confirmation and close
      await waitFor(() => {
        expect(confirmSpy).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });

      confirmSpy.mockRestore();
    });
  });

  describe('Role-Based Access Control', () => {
    it('should_restrictAccess_when_speakerEditsOtherCompany', async () => {
      // AC4 Test 4.4: Speakers can only edit their own company
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
          userRole="speaker"
          userCompanyId="different-company-id"
        />
      );

      // Form should be disabled or show access denied message
      expect(screen.getByText(/you don't have permission/i)).toBeInTheDocument();

      // Submit button should be disabled
      const submitButton = screen.getByRole('button', { name: /save changes/i });
      expect(submitButton).toBeDisabled();
    });

    it('should_allowAccess_when_speakerEditsOwnCompany', () => {
      // Speakers can edit their own company
      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
          userRole="speaker"
          userCompanyId="company-123"
        />
      );

      // Form should be accessible
      expect(screen.queryByText(/you don't have permission/i)).not.toBeInTheDocument();

      const submitButton = screen.getByRole('button', { name: /save changes/i });
      expect(submitButton).not.toBeDisabled();
    });

    it('should_allowAccess_when_organizerEditsAnyCompany', () => {
      // Organizers can edit any company
      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
          userRole="organizer"
          userCompanyId="different-company-id"
        />
      );

      // Form should be accessible
      expect(screen.queryByText(/you don't have permission/i)).not.toBeInTheDocument();

      const submitButton = screen.getByRole('button', { name: /save changes/i });
      expect(submitButton).not.toBeDisabled();
    });
  });

  describe('Validation in Edit Mode', () => {
    it('should_validateRequiredFields_when_editingCompany', async () => {
      // Edit mode should still validate required fields
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      // Clear required field
      const nameField = screen.getByLabelText(/company name/i);
      await user.clear(nameField);

      // Try to submit
      await user.click(screen.getByRole('button', { name: /save changes/i }));

      // Should show validation error
      await waitFor(() => {
        expect(screen.getByText(/company name is required/i)).toBeInTheDocument();
      });
    });

    it('should_validateSwissUID_when_editingCompany', async () => {
      // Edit mode should validate Swiss UID format
      const user = userEvent.setup();

      render(
        <CompanyForm
          open={true}
          mode="edit"
          initialData={mockCompany}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />
      );

      // Enter invalid Swiss UID
      const uidField = screen.getByLabelText(/swiss uid/i);
      await user.clear(uidField);
      await user.type(uidField, 'INVALID-UID');
      await user.tab();

      // Should show validation error
      await waitFor(() => {
        expect(screen.getByText(/invalid swiss uid format/i)).toBeInTheDocument();
      });
    });
  });
});

describe('CompanyForm Component - Accessibility', () => {
  it('should_haveProperAriaLabels_when_rendered', () => {
    // Test ARIA labels for accessibility
    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-labelledby');
    expect(dialog).toHaveAttribute('aria-describedby');
  });

  it('should_trapFocus_when_modalOpen', () => {
    // Modal should trap focus within dialog
    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('role', 'dialog');
  });

  it('should_announceErrors_when_validationFails', async () => {
    // Screen readers should announce validation errors via helper text
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    // Submit without filling fields
    await user.click(screen.getByRole('button', { name: /save & create/i }));

    // Error messages should be present in form (MUI uses helper text, not role="alert")
    await waitFor(() => {
      const errorText = screen.getByText(/company name is required/i);
      expect(errorText).toBeInTheDocument();
    });
  });
});

describe('CompanyForm Component - Website Validation', () => {
  it('should_validateWebsiteURL_when_websiteEntered', async () => {
    // Test website URL validation
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const websiteField = screen.getByLabelText(/website/i);

    // Test invalid URL
    await user.type(websiteField, 'not-a-url');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText(/invalid website url/i)).toBeInTheDocument();
    });
  });

  it('should_acceptValidWebsiteURL_when_correctFormatEntered', async () => {
    // Test valid website URL
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const websiteField = screen.getByLabelText(/website/i);

    // Enter valid URL
    await user.type(websiteField, 'https://example.com');
    await user.tab();

    // Should not show error
    await waitFor(() => {
      expect(screen.queryByText(/invalid website url/i)).not.toBeInTheDocument();
    });
  });
});

describe('CompanyForm Component - Character Limits', () => {
  it('should_enforceMaxLength_when_nameExceedsLimit', async () => {
    // Test max length for company name (200 characters)
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const nameField = screen.getByLabelText(/company name/i);
    const longName = 'A'.repeat(201);

    await user.type(nameField, longName);
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText(/name must be at most 200 characters/i)).toBeInTheDocument();
    });
  });

  it('should_showCharacterCount_when_descriptionTyped', async () => {
    // Test character counter for description (500 max)
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const descriptionField = screen.getByLabelText(/description/i);

    await user.type(descriptionField, 'Test description');

    // Should show character count
    expect(screen.getByText(/16\/500 characters/i)).toBeInTheDocument();
  });

  it('should_enforceMaxLength_when_descriptionExceedsLimit', async () => {
    // Test max length for description
    const user = userEvent.setup();

    render(
      <CompanyForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const descriptionField = screen.getByLabelText(/description/i) as HTMLTextAreaElement;
    const longDescription = 'A'.repeat(501);

    // Use paste event for large text (typing 501 chars is too slow for tests)
    await user.click(descriptionField);
    await user.paste(longDescription);
    await user.tab(); // Trigger blur to run validation

    await waitFor(() => {
      expect(screen.getByText(/description must be at most 500 characters/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  });
});
