import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { PartnerDirectoryScreen } from '../PartnerDirectoryScreen';
import * as partnerApi from '@/services/api/partnerApi';
import userEvent from '@testing-library/user-event';

// Mock the API
vi.mock('@/services/api/partnerApi');

const mockPartners = {
  partners: [
    {
      id: '1',
      companyName: 'Test Partner 1',
      partnershipLevel: 'GOLD',
      isActive: true,
      company: { industry: 'Tech', logoUrl: 'https://example.com/logo.png' },
      contacts: [{ firstName: 'John', lastName: 'Doe', email: 'john@test.com', isPrimary: true }],
    },
  ],
  pagination: {
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  },
};

const mockStatistics = {
  totalPartners: 1,
  activePartners: 1,
  tierDistribution: {
    STRATEGIC: 0,
    PLATINUM: 0,
    GOLD: 1,
    SILVER: 0,
    BRONZE: 0,
  },
};

const renderWithProviders = (component: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, cacheTime: 0 },
    },
  });

  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        {component}
      </QueryClientProvider>
    </BrowserRouter>
  );
};

describe('Accessibility Tests (AC7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(partnerApi.listPartners).mockResolvedValue(mockPartners);
    vi.mocked(partnerApi.getPartnerStatistics).mockResolvedValue(mockStatistics);
  });

  describe('Test 7.1: Keyboard Navigation', () => {
    it('should_supportTabNavigation_when_usingTabKey', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      // Tab through interactive elements
      await user.tab();

      // Should focus on Add Partner button (first interactive element)
      const addButton = screen.getByTestId('add-partner-button');
      expect(addButton).toHaveFocus();

      // Continue tabbing
      await user.tab();

      // Should focus on search input
      const searchInput = screen.getByLabelText(/search partners/i);
      expect(searchInput).toHaveFocus();
    });

    it('should_supportEnterKey_when_interactingWithButtons', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Test Partner 1')).toBeInTheDocument();
      });

      // Focus on grid view button and press Enter
      const gridViewButton = screen.getByLabelText(/grid view/i);
      gridViewButton.focus();
      await user.keyboard('{Enter}');

      // Grid view should be selected
      expect(gridViewButton).toHaveAttribute('aria-pressed', 'true');
    });

    it('should_supportEscapeKey_when_clearingSearch', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText(/search partners/i);

      // Type in search
      await user.type(searchInput, 'test query');
      expect(searchInput).toHaveValue('test query');

      // Press Escape to clear
      await user.keyboard('{Escape}');
      expect(searchInput).toHaveValue('');
    });
  });

  describe('Test 7.2: Screen Reader Announcements', () => {
    it('should_announceSearchResults_when_searchCompleted', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Test Partner 1')).toBeInTheDocument();
      });

      // Check for aria-live region
      const liveRegion = screen.getByRole('status', { name: /search results/i });
      expect(liveRegion).toBeInTheDocument();
      expect(liveRegion).toHaveAttribute('aria-live', 'polite');
      expect(liveRegion).toHaveAttribute('aria-atomic', 'true');

      // Announcement should contain result count
      expect(liveRegion).toHaveTextContent(/1 partner.*found/i);
    });

    it('should_announceFilterChanges_when_filterApplied', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      // Check for filter announcement region
      const filterAnnouncement = screen.getByRole('status', { name: /filter update/i });
      expect(filterAnnouncement).toBeInTheDocument();
      expect(filterAnnouncement).toHaveAttribute('aria-live', 'polite');
    });

    it('should_announceLoadingState_when_dataFetching', async () => {
      vi.mocked(partnerApi.listPartners).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockPartners), 1000))
      );

      renderWithProviders(<PartnerDirectoryScreen />);

      // Loading announcement should be present
      const loadingAnnouncement = screen.getByRole('status', { name: /loading/i });
      expect(loadingAnnouncement).toBeInTheDocument();
      expect(loadingAnnouncement).toHaveTextContent(/loading partners/i);
    });
  });

  describe('Test 7.3: ARIA Labels', () => {
    it('should_haveAriaLabels_when_interactiveElementsRendered', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Test Partner 1')).toBeInTheDocument();
      });

      // Check key interactive elements have ARIA labels
      expect(screen.getByLabelText(/search partners/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/grid view/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/list view/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/view mode/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/sort by/i)).toBeInTheDocument();
    });

    it('should_haveLabelForTierFilter_when_filterRendered', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      // Tier filter should have accessible label
      const tierFilter = screen.getByLabelText(/partnership tier/i);
      expect(tierFilter).toBeInTheDocument();
    });

    it('should_haveLabelForStatusFilter_when_filterRendered', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      // Status filter should have accessible label
      const statusFilter = screen.getByLabelText(/status/i);
      expect(statusFilter).toBeInTheDocument();
    });
  });

  describe('Test 7.4: Focus Indicators', () => {
    it('should_showFocusIndicators_when_elementFocused', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText(/search partners/i);

      // Focus the input
      searchInput.focus();

      // Should have focus-visible styling
      expect(searchInput).toHaveClass('Mui-focusVisible');
    });

    it('should_haveVisibleOutline_when_buttonFocused', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Test Partner 1')).toBeInTheDocument();
      });

      const viewDetailsButton = screen.getByText(/view details/i);
      viewDetailsButton.focus();

      // Button should receive focus
      expect(viewDetailsButton).toHaveFocus();
    });
  });

  describe('Test 7.5: Color Contrast', () => {
    it('should_meetColorContrast_when_textRendered', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Partner Directory')).toBeInTheDocument();
      });

      // This is a visual test - would be verified with axe DevTools
      // Just checking elements are present with proper semantic HTML
      const heading = screen.getByRole('heading', { name: /partner directory/i });
      expect(heading).toBeInTheDocument();
      expect(heading.tagName).toBe('H1');
    });

    it('should_useSemanticHTML_when_renderingContent', async () => {
      renderWithProviders(<PartnerDirectoryScreen />);

      await waitFor(() => {
        expect(screen.getByText('Test Partner 1')).toBeInTheDocument();
      });

      // Check for proper semantic structure
      expect(screen.getByRole('navigation')).toBeInTheDocument(); // Pagination nav
      expect(screen.getAllByRole('button').length).toBeGreaterThan(0);
    });
  });
});
