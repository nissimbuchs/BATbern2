/**
 * Main App Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 */

import React, { useEffect, Suspense, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HelmetProvider } from 'react-helmet-async';
import { useAuth } from '@hooks/useAuth';
import HomePage from '@pages/public/HomePage';
import { ProtectedRoute, SpeakerRoute, PartnerRoute } from '@components/auth/ProtectedRoute';
import { setNavigationCallback } from '@/services/api/apiClient';
import { LanguageSync } from '@components/shared/LanguageSync/LanguageSync';
import { AuthProvider } from '@/contexts/AuthContext';

// MUI is kept OUT of the eager entry chunk so the public homepage never downloads it
// (~158 KB vendor-mui). The MUI theme provider, the MUI-using layouts, and the
// language switcher are all lazy-loaded; they only arrive when a route under the
// <MuiLayout> boundary (auth / organizer / speaker / partner / registration flow) is
// visited. See MuiLayout.tsx. The public routes below are Tailwind-only siblings.
const MuiLayout = React.lazy(() => import('@/MuiLayout'));
const BaseLayout = React.lazy(() =>
  import('@components/shared/Layout/BaseLayout').then((m) => ({ default: m.BaseLayout }))
);
const AuthPageLayout = React.lazy(() =>
  import('@components/shared/Layout/AuthPageLayout').then((m) => ({ default: m.AuthPageLayout }))
);
const PartnerPortalLayout = React.lazy(() =>
  import('@/components/partner/PartnerPortalLayout').then((m) => ({
    default: m.PartnerPortalLayout,
  }))
);
const LanguageSwitcher = React.lazy(
  () => import('@components/shared/LanguageSwitcher/LanguageSwitcher')
);

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 3,
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});

// Auth forms — lazy-loaded so they stay out of the entry chunk (the public
// homepage never needs them). Named exports, hence the default-interop wrapper.
const LoginForm = React.lazy(() =>
  import('@components/auth/LoginForm').then((m) => ({ default: m.LoginForm }))
);
const ForgotPasswordForm = React.lazy(() =>
  import('@components/auth/ForgotPasswordForm').then((m) => ({ default: m.ForgotPasswordForm }))
);
const ResetPasswordForm = React.lazy(() =>
  import('@components/auth/ResetPasswordForm').then((m) => ({ default: m.ResetPasswordForm }))
);
const RegistrationWizard = React.lazy(() =>
  import('@components/auth/RegistrationWizard').then((m) => ({ default: m.RegistrationWizard }))
);
const EmailVerification = React.lazy(() =>
  import('@components/auth/EmailVerification').then((m) => ({ default: m.EmailVerification }))
);
// Story 12.7 (SSO Phase 4): federated-login callback + logout routes.
const AuthCallbackPage = React.lazy(() =>
  import('@components/auth/AuthCallbackPage').then((m) => ({ default: m.AuthCallbackPage }))
);
const LogoutPage = React.lazy(() =>
  import('@components/auth/LogoutPage').then((m) => ({ default: m.LogoutPage }))
);

// Route-level code splitting with React.lazy() (Task 13b)
const Dashboard = React.lazy(() => import('@pages/Dashboard'));
const Speakers = React.lazy(() => import('@pages/Speakers'));
const OrganizerPartners = React.lazy(() => import('@pages/OrganizerPartners'));
const OrganizerPartnerDetail = React.lazy(() => import('@pages/OrganizerPartnerDetail'));

// Story 8.0: Partner Portal
const PartnerCompanyPage = React.lazy(() => import('@pages/PartnerCompanyPage'));
// Story 8.1: PartnerAnalyticsPlaceholder replaced by PartnerAttendanceDashboard
const PartnerAttendanceDashboard = React.lazy(
  () => import('@components/partner/PartnerAttendanceDashboardPage')
);
// Story 8.2: TopicListPage replaces PartnerTopicsPlaceholder
const TopicListPage = React.lazy(() => import('@components/partner/TopicListPage'));
// Story 8.2: Organizer partner-topics status panel
const TopicStatusPanel = React.lazy(() => import('@components/organizer/TopicStatusPanel'));
// Story 8.3: Organizer partner meetings page
const PartnerMeetingsPage = React.lazy(() => import('@components/organizer/PartnerMeetingsPage'));
const CompanyManagement = React.lazy(
  () => import('@components/shared/Company/CompanyManagementScreen')
);
const UserManagement = React.lazy(
  () => import('@components/organizer/UserManagement/UserManagement')
);
const NewsletterSubscribers = React.lazy(
  () => import('@components/organizer/NewsletterSubscribers/NewsletterSubscribers')
);
const UserAccountPage = React.lazy(() => import('@pages/UserAccountPage/UserAccountPage'));

