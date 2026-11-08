/**
 * ProtectedRoute Component Tests
 * Story 1.17, Task 10a: Route protection and guard tests
 *
 * The real `useAuth` reads the auth state from the real `AuthContext`, supplied by the test auth
 * helper (17.0c2): `canAccess` and `hasRole` are the provider's own rules, so a path the rules
 * deny is denied here too. No network.
 *
 * Test Coverage:
 * - Route protection based on user role
 * - Unauthorized redirect to dashboard
 * - Session expired redirect to login
 * - Onboarding consent gate, email verification
 */

import type { ReactElement } from 'react';
import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import {
  ProtectedRoute,
  OrganizerRoute,
  SpeakerRoute,
  PartnerRoute,
  AttendeeRoute,
} from './ProtectedRoute';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import type { UserContext, UserRole } from '@/types/auth';
import { AuthTestProvider, anAuthState, anAuthUser, signedInAs, signedOut } from '@/test/auth';

// Test components
const DashboardPage = () => <div>Dashboard Page</div>;
const OrganizerPage = () => <div>Organizer Page</div>;
const SpeakerPage = () => <div>Speaker Page</div>;
const PartnerPage = () => <div>Partner Page</div>;
const AttendeePage = () => <div>Attendee Page</div>;
const LoginPage = () => <div>Login Page</div>;

/** Renders `component` on the role routes and `/`, with `/login` and `/dashboard` as sentinels. */
const renderWithRouter = (auth: UseAuthReturn, component: ReactElement, initialRoute = '/') =>
  render(
    <AuthTestProvider auth={auth}>
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
    </AuthTestProvider>
  );

const as = (role: UserRole, user: Partial<UserContext> = {}) => signedInAs(role, user);

describe('ProtectedRoute - Route Protection Based on User Role', () => {
  test('should_allowAccess_when_userHasAllowedRole', async () => {
    renderWithRouter(
      as('organizer'),
      <ProtectedRoute allowedRoles={['organizer']}>
        <OrganizerPage />
      </ProtectedRoute>
    );

    expect(await screen.findByText('Organizer Page')).toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_userLacksAllowedRole', async () => {
    renderWithRouter(
      as('speaker'),
      <ProtectedRoute allowedRoles={['organizer']}>
        <OrganizerPage />
      </ProtectedRoute>,
      '/organizer'
    );

    // Should redirect to dashboard instead of showing error alert
    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Organizer Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_organizerAccessesOrganizerRoute', async () => {
    renderWithRouter(
      as('organizer'),
      <OrganizerRoute>
        <OrganizerPage />
      </OrganizerRoute>,
      '/organizer'
    );

    expect(await screen.findByText('Organizer Page')).toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_speakerAccessesOrganizerRoute', async () => {
    renderWithRouter(
      as('speaker'),
      <OrganizerRoute>
        <OrganizerPage />
      </OrganizerRoute>,
      '/organizer'
    );

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Organizer Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_speakerAccessesSpeakerRoute', async () => {
    renderWithRouter(
      as('speaker'),
      <SpeakerRoute>
        <SpeakerPage />
      </SpeakerRoute>,
      '/speaker'
    );

    expect(await screen.findByText('Speaker Page')).toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_attendeeAccessesSpeakerRoute', async () => {
    renderWithRouter(
      as('attendee'),
      <SpeakerRoute>
        <SpeakerPage />
      </SpeakerRoute>,
      '/speaker'
    );

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Speaker Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_partnerAccessesPartnerRoute', async () => {
    renderWithRouter(
      as('partner'),
      <PartnerRoute>
        <PartnerPage />
      </PartnerRoute>,
      '/partner'
    );

    expect(await screen.findByText('Partner Page')).toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_speakerAccessesPartnerRoute', async () => {
    renderWithRouter(
      as('speaker'),
      <PartnerRoute>
        <PartnerPage />
      </PartnerRoute>,
      '/partner'
    );

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Partner Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_attendeeAccessesAttendeeRoute', async () => {
    renderWithRouter(
      as('attendee'),
      <AttendeeRoute>
        <AttendeePage />
      </AttendeeRoute>,
      '/attendee'
    );

    expect(await screen.findByText('Attendee Page')).toBeInTheDocument();
  });

  test('should_allowOrganizerAccess_when_organizerAccessesAttendeeRoute', async () => {
    // The role check of AttendeeRoute admits organizers; on `/` the path rules admit everyone.
    renderWithRouter(
      as('organizer'),
      <AttendeeRoute>
        <AttendeePage />
      </AttendeeRoute>
    );

    expect(await screen.findByText('Attendee Page')).toBeInTheDocument();
  });

  test('should_allowAccess_when_anyOfSeveralRolesIsAllowed', async () => {
    // Story 11.E.3: a user with roles [organizer, speaker] reaches the speaker routes.
    renderWithRouter(
      as('organizer', { roles: ['organizer', 'speaker'] }),
      <SpeakerRoute>
        <SpeakerPage />
      </SpeakerRoute>,
      '/speaker'
    );

    expect(await screen.findByText('Speaker Page')).toBeInTheDocument();
  });
});

