/**
 * UserTable Component Tests (RED Phase)
 *
 * TDD: Writing tests FIRST before implementation
 * Story 2.5.2: User Management Frontend - Task 6a (RED Phase)
 *
 * Test Coverage - AC1: User table with sorting and actions
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserTable from './UserTable';
import '@testing-library/jest-dom';
import i18n from '@/i18n/config';
import type { User } from '@/types/user.types';

const mockUsers: User[] = [
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
];

describe('UserTable', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de');
  });

  it('should_renderTableHeaders_when_rendered', () => {
    const onRowClick = vi.fn();
    const onAction = vi.fn();

    render(<UserTable users={mockUsers} onRowClick={onRowClick} onAction={onAction} />);

    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('E-Mail')).toBeInTheDocument();
    expect(screen.getByText('Rollen')).toBeInTheDocument();
  });

  it('should_renderAllUsers_when_usersProvided', () => {
    const onRowClick = vi.fn();
    const onAction = vi.fn();

    render(<UserTable users={mockUsers} onRowClick={onRowClick} onAction={onAction} />);

    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
    expect(screen.getByText('jane.smith@example.com')).toBeInTheDocument();
  });

  it('should_displayRoleBadges_when_usersHaveRoles', () => {
    const onRowClick = vi.fn();
    const onAction = vi.fn();

    render(<UserTable users={mockUsers} onRowClick={onRowClick} onAction={onAction} />);

    expect(screen.getByText('ATTENDEE')).toBeInTheDocument();
    expect(screen.getByText('ORGANIZER')).toBeInTheDocument();
    expect(screen.getByText('SPEAKER')).toBeInTheDocument();
  });

  it('should_callOnRowClick_when_rowClicked', async () => {
    const user = userEvent.setup();
    const onRowClick = vi.fn();
    const onAction = vi.fn();

    render(<UserTable users={mockUsers} onRowClick={onRowClick} onAction={onAction} />);

    const firstRow = screen.getByText('John Doe').closest('tr');
    if (firstRow) {
      await user.click(firstRow);
      expect(onRowClick).toHaveBeenCalledWith(mockUsers[0]);
    }
  });

  it('should_renderEmptyState_when_noUsers', () => {
    const onRowClick = vi.fn();
    const onAction = vi.fn();

    render(<UserTable users={[]} onRowClick={onRowClick} onAction={onAction} />);

    expect(screen.getByText(/Keine Benutzer gefunden/i)).toBeInTheDocument();
  });
});
