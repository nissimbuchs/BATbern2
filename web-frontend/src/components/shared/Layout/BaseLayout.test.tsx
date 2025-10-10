/**
 * BaseLayout Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive layout component
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BaseLayout } from './BaseLayout';

// Mock useAuth hook
vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    user: {
      userId: 'user-123',
      email: 'test@batbern.ch',
      emailVerified: true,
      role: 'organizer',
      companyId: 'company-123',
      preferences: {
        language: 'de',
        theme: 'light',
        notifications: { email: true, sms: false, push: true },
        privacy: { showProfile: true, allowMessages: true },
      },
      issuedAt: 0,
      expiresAt: 0,
      tokenId: '',
    },
    isAuthenticated: true,
    isLoading: false,
    error: null,
    signOut: vi.fn(),
  })),
}));

// Mock UI store
vi.mock('@/stores/uiStore', () => ({
  useUIStore: vi.fn(() => ({
    locale: 'de',
    sidebarCollapsed: false,
    notificationDrawerOpen: false,
    userMenuOpen: false,
    setLocale: vi.fn(),
    toggleSidebar: vi.fn(),
    setSidebarCollapsed: vi.fn(),
    setNotificationDrawerOpen: vi.fn(),
    setUserMenuOpen: vi.fn(),
    reset: vi.fn(),
  })),
}));

// Mock useNotifications hook
vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: vi.fn(() => ({
    data: {
      notifications: [],
      unreadCount: 0,
    },
    isLoading: false,
  })),
}));

// Mock useBreakpoints hook
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: vi.fn(() => ({
    isMobile: false,
    isTablet: false,
    isDesktop: true,
    isLargeDesktop: false,
  })),
}));

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'navigation.dashboard': 'Dashboard',
        'navigation.events': 'Events',
        'navigation.speakers': 'Speakers',
        'navigation.partners': 'Partners',
        'navigation.analytics': 'Analytics',
        'menu.profile': 'Profile',
        'menu.settings': 'Settings',
        'menu.help': 'Help',
        'menu.logout': 'Logout',
        'role.organizer': 'Organizer',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
      changeLanguage: vi.fn().mockResolvedValue(undefined),
    },
  }),
}));

describe('BaseLayout Component', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const renderWithProviders = (component: React.ReactElement) => {
    return render(
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>{component}</QueryClientProvider>
      </BrowserRouter>
    );
  };

  describe('Basic Rendering', () => {
    test('should_renderAppHeader_when_layoutMounted', () => {
      renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Should render AppHeader with logo/navigation
      expect(screen.getByRole('banner')).toBeInTheDocument();
    });

    test('should_renderChildrenContent_when_layoutMounted', () => {
      renderWithProviders(
        <BaseLayout>
          <div data-testid="child-content">Test Content</div>
        </BaseLayout>
      );

      expect(screen.getByTestId('child-content')).toBeInTheDocument();
      expect(screen.getByText('Test Content')).toBeInTheDocument();
    });

    test('should_renderMainContentArea_when_layoutMounted', () => {
      renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Should have main content area with proper semantic HTML
      const mainElement = screen.getByRole('main');
      expect(mainElement).toBeInTheDocument();
    });
  });

  describe('Responsive Behavior', () => {
    test('should_showDesktopNavigation_when_screenWidthAbove768px', () => {
      // Mock window.innerWidth for desktop
      global.innerWidth = 1024;
      global.dispatchEvent(new Event('resize'));

      renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Desktop navigation should be visible
      const navigation = screen.getByRole('navigation');
      expect(navigation).toBeVisible();
    });

    test('should_showHamburgerMenu_when_screenWidthBelow768px', () => {
      // Mock window.innerWidth for mobile
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Hamburger menu button should be visible
      const menuButton = screen.getByLabelText(/menu/i);
      expect(menuButton).toBeInTheDocument();
    });
  });

  describe('Layout Structure', () => {
    test('should_haveProperSemanticHTML_when_rendered', () => {
      renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Should have semantic HTML structure
      expect(screen.getByRole('banner')).toBeInTheDocument(); // header
      expect(screen.getByRole('navigation')).toBeInTheDocument(); // nav
      expect(screen.getByRole('main')).toBeInTheDocument(); // main
    });

    test('should_applySwissGridLayout_when_rendered', () => {
      const { container } = renderWithProviders(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>
      );

      // Should use Material-UI Grid or custom grid system
      const layoutRoot = container.firstChild;
      expect(layoutRoot).toHaveClass(/MuiBox-root|layout/i);
    });
  });
});
