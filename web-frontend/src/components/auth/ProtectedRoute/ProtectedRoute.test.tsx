/**
 * ProtectedRoute Component Tests
 * Story 1.17, Task 10a (RED Phase): Route protection and guard tests
 *
 * Test Coverage:
 * - Route protection based on user role
 * - Unauthorized redirect to dashboard
 * - Session expired redirect to login
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import {
  ProtectedRoute,
  OrganizerRoute,
  SpeakerRoute,
  PartnerRoute,
  AttendeeRoute,
} from './ProtectedRoute';
import * as useAuthModule from '@hooks/useAuth';
import { UserRole } from '@/types/auth';

// Mock the useAuth hook
vi.mock('@hooks/useAuth');

// Test components
const DashboardPage = () => <div>Dashboard Page</div>;
const OrganizerPage = () => <div>Organizer Page</div>;
const SpeakerPage = () => <div>Speaker Page</div>;
const PartnerPage = () => <div>Partner Page</div>;
const AttendeePage = () => <div>Attendee Page</div>;
const LoginPage = () => <div>Login Page</div>;

// Helper function to render with router
const renderWithRouter = (component: React.ReactElement, initialRoute = '/') => {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/organizer" element={component} />
        <Route path="/speaker" element={component} />
        <Route path="/partner" element={component} />
        <Route path="/attendee" element={component} />
        <Route path="/" element={component} />
      </Routes>
    </MemoryRouter>
  );
};

describe('ProtectedRoute - Route Protection Based on User Role', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should_allowAccess_when_userHasAllowedRole', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'organizer@test.com',
        role: 'organizer' as UserRole,
        firstName: 'Test',
        lastName: 'Organizer',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute allowedRoles={['organizer']}>
        <OrganizerPage />
      </ProtectedRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Organizer Page')).toBeInTheDocument();
    });
  });

  test('should_redirectToDashboard_when_userLacksAllowedRole', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute allowedRoles={['organizer']}>
        <OrganizerPage />
      </ProtectedRoute>,
      '/organizer'
    );

    // Should redirect to dashboard instead of showing error alert
    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Organizer Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_organizerAccessesOrganizerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'organizer@test.com',
        role: 'organizer' as UserRole,
        firstName: 'Test',
        lastName: 'Organizer',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <OrganizerRoute>
        <OrganizerPage />
      </OrganizerRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Organizer Page')).toBeInTheDocument();
    });
  });

  test('should_redirectToDashboard_when_speakerAccessesOrganizerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-2',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <OrganizerRoute>
        <OrganizerPage />
      </OrganizerRoute>,
      '/organizer'
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Organizer Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_speakerAccessesSpeakerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-2',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <SpeakerRoute>
        <SpeakerPage />
      </SpeakerRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Speaker Page')).toBeInTheDocument();
    });
  });

  test('should_redirectToDashboard_when_attendeeAccessesSpeakerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-4',
        email: 'attendee@test.com',
        role: 'attendee' as UserRole,
        firstName: 'Test',
        lastName: 'Attendee',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <SpeakerRoute>
        <SpeakerPage />
      </SpeakerRoute>,
      '/speaker'
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Speaker Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_partnerAccessesPartnerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-3',
        email: 'partner@test.com',
        role: 'partner' as UserRole,
        firstName: 'Test',
        lastName: 'Partner',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <PartnerRoute>
        <PartnerPage />
      </PartnerRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Partner Page')).toBeInTheDocument();
    });
  });

  test('should_redirectToDashboard_when_speakerAccessesPartnerRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-2',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <PartnerRoute>
        <PartnerPage />
      </PartnerRoute>,
      '/partner'
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Partner Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_attendeeAccessesAttendeeRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-4',
        email: 'attendee@test.com',
        role: 'attendee' as UserRole,
        firstName: 'Test',
        lastName: 'Attendee',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <AttendeeRoute>
        <AttendeePage />
      </AttendeeRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Attendee Page')).toBeInTheDocument();
    });
  });

  test('should_allowOrganizerAccess_when_organizerAccessesAttendeeRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'organizer@test.com',
        role: 'organizer' as UserRole,
        firstName: 'Test',
        lastName: 'Organizer',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <AttendeeRoute>
        <AttendeePage />
      </AttendeeRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Attendee Page')).toBeInTheDocument();
    });
  });
});

describe('ProtectedRoute - Unauthorized Redirect to Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should_redirectToDashboard_when_userAccessesUnauthorizedRoute', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-2',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute allowedRoles={['organizer', 'partner']}>
        <div>Restricted Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    // Should redirect to dashboard, not show error alert
    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Restricted Content')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_userRoleNotInAllowedRoles', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-4',
        email: 'attendee@test.com',
        role: 'attendee' as UserRole,
        firstName: 'Test',
        lastName: 'Attendee',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute allowedRoles={['organizer', 'speaker', 'partner']}>
        <div>Admin Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_canAccessReturnsFalse', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-2',
        email: 'speaker@test.com',
        role: 'speaker' as UserRole,
        firstName: 'Test',
        lastName: 'Speaker',
        emailVerified: true,
      },
      canAccess: vi.fn(() => false), // Simulate path-based access denial
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute allowedRoles={['speaker']}>
        <SpeakerPage />
      </ProtectedRoute>,
      '/speaker'
    );

    // Should redirect to dashboard instead of showing error alert
    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Speaker Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });
});

describe('ProtectedRoute - Session Expired Redirect to Login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should_redirectToLogin_when_userNotAuthenticated', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      canAccess: vi.fn(() => false),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/protected"
            element={
              <ProtectedRoute>
                <div>Protected Content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('should_redirectToLogin_when_sessionExpired', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null, // User cleared due to expired session
      canAccess: vi.fn(() => false),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute requiresAuth={true}>
        <div>Protected Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('should_preserveReturnUrl_when_redirectingToLogin', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      canAccess: vi.fn(() => false),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/organizer/events/123']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/organizer/events/:id"
            element={
              <ProtectedRoute>
                <div>Protected Content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    // Note: The actual return URL preservation is handled by React Router's location state
    // This test verifies redirect happens; actual state preservation is tested via integration tests
  });

  test('should_showLoadingSpinner_when_authenticationCheckInProgress', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true, // Simulating loading state
      user: null,
      canAccess: vi.fn(() => false),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute>
        <DashboardPage />
      </ProtectedRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Checking authentication...')).toBeInTheDocument();
    });

    expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  test('should_allowAccessToPublicRoute_when_requiresAuthIsFalse', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      canAccess: vi.fn(() => false),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute requiresAuth={false}>
        <div>Public Content</div>
      </ProtectedRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Public Content')).toBeInTheDocument();
    });

    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });
});

describe('ProtectedRoute - Email Verification Requirements', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('should_showVerificationWarning_when_emailNotVerifiedAndVerificationRequired', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'organizer@test.com',
        role: 'organizer' as UserRole,
        firstName: 'Test',
        lastName: 'Organizer',
        emailVerified: false, // Not verified
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute requiresVerification={true}>
        <div>Verified Only Content</div>
      </ProtectedRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Email Verification Required')).toBeInTheDocument();
    });

    expect(screen.queryByText('Verified Only Content')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_emailVerifiedAndVerificationRequired', async () => {
    const mockUseAuth = vi.spyOn(useAuthModule, 'useAuth');
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'user-1',
        email: 'organizer@test.com',
        role: 'organizer' as UserRole,
        firstName: 'Test',
        lastName: 'Organizer',
        emailVerified: true,
      },
      canAccess: vi.fn(() => true),
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });

    renderWithRouter(
      <ProtectedRoute requiresVerification={true}>
        <div>Verified Only Content</div>
      </ProtectedRoute>
    );

    await waitFor(() => {
      expect(screen.getByText('Verified Only Content')).toBeInTheDocument();
    });

    expect(screen.queryByText('Email Verification Required')).not.toBeInTheDocument();
  });
});