// Event Management Pages - Story 2.5.3, Task 4
const EventManagementDashboard = React.lazy(() => import('@pages/EventManagementDashboard'));
const OrganizerAnalyticsPage = React.lazy(() => import('@pages/organizer/OrganizerAnalyticsPage'));
const NotificationsPage = React.lazy(() => import('@pages/organizer/NotificationsPage'));
const EventPage = React.lazy(() => import('@pages/organizer/EventPage')); // Story 5.6: Unified event page
// Story 10.1: Admin page consolidating Event Types, Import Data, Task Templates
const EventManagementAdminPage = React.lazy(
  () => import('@pages/organizer/EventManagementAdminPage')
);

// Topic Management Page - Story 5.2
const TopicManagementPage = React.lazy(() => import('@pages/organizer/TopicManagementPage'));

// Story 10.4: Blob Topic Selector — full-screen, no AuthLayout
const BlobTopicSelectorPage = React.lazy(() => import('@pages/organizer/BlobTopicSelectorPage'));

// Task Management Page - Story 5.5
const TaskBoardPage = React.lazy(() => import('@pages/organizer/TaskBoardPage'));

// Slot Assignment Page - Story 5.7 (BAT-11)
const SlotAssignmentPage = React.lazy(() => import('@pages/organizer/SlotAssignmentPage'));

// Public Pages - Story 4.1.2, 4.1.3, 4.1.5, 4.1.6, 4.2
// HomePage is eagerly imported (top of file) to avoid double-spinner on first load.
const AboutPage = React.lazy(() => import('@pages/AboutPage'));
const PublicRegistrationPage = React.lazy(() => import('@pages/public/RegistrationPage'));
const RegistrationSuccessPage = React.lazy(() => import('@pages/public/RegistrationSuccessPage'));
const ConfirmRegistrationPage = React.lazy(() => import('@pages/public/ConfirmRegistrationPage'));
const CancelRegistrationPage = React.lazy(() => import('@pages/public/CancelRegistrationPage'));
const RegistrationConfirmationPage = React.lazy(
  () => import('@pages/public/RegistrationConfirmationPage')
);
const AttendeeWelcomePage = React.lazy(() => import('@pages/attendee/AttendeeWelcomePage'));
// Story 4.2: Archive browsing pages
const ArchivePage = React.lazy(() => import('@pages/public/ArchivePage'));

// Legal pages
const PrivacyPage = React.lazy(() => import('@pages/public/PrivacyPage'));
const SupportPage = React.lazy(() => import('@pages/public/SupportPage'));

// Story 6.2a: Speaker Portal - Invitation Response
const InvitationResponsePage = React.lazy(
  () => import('@pages/speaker-portal/InvitationResponsePage')
);

// Story 6.2b: Speaker Portal - Profile Update
// Story 12.11: role-neutral profile page (generalized from the speaker-portal ProfileUpdatePage)
const ProfilePage = React.lazy(() => import('@pages/profile/ProfilePage'));

// Story 6.3: Speaker Portal - Content Submission
const ContentSubmissionPage = React.lazy(
  () => import('@pages/speaker-portal/ContentSubmissionPage')
);

// Story 6.4: Speaker Portal - Dashboard
const SpeakerDashboardPage = React.lazy(() => import('@pages/speaker-portal/SpeakerDashboardPage'));

// Story 10.7: Newsletter unsubscribe page
const UnsubscribePage = React.lazy(() => import('@pages/public/UnsubscribePage'));

// Additional-email verification (v2): public token-credentialed verify page
const VerifyAdditionalEmailPage = React.lazy(
  () => import('@pages/public/VerifyAdditionalEmailPage')
);

// Story 10.12: Self-service deregistration page
const DeregistrationPage = React.lazy(() => import('@pages/public/DeregistrationPage'));

// Dev tool: local email inbox (no auth, no layout)
const DevEmailInboxPage = React.lazy(() => import('@pages/dev/DevEmailInboxPage'));

// Story 10.8a: Fullscreen moderator presentation page (no auth, no layout wrapper)
const PresentationPage = React.lazy(() => import('@pages/PresentationPage'));

// Fullscreen organizer live session control page (no layout wrapper, mobile-optimized)
const LiveControlPage = React.lazy(() => import('@pages/LiveControlPage/LiveControlPage'));