describe('ProtectedRoute - Unauthorized Redirect to Dashboard', () => {
  test('should_redirectToDashboard_when_userAccessesUnauthorizedRoute', async () => {
    renderWithRouter(
      as('speaker'),
      <ProtectedRoute allowedRoles={['organizer', 'partner']}>
        <div>Restricted Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    // Should redirect to dashboard, not show error alert
    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Restricted Content')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_userRoleNotInAllowedRoles', async () => {
    renderWithRouter(
      as('attendee'),
      <ProtectedRoute allowedRoles={['organizer', 'speaker', 'partner']}>
        <div>Admin Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument();
  });

  test('should_redirectToDashboard_when_canAccessReturnsFalse', async () => {
    // The role passes allowedRoles, but the path rules give speakers no `/organizer`.
    renderWithRouter(
      as('speaker'),
      <ProtectedRoute allowedRoles={['speaker']}>
        <SpeakerPage />
      </ProtectedRoute>,
      '/organizer'
    );

    // Should redirect to dashboard instead of showing error alert
    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
    expect(screen.queryByText('Speaker Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_pathRulesAllowTheSamePath (positive control)', async () => {
    renderWithRouter(
      as('speaker'),
      <ProtectedRoute allowedRoles={['speaker']}>
        <SpeakerPage />
      </ProtectedRoute>,
      '/speaker'
    );

    expect(await screen.findByText('Speaker Page')).toBeInTheDocument();
  });
});

describe('ProtectedRoute - Session Expired Redirect to Login', () => {
  test('should_redirectToLogin_when_userNotAuthenticated', async () => {
    render(
      <AuthTestProvider auth={signedOut()}>
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
      </AuthTestProvider>
    );

    expect(await screen.findByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('should_redirectToLogin_when_sessionExpired', async () => {
    // User cleared due to expired session
    renderWithRouter(
      signedOut(),
      <ProtectedRoute requiresAuth={true}>
        <div>Protected Content</div>
      </ProtectedRoute>,
      '/organizer'
    );

    expect(await screen.findByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('should_preserveReturnUrl_when_redirectingToLogin', async () => {
    const LoginWithFrom = () => {
      // The page the user wanted travels in the router state, for the redirect after login.
      const { state } = useLocation() as { state: { from?: { pathname: string } } | null };
      return <div>Login Page, back to {state?.from?.pathname}</div>;
    };
    render(
      <AuthTestProvider auth={signedOut()}>
        <MemoryRouter initialEntries={['/organizer/events/123']}>
          <Routes>
            <Route path="/login" element={<LoginWithFrom />} />
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
      </AuthTestProvider>
    );

    expect(
      await screen.findByText('Login Page, back to /organizer/events/123')
    ).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('should_showLoadingSpinner_when_authenticationCheckInProgress', async () => {
    renderWithRouter(
      signedOut({ isLoading: true }),
      <ProtectedRoute>
        <DashboardPage />
      </ProtectedRoute>
    );

    expect(await screen.findByText('Checking authentication...')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  test('should_allowAccessToPublicRoute_when_requiresAuthIsFalse', async () => {
    renderWithRouter(
      signedOut(),
      <ProtectedRoute requiresAuth={false}>
        <div>Public Content</div>
      </ProtectedRoute>
    );

    expect(await screen.findByText('Public Content')).toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });
});

describe('ProtectedRoute - Onboarding Consent Gate (Story 12.11 AC6/AC7)', () => {
  const ProfilePageStub = () => <div>Profile Page</div>;

  function renderGate(initialRoute: string, termsAcceptedAt: string | null | undefined) {
    const user = anAuthUser({ role: 'attendee', roles: ['attendee'] });
    // `undefined` must stay undefined (not hydrated), so it is set, not defaulted.
    user.termsAcceptedAt = termsAcceptedAt;
    return render(
      <AuthTestProvider auth={anAuthState(user)}>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/events"
              element={
                <ProtectedRoute>
                  <div>Events Page</div>
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <ProfilePageStub />
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthTestProvider>
    );
  }

  test('should_redirectToOnboardingProfile_when_freshFederatedUserHasNoConsent', async () => {
    // AC7: AuthCallbackPage keeps navigating to /dashboard; the gate alone redirects
    // a consent-less (fresh federated) user to /profile?onboarding=1.
    renderGate('/dashboard', null);

    expect(await screen.findByText('Profile Page')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument();
  });

  test('should_redirectFromAnyProtectedPath_when_consentMissing', async () => {
    renderGate('/events', null);

    expect(await screen.findByText('Profile Page')).toBeInTheDocument();
    expect(screen.queryByText('Events Page')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_consentRecorded', async () => {
    renderGate('/dashboard', '2026-01-15T10:00:00Z');

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
  });

  test('should_failOpen_when_consentStateUnknown', async () => {
    // undefined = hydration failed / not yet hydrated → never lock the user out
    // on a transient /users/me failure.
    renderGate('/dashboard', undefined);

    expect(await screen.findByText('Dashboard Page')).toBeInTheDocument();
  });

  test('should_renderProfile_when_consentMissingOnProfileItself', async () => {
    // /profile must stay reachable or the gate would loop forever.
    renderGate('/profile', null);

    expect(await screen.findByText('Profile Page')).toBeInTheDocument();
  });
});

describe('ProtectedRoute - Email Verification Requirements', () => {
  test('should_showVerificationWarning_when_emailNotVerifiedAndVerificationRequired', async () => {
    renderWithRouter(
      as('organizer', { emailVerified: false }),
      <ProtectedRoute requiresVerification={true}>
        <div>Verified Only Content</div>
      </ProtectedRoute>
    );

    expect(await screen.findByText('Email Verification Required')).toBeInTheDocument();
    expect(screen.queryByText('Verified Only Content')).not.toBeInTheDocument();
  });

  test('should_allowAccess_when_emailVerifiedAndVerificationRequired', async () => {
    renderWithRouter(
      as('organizer', { emailVerified: true }),
      <ProtectedRoute requiresVerification={true}>
        <div>Verified Only Content</div>
      </ProtectedRoute>
    );

    expect(await screen.findByText('Verified Only Content')).toBeInTheDocument();
    expect(screen.queryByText('Email Verification Required')).not.toBeInTheDocument();
  });
});
