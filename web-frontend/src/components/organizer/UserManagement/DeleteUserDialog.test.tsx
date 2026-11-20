import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DeleteUserDialog from './DeleteUserDialog';
import type { User } from '../../../types/user.types';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n/config';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';

// HTTP is answered by the MSW harness (spec-checked): the real hook, service and apiClient run.

const mockUser: User = {
  id: 'john.doe',
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

describe('DeleteUserDialog Component', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/delete user/i)).toBeInTheDocument();
  });

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={false}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_sayTheProfileIsDeleted_when_confirmationShown', () => {
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByTestId('delete-user-gdpr-warning')).toHaveTextContent(
      /deletes the profile with its additional addresses, roles and watch pairings/i
    );
  });

  it('should_displayUserInfo_when_rendered', () => {
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/john doe/i)).toBeInTheDocument();
    expect(screen.getByText(/john.doe@example.com/i)).toBeInTheDocument();
  });

  it('should_closeDialog_when_cancelButtonClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DeleteUserDialog
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

  it('should_callDeleteAPI_when_confirmButtonClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const deleteButton = screen.getByRole('button', { name: /delete/i });
    await user.click(deleteButton);

    await waitFor(() => {
      expect(mockOnSuccess).toHaveBeenCalledTimes(1);
    });
    expect(api.calls('deleteUser')[0].params.username).toBe('john.doe');
  });

  it('should_sayWhatIsKept_when_rendered', () => {
    // #1120 postponed (owner 2026-11-20): the delete removes the profile only, so the dialog must
    // not promise a full erasure.
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const kept = screen.getByTestId('delete-user-cascade-warning');
    expect(kept).toHaveTextContent(/kept: the login, registrations, talks/i);
    expect(kept).toHaveTextContent(/handled by hand/i);
    expect(screen.queryByText(/all associated data/i)).not.toBeInTheDocument();
  });

  it('should_showError_when_deleteFails', async () => {
    api.on('deleteUser', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));
    const user = userEvent.setup();
    renderWithProviders(
      <DeleteUserDialog
        user={mockUser}
        open={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    await user.click(screen.getByRole('button', { name: /delete/i }));

    expect(
      await screen.findByText(i18n.t('userManagement:error.deleteFailed'))
    ).toBeInTheDocument();
    expect(mockOnSuccess).not.toHaveBeenCalled();
  });
});
