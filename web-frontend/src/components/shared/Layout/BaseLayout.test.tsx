/**
 * BaseLayout Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive layout component
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BaseLayout } from './BaseLayout';

// Mock auth store
vi.mock('@/stores/authStore', () => ({
  useAuthStore: vi.fn(() => ({
    user: {
      userId: 'user-123',
      email: 'test@batbern.ch',
      role: 'organizer',
      companyId: 'company-123',
    },
    isAuthenticated: true,
  })),
}));

// Mock UI store
vi.mock('@/stores/uiStore', () => ({
  useUIStore: vi.fn(() => ({
    sidebarCollapsed: false,
    setSidebarCollapsed: vi.fn(),
  })),
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
