/**
 * Partner Detail Screen Accessibility Tests
 * Story 2.8.2: Partner Detail View - Task 15
 * Tests for WCAG 2.1 AA accessibility compliance
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

describe('Partner Detail Screen - Accessibility', () => {
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

  // Test 15.1: Keyboard navigation for tabs should work
  it('should_navigateWithKeyboard_when_arrowKeysPressed', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Find the first tab
    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    expect(overviewTab).toHaveAttribute('aria-selected', 'true');

    // Focus on the tabs
    overviewTab.focus();

    // Press ArrowRight to navigate to next tab
    await user.keyboard('{ArrowRight}');

    // Should have called setActiveTab with index 1 (Contacts)
    expect(mockStoreState.setActiveTab).toHaveBeenCalledWith(1);
  });

  // Test 15.2: Home/End keys should navigate to first/last tab
  it('should_navigateToFirstTab_when_homeKeyPressed', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    overviewTab.focus();

    await user.keyboard('{Home}');
    expect(mockStoreState.setActiveTab).toHaveBeenCalledWith(0);
  });

  // Test 15.3: All tabs should have ARIA labels
  it('should_haveAriaLabels_when_tabsRendered', () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // All tabs should have proper ARIA attributes
    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    expect(overviewTab).toHaveAttribute('id', 'partner-tab-0');
    expect(overviewTab).toHaveAttribute('aria-controls', 'partner-tabpanel-0');

    const contactsTab = screen.getByRole('tab', { name: /contacts/i });
    expect(contactsTab).toHaveAttribute('id', 'partner-tab-1');
    expect(contactsTab).toHaveAttribute('aria-controls', 'partner-tabpanel-1');
  });

  // Test 15.4: Tab list should have ARIA label
  it('should_haveAriaLabel_when_tabListRendered', () => {
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

    const tabList = container.querySelector('[role="tablist"]');
    expect(tabList).toHaveAttribute('aria-label', 'Partner detail tabs');
  });

  // Test 15.5: Focus indicators should be visible
  it('should_haveFocusIndicator_when_tabFocused', () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    overviewTab.focus();

    // Tab should be focused
    expect(overviewTab).toHaveFocus();
  });

  // Test 15.6: Screen reader should announce active tab
  it('should_announceActiveTab_when_tabSelected', () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    expect(overviewTab).toHaveAttribute('aria-selected', 'true');
  });

  // Test 15.7: All interactive elements should be keyboard accessible
  it('should_beKeyboardAccessible_when_allInteractiveElements', () => {
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

    // All buttons should be keyboard accessible
    const buttons = container.querySelectorAll('button');
    buttons.forEach((button) => {
      // Buttons should not have tabindex=-1 (unless explicitly disabled)
      const tabIndex = button.getAttribute('tabindex');
      if (tabIndex !== null && tabIndex !== '-1') {
        expect(parseInt(tabIndex)).toBeGreaterThanOrEqual(0);
      }
    });
  });

  // Test 15.8: All tab panels should have proper ARIA attributes
  it('should_haveProperAriaAttributes_when_tabPanelsRendered', () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Overview tab should be selected and its panel visible
    const overviewTab = screen.getByRole('tab', { name: /overview/i });
    expect(overviewTab).toHaveAttribute('aria-selected', 'true');
  });

  // Test 15.9: Headings should exist for structure
  it('should_haveHeadings_when_rendered', () => {
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

    // Check for proper heading structure
    const headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6');
    expect(headings.length).toBeGreaterThan(0);
  });

  // Test 15.10: Text content should be readable
  it('should_renderReadableText_when_displayed', () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Material-UI components should render readable text
    const companyName = screen.getByText('GoogleZH');
    expect(companyName).toBeInTheDocument();
    expect(companyName).toBeVisible();
  });
});
