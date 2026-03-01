import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserDetailModal from './UserDetailModal';
import '../../../i18n/config';
import type { User } from '../../../types/user.types';

describe('UserDetailModal Component', () => {
  let queryClient: QueryClient;

  const mockUser: User = {
    id: '123',
    firstName: 'Anna',
    lastName: 'Müller',
    email: 'anna.mueller@example.com',
    roles: ['ORGANIZER', 'SPEAKER'],
    isActive: true,
    profilePictureUrl: 'https://example.com/avatar.jpg',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    company: {
      id: 'company-1',
      name: 'TechCorp AG',
      website: 'https://techcorp.com',
      description: 'Technology company',
      logoUrl: 'https://example.com/logo.jpg',
      isActive: true,
      createdAt: '2024-01-01T10:00:00Z',
      updatedAt: '2024-01-01T10:00:00Z',
    },
  };

  const mockOnClose = vi.fn();

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    mockOnClose.mockClear();
  });

  const renderComponent = (user: User | null, open: boolean) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <UserDetailModal user={user} open={open} onClose={mockOnClose} />
      </QueryClientProvider>
    );
  };

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderComponent(mockUser, false);

    // Dialog should not be visible
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderComponent(mockUser, true);

    // Dialog should be visible
    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
  });

  it('should_displayUserName_when_userProvided', () => {
    renderComponent(mockUser, true);

    // User's full name should be displayed
    expect(screen.getByText(/Anna Müller/i)).toBeInTheDocument();
  });

  it('should_displayUserEmail_when_userProvided', () => {
    renderComponent(mockUser, true);

    // User's email should be displayed
    expect(screen.getByText(/anna.mueller@example.com/i)).toBeInTheDocument();
  });

  it('should_displayUserRoles_when_userProvided', () => {
    renderComponent(mockUser, true);

    // Roles should be displayed as chips
    expect(screen.getByText(/Organizer/i)).toBeInTheDocument();
    expect(screen.getByText(/Speaker/i)).toBeInTheDocument();
  });

  it('should_displayCompanyName_when_companyProvided', () => {
    renderComponent(mockUser, true);

    // Company name should be displayed
    expect(screen.getByText(/TechCorp AG/i)).toBeInTheDocument();
  });

  it('should_displayProfilePicture_when_urlProvided', () => {
    renderComponent(mockUser, true);

    // Profile picture should be rendered
    const avatar = screen.getByRole('img', { name: /Anna Müller/i });
    expect(avatar).toBeInTheDocument();
    expect(avatar).toHaveAttribute('src', mockUser.profilePictureUrl);
  });

  it('should_displayActiveStatus_when_userIsActive', () => {
    renderComponent(mockUser, true);

    // Active status chip should be displayed
    expect(screen.getByText(/Active/i)).toBeInTheDocument();
  });

  it('should_closeModal_when_closeButtonClicked', () => {
    renderComponent(mockUser, true);

    // Click close button (the outlined button in the footer)
    const closeButton = screen.getByRole('button', { name: /^Close$/ });
    fireEvent.click(closeButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('should_closeModal_when_escapeKeyPressed', () => {
    renderComponent(mockUser, true);

    // Press escape key
    const dialog = screen.getByRole('dialog');
    fireEvent.keyDown(dialog, { key: 'Escape', code: 'Escape' });

    expect(mockOnClose).toHaveBeenCalled();
  });

  it('should_notRender_when_userIsNull', () => {
    renderComponent(null, true);

    // Should not render dialog when user is null
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
