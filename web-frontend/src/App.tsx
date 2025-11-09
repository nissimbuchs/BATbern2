/**
 * Main App Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 */

import React, { useEffect, Suspense, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { Box, CircularProgress } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HelmetProvider } from 'react-helmet-async';
import { useAuth } from '@hooks/useAuth';
import { BaseLayout } from '@components/shared/Layout/BaseLayout';
import { AuthPageLayout } from '@components/shared/Layout/AuthPageLayout';
import {
  ProtectedRoute,
  SpeakerRoute,
  PartnerRoute,
  AttendeeRoute,
} from '@components/auth/ProtectedRoute';
import { LoginForm } from '@components/auth/LoginForm';
import { ForgotPasswordForm } from '@components/auth/ForgotPasswordForm';
import { ResetPasswordForm } from '@components/auth/ResetPasswordForm';
import { RegistrationWizard } from '@components/auth/RegistrationWizard';
import { EmailVerification } from '@components/auth/EmailVerification';
import { setNavigationCallback } from '@/services/api/apiClient';
import LanguageSwitcher from '@components/shared/LanguageSwitcher/LanguageSwitcher';
import theme from '@/theme';

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 3,
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});

// Route-level code splitting with React.lazy() (Task 13b)
const Dashboard = React.lazy(() => import('@pages/Dashboard'));
const Events = React.lazy(() => import('@pages/Events'));
const Speakers = React.lazy(() => import('@pages/Speakers'));
const Partners = React.lazy(() => import('@pages/Partners'));
const Content = React.lazy(() => import('@pages/Content'));
const Analytics = React.lazy(() => import('@pages/Analytics'));
const CompanyManagement = React.lazy(
  () => import('@components/shared/Company/CompanyManagementScreen')
);
const UserManagement = React.lazy(() => import('@components/organizer/UserManagement/UserList'));

// Partner Management Pages - Story 2.8.1
const PartnerDirectoryScreen = React.lazy(() =>
  import('@components/organizer/PartnerManagement/PartnerDirectoryScreen').then((module) => ({
    default: module.PartnerDirectoryScreen,
  }))
);

// Event Management Pages - Story 2.5.3, Task 4
const EventManagementDashboard = React.lazy(() => import('@pages/EventManagementDashboard'));
const EventCreate = React.lazy(() => import('@pages/EventCreate'));
const EventTimeline = React.lazy(() => import('@pages/EventTimeline'));
const EventDetailEdit = React.lazy(() => import('@pages/EventDetailEdit')); // Comprehensive edit page with Tasks 9-13

// Partner Management Pages - Story 2.8.2, Task 13
const PartnerDetailScreen = React.lazy(() =>
  import('@components/organizer/PartnerManagement/PartnerDetailScreen').then((module) => ({
    default: module.PartnerDetailScreen,
  }))
);

// Public Pages - Story 4.1.2, 4.1.3
const HomePage = React.lazy(() => import('@pages/public/HomePage'));

// Loading fallback component for Suspense
const PageLoader = () => (
  <Box
    sx={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '50vh',
    }}
  >
    <CircularProgress />
  </Box>
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
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', p: 2 }}>
        <LanguageSwitcher />
      </Box>
      <RegistrationWizard />
    </Box>
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
        <ThemeProvider theme={theme}>
          <Router>
            <NavigationSetup>
              <Suspense fallback={<PageLoader />}>
                <Routes>
                  {/* Public routes - Story 4.1.2, 4.1.3 */}
                  <Route path="/" element={<HomePage />} />
                  <Route
                    path="/archive"
                    element={
                      <div className="min-h-screen flex items-center justify-center bg-zinc-950 text-zinc-100">
                        Archive (Coming in 4.2)
                      </div>
                    }
                  />
                  <Route
                    path="/search"
                    element={
                      <div className="min-h-screen flex items-center justify-center bg-zinc-950 text-zinc-100">
                        Search (Coming in 4.3)
                      </div>
                    }
                  />
                  <Route
                    path="/about"
                    element={
                      <div className="min-h-screen flex items-center justify-center bg-zinc-950 text-zinc-100">
                        About (Coming later)
                      </div>
                    }
                  />
                  <Route
                    path="/privacy"
                    element={
                      <div className="min-h-screen flex items-center justify-center bg-zinc-950 text-zinc-100">
                        Privacy Policy (Coming later)
                      </div>
                    }
                  />
                  <Route
                    path="/terms"
                    element={
                      <div className="min-h-screen flex items-center justify-center bg-zinc-950 text-zinc-100">
                        Terms of Service (Coming later)
                      </div>
                    }
                  />

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

                  <Route
                    path="/events"
                    element={
                      <ProtectedRoute>
                        <AuthLayout>
                          <Events />
                        </AuthLayout>
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
                  <Route
                    path="/organizer/events/create"
                    element={
                      <ProtectedRoute>
                        <AuthLayout>
                          <EventCreate />
                        </AuthLayout>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/organizer/events/timeline"
                    element={
                      <ProtectedRoute>
                        <AuthLayout>
                          <EventTimeline />
                        </AuthLayout>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/organizer/events/:eventCode"
                    element={
                      <ProtectedRoute>
                        <AuthLayout>
                          <EventDetailEdit />
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

                  {/* Partner Management Routes - Story 2.8.1, 2.8.2 */}
                  <Route
                    path="/organizer/partners"
                    element={
                      <ProtectedRoute>
                        <AuthLayout>
                          <PartnerDirectoryScreen />
                        </AuthLayout>
                      </ProtectedRoute>
                    }
                  />

                  <Route
                    path="/partners"
                    element={
                      <PartnerRoute>
                        <AuthLayout>
                          <Partners />
                        </AuthLayout>
                      </PartnerRoute>
                    }
                  />

                  {/* Partner Detail Route - Story 2.8.2, Task 13 */}
                  <Route
                    path="/partners/:companyName"
                    element={
                      <PartnerRoute>
                        <AuthLayout>
                          <PartnerDetailScreen />
                        </AuthLayout>
                      </PartnerRoute>
                    }
                  />

                  <Route
                    path="/analytics"
                    element={
                      <PartnerRoute>
                        <AuthLayout>
                          <Analytics />
                        </AuthLayout>
                      </PartnerRoute>
                    }
                  />

                  <Route
                    path="/content"
                    element={
                      <AttendeeRoute>
                        <AuthLayout>
                          <Content />
                        </AuthLayout>
                      </AttendeeRoute>
                    }
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

                  {/* Catch all route - redirect to home */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </NavigationSetup>
          </Router>
        </ThemeProvider>
      </HelmetProvider>
    </QueryClientProvider>
  );
}

export default App;
