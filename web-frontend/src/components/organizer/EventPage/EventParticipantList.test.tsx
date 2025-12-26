/**
 * EventParticipantList Component Tests
 *
 * TDD Tests for event participant list container component
 * RED Phase: Tests written first
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import EventParticipantList from './EventParticipantList';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEventRegistrations } from '../../../hooks/useEventManagement/useEventRegistrations';
import { useEventParticipantStore } from '../../../stores/eventParticipantStore';

// Mock hooks and components
vi.mock('../../../hooks/useEventManagement/useEventRegistrations');
vi.mock('../../../stores/eventParticipantStore');
vi.mock('./EventParticipantFilters', () => ({
  default: () => <div data-testid="participant-filters">Filters</div>,
}));
vi.mock('./EventParticipantTable', () => ({
  default: ({ participants, isLoading }: { participants: any[]; isLoading: boolean }) => (
    <div data-testid="participant-table">
      {isLoading ? 'Loading...' : `${participants.length} participants`}
    </div>
  ),
}));
vi.mock('../UserManagement/UserPagination', () => ({
  default: ({ page, totalPages }: { page: number; totalPages: number }) => (
    <div data-testid="pagination">
      Page {page} of {totalPages}
    </div>
  ),
}));

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

const mockParticipants = [
  {
    registrationCode: 'REG-001',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'john.doe',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
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
    status: 'REGISTERED',
    registrationDate: '2024-01-16T14:20:00Z',
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

describe('EventParticipantList Component', () => {
  const mockSetPage = vi.fn();
  const mockSetLimit = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Setup default store mock
    (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
      filters: {},
      pagination: { page: 1, limit: 25 },
      searchQuery: '',
      setPage: mockSetPage,
      setLimit: mockSetLimit,
      setFilters: vi.fn(),
      setSearchQuery: vi.fn(),
      resetFilters: vi.fn(),
      reset: vi.fn(),
    });

    // Setup default hook mock (success state)
    (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
      data: {
        registrations: mockParticipants,
        total: 50,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });
  });

  describe('Rendering', () => {
    it('should render filters component', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByTestId('participant-filters')).toBeInTheDocument();
    });

    it('should render table component', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByTestId('participant-table')).toBeInTheDocument();
    });

    it('should render pagination when data is available', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByTestId('pagination')).toBeInTheDocument();
    });

    it('should display participant count in table', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('2 participants')).toBeInTheDocument();
    });
  });

  describe('Data Loading', () => {
    it('should call useEventRegistrations with correct parameters', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(useEventRegistrations).toHaveBeenCalledWith({
        eventCode: 'BAT-2024-01',
        filters: {},
        pagination: { page: 1, limit: 25 },
        enabled: true,
      });
    });

    it('should show loading state when data is loading', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('participantList.loading')).toBeInTheDocument();
    });

    it('should show error state when data loading fails', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('participantList.error.loadFailed')).toBeInTheDocument();
    });

    it('should show retry button when error occurs', () => {
      const mockRefetch = vi.fn();
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
        refetch: mockRefetch,
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      const retryButton = screen.getByRole('button', { name: /retry/i });
      expect(retryButton).toBeInTheDocument();
    });

    it('should call refetch when retry button is clicked', async () => {
      const user = userEvent.setup();
      const mockRefetch = vi.fn();
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
        refetch: mockRefetch,
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      const retryButton = screen.getByRole('button', { name: /retry/i });
      await user.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('Pagination Integration', () => {
    it('should calculate total pages correctly', () => {
      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      // 50 total participants / 25 per page = 2 pages
      expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    });

    it('should handle pagination with different limit', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: {},
        pagination: { page: 1, limit: 50 },
        searchQuery: '',
        setPage: mockSetPage,
        setLimit: mockSetLimit,
        setFilters: vi.fn(),
        setSearchQuery: vi.fn(),
        resetFilters: vi.fn(),
        reset: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      // 50 total participants / 50 per page = 1 page
      expect(screen.getByText('Page 1 of 1')).toBeInTheDocument();
    });

    it('should not show pagination when no data', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.queryByTestId('pagination')).not.toBeInTheDocument();
    });
  });

  describe('Filter Integration', () => {
    it('should pass filters from store to useEventRegistrations', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: { status: ['CONFIRMED'] },
        pagination: { page: 1, limit: 25 },
        searchQuery: 'john',
        setPage: mockSetPage,
        setLimit: mockSetLimit,
        setFilters: vi.fn(),
        setSearchQuery: vi.fn(),
        resetFilters: vi.fn(),
        reset: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(useEventRegistrations).toHaveBeenCalledWith({
        eventCode: 'BAT-2024-01',
        filters: { status: ['CONFIRMED'] },
        pagination: { page: 1, limit: 25 },
        enabled: true,
      });
    });
  });

  describe('Empty State', () => {
    it('should show empty state when no participants', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: {
          registrations: [],
          total: 0,
        },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('0 participants')).toBeInTheDocument();
    });
  });
});
