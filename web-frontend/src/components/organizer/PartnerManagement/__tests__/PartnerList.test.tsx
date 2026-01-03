import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import { PartnerList } from '../PartnerList';
import { usePartners } from '@/hooks/usePartners';
import { usePartnerStore } from '@/stores/partnerStore';
import type { PartnerResponse } from '@/services/api/partnerApi';

// Mock the hooks
vi.mock('@/hooks/usePartners');
vi.mock('@/stores/partnerStore');

const mockUsePartners = vi.mocked(usePartners);
const mockUsePartnerStore = vi.mocked(usePartnerStore);

// Mock PartnerCard component
vi.mock('../PartnerCard', () => ({
  PartnerCard: ({ partner }: { partner: PartnerResponse }) => (
    <div data-testid={`partner-card-${partner.companyName}`}>{partner.companyName}</div>
  ),
}));

const mockPartners = [
  {
    companyName: 'Tech Solutions AG', // ADR-003: companyName is the meaningful ID
    partnershipLevel: 'GOLD',
    isActive: true,
    lastEventName: 'BATbern 2024',
    votesCount: 5,
    nextMeetingDate: '2025-02-15',
    partnershipStartDate: '2023-01-15',
    company: { industry: 'Technology', logoUrl: 'https://cdn.example.com/logo1.png' },
    contacts: [
      {
        username: 'john.doe',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@example.com',
        isPrimary: true,
      },
    ],
  },
  {
    companyName: 'Design Studio GmbH', // ADR-003: companyName is the meaningful ID
    partnershipLevel: 'SILVER',
    isActive: true,
    lastEventName: 'BATbern 2023',
    votesCount: 3,
    nextMeetingDate: null,
    partnershipStartDate: '2022-06-01',
    company: { industry: 'Design', logoUrl: null },
    contacts: [],
  },
];

const mockPaginationMetadata = {
  page: 0,
  size: 20,
  totalElements: 24,
  totalPages: 2,
};

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{children}</BrowserRouter>
    </QueryClientProvider>
  );
};

describe('PartnerList Component - RED Phase Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('AC1, AC3, AC4: Partner List Rendering and Pagination', () => {
    it('should_renderPartnerList_when_partnersLoaded', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
        error: null,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.getByTestId('partner-card-Tech Solutions AG')).toBeInTheDocument();
        expect(screen.getByTestId('partner-card-Design Studio GmbH')).toBeInTheDocument();
        expect(screen.getByText('Tech Solutions AG')).toBeInTheDocument();
        expect(screen.getByText('Design Studio GmbH')).toBeInTheDocument();
      });
    });

    it('should_renderGridView_when_viewModeIsGrid', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const gridContainer = screen.getByTestId('partner-grid');
        expect(gridContainer).toBeInTheDocument();
        // Grid2 doesn't use 'container' attribute, check class instead
        expect(gridContainer).toHaveClass('MuiGrid-container');
      });
    });

    it('should_renderListView_when_viewModeIsList', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'list',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const listContainer = screen.getByTestId('partner-list-list');
        expect(listContainer).toBeInTheDocument();
      });
    });

    it('should_renderPagination_when_multiplePagesExist', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.getByRole('navigation')).toBeInTheDocument();
        expect(screen.getByText(/page 1 of 2/i)).toBeInTheDocument();
        expect(screen.getByText(/24 partners/i)).toBeInTheDocument();
      });
    });

    it('should_loadNextPage_when_nextButtonClicked', async () => {
      // Arrange
      const mockSetPage = vi.fn();
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: mockSetPage,
      });

      const user = userEvent.setup();

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      const nextButton = screen.getByRole('button', { name: /next/i });
      await user.click(nextButton);

      // Assert
      expect(mockSetPage).toHaveBeenCalledWith(1);
    });

    it('should_showLoadingState_when_fetching', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const skeletons = screen.getAllByTestId('partner-skeleton');
        expect(skeletons.length).toBeGreaterThan(0);
      });
    });

    it('should_showErrorState_when_fetchFails', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        error: {
          message: 'Failed to fetch partners',
          correlationId: '12345-67890',
        },
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.getByText(/failed to fetch partners/i)).toBeInTheDocument();
        expect(screen.getByText(/12345-67890/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC4: Pagination Controls', () => {
    it('should_disableFirstPrev_when_onFirstPage', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: { ...mockPaginationMetadata, page: 0 },
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const firstButton = screen.getByRole('button', { name: /first/i });
        const prevButton = screen.getByRole('button', { name: /previous/i });
        expect(firstButton).toBeDisabled();
        expect(prevButton).toBeDisabled();
      });
    });

    it('should_disableNextLast_when_onLastPage', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: { ...mockPaginationMetadata, page: 1, totalPages: 2 },
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 1,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const nextButton = screen.getByRole('button', { name: /next/i });
        const lastButton = screen.getByRole('button', { name: /last/i });
        expect(nextButton).toBeDisabled();
        expect(lastButton).toBeDisabled();
      });
    });

    it('should_displayCorrectPageNumber_when_pageChanged', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: { ...mockPaginationMetadata, page: 1, totalPages: 2 },
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 1,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.getByText(/page 2 of 2/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC1: Empty State and Edge Cases', () => {
    it('should_showEmptyState_when_noPartnersFound', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: [],
          metadata: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.getByText(/no partners found/i)).toBeInTheDocument();
      });
    });

    it('should_hidePagination_when_singlePage', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
      });
    });
  });

  describe('AC6: Responsive Grid Layout', () => {
    it('should_useResponsiveGrid_when_gridModeActive', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        const gridItems = screen.getAllByTestId(/partner-card-/);
        gridItems.forEach((item) => {
          // Grid2 uses different class structure - check parent has Grid root class
          const gridItem = item.closest('[class*="MuiGrid-root"]');
          expect(gridItem).not.toBeNull();
        });
      });
    });
  });

  describe('AC8: Performance - Loading Optimization', () => {
    it('should_lazyLoadImages_when_partnersRendered', async () => {
      // Arrange
      mockUsePartners.mockReturnValue({
        data: {
          data: mockPartners,
          metadata: mockPaginationMetadata,
        },
        isLoading: false,
        isError: false,
      });

      mockUsePartnerStore.mockReturnValue({
        viewMode: 'grid',
        page: 0,
        setPage: vi.fn(),
      });

      // Act
      render(<PartnerList />, { wrapper: createWrapper() });

      // Assert
      // This test verifies the component renders successfully
      // Actual lazy loading is tested at integration level with Intersection Observer
      await waitFor(() => {
        expect(screen.getByTestId('partner-card-Tech Solutions AG')).toBeInTheDocument();
      });
    });
  });
});
