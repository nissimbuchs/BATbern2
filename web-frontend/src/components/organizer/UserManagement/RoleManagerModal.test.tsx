import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RoleManagerModal from './RoleManagerModal';
import type { User } from '../../../types/user.types';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n/config';

// Mock the useUpdateUserRoles hook
vi.mock('../../../hooks/useUserManagement', () => ({
  useUpdateUserRoles: () => ({
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
    isError: false,
  }),
}));

const mockUser: User = {
  id: 'user-123',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john.doe@example.com',
  roles: ['ATTENDEE'],
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </QueryClientProvider>
  );
};

describe('RoleManagerModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/edit roles/i)).toBeInTheDocument();
  });

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={false}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_displayCurrentRoles_when_modalOpens', () => {
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    // ATTENDEE should be checked
    const attendeeCheckbox = screen.getByRole('checkbox', { name: /attendee/i });
    expect(attendeeCheckbox).toBeChecked();

    // Others should not be checked
    const organizerCheckbox = screen.getByRole('checkbox', { name: /organizer/i });
    expect(organizerCheckbox).not.toBeChecked();
  });

  it('should_toggleRoles_when_checkboxClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const speakerCheckbox = screen.getByRole('checkbox', { name: /speaker/i });
    expect(speakerCheckbox).not.toBeChecked();

    await user.click(speakerCheckbox);

    await waitFor(() => {
      expect(speakerCheckbox).toBeChecked();
    });
  });

  it('should_closeModal_when_cancelButtonClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    await user.click(cancelButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('should_callOnSuccess_when_saveButtonClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockOnSuccess).toHaveBeenCalledTimes(1);
    });
  });

  it('should_displayUserName_when_rendered', () => {
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/john doe/i)).toBeInTheDocument();
  });

  it('should_requireAtLeastOneRole_when_validating', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <RoleManagerModal
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    // Uncheck the only role (ATTENDEE)
    const attendeeCheckbox = screen.getByRole('checkbox', { name: /attendee/i });
    await user.click(attendeeCheckbox);

    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    // Should show error message
    await waitFor(() => {
      expect(screen.getByText(/at least one role/i)).toBeInTheDocument();
    });

    // Should not call onSuccess
    expect(mockOnSuccess).not.toHaveBeenCalled();
  });
});
