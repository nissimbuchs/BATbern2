/**
 * AppHeader Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive application header
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AppHeader from './AppHeader';
import { useUIStore } from '@/stores/uiStore';

// Mock stores and hooks
vi.mock('@/stores/uiStore');
vi.mock('@/hooks/useAuth');

// Mock useNotifications hook
vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: vi.fn(() => ({
    data: {
      notifications: [],
      unreadCount: 3,
    },
    isLoading: false,
  })),
}));

// Mock useBreakpoints hook to return desktop breakpoints
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
        'navigation.myEvents': 'My Events',
        'navigation.myContent': 'My Content',
        'navigation.myRegistrations': 'My Registrations',
        'navigation.profile': 'Profile',
        'menu.profile': 'Profile',
        'menu.settings': 'Settings',
        'menu.help': 'Help',
        'menu.logout': 'Logout',
        'role.organizer': 'Organizer',
        'role.speaker': 'Speaker',
        'role.partner': 'Partner',
        'role.attendee': 'Attendee',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
      changeLanguage: vi.fn().mockResolvedValue(undefined),
    },
  }),
}));

describe('AppHeader Component', () => {
  let queryClient: QueryClient;
  const mockSignOut = vi.fn();

  beforeEach(async () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();

    // Mock useAuth hook
    const { useAuth } = await import('@/hooks/useAuth');
    vi.mocked(useAuth).mockReturnValue({
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
      signIn: vi.fn(),
      signUp: vi.fn(),
      signOut: mockSignOut,
      confirmSignUp: vi.fn(),
      resendConfirmationCode: vi.fn(),
      forgotPassword: vi.fn(),
      confirmForgotPassword: vi.fn(),
      changePassword: vi.fn(),
      updateUserAttributes: vi.fn(),
      refreshSession: vi.fn(),
    });

    vi.mocked(useUIStore).mockReturnValue({
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
    });
  });

  const renderWithProviders = (component: React.ReactElement) => {
    // Set desktop viewport size to ensure navigation menu is visible (not mobile drawer)
    global.innerWidth = 1024;
    global.innerHeight = 768;
    global.dispatchEvent(new Event('resize'));

    return render(
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>{component}</QueryClientProvider>
      </BrowserRouter>
    );
  };

  describe('Basic Rendering', () => {
    test('should_renderMaterialUIAppBar_when_headerMounted', () => {
      renderWithProviders(<AppHeader />);

      // Should render Material-UI AppBar
      const appBar = screen.getByRole('banner');
      expect(appBar).toBeInTheDocument();
    });

    test('should_renderBATbernLogo_when_headerMounted', () => {
      renderWithProviders(<AppHeader />);

      // Should display BATbern logo/branding
      const logo = screen.getByRole('img', { name: /batbern/i });
      expect(logo).toBeInTheDocument();
    });

    test('should_renderNavigationMenu_when_headerMounted', () => {
      renderWithProviders(<AppHeader />);

      // Should render navigation menu
      const navigation = screen.getByRole('navigation');
      expect(navigation).toBeInTheDocument();
    });
  });

  describe('Role-Based Navigation', () => {
    test('should_renderOrganizerNavigation_when_userRoleIsOrganizer', () => {
      renderWithProviders(<AppHeader />);

      // Organizer should see Events, Speakers, Partners menu items
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
    });

    test('should_renderSpeakerNavigation_when_userRoleIsSpeaker', async () => {
      const { useAuth } = await import('@/hooks/useAuth');
      vi.mocked(useAuth).mockReturnValue({
        ...vi.mocked(useAuth)(),
        user: {
          ...vi.mocked(useAuth)().user!,
          role: 'speaker',
        },
      });

      renderWithProviders(<AppHeader />);

      // Speaker should see Dashboard, My Events, My Content
      expect(screen.getAllByText(/dashboard/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my content/i)[0]).toBeInTheDocument();
    });

    test('should_renderPartnerNavigation_when_userRoleIsPartner', async () => {
      const { useAuth } = await import('@/hooks/useAuth');
      vi.mocked(useAuth).mockReturnValue({
        ...vi.mocked(useAuth)(),
        user: {
          ...vi.mocked(useAuth)().user!,
          role: 'partner',
        },
      });

      renderWithProviders(<AppHeader />);

      // Partner should see Dashboard, Events, Analytics
      expect(screen.getAllByText(/dashboard/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
    });

    test('should_renderAttendeeNavigation_when_userRoleIsAttendee', async () => {
      const { useAuth } = await import('@/hooks/useAuth');
      vi.mocked(useAuth).mockReturnValue({
        ...vi.mocked(useAuth)(),
        user: {
          ...vi.mocked(useAuth)().user!,
          role: 'attendee',
        },
      });

      renderWithProviders(<AppHeader />);

      // Attendee should see Events, Speakers (read-only)
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
    });
  });

  describe('Notification Bell', () => {
    test('should_renderNotificationBell_when_headerMounted', () => {
      renderWithProviders(<AppHeader />);

      // Should render notification bell icon
      const notificationButton = screen.getByLabelText(/notifications/i);
      expect(notificationButton).toBeInTheDocument();
    });

    test('should_showUnreadBadge_when_unreadNotificationsExist', () => {
      renderWithProviders(<AppHeader />);

      // Should show badge with unread count
      const badge = screen.getByText('3');
      expect(badge).toBeInTheDocument();
    });

    test('should_openNotificationDrawer_when_bellClicked', () => {
      const setNotificationDrawerOpen = vi.fn();
      vi.mocked(useUIStore).mockReturnValue({
        ...vi.mocked(useUIStore)(),
        setNotificationDrawerOpen,
      });

      renderWithProviders(<AppHeader />);

      const notificationButton = screen.getByLabelText(/notifications/i);
      fireEvent.click(notificationButton);

      expect(setNotificationDrawerOpen).toHaveBeenCalledWith(true);
    });
  });

  describe('User Menu', () => {
    test('should_renderUserAvatar_when_headerMounted', () => {
      renderWithProviders(<AppHeader />);

      // Should render user avatar/profile button
      const userButton = screen.getByLabelText(/user menu|profile/i);
      expect(userButton).toBeInTheDocument();
    });

    test('should_openUserMenu_when_avatarClicked', () => {
      const setUserMenuOpen = vi.fn();
      vi.mocked(useUIStore).mockReturnValue({
        ...vi.mocked(useUIStore)(),
        setUserMenuOpen,
      });

      renderWithProviders(<AppHeader />);

      const userButton = screen.getByLabelText(/user menu|profile/i);
      fireEvent.click(userButton);

      expect(setUserMenuOpen).toHaveBeenCalledWith(true);
    });
  });

  // Language Switcher test removed - language switcher is now in UserMenuDropdown
  // See UserMenuDropdown.test.tsx for language switcher tests

  describe('Mobile Responsive', () => {
    test('should_renderHamburgerMenu_when_mobile', async () => {
      // Mock useBreakpoints to return mobile
      const { useBreakpoints } = await import('@/hooks/useBreakpoints');
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
        isLargeDesktop: false,
      });

      renderWithProviders(<AppHeader />);

      // Should render hamburger menu button with exact aria-label "menu"
      const hamburgerButton = screen.getByLabelText('menu');
      expect(hamburgerButton).toBeInTheDocument();
    });

    test('should_hideDesktopNavigation_when_mobile', async () => {
      // Mock useBreakpoints to return mobile
      const { useBreakpoints } = await import('@/hooks/useBreakpoints');
      vi.mocked(useBreakpoints).mockReturnValue({
        isMobile: true,
        isTablet: false,
        isDesktop: false,
        isLargeDesktop: false,
      });

      renderWithProviders(<AppHeader />);

      // Desktop navigation should NOT be rendered when isMobile is true
      const navigation = screen.queryByRole('navigation');
      expect(navigation).not.toBeInTheDocument();
    });
  });
});