// Loading fallback component for Suspense
const PageLoader = () => (
  <div className="flex min-h-[50vh] items-center justify-center">
    <BATbernLoader size={96} />
  </div>
);

// Layout wrapper for authenticated routes
const AuthLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return <BaseLayout>{children}</BaseLayout>;
};

// Login page component with navigation logic
const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // Redirect to dashboard if already authenticated
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const handleLoginSuccess = useCallback(() => {
    console.log('[LoginPage] handleLoginSuccess called, navigating to /dashboard');
    navigate('/dashboard', { replace: true });
  }, [navigate]);

  const handleForgotPassword = useCallback(() => {
    navigate('/auth/forgot-password');
  }, [navigate]);

  const handleSignUp = useCallback(() => {
    navigate('/auth/register');
  }, [navigate]);

  return (
    <LoginForm
      onSuccess={handleLoginSuccess}
      onForgotPassword={handleForgotPassword}
      onSignUp={handleSignUp}
    />
  );
};

// Forgot Password page component with navigation logic
const ForgotPasswordPage: React.FC = () => {
  // For now, render the ForgotPasswordForm as-is
  // ForgotPasswordForm has a hardcoded link to /auth/login
  // TODO: Refactor to accept onBackToLogin callback for programmatic navigation
  return <ForgotPasswordForm />;
};

// Reset Password page component (Story 1.2.2a)
const ResetPasswordPage: React.FC = () => {
  // Renders the ResetPasswordForm which handles code verification and password reset
  // Email is passed via URL parameter: /auth/reset-password?email=user@example.com
  return <ResetPasswordForm />;
};

// Registration page (Story 1.2.3)
const RegistrationPage: React.FC = () => {
  return (
    <div className="flex min-h-screen flex-col">
      <div className="flex justify-end p-4">
        <LanguageSwitcher />
      </div>
      <RegistrationWizard />
    </div>
  );
};

