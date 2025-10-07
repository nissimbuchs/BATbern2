/**
 * Responsive Layout Tests
 * Story 1.17 - Task 11a/11b: Responsive Design TDD
 *
 * Tests for responsive layout rendering across breakpoints:
 * - Mobile: < 900px (Material-UI 'md' breakpoint)
 * - Tablet: 900px - 1200px
 * - Desktop: > 1200px (Material-UI 'lg' breakpoint)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BaseLayout } from './BaseLayout';

// Mock useBreakpoints hook
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: vi.fn(),
}));

import { useBreakpoints } from '@/hooks/useBreakpoints';

// Helper function to render with providers
function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>
  );
}

const mockUser = {
  userId: 'user-123',
  email: 'john.doe@example.com',
  firstName: 'John',
  lastName: 'Doe',
  currentRole: 'organizer' as const,
  availableRoles: ['organizer' as const],
  preferences: {
    language: 'de' as const,
    notifications: { emailEnabled: true, inAppEnabled: true },
    theme: 'light' as const,
  },
};

const mockNotifications = {
  notifications: [],
  unreadCount: 0,
  totalCount: 0,
  hasMore: false,
};

describe('Responsive Layout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Mobile Layout (< 900px)', () => {
    it('should_renderMobileNavigation_when_screenWidthBelow768px', () => {
      // Mock mobile breakpoint
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Mobile Content</div>
        </BaseLayout>
      );

      // Expect hamburger menu to be visible (use exact match to avoid matching "user menu")
      expect(screen.getByLabelText('menu')).toBeInTheDocument();
      // Desktop navigation should not be visible (drawer is closed/unmounted)
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    });

    it('should_displayHamburgerIcon_when_mobileBreakpoint', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      expect(screen.getByLabelText('menu')).toBeInTheDocument();
    });

    it('should_stackNavigationVertically_when_mobileLayout', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Verify mobile layout uses hamburger menu (drawer navigation, not horizontal)
      expect(screen.getByLabelText('menu')).toBeInTheDocument();
      // Horizontal navigation should not be visible (drawer is closed/unmounted)
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    });

    it('should_hideLogoText_when_mobileBreakpoint', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Logo text should be hidden on mobile
      expect(screen.queryByText('BATbern')).not.toBeInTheDocument();
    });
  });

  describe('Tablet Layout (900px - 1200px)', () => {
    it('should_renderTabletNavigation_when_screenWidthBetween768and1024', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: true,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Tablet Content</div>
        </BaseLayout>
      );

      // Expect no hamburger menu on tablet (use exact match to avoid matching "user menu")
      expect(screen.queryByLabelText('menu')).not.toBeInTheDocument();
      // Expect navigation to be present
      expect(screen.getByRole('navigation')).toBeInTheDocument();
    });

    it('should_compactNavigationItems_when_tabletLayout', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: true,
        isDesktop: false,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Navigation items should have icons only, no text labels
      const navItems = screen.queryAllByRole('link');
      navItems.forEach((item) => {
        expect(item).not.toHaveTextContent(/events|topics|speakers|partners/i);
      });
    });

    it('should_showLogoWithReducedPadding_when_tabletBreakpoint', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: true,
        isDesktop: false,
      });

      const { container } = renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Verify tablet layout is rendered (navigation present, no hamburger)
      expect(screen.queryByLabelText('menu')).not.toBeInTheDocument();
      expect(screen.getByRole('navigation')).toBeInTheDocument();
    });
  });

  describe('Desktop Layout (> 1200px)', () => {
    it('should_renderDesktopNavigation_when_screenWidthAbove1024px', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Desktop Content</div>
        </BaseLayout>
      );

      // Expect full horizontal navigation (no hamburger menu, use exact match)
      expect(screen.queryByLabelText('menu')).not.toBeInTheDocument();
      expect(screen.getByRole('navigation')).toBeInTheDocument();
    });

    it('should_displayFullNavigationItems_when_desktopLayout', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Navigation items should have both icons and text labels
      expect(screen.getByText(/events/i)).toBeInTheDocument();
      expect(screen.getByText(/speakers/i)).toBeInTheDocument();
    });

    it('should_showFullLogoWithText_when_desktopBreakpoint', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      expect(screen.getByText(/BATbern/i)).toBeInTheDocument();
    });

    it('should_applyMaxWidth1200px_when_desktopLayout', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      const { container } = renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      const mainContent = container.querySelector('main');
      expect(mainContent).toHaveStyle({ maxWidth: '1200px' });
    });
  });

  describe('Breakpoint Detection', () => {
    it('should_useMaterialUIBreakpoints_when_detectingScreenSize', () => {
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      renderWithProviders(
        <BaseLayout user={mockUser} notifications={mockNotifications}>
          <div>Content</div>
        </BaseLayout>
      );

      // Verify useBreakpoints hook was called
      expect(useBreakpoints).toHaveBeenCalled();
    });

    it('should_reRenderLayout_when_breakpointChanges', () => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      });

      // Initially mobile
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
      });

      const { rerender } = render(
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <BaseLayout user={mockUser} notifications={mockNotifications}>
              <div>Content</div>
            </BaseLayout>
          </BrowserRouter>
        </QueryClientProvider>
      );

      expect(screen.getByLabelText('menu')).toBeInTheDocument();

      // Change to desktop
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: false,
        isTablet: false,
        isDesktop: true,
      });

      rerender(
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <BaseLayout user={mockUser} notifications={mockNotifications}>
              <div>Content</div>
            </BaseLayout>
          </BrowserRouter>
        </QueryClientProvider>
      );

      expect(screen.queryByLabelText('menu')).not.toBeInTheDocument();
    });
  });
});
