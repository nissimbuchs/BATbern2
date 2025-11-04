/**
 * PublicNavigation Component Tests
 * Story 4.1.2: Public Layout & Navigation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
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

    expect(screen.getByText('BATbern')).toBeInTheDocument();
  });

  it('should render navigation links', () => {
    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('About')).toBeInTheDocument();
    expect(screen.getByText('Past Events')).toBeInTheDocument();
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

  it('should render mobile menu toggle button', () => {
    render(
      <BrowserRouter>
        <PublicNavigation />
      </BrowserRouter>
    );

    // Check for toggle menu button (sr-only text)
    expect(screen.getByText('Toggle menu')).toBeInTheDocument();
  });
});
