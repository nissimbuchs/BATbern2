/**
 * UserList Component Tests
 * Story 2.5.2: User Management Frontend - Task 5b
 *
 * Tests for UserList component covering rendering and basic interactions
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import UserList from './UserList';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { User } from '@/types/user.types';

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock store
const mockSetSelectedUser = vi.fn();
const mockSetPage = vi.fn();
const mockSetLimit = vi.fn();

vi.mock('@/stores/userManagementStore', () => ({
  useUserManagementStore: () => ({
    filters: {},
    pagination: { page: 1, limit: 10 },
    selectedUser: null,
    setSelectedUser: mockSetSelectedUser,
    setPage: mockSetPage,
    setLimit: mockSetLimit,
  }),
}));

// Mock useUserList hook
const mockRefetch = vi.fn();
let mockData = {
  data: [] as User[],
  pagination: {
    page: 1,
    totalPages: 1,
    limit: 10,
    total: 0,
  },
};
let mockIsLoading = false;
let mockIsError = false;

vi.mock('@/hooks/useUserManagement', () => ({
  useUserList: () => ({
    data: mockData,
    isLoading: mockIsLoading,
    isError: mockIsError,
    refetch: mockRefetch,
  }),
}));

// Mock child components
vi.mock('./UserTable', () => ({
  default: ({ users, onRowClick }: { users: User[]; onRowClick: (user: User) => void }) => (
    <div data-testid="user-table">
      {users.map((user) => (
        <div key={user.id} onClick={() => onRowClick(user)}>
          {user.firstName} {user.lastName}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('./UserFilters', () => ({
  default: () => <div data-testid="user-filters">Filters</div>,
}));

vi.mock('./UserDetailModal', () => ({
  default: () => <div data-testid="user-detail-modal">Detail Modal</div>,
}));

vi.mock('./UserCreateEditModal', () => ({
  default: () => <div data-testid="user-create-edit-modal">Create/Edit Modal</div>,
}));

vi.mock('./RoleManagerModal', () => ({
  default: () => <div data-testid="role-manager-modal">Role Manager</div>,
}));

vi.mock('./DeleteUserDialog', () => ({
  default: () => <div data-testid="delete-user-dialog">Delete Dialog</div>,
}));

vi.mock('./UserPagination', () => ({
  default: () => <div data-testid="user-pagination">Pagination</div>,
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
];

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>
  );
};

describe('UserList Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockClear();
    mockIsLoading = false;
    mockIsError = false;
    mockData = {
      data: mockUsers,
      pagination: {
        page: 1,
        totalPages: 1,
        limit: 10,
        total: 1,
      },
    };
  });

  describe('Loading State', () => {
    it('should_showLoadingIndicator_when_dataIsLoading', () => {
      mockIsLoading = true;

      renderWithProviders(<UserList />);

      expect(screen.getByText('loading.users')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should_showErrorMessage_when_loadFails', () => {
      mockIsError = true;

      renderWithProviders(<UserList />);

      expect(screen.getByText('error.loadFailed')).toBeInTheDocument();
    });

    it('should_showRetryButton_when_loadFails', async () => {
      const user = userEvent.setup();
      mockIsError = true;

      renderWithProviders(<UserList />);

      const retryButton = screen.getByText('common:actions.retry');
      await user.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('Rendering', () => {
    it('should_renderTitle_when_componentMounts', () => {
      renderWithProviders(<UserList />);

      expect(screen.getByText('title')).toBeInTheDocument();
    });

    it('should_renderAddUserButton_when_componentMounts', () => {
      renderWithProviders(<UserList />);

      expect(screen.getByText('addUser')).toBeInTheDocument();
    });

    it('should_renderUserTable_when_dataLoaded', () => {
      renderWithProviders(<UserList />);

      expect(screen.getByTestId('user-table')).toBeInTheDocument();
    });

    it('should_renderFilters_when_componentMounts', () => {
      renderWithProviders(<UserList />);

      expect(screen.getByTestId('user-filters')).toBeInTheDocument();
    });

    it('should_renderPagination_when_dataHasPagination', () => {
      renderWithProviders(<UserList />);

      expect(screen.getByTestId('user-pagination')).toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('should_openCreateModal_when_addUserClicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(<UserList />);

      const addButton = screen.getByText('addUser');
      await user.click(addButton);

      await waitFor(() => {
        expect(screen.getByTestId('user-create-edit-modal')).toBeInTheDocument();
      });
    });

    it('should_setSelectedUser_when_userRowClicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(<UserList />);

      const userRow = screen.getByText('John Doe');
      await user.click(userRow);

      expect(mockNavigate).toHaveBeenCalledWith('/organizer/users/user-1');
    });
  });
});
