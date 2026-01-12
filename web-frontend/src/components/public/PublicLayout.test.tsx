/**
 * PublicLayout Component Tests
 * Story 4.1.2: Public Layout & Navigation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { PublicLayout } from './PublicLayout';

// Mock i18next with actual translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'app.name': 'BATbern',
        'footer.tagline': 'Berner Architekten Treffen',
        'footer.quickLinks': 'Quick Links',
        'footer.currentEvent': 'Current Event',
        'footer.legal': 'Legal',
        'footer.privacyPolicy': 'Privacy Policy',
        'footer.termsOfService': 'Terms of Service',
        'footer.copyright': `© ${params?.year} Berner Architekten Treffen`,
        'navigation.home': 'Home',
        'navigation.about': 'About',
        'navigation.pastEvents': 'Past Events',
        'public.goToPortal': 'Go to Portal',
        'public.login': 'Login',
        'public.joinUp': 'Join Up',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
    },
  }),
}));

// Mock the useAuth hook
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    isLoading: false,
    user: null,
    canAccess: vi.fn(() => false),
    login: vi.fn(),
    logout: vi.fn(),
    refreshSession: vi.fn(),
  }),
}));

// Mock child component
const TestChild = () => <div>Test Content</div>;

describe('PublicLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render children correctly', () => {
    render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    expect(screen.getByText('Test Content')).toBeInTheDocument();
  });

  it('should apply dark theme classes', () => {
    const { container } = render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    const layoutDiv = container.firstChild as HTMLElement;
    expect(layoutDiv).toHaveClass('min-h-screen');
    expect(layoutDiv).toHaveClass('w-full');
    expect(layoutDiv).toHaveClass('flex');
    expect(layoutDiv).toHaveClass('flex-col');

    // Dark theme is applied to html element via useEffect, not to the layout div
    expect(document.documentElement).toHaveClass('dark');
  });

  it('should render navigation and footer', () => {
    const { container } = render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    // Check for navigation - look for logo image and navigation links
    const logo = screen.getByAltText('BATbern');
    expect(logo).toBeInTheDocument();

    // Get the nav element and scope queries to it
    const nav = container.querySelector('nav');
    expect(nav).toBeInTheDocument();

    // Query links within navigation scope to avoid conflicts with footer links
    const navigationLinks = nav!.querySelectorAll('a');
    const navHomeLink = Array.from(navigationLinks).find((link) => link.textContent === 'Home');
    const navAboutLink = Array.from(navigationLinks).find((link) => link.textContent === 'About');
    const navPastEventsLink = Array.from(navigationLinks).find(
      (link) => link.textContent === 'Past Events'
    );

    expect(navHomeLink).toBeInTheDocument();
    expect(navAboutLink).toBeInTheDocument();
    expect(navPastEventsLink).toBeInTheDocument();

    // Check for footer - look for branding and copyright
    expect(screen.getByText('BATbern')).toBeInTheDocument();
    expect(screen.getByText('Berner Architekten Treffen')).toBeInTheDocument();
    expect(screen.getByText(/© .* Berner Architekten Treffen/i)).toBeInTheDocument();
  });
});
