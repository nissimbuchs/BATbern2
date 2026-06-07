/**
 * PublicNavigation Component Tests
 * Story 4.1.2: Public Layout & Navigation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { PublicNavigation } from './PublicNavigation';
import * as useAuthModule from '@/hooks/useAuth';

// Mock useAuth hook
vi.mock('@/hooks/useAuth');

describe('PublicNavigation', () => {
  beforeEach(() => {
    // Default mock: not authenticated
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      accessToken: null,
      signIn: vi.fn(),
      signOut: vi.fn(),
      signUp: vi.fn(),
      refreshToken: vi.fn(),
      clearError: vi.fn(),
      hasRole: vi.fn(),
      hasPermission: vi.fn(),
      canAccess: vi.fn(),
      isTokenExpired: vi.fn(),
    });
  });

  it('should render logo', () => {
    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    expect(screen.getByAltText('BATbern')).toBeInTheDocument();
  });

  it('should render navigation links', () => {
    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    expect(screen.getAllByText('Home').length).toBeGreaterThan(0);
    expect(screen.getAllByText('About').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Past Events').length).toBeGreaterThan(0);
  });

  it('should show Login button when not authenticated', () => {
    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    // Should show Login button
    const loginButtons = screen.getAllByText('Login');
    expect(loginButtons.length).toBeGreaterThan(0);
  });

  it('should show Go to Portal button when authenticated', () => {
    // Mock authenticated state
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: {
        userId: '123',
        email: 'test@example.com',
        role: 'organizer',
        companyId: '456',
        firstName: 'Test',
        lastName: 'User',
      },
      isAuthenticated: true,
      isLoading: false,
      error: null,
      accessToken: 'fake-token',
      signIn: vi.fn(),
      signOut: vi.fn(),
      signUp: vi.fn(),
      refreshToken: vi.fn(),
      clearError: vi.fn(),
      hasRole: vi.fn(),
      hasPermission: vi.fn(),
      canAccess: vi.fn(),
      isTokenExpired: vi.fn(),
    });

    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    // Should show Go to Portal button
    const portalButtons = screen.getAllByText('Go to Portal');
    expect(portalButtons.length).toBeGreaterThan(0);
  });

  // Note: Mobile menu toggle not yet implemented in simplified navigation
  // Component currently uses md:flex to hide nav on mobile
  // TODO: Add mobile hamburger menu in future iteration

  // 2026-06-05: "My Profile" must be visible to EVERY authenticated user (the
  // role-neutral /profile page serves all roles since Story 12.11), not just speakers.
  describe('My Profile dropdown entry', () => {
    const authenticatedAs = (roles: string[]) => {
      vi.mocked(useAuthModule.useAuth).mockReturnValue({
        user: {
          userId: '123',
          email: 'test@example.com',
          role: roles[0],
          roles,
          companyId: '456',
          firstName: 'Test',
          lastName: 'User',
        },
        isAuthenticated: true,
        isLoading: false,
        error: null,
        accessToken: 'fake-token',
        signIn: vi.fn(),
        signOut: vi.fn(),
        signUp: vi.fn(),
        refreshToken: vi.fn(),
        clearError: vi.fn(),
        hasRole: vi.fn(),
        hasPermission: vi.fn(),
        canAccess: vi.fn(),
        isTokenExpired: vi.fn(),
      } as never);
    };

    it.each([['attendee'], ['speaker'], ['organizer'], ['partner']])(
      'should_showMyProfileLinkingToProfile_when_%sOpensUserDropdown',
      async (role) => {
        authenticatedAs([role]);
        const user = userEvent.setup();

        render(
          <BrowserRouter>
            <PublicNavigation />
          </BrowserRouter>
        );

        await user.click(screen.getByTestId('public-nav-user-menu'));

        const profileLink = await screen.findByRole('link', { name: /my profile/i });
        expect(profileLink).toHaveAttribute('href', '/profile');
      }
    );
  });
});
