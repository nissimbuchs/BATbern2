import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserCreateModal from './UserCreateModal';
import '../../../i18n/config';

describe('UserCreateModal Component', () => {
  let queryClient: QueryClient;
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    mockOnClose.mockClear();
    mockOnSuccess.mockClear();
  });

  const renderComponent = (open: boolean) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <UserCreateModal open={open} onClose={mockOnClose} onSuccess={mockOnSuccess} />
      </QueryClientProvider>
    );
  };

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderComponent(false);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderComponent(true);

    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
  });

  it('should_displayFormFields_when_rendered', () => {
    renderComponent(true);

    // Check for required form fields
    expect(screen.getByLabelText(/first name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/last name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  });

  it('should_showValidationError_when_requiredFieldEmpty', async () => {
    renderComponent(true);

    // Try to submit without filling required fields
    const submitButton = screen.getByRole('button', { name: /create new user/i });
    fireEvent.click(submitButton);

    // Should show validation errors (multiple fields required)
    await waitFor(() => {
      const errors = screen.getAllByText(/this field is required/i);
      expect(errors.length).toBeGreaterThan(0);
    });
  });

  it('should_validateEmailFormat_when_invalidEmail', async () => {
    renderComponent(true);

    const emailInput = screen.getByLabelText(/email/i);
    fireEvent.change(emailInput, { target: { value: 'invalid-email' } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
    });
  });

  it('should_displayRoleCheckboxes_when_rendered', () => {
    renderComponent(true);

    // Check for role selection
    expect(screen.getByRole('checkbox', { name: /organizer/i })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /speaker/i })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /partner/i })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /attendee/i })).toBeInTheDocument();
  });

  it('should_requireAtLeastOneRole_when_submitting', async () => {
    renderComponent(true);

    // Fill in valid data but no roles
    fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: 'John' } });
    fireEvent.change(screen.getByLabelText(/last name/i), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'john.doe@example.com' },
    });

    const submitButton = screen.getByRole('button', { name: /create new user/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/select at least one role/i)).toBeInTheDocument();
    });
  });

  it('should_enableSubmitButton_when_formValid', async () => {
    renderComponent(true);

    // Fill in all required fields
    fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: 'John' } });
    fireEvent.change(screen.getByLabelText(/last name/i), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'john.doe@example.com' },
    });

    // Select at least one role
    const attendeeCheckbox = screen.getByRole('checkbox', { name: /attendee/i });
    fireEvent.click(attendeeCheckbox);

    const submitButton = screen.getByRole('button', { name: /create new user/i });
    expect(submitButton).not.toBeDisabled();
  });

  it('should_closeModal_when_cancelButtonClicked', () => {
    renderComponent(true);

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('should_resetForm_when_modalOpens', () => {
    const { rerender } = renderComponent(false);

    // Open modal, fill form, close, reopen
    rerender(
      <QueryClientProvider client={queryClient}>
        <UserCreateModal open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />
      </QueryClientProvider>
    );

    fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: 'John' } });

    rerender(
      <QueryClientProvider client={queryClient}>
        <UserCreateModal open={false} onClose={mockOnClose} onSuccess={mockOnSuccess} />
      </QueryClientProvider>
    );

    rerender(
      <QueryClientProvider client={queryClient}>
        <UserCreateModal open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />
      </QueryClientProvider>
    );

    // Form should be reset
    const firstNameInput = screen.getByLabelText(/first name/i) as HTMLInputElement;
    expect(firstNameInput.value).toBe('');
  });
});
