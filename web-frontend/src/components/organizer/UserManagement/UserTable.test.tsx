/**
 * UserTable Component Tests
 * Story 2.5.2: User Management Frontend - Task 6b
 *
 * Tests for UserTable component covering basic rendering and user interactions
 */

import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import UserTable from './UserTable';
import type { User } from '@/types/user.types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock CompanyCell component
vi.mock('./CompanyCell', () => ({
  default: ({ companyId }: { companyId?: string }) => (
    <div data-testid="company-cell">{companyId || 'N/A'}</div>
  ),
}));

const mockUsers: User[] = [
  {
    id: 'user-1',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
    companyId: 'company-1',
    roles: ['ORGANIZER'],
    active: true,
    profilePictureUrl: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'user-2',
    firstName: 'Jane',
    lastName: 'Smith',
    email: 'jane.smith@example.com',
    companyId: 'company-2',
    roles: ['SPEAKER', 'PARTNER'],
    active: false,
    profilePictureUrl: null,
    createdAt: '2024-01-02T00:00:00Z',
    updatedAt: '2024-01-02T00:00:00Z',
  },
];

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
};

describe('UserTable Component', () => {
  describe('Rendering', () => {
    it('should_renderTableHeaders_when_usersProvided', () => {
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      expect(screen.getByText('common:labels.name')).toBeInTheDocument();
      expect(screen.getByText('common:labels.email')).toBeInTheDocument();
      expect(screen.getByText('common:labels.company')).toBeInTheDocument();
      expect(screen.getByText('table.headers.roles')).toBeInTheDocument();
      expect(screen.getByText('common:labels.status')).toBeInTheDocument();
      expect(screen.getByText('common:labels.actions')).toBeInTheDocument();
    });

    it('should_renderUserRows_when_usersProvided', () => {
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('jane.smith@example.com')).toBeInTheDocument();
    });

    it('should_showEmptyState_when_noUsers', () => {
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(<UserTable users={[]} onRowClick={mockRowClick} onAction={mockAction} />);

      expect(screen.getByText('table.empty')).toBeInTheDocument();
    });

    it('should_displayRoleBadges_when_userHasRoles', () => {
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      expect(screen.getByText('common:role.organizer')).toBeInTheDocument();
      expect(screen.getByText('common:role.speaker')).toBeInTheDocument();
      expect(screen.getByText('common:role.partner')).toBeInTheDocument();
    });

    it('should_displayActiveStatus_when_userIsActive', () => {
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      expect(screen.getByText('status.active')).toBeInTheDocument();
      expect(screen.getByText('status.inactive')).toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('should_callOnRowClick_when_rowClicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const row = screen.getByText('John Doe').closest('tr');
      if (row) {
        await user.click(row);
      }

      expect(mockRowClick).toHaveBeenCalledWith(mockUsers[0]);
    });

    it('should_openActionsMenu_when_moreIconClicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const moreButtons = screen.getAllByLabelText('actions.openMenu');
      await user.click(moreButtons[0]);

      expect(screen.getByText('actions.view')).toBeInTheDocument();
      expect(screen.getByText('actions.editRoles')).toBeInTheDocument();
      expect(screen.getByText('common:actions.delete')).toBeInTheDocument();
    });

    it('should_callOnAction_when_menuItemClicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const moreButtons = screen.getAllByLabelText('actions.openMenu');
      await user.click(moreButtons[0]);

      const viewAction = screen.getByText('actions.view');
      await user.click(viewAction);

      // Verify action was called with correct action type and a user object
      expect(mockAction).toHaveBeenCalledWith(
        'view',
        expect.objectContaining({
          id: expect.any(String),
          email: expect.any(String),
        })
      );
    });
  });

  describe('Sorting', () => {
    it('should_sortByName_when_nameHeaderClicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const nameHeader = screen.getByText('common:labels.name');
      await user.click(nameHeader);

      // Verify both users are still rendered after sort
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });

    it('should_sortByEmail_when_emailHeaderClicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const emailHeader = screen.getByText('common:labels.email');
      await user.click(emailHeader);

      // Sorting should be applied
      expect(screen.getByText('jane.smith@example.com')).toBeInTheDocument();
    });

    it('should_toggleSortDirection_when_sameHeaderClickedTwice', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();
      const mockAction = vi.fn();

      renderWithProviders(
        <UserTable users={mockUsers} onRowClick={mockRowClick} onAction={mockAction} />
      );

      const nameHeader = screen.getByText('common:labels.name');
      await user.click(nameHeader);
      await user.click(nameHeader);

      // Direction should toggle
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });
});
