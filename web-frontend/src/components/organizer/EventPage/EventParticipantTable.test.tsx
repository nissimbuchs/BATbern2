/**
 * EventParticipantTable Component Tests
 *
 * TDD Tests for event participant table component
 * RED Phase: Tests written first
 */

import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import EventParticipantTable from './EventParticipantTable';
import type { EventParticipant } from '../../../types/eventParticipant.types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock CompanyCell component
vi.mock('../UserManagement/CompanyCell', () => ({
  default: ({ companyId }: { companyId?: string }) => (
    <div data-testid="company-cell">{companyId || 'N/A'}</div>
  ),
}));

const mockParticipants: EventParticipant[] = [
  {
    registrationCode: 'REG-001',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'john.doe',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
    company: {
      id: 'company-1',
      name: 'Centris AG',
      logo: 'https://example.com/logo.png',
    },
    status: 'CONFIRMED',
    registrationDate: '2024-01-15T10:30:00Z',
  },
  {
    registrationCode: 'REG-002',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'jane.smith',
    firstName: 'Jane',
    lastName: 'Smith',
    email: 'jane.smith@example.com',
    company: {
      id: 'company-2',
      name: 'Puzzle ITC',
    },
    status: 'REGISTERED',
    registrationDate: '2024-01-16T14:20:00Z',
  },
  {
    registrationCode: 'REG-003',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'bob.wilson',
    firstName: 'Bob',
    lastName: 'Wilson',
    email: 'bob.wilson@example.com',
    status: 'WAITLISTED',
    registrationDate: '2024-01-17T09:00:00Z',
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

describe('EventParticipantTable Component', () => {
  describe('Rendering', () => {
    it('should render table headers', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('participantTable.headers.name')).toBeInTheDocument();
      expect(screen.getByText('participantTable.headers.email')).toBeInTheDocument();
      expect(screen.getByText('participantTable.headers.company')).toBeInTheDocument();
      expect(screen.getByText('participantTable.headers.status')).toBeInTheDocument();
      expect(screen.getByText('participantTable.headers.registrationDate')).toBeInTheDocument();
    });

    it('should render participant rows', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('jane.smith@example.com')).toBeInTheDocument();
    });

    it('should show empty state when no participants', () => {
      renderWithProviders(<EventParticipantTable participants={[]} isLoading={false} />);

      expect(screen.getByText('participantTable.empty')).toBeInTheDocument();
    });

    it('should render participant avatars with initials', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const firstRow = screen.getByText('John Doe').closest('tr');
      expect(firstRow).toBeInTheDocument();

      // Avatar should contain initials
      const avatar = within(firstRow!).getByText('JD');
      expect(avatar).toBeInTheDocument();
    });

    it('should render company cell for participants with company', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const companyCells = screen.getAllByTestId('company-cell');
      expect(companyCells).toHaveLength(3);
      // Table sorts by name ascending by default: Bob, Jane, John
      expect(companyCells[0]).toHaveTextContent('N/A'); // Bob has no company
      expect(companyCells[1]).toHaveTextContent('company-2'); // Jane
      expect(companyCells[2]).toHaveTextContent('company-1'); // John
    });

    it('should render status chips with correct labels', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
      expect(screen.getByText('REGISTERED')).toBeInTheDocument();
      expect(screen.getByText('WAITLISTED')).toBeInTheDocument();
    });
  });

  describe('Sorting', () => {
    it('should sort by name when name header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const nameHeader = screen.getByText('participantTable.headers.name');

      // Table defaults to name ascending (Bob is already first)
      let rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();

      // Click to toggle to descending (John should be first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/John/)).toBeInTheDocument();
    });

    it('should toggle sort direction when clicking same header twice', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const nameHeader = screen.getByText('participantTable.headers.name');

      // Default is ascending (Bob first)
      let rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();

      // First click - toggle to descending (John first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/John/)).toBeInTheDocument();

      // Second click - toggle back to ascending (Bob first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();
    });

    it('should sort by email when email header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const emailHeader = screen.getByText('participantTable.headers.email');
      await user.click(emailHeader);

      const rows = screen.getAllByRole('row');
      // Bob's email (bob.wilson@...) should be first alphabetically
      expect(within(rows[1]).getByText('bob.wilson@example.com')).toBeInTheDocument();
    });

    it('should sort by registration date when date header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const dateHeader = screen.getByText('participantTable.headers.registrationDate');
      await user.click(dateHeader);

      const rows = screen.getAllByRole('row');
      // John registered first (2024-01-15)
      expect(within(rows[1]).getByText('John Doe')).toBeInTheDocument();
    });
  });

  describe('Interactions', () => {
    it('should call onRowClick when row is clicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();

      renderWithProviders(
        <EventParticipantTable
          participants={mockParticipants}
          isLoading={false}
          onRowClick={mockRowClick}
        />
      );

      const firstRow = screen.getByText('John Doe').closest('tr');
      await user.click(firstRow!);

      expect(mockRowClick).toHaveBeenCalledWith(mockParticipants[0]);
    });

    it('should apply hover style on row hover', () => {
      const mockRowClick = vi.fn();

      renderWithProviders(
        <EventParticipantTable
          participants={mockParticipants}
          isLoading={false}
          onRowClick={mockRowClick}
        />
      );

      // When onRowClick is provided, cursor should be pointer
      const firstRow = screen.getByText(/John/).closest('tr');
      expect(firstRow).toHaveStyle({ cursor: 'pointer' });
    });
  });

  describe('Loading State', () => {
    it('should show loading skeleton when isLoading is true', () => {
      renderWithProviders(<EventParticipantTable participants={[]} isLoading={true} />);

      // Should show loading skeleton rows
      const skeletons = screen.getAllByTestId('participant-skeleton');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Date Formatting', () => {
    it('should format registration date correctly', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      // Should display formatted date (exact format depends on implementation)
      // Looking for any date-like format in the first row
      const firstRow = screen.getByText('John Doe').closest('tr');
      expect(firstRow).toBeInTheDocument();
      // The formatted date should be visible somewhere in the row
      expect(within(firstRow!).getByText(/2024|Jan|15/)).toBeInTheDocument();
    });
  });
});
