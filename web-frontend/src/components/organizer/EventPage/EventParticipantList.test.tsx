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
import { promoteFromWaitlist } from '@/services/api/eventRegistrationService';

// Mock hooks and components
vi.mock('../../../hooks/useEventManagement/useEventRegistrations');
vi.mock('../../../stores/eventParticipantStore');
vi.mock('@/services/api/eventRegistrationService', () => ({
  promoteFromWaitlist: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('./EventParticipantFilters', () => ({
  default: () => <div data-testid="participant-filters">Filters</div>,
}));
// The table is mocked; in waitlist mode it exposes the parent's onPromote so the
// List's confirm-dialog flow (FR28/NFR1) can be exercised here.
vi.mock('./EventParticipantTable', () => ({
  default: ({
    participants,
    isLoading,
    waitlistMode,
    onPromote,
  }: {
    participants: any[];
    isLoading: boolean;
    waitlistMode?: boolean;
    onPromote?: (p: any) => void;
  }) => (
    <div data-testid="participant-table">
      {isLoading ? 'Loading...' : `${participants.length} participants`}
      {waitlistMode &&
        onPromote &&
        participants.map((p) => (
          <button
            key={p.registrationCode}
            data-testid={`mock-promote-${p.registrationCode}`}
            onClick={() => onPromote(p)}
          >
            promote
          </button>
        ))}
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

// Mock translation — surfaces interpolation opts so counter values can be asserted.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: unknown) =>
      opts && typeof opts === 'object' && !Array.isArray(opts)
        ? `${key}::${Object.entries(opts as Record<string, unknown>)
            .map(([k, v]) => `${k}=${v}`)
            .join(',')}`
        : key,
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
        data: mockParticipants,
        pagination: {
          page: 1,
          limit: 25,
          total: 50,
          totalPages: 2,
        },
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
        search: '',
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

      expect(screen.getByText('eventPage.participantList.loading')).toBeInTheDocument();
    });

    it('should show error state when data loading fails', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('eventPage.participantList.error.loadFailed')).toBeInTheDocument();
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

      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: {
          data: mockParticipants,
          pagination: {
            page: 1,
            limit: 50,
            total: 50,
            totalPages: 1,
          },
        },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
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
        search: 'john',
        enabled: true,
      });
    });
  });

  describe('Empty State', () => {
    it('should show empty state when no participants', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: {
          data: [],
          pagination: {
            page: 1,
            limit: 25,
            total: 0,
            totalPages: 0,
          },
        },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByText('0 participants')).toBeInTheDocument();
    });
  });

  describe('Result counter (Epic 14 FR D.4 / NFR3)', () => {
    it('shows "Showing from–to of total" with correct boundaries on a partial last page', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: {},
        pagination: { page: 2, limit: 25 },
        searchQuery: '',
        setPage: mockSetPage,
        setLimit: mockSetLimit,
        setFilters: vi.fn(),
        setSearchQuery: vi.fn(),
        resetFilters: vi.fn(),
        reset: vi.fn(),
      });
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: {
          data: mockParticipants, // 2 rows on the last page
          pagination: { page: 2, limit: 25, totalItems: 27, totalPages: 2 },
        },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      const counter = screen.getByTestId('participants-result-count');
      expect(counter).toHaveTextContent('eventPage.participantList.showing');
      expect(counter).toHaveTextContent('from=26');
      expect(counter).toHaveTextContent('to=27');
      expect(counter).toHaveTextContent('total=27');
    });

    it('shows the empty counter ("0 of 0") when there are no results', () => {
      (useEventRegistrations as ReturnType<typeof vi.fn>).mockReturnValue({
        data: { data: [], pagination: { page: 1, limit: 25, totalItems: 0, totalPages: 0 } },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      });

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      expect(screen.getByTestId('participants-result-count')).toHaveTextContent(
        'eventPage.participantList.showingEmpty'
      );
    });
  });

  describe('Waitlist promote (Epic 14 FR28 / NFR1 — confirm before consequential action)', () => {
    const waitlistStore = {
      filters: { status: ['WAITLIST'] },
      pagination: { page: 1, limit: 25 },
      searchQuery: '',
      setPage: mockSetPage,
      setLimit: mockSetLimit,
      setFilters: vi.fn(),
      setSearchQuery: vi.fn(),
      resetFilters: vi.fn(),
      reset: vi.fn(),
    };

    it('opens a confirm dialog and does NOT promote until confirmed', async () => {
      const user = userEvent.setup();
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue(waitlistStore);

      renderWithProviders(<EventParticipantList eventCode="BAT-2024-01" />);

      await user.click(screen.getByTestId('mock-promote-REG-001'));

      // Dialog is shown; promotion has NOT fired yet.
      expect(screen.getByTestId('waitlist-promote-confirm')).toBeInTheDocument();
      expect(promoteFromWaitlist).not.toHaveBeenCalled();

      // Confirm → the service is called with the event + registration codes.
      await user.click(screen.getByTestId('waitlist-promote-confirm-button'));
      await waitFor(() =>
        expect(promoteFromWaitlist).toHaveBeenCalledWith('BAT-2024-01', 'REG-001')
      );
    });
  });
});
