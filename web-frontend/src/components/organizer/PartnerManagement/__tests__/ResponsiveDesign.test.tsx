import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { PartnerDirectoryScreen } from '../PartnerDirectoryScreen';
import { PartnerList } from '../PartnerList';
import * as partnerApi from '@/services/api/partnerApi';

// Mock the API
vi.mock('@/services/api/partnerApi');

const mockPartners = {
  content: [
    {
      id: '1',
      companyName: 'Test Partner 1',
      partnershipLevel: 'GOLD',
      isActive: true,
      company: { industry: 'Tech' },
      contacts: [
        { username: 'john.doe', firstName: 'John', lastName: 'Doe', email: 'john@test.com' },
      ],
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

const renderWithProviders = (component: React.ReactElement, width: number) => {
  // Mock window.matchMedia for responsive testing
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width,
  });

  window.matchMedia = vi.fn((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, cacheTime: 0 },
    },
  });

  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>{component}</QueryClientProvider>
    </BrowserRouter>
  );
};

// TODO: Fix responsive design tests - require proper viewport mocking and element checks
describe.skip('Responsive Design Tests (AC6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(partnerApi.listPartners).mockResolvedValue(mockPartners);
    vi.mocked(partnerApi.getPartnerStatistics).mockResolvedValue(mockStatistics);
  });

  describe('Test 6.1: Mobile Viewport (<640px)', () => {
    it('should_renderSingleColumn_when_mobileViewport', async () => {
      renderWithProviders(<PartnerList />, 375);

      // Wait for partners to load
      await screen.findByText('Test Partner 1');

      // Check for mobile-specific layout
      // Grid should use xs={12} for single column
      const partnerCards = screen.getAllByTestId('partner-card');
      expect(partnerCards.length).toBeGreaterThan(0);

      // In mobile view, filter panel should be collapsible/hidden by default
      // This will be verified through visual inspection and CSS classes
    });

    it('should_stackFiltersVertically_when_mobileViewport', async () => {
      renderWithProviders(<PartnerDirectoryScreen />, 375);

      // Wait for component to mount
      await screen.findByPlaceholderText(/search partners/i);

      // Filters should be stacked vertically in mobile view
      const filterContainer = screen.getByTestId('filter-container');
      expect(filterContainer).toHaveStyle({ flexDirection: 'column' });
    });

    it('should_collapseFilterPanel_when_mobileViewport', async () => {
      renderWithProviders(<PartnerDirectoryScreen />, 375);

      await screen.findByPlaceholderText(/search partners/i);

      // Filter panel should be collapsed by default on mobile
      const filterPanel = screen.getByTestId('filter-panel');
      expect(filterPanel).toHaveAttribute('aria-expanded', 'false');
    });

    it('should_displaySimplifiedCards_when_mobileViewport', async () => {
      renderWithProviders(<PartnerList />, 375);

      await screen.findByText('Test Partner 1');

      // Cards should have mobile-friendly layout
      const card = screen.getByTestId('partner-card');
      // Check for mobile-specific class or style
      expect(card).toHaveClass(/mobile/);
    });
  });

  describe('Test 6.2: Tablet Viewport (640-1024px)', () => {
    it('should_renderTwoColumns_when_tabletViewport', async () => {
      renderWithProviders(<PartnerList />, 768);

      await screen.findByText('Test Partner 1');

      // Grid should use sm={6} for 2 columns
      const gridContainer = screen.getByTestId('partner-grid');
      expect(gridContainer).toHaveAttribute('data-columns', '2');
    });

    it('should_renderCollapsibleFilterPanel_when_tabletViewport', async () => {
      renderWithProviders(<PartnerDirectoryScreen />, 768);

      await screen.findByPlaceholderText(/search partners/i);

      // Filter panel should be collapsible on tablet
      const filterToggleButton = screen.getByLabelText(/toggle filters/i);
      expect(filterToggleButton).toBeInTheDocument();
    });
  });

  describe('Test 6.3: Desktop Viewport (>1024px)', () => {
    it('should_renderThreeColumns_when_desktopViewport', async () => {
      renderWithProviders(<PartnerList />, 1440);

      await screen.findByText('Test Partner 1');

      // Grid should use md={4} for 3 columns
      const gridContainer = screen.getByTestId('partner-grid');
      expect(gridContainer).toHaveAttribute('data-columns', '3');
    });

    it('should_displaySideFilterPanel_when_desktopViewport', async () => {
      renderWithProviders(<PartnerDirectoryScreen />, 1440);

      await screen.findByPlaceholderText(/search partners/i);

      // Filter panel should be visible as sidebar on desktop
      const filterPanel = screen.getByTestId('filter-panel');
      expect(filterPanel).toHaveAttribute('aria-expanded', 'true');
      expect(filterPanel).toHaveStyle({ position: 'sticky' });
    });

    it('should_displayFullFeatureSet_when_desktopViewport', async () => {
      renderWithProviders(<PartnerDirectoryScreen />, 1440);

      await screen.findByPlaceholderText(/search partners/i);

      // All features should be visible on desktop
      expect(screen.getByText(/total partners/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/view mode/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/sort by/i)).toBeInTheDocument();
    });
  });

  describe('Test 6.4: Responsive Grid System', () => {
    it('should_adjustGridSpacing_when_viewportChanges', async () => {
      const { rerender } = renderWithProviders(<PartnerList />, 375);

      await screen.findByText('Test Partner 1');

      // Mobile: smaller spacing
      let gridContainer = screen.getByTestId('partner-grid');
      expect(gridContainer).toHaveAttribute('data-spacing', '2');

      // Tablet: medium spacing
      window.innerWidth = 768;
      rerender(
        <BrowserRouter>
          <QueryClientProvider client={new QueryClient()}>
            <PartnerList />
          </QueryClientProvider>
        </BrowserRouter>
      );
      gridContainer = screen.getByTestId('partner-grid');
      expect(gridContainer).toHaveAttribute('data-spacing', '3');

      // Desktop: larger spacing
      window.innerWidth = 1440;
      rerender(
        <BrowserRouter>
          <QueryClientProvider client={new QueryClient()}>
            <PartnerList />
          </QueryClientProvider>
        </BrowserRouter>
      );
      gridContainer = screen.getByTestId('partner-grid');
      expect(gridContainer).toHaveAttribute('data-spacing', '3');
    });
  });
});