// Navigation setup component (must be inside Router)
const NavigationSetup: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();

  useEffect(() => {
    // Configure API client to use React Router navigate instead of window.location
    // ARCH-002 Fix: Replace window.location.href with React Router navigate()
    setNavigationCallback(navigate);
  }, [navigate]);

  return <>{children}</>;
};

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <HelmetProvider>
        <AuthProvider>
          <Router>
            <NavigationSetup>
              {/* Sync user language preferences after authentication */}
              <LanguageSync />
              <Suspense fallback={<PageLoader />}>
                <Routes>
                  {/* All MUI-rendering routes live under this lazy ThemeProvider boundary, so
                      @mui/material (~158 KB) is fetched only when one is visited — never for the
                      public homepage. The Tailwind-only public routes are declared as siblings
                      AFTER this boundary; React Router ranks by specificity, not source order. */}
                  <Route
                    element={
                      <Suspense fallback={<PageLoader />}>
                        <MuiLayout />
                      </Suspense>
                    }
                  >
                    <Route path="/register/:eventCode" element={<PublicRegistrationPage />} />
                    <Route path="/registration-success" element={<RegistrationSuccessPage />} />
                    <Route
                      path="/events/:eventCode/confirm-registration"
                      element={<ConfirmRegistrationPage />}
                    />
                    <Route
                      path="/events/:eventCode/cancel-registration"
                      element={<CancelRegistrationPage />}
                    />
                    <Route
                      path="/registration-confirmation/:confirmationCode"
                      element={<RegistrationConfirmationPage />}
                    />
                    {/* Story 11.E.3: Speaker Portal routes — Cognito-authenticated.
                        Wrapped in <SpeakerRoute> so unauthenticated callers redirect to /login.
                        eventCode is a path parameter (Q#1 resolved 2026-05-17). */}
                    <Route
                      path="/speaker-portal/dashboard"
                      element={
                        <SpeakerRoute>
                          <SpeakerDashboardPage />
                        </SpeakerRoute>
                      }
                    />
                    <Route
                      path="/speaker-portal/respond/:eventCode"
                      element={
                        <SpeakerRoute>
                          <InvitationResponsePage />
                        </SpeakerRoute>
                      }
                    />
                    <Route
                      path="/speaker-portal/content/:eventCode"
                      element={
                        <SpeakerRoute>
                          <ContentSubmissionPage />
                        </SpeakerRoute>
                      }
                    />
                    {/* Story 12.11 (AC5): the profile page is role-neutral at /profile.
                        Old speaker-portal paths redirect there (bookmarks + old emails).
                        Code review 2026-05-18 (D1): profile is user-level (CUMS), not
                        per-event, so the /:eventCode form also collapses to /profile. */}
                    <Route
                      path="/speaker-portal/profile"
                      element={<Navigate to="/profile" replace />}
                    />
                    <Route
                      path="/speaker-portal/profile/:eventCode"
                      element={<Navigate to="/profile" replace />}
                    />
                    {/* Code review 2026-05-18 (P15): backward-compat redirect for old email
                        deep-links (`/speaker-portal/respond?token=...`) that no longer match
                        the new `/speaker-portal/respond/:eventCode` route. Sends the user to
                        the dashboard, where they can pick their pending invitation. */}
                    <Route
                      path="/speaker-portal/respond"
                      element={<Navigate to="/speaker-portal/dashboard" replace />}
                    />

                    {/* Story 10.7: Newsletter unsubscribe */}
                    <Route path="/unsubscribe" element={<UnsubscribePage />} />

                    {/* Additional-email verification (v2): public token-credentialed verify */}
                    <Route path="/verify-email" element={<VerifyAdditionalEmailPage />} />

                    {/* Story 10.12: Self-service deregistration (token-protected, no auth required) */}
                    <Route path="/deregister" element={<DeregistrationPage />} />

                    {/* Story 10.8a: Fullscreen moderator presentation page — public, no auth */}
                    <Route path="/present/:eventCode" element={<PresentationPage />} />

                    {/* Dev tool: local email inbox — no auth, no layout */}
                    <Route path="/dev/emails" element={<DevEmailInboxPage />} />

                    {/* Authentication routes */}
                    <Route
                      path="/login"
                      element={
                        <AuthPageLayout>
                          <LoginPage />
                        </AuthPageLayout>
                      }
                    />
                    <Route
                      path="/auth/login"
                      element={
                        <AuthPageLayout>
                          <LoginPage />
                        </AuthPageLayout>
                      }
                    />
                    <Route
                      path="/auth/forgot-password"
                      element={
                        <AuthPageLayout>
                          <ForgotPasswordPage />
                        </AuthPageLayout>
                      }
                    />
                    <Route
                      path="/auth/reset-password"
                      element={
                        <AuthPageLayout>
                          <ResetPasswordPage />
                        </AuthPageLayout>
                      }
                    />
                    <Route
                      path="/auth/register"
                      element={
                        <AuthPageLayout>
                          <RegistrationPage />
                        </AuthPageLayout>
                      }
                    />
                    <Route
                      path="/auth/verify-email"
                      element={
                        <AuthPageLayout>
                          <EmailVerification />
                        </AuthPageLayout>
                      }
                    />

                    {/* Story 12.7 (SSO Phase 4): federated-login callback + logout.
                        No AuthPageLayout wrapper — both are transient redirect targets
                        that render only a text-free loader. */}
                    <Route path="/auth/callback" element={<AuthCallbackPage />} />
                    <Route path="/logout" element={<LogoutPage />} />

                    {/* Protected routes with lazy-loaded components */}
                    <Route
                      path="/dashboard"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <Dashboard />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Story 12.11 (AC5): role-neutral profile page — any authenticated
                        role. Also the onboarding-gate target (/profile?onboarding=1). */}
                    <Route
                      path="/profile"
                      element={
                        <ProtectedRoute>
                          <ProfilePage />
                        </ProtectedRoute>
                      }
                    />

                    {/* Attendee landing page — Epic 7 stub */}
                    <Route
                      path="/attendee"
                      element={
                        <ProtectedRoute>
                          <AttendeeWelcomePage />
                        </ProtectedRoute>
                      }
                    />

                    {/* Event Management Routes - Story 2.5.3, Task 4 */}
                    <Route
                      path="/organizer/events"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <EventManagementDashboard />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 5.6: Unified Event Page */}
                    <Route
                      path="/organizer/events/:eventCode"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <EventPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 5.7 (BAT-11): Dedicated Slot Assignment Page */}
                    <Route
                      path="/organizer/events/:eventCode/slot-assignment"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <SlotAssignmentPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                    {/* Live session control — fullscreen, mobile-optimized, no AuthLayout */}
                    <Route
                      path="/organizer/events/:eventCode/live-control"
                      element={
                        <ProtectedRoute>
                          <LiveControlPage />
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 5.6: Redirect deprecated /edit route to unified page */}
                    <Route
                      path="/organizer/events/:eventCode/edit"
                      element={<Navigate to=".." replace />}
                    />
                    {/* Story 10.1: Admin page with Event Types, Import Data, Task Templates tabs */}
                    <Route
                      path="/organizer/admin"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <EventManagementAdminPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 10.1: Redirect old /event-types route to admin tab 0 */}
                    <Route
                      path="/organizer/event-types"
                      element={<Navigate to="/organizer/admin?tab=0" replace />}
                    />
                    <Route
                      path="/organizer/topics"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <TopicManagementPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 10.4: Blob Topic Selector — full-screen, no sidebar */}
                    <Route
                      path="/organizer/events/:eventCode/topic-blob"
                      element={
                        <ProtectedRoute>
                          <BlobTopicSelectorPage />
                        </ProtectedRoute>
                      }
                    />
                    {/* Story 8.2: Organizer Partner Topics Status Panel */}
                    <Route
                      path="/organizer/partner-topics"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <TopicStatusPanel />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Story 8.3: Organizer Partner Meetings */}
                    <Route
                      path="/organizer/partner-meetings"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <PartnerMeetingsPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Story 5.5: Task Management Page */}
                    <Route
                      path="/organizer/tasks"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <TaskBoardPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Story 6.0: Organizer Speaker Management */}
                    <Route
                      path="/organizer/speakers"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <Speakers />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    <Route
                      path="/speakers"
                      element={
                        <SpeakerRoute>
                          <AuthLayout>
                            <Speakers />
                          </AuthLayout>
                        </SpeakerRoute>
                      }
                    />

                    {/* Partner Management Routes */}
                    <Route
                      path="/organizer/partners"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <OrganizerPartners />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    <Route
                      path="/organizer/partners/:companyName"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <OrganizerPartnerDetail />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Story 8.0: Partner Portal */}
                    <Route
                      path="/partners"
                      element={
                        <PartnerRoute>
                          <AuthLayout>
                            <PartnerPortalLayout />
                          </AuthLayout>
                        </PartnerRoute>
                      }
                    >
                      <Route index element={<Navigate to="company" replace />} />
                      <Route path="company" element={<PartnerCompanyPage />} />
                      <Route path="analytics" element={<PartnerAttendanceDashboard />} />
                      <Route path="topics" element={<TopicListPage />} />
                    </Route>

                    {/* Story 8.0: Redirect old /analytics stub to partner portal */}
                    <Route
                      path="/analytics"
                      element={<Navigate to="/partners/analytics" replace />}
                    />

                    {/* Company Management Routes */}
                    <Route
                      path="/organizer/companies/*"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <CompanyManagement />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    <Route
                      path="/speaker/company/*"
                      element={
                        <SpeakerRoute>
                          <AuthLayout>
                            <CompanyManagement />
                          </AuthLayout>
                        </SpeakerRoute>
                      }
                    />

                    {/* Organizer Notifications */}
                    <Route
                      path="/organizer/notifications"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <NotificationsPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Organizer Analytics - placeholder until implemented */}
                    <Route
                      path="/organizer/analytics"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <OrganizerAnalyticsPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* User Management Routes - Story 2.5.2 */}
                    <Route
                      path="/organizer/users/*"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <UserManagement />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* Newsletter Subscriber Management - Story 10.28 */}
                    <Route
                      path="/organizer/newsletter-subscribers"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <NewsletterSubscribers />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />

                    {/* User Account Page - Story 2.6; :tab? = profile (default) | settings */}
                    <Route
                      path="/account/:tab?"
                      element={
                        <ProtectedRoute>
                          <AuthLayout>
                            <UserAccountPage />
                          </AuthLayout>
                        </ProtectedRoute>
                      }
                    />
                  </Route>
                  {/* ── Public, Tailwind-only routes (no MUI) — siblings of the boundary ──
                      Declared after the <MuiLayout> route on purpose: React Router ranks by
                      path specificity, so these win for their exact paths without pulling MUI. */}
                  <Route path="/" element={<HomePage />} />
                  {/* Story 5.7: Public event page with preview mode support */}
                  <Route path="/events/:eventCode" element={<HomePage />} />
                  {/* Story 4.2: Archive browsing routes */}
                  <Route path="/archive" element={<ArchivePage />} />
                  {/* Story 4.2 / 10.21: Archive detail — reuses HomePage (dark-theme components) */}
                  <Route path="/archive/:eventCode" element={<HomePage />} />
                  <Route path="/about" element={<AboutPage />} />
                  <Route path="/privacy" element={<PrivacyPage />} />
                  <Route path="/support" element={<SupportPage />} />
                  {/* Catch all route - redirect to home (MUI-free) */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </NavigationSetup>
          </Router>
        </AuthProvider>
      </HelmetProvider>
    </QueryClientProvider>
  );
}

export default App;
