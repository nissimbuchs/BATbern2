/**
 * UserList Component Tests (RED Phase)
 *
 * TDD: Writing tests FIRST before implementation
 * Story 2.5.2: User Management Frontend - Task 5a (RED Phase)
 *
 * Test Coverage - AC1: User Management Screen
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import UserList from './UserList';
import '@testing-library/jest-dom';
import i18n from '@/i18n/config';

// Mock the API
vi.mock('@/services/api/userManagementApi', () => ({
  listUsers: vi.fn(() =>
    Promise.resolve({
      data: [
        {
          id: 'john.doe',
          email: 'john.doe@example.com',
          firstName: 'John',
          lastName: 'Doe',
          roles: ['ATTENDEE'],
          isActive: true,
          createdAt: '2025-01-15T10:00:00Z',
          updatedAt: '2025-01-15T10:00:00Z',
        },
        {
          id: 'jane.smith',
          email: 'jane.smith@example.com',
          firstName: 'Jane',
          lastName: 'Smith',
          roles: ['ORGANIZER', 'SPEAKER'],
          companyId: 'TechCorp AG',
          isActive: true,
          createdAt: '2025-01-15T10:00:00Z',
          updatedAt: '2025-01-15T10:00:00Z',
        },
      ],
      pagination: {
        page: 1,
        limit: 20,
        totalItems: 2,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    })
  ),
  searchUsers: vi.fn(() => Promise.resolve([])),
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </BrowserRouter>
  );
};

describe('UserList', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('de');
  });

  it('should_renderUserListTable_when_componentMounts', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText(/Benutzerverwaltung/i)).toBeInTheDocument();
    });
  });

  it('should_displayUserColumns_when_usersLoaded', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
    });
  });

  it('should_showAddUserButton_when_rendered', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await waitFor(() => {
      const addButton = screen.getByRole('button', { name: /Benutzer hinzufügen/i });
      expect(addButton).toBeInTheDocument();
    });
  });

  it('should_showLoadingState_when_dataFetching', () => {
    render(<UserList />, { wrapper: createWrapper() });

    // Loading state should be visible initially
    expect(screen.getByText(/Benutzer werden geladen/i)).toBeInTheDocument();
  });
});
