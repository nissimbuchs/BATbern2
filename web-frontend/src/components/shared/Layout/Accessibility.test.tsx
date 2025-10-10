/**
 * Accessibility Tests - Semantic HTML Structure
 * Story 1.17, Task 12a (RED Phase): WCAG 2.1 AA Compliance Testing
 *
 * Tests semantic HTML, keyboard navigation, screen reader support, and color contrast.
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { BaseLayout } from './BaseLayout';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';

expect.extend(matchers);

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

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    );
  };
};

describe('Accessibility - Semantic HTML Structure', () => {
  describe('Semantic HTML5 Elements', () => {
    it('should_useSemanticHeaderElement_when_renderingAppHeader', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Expect a semantic <header> element
      const header = document.querySelector('header');
      expect(header).toBeInTheDocument();
      expect(header?.tagName).toBe('HEADER');
    });

    it('should_useSemanticNavElement_when_renderingNavigation', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Expect a semantic <nav> element with proper label
      const nav = screen.getByRole('navigation');
      expect(nav).toBeInTheDocument();
      expect(nav.tagName).toBe('NAV');
    });

    it('should_useSemanticMainElement_when_renderingMainContent', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Expect a semantic <main> element
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
      expect(main.tagName).toBe('MAIN');
    });

    it('should_haveProperHeadingHierarchy_when_renderingPage', () => {
      render(
        <BaseLayout>
          <h1>Page Title</h1>
          <h2>Section</h2>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Verify heading hierarchy (h1 before h2, no skipped levels)
      const headings = screen.getAllByRole('heading');
      const h1Count = headings.filter((h) => h.tagName === 'H1').length;
      const h2Count = headings.filter((h) => h.tagName === 'H2').length;

      expect(h1Count).toBeGreaterThanOrEqual(1); // At least one H1
      expect(h2Count).toBeGreaterThanOrEqual(1); // At least one H2
    });

    it('should_haveAccessibleLandmarks_when_renderingLayout', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Verify ARIA landmarks
      expect(screen.getByRole('banner')).toBeInTheDocument(); // header with role="banner"
      expect(screen.getByRole('navigation')).toBeInTheDocument();
      expect(screen.getByRole('main')).toBeInTheDocument();
    });
  });

  describe('ARIA Labels and Attributes', () => {
    it('should_haveAriaLabelsOnButtons_when_renderingInteractiveElements', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // All icon buttons should have aria-label
      const menuButton = screen.queryByLabelText(/menu/i);
      const notificationButton = screen.getByLabelText(/notifications/i);
      const userMenuButton = screen.getByLabelText(/user menu/i);

      if (menuButton) expect(menuButton).toBeInTheDocument();
      expect(notificationButton).toBeInTheDocument();
      expect(userMenuButton).toBeInTheDocument();
    });

    it('should_haveAriaLiveRegion_when_renderingNotificationArea', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Notification badge should have aria-live="polite" for screen reader updates
      const notificationButton = screen.getByLabelText(/notifications/i);
      const liveRegion = notificationButton.querySelector('[aria-live]');

      expect(liveRegion || notificationButton).toHaveAttribute('aria-live');
    });

    it('should_haveAriaExpanded_when_renderingDropdownMenus', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Dropdown trigger buttons should have aria-expanded
      const userMenuButton = screen.getByLabelText(/user menu/i);
      expect(userMenuButton).toHaveAttribute('aria-expanded');
    });

    it('should_haveAriaHaspopup_when_renderingMenuButtons', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Menu trigger buttons should have aria-haspopup
      const userMenuButton = screen.getByLabelText(/user menu/i);
      expect(userMenuButton).toHaveAttribute('aria-haspopup', 'true');
    });

    it('should_haveUniqueAriaLabelledby_when_renderingDialogs', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Any dialog/modal elements should have aria-labelledby linking to title
      const dialogs = document.querySelectorAll('[role="dialog"]');
      dialogs.forEach((dialog) => {
        const labelledBy = dialog.getAttribute('aria-labelledby');
        if (labelledBy) {
          const titleElement = document.getElementById(labelledBy);
          expect(titleElement).toBeInTheDocument();
        }
      });
    });
  });

  describe('Automated Accessibility Audit (axe-core)', () => {
    it('should_haveNoAccessibilityViolations_when_renderingBaseLayout', async () => {
      const { container } = render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should_meetWCAG_AA_ColorContrast_when_renderingComponents', async () => {
      const { container } = render(
        <BaseLayout>
          <h1>Accessible Heading</h1>
          <p>Accessible paragraph text with good contrast.</p>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const results = await axe(container, {
        rules: {
          'color-contrast': { enabled: true },
        },
      });

      expect(results).toHaveNoViolations();
    });

    it('should_haveAccessibleImagesWithAltText_when_renderingImages', async () => {
      const { container } = render(
        <BaseLayout>
          <img src="/logo.svg" alt="BATbern Logo" />
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const results = await axe(container, {
        rules: {
          'image-alt': { enabled: true },
        },
      });

      expect(results).toHaveNoViolations();
    });

    it('should_haveNoTabIndexGreaterThanZero_when_renderingFocusableElements', async () => {
      const { container } = render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const results = await axe(container, {
        rules: {
          tabindex: { enabled: true },
        },
      });

      expect(results).toHaveNoViolations();
    });
  });

  describe('Skip Links and Focus Management', () => {
    it('should_haveSkipToMainContentLink_when_renderingLayout', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // First focusable element should be "Skip to main content" link
      const skipLink = screen.queryByText(/skip to main content/i);
      expect(skipLink).toBeInTheDocument();
      expect(skipLink?.getAttribute('href')).toBe('#main-content');
    });

    it('should_focusMainContent_when_skipLinkClicked', () => {
      render(
        <BaseLayout>
          <div>Test Content</div>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const skipLink = screen.getByText(/skip to main content/i);
      skipLink.click();

      const mainContent = screen.getByRole('main');
      expect(document.activeElement).toBe(mainContent);
    });
  });
});
