/**
 * Partner Detail Screen Responsive Design Tests
 * Story 2.8.2: Partner Detail View - Task 14 (RED Phase)
 * Tests for responsive breakpoints: mobile, tablet, desktop
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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

// Mock store
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
import { usePartnerMeetings } from '@/hooks/usePartnerMeetings';
import { usePartnerActivity } from '@/hooks/usePartnerActivity';
import { usePartnerNotes } from '@/hooks/usePartnerNotes';

// Mock partner data
const mockPartnerDetail = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  companyName: 'GoogleZH',
  partnershipLevel: 'GOLD' as const,
  partnershipStartDate: '2022-01-01T00:00:00Z',
  tierStartDate: '2023-01-01T00:00:00Z',
  previousTier: 'SILVER' as const,
  isActive: true,
  renewalDate: '2026-01-01T00:00:00Z',
  autoRenewal: true,
  company: {
    companyName: 'GoogleZH',
    legalName: 'Google Switzerland GmbH',
    industry: 'Technology',
    website: 'https://www.google.ch',
    location: 'Zurich, Switzerland',
    swissUid: 'CHE-123.456.789',
    taxStatus: 'VAT Registered',
  },
  statistics: {
    eventsAttended: 24,
    lastEventName: 'Spring 25',
    activeVotes: 5,
    totalMeetings: 12,
  },
  lastEvent: {
    eventName: 'Spring 25',
    date: '2025-03-15T00:00:00Z',
    attendeeCount: 150,
    registrations: 180,
    downloads: 45,
  },
  nextMeeting: {
    type: 'Strategic Review',
    scheduledDate: '2025-06-20T00:00:00Z',
    location: 'Bern Office',
  },
};

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        cacheTime: 0,
      },
    },
  });

/**
 * Helper function to set viewport size for responsive testing
 */
const setViewportSize = (width: number, height: number) => {
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width,
  });
  Object.defineProperty(window, 'innerHeight', {
    writable: true,
    configurable: true,
    value: height,
  });
  window.dispatchEvent(new Event('resize'));
};

describe('Partner Detail Screen - Responsive Design', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Setup default mock returns
    (usePartnerDetail as any).mockReturnValue({
      data: mockPartnerDetail,
      isLoading: false,
      isError: false,
      error: null,
    });

    (usePartnerVotes as any).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    });

    (usePartnerMeetings as any).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    });

    (usePartnerActivity as any).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    });

    (usePartnerNotes as any).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    });
  });

  // Test 14.1: Mobile viewport (<640px) should render correctly
  it('should_renderCorrectly_when_mobileViewport', () => {
    setViewportSize(375, 667); // iPhone SE size

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should find main container
    const mainContainer = container.querySelector('[data-testid="partner-detail-container"]');
    expect(mainContainer).toBeInTheDocument();

    // Quick stats cards should be present
    const statsCards = container.querySelectorAll('[data-testid="stat-card"]');
    expect(statsCards.length).toBe(4); // Partner Since, Events, Votes, Meetings
  });

  // Test 14.2: Mobile viewport should show vertical tabs
  it('should_renderVerticalTabs_when_mobileViewport', () => {
    setViewportSize(375, 667);

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Tabs should have vertical orientation on mobile
    const tabs = container.querySelector('.MuiTabs-root');
    // MUI adds vertical class when orientation is vertical
    expect(tabs).toHaveClass('MuiTabs-vertical');
  });

  // Test 14.3: Tablet viewport (640-1024px) should render correctly
  it('should_renderCorrectly_when_tabletViewport', () => {
    setViewportSize(768, 1024); // iPad size

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Quick stats cards should be present
    const statsCards = container.querySelectorAll('[data-testid="stat-card"]');
    expect(statsCards.length).toBe(4);
  });

  // Test 14.4: Tablet viewport should have horizontal tabs
  it('should_renderHorizontalTabs_when_tabletViewport', () => {
    setViewportSize(768, 1024);

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Tabs should NOT have vertical class (horizontal is default)
    const tabs = container.querySelector('.MuiTabs-root');
    expect(tabs).not.toHaveClass('MuiTabs-vertical');
  });

  // Test 14.5: Desktop viewport (>1024px) should render correctly
  it('should_renderCorrectly_when_desktopViewport', () => {
    setViewportSize(1920, 1080); // Full HD desktop

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Quick stats cards should be present
    const statsCards = container.querySelectorAll('[data-testid="stat-card"]');
    expect(statsCards.length).toBe(4);
  });

  // Test 14.6: Desktop viewport should have horizontal tabs
  it('should_renderHorizontalTabs_when_desktopViewport', () => {
    setViewportSize(1920, 1080);

    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Tabs should NOT have vertical class (horizontal is default)
    const tabs = container.querySelector('.MuiTabs-root');
    expect(tabs).not.toHaveClass('MuiTabs-vertical');
  });

  // Test 14.7: Components should render on all viewports
  it('should_renderAllComponents_when_anyViewport', () => {
    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // All major components should render
    expect(container.querySelector('[data-testid="partner-detail-container"]')).toBeInTheDocument();
    expect(container.querySelector('.MuiTabs-root')).toBeInTheDocument();
    expect(container.querySelectorAll('[data-testid="stat-card"]').length).toBe(4);
  });

  // Test 14.8: Container should have responsive max-width
  it('should_haveResponsiveMaxWidth_when_anyViewport', () => {
    const queryClient = createTestQueryClient();
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Container should have maxWidth prop (Material-UI Container)
    const mainContainer = container.querySelector('.MuiContainer-root');
    expect(mainContainer).toHaveClass('MuiContainer-maxWidthXl');
  });
});
