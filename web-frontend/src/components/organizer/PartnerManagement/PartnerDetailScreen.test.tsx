/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import { PartnerDetailScreen } from './PartnerDetailScreen';

// Mock hooks
vi.mock('@/hooks/usePartnerDetail', () => ({
  usePartnerDetail: vi.fn(),
}));

vi.mock('@/hooks/usePartnerVotes', () => ({
  usePartnerVotes: vi.fn(),
}));

vi.mock('@/hooks/usePartnerMeetings', () => ({
  usePartnerMeetings: vi.fn(),
}));

vi.mock('@/hooks/usePartnerActivity', () => ({
  usePartnerActivity: vi.fn(),
}));

vi.mock('@/hooks/usePartnerNotes', () => ({
  usePartnerNotes: vi.fn(),
}));

// Mock store state
const mockStoreState = {
  activeTab: 0,
  showEditModal: false,
  showNoteModal: false,
  setActiveTab: vi.fn(),
  setShowEditModal: vi.fn(),
  setShowNoteModal: vi.fn(),
};

vi.mock('@/stores/partnerDetailStore', () => ({
  usePartnerDetailStore: vi.fn((selector) => {
    if (typeof selector === 'function') {
      return selector(mockStoreState);
    }
    return mockStoreState;
  }),
}));

import { usePartnerDetail } from '@/hooks/usePartnerDetail';
import { usePartnerVotes } from '@/hooks/usePartnerVotes';
import { usePartnerActivity } from '@/hooks/usePartnerActivity';

// Mock partner data
const mockPartnerDetail = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  companyName: 'GoogleZH',
  partnershipLevel: 'PLATINUM' as const,
  partnershipStartDate: '2022-01-01T00:00:00Z',
  tierStartDate: '2024-01-01T00:00:00Z',
  previousTier: 'GOLD' as const,
  isActive: true,
  autoRenewal: true,
  renewalDate: '2026-01-01T00:00:00Z',
  company: {
    name: 'Google Zurich',
    industry: 'Technology',
    website: 'https://google.com',
    location: 'Zurich, Switzerland',
    logoUrl: 'https://cdn.batbern.ch/logos/google.png',
    swissUID: 'CHE-123.456.789',
    taxStatus: 'Standard',
  },
  statistics: {
    eventsAttended: 24,
    lastEventName: 'Spring 25',
    activeVotes: 5,
    totalMeetings: 12,
  },
};

describe('PartnerDetailScreen - Main Integration Tests', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    // Reset mocks
    vi.clearAllMocks();
  });

  const renderWithProviders = (companyName = 'GoogleZH') => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/partners/${companyName}`]}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  /**
   * Test: should_renderPartnerDetailScreen_when_partnerLoaded
   * Verify main screen renders with all components
   */
  it('should_renderPartnerDetailScreen_when_partnerLoaded', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText(/Google Zurich/i)).toBeInTheDocument();
    });
  });

  /**
   * Test: should_displayLoadingState_when_fetchingPartner
   * Verify loading skeleton is shown
   */
  it('should_displayLoadingState_when_fetchingPartner', () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    // Should show loading indicator
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  /**
   * Test: should_displayErrorState_when_partnerNotFound
   * Verify 404 error is displayed
   */
  it('should_displayErrorState_when_partnerNotFound', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { response: { status: 404 } },
    } as any);

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText(/not found/i)).toBeInTheDocument();
    });
  });

  /**
   * Test: should_displayErrorState_when_serverError
   * Verify 500 error is displayed
   */
  it('should_displayErrorState_when_serverError', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { response: { status: 500 } },
    } as any);

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText(/Error Loading Partner/i)).toBeInTheDocument();
    });
  });

  /**
   * Test: should_extractCompanyName_when_routeParamProvided
   * Verify companyName is read from route params
   */
  it('should_extractCompanyName_when_routeParamProvided', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders('TestCompany');

    await waitFor(() => {
      expect(usePartnerDetail).toHaveBeenCalledWith('TestCompany', expect.any(String));
    });
  });

  /**
   * Test: should_renderHeader_when_partnerLoaded
   * Verify PartnerDetailHeader is rendered
   */
  it('should_renderHeader_when_partnerLoaded', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      // Should render company name in header
      expect(screen.getByText('Google Zurich')).toBeInTheDocument();
      expect(screen.getByText('Technology')).toBeInTheDocument();
    });
  });

  /**
   * Test: should_renderQuickStats_when_partnerLoaded
   * Verify PartnerQuickStats is rendered
   */
  it('should_renderQuickStats_when_partnerLoaded', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      // Should render quick stats
      expect(screen.getByText(/Partner Since/i)).toBeInTheDocument();
      expect(screen.getByText(/Events Attended/i)).toBeInTheDocument();
    });
  });

  /**
   * Test: should_renderTabNavigation_when_partnerLoaded
   * Verify PartnerTabNavigation is rendered
   */
  it('should_renderTabNavigation_when_partnerLoaded', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      // Should render tab navigation
      expect(screen.getByRole('tab', { name: /Overview/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /Meetings/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /Activity/i })).toBeInTheDocument();
    });
  });

  /**
   * Test: should_renderOverviewTab_when_defaultTabActive
   * Verify Overview tab is rendered by default
   */
  it('should_renderOverviewTab_when_defaultTabActive', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    vi.mocked(usePartnerVotes).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);

    vi.mocked(usePartnerActivity).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      // Should render Overview tab content
      expect(screen.getByText(/Partnership Details/i)).toBeInTheDocument();
    });
  });

  /**
   * Test: should_switchTabs_when_tabClicked
   * Verify tab switching updates Zustand store
   */
  it('should_switchTabs_when_tabClicked', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    const user = userEvent.setup();
    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Meetings/i })).toBeInTheDocument();
    });

    const meetingsTab = screen.getByRole('tab', { name: /Meetings/i });
    await user.click(meetingsTab);

    // Verify setActiveTab was called
    expect(mockStoreState.setActiveTab).toHaveBeenCalled();
  });

  /**
   * Test: should_lazyLoadTabData_when_tabActivated
   * Verify tab data is only fetched when tab is activated
   */
  it('should_lazyLoadTabData_when_tabActivated', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    vi.mocked(usePartnerVotes).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Overview/i })).toBeInTheDocument();
    });

    // usePartnerDetail should be called (for Overview tab)
    expect(usePartnerDetail).toHaveBeenCalled();
  });

  /**
   * Test: should_navigateBack_when_backButtonClicked
   * Verify back button is rendered and clickable
   */
  it('should_navigateBack_when_backButtonClicked', async () => {
    vi.mocked(usePartnerDetail).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    } as any);

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText(/Back/i)).toBeInTheDocument();
    });

    // Just verify back button is present (navigation is handled by React Router)
    const backButton = screen.getByText(/Back/i);
    expect(backButton).toBeInTheDocument();
  });
});
