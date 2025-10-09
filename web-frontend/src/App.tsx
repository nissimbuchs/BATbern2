/**
 * Main App Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 */

import React, { useEffect, Suspense, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline, Box, CircularProgress, Typography, Button } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuth } from '@hooks/useAuth';
import { AuthenticatedLayout } from '@components/auth/AuthenticatedLayout';
import {
  ProtectedRoute,
  SpeakerRoute,
  PartnerRoute,
  AttendeeRoute,
} from '@components/auth/ProtectedRoute';
import { LoginForm } from '@components/auth/LoginForm';
import { ForgotPasswordForm } from '@components/auth/ForgotPasswordForm';
import { setNavigationCallback } from '@/services/api/apiClient';
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
  return <AuthenticatedLayout>{children}</AuthenticatedLayout>;
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
    navigate('/dashboard', { replace: true });
  }, [navigate]);

  const handleForgotPassword = useCallback(() => {
    navigate('/auth/forgot-password');
  }, [navigate]);

  const handleSignUp = useCallback(() => {
    navigate('/auth/signup');
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

// Signup placeholder page (Story 1.2.3 not implemented yet)
const SignupPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 8, textAlign: 'center' }}>
      <Typography variant="h4" gutterBottom>
        Sign Up
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        User registration is not yet available. Please contact an administrator to create your
        account.
      </Typography>
      <Button variant="outlined" onClick={() => navigate('/login')}>
        Back to Login
      </Button>
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
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router>
          <NavigationSetup>
            <Suspense fallback={<PageLoader />}>
              <Routes>
                {/* Public routes */}
                <Route path="/login" element={<LoginPage />} />
                <Route path="/auth/forgot-password" element={<ForgotPasswordPage />} />
                <Route path="/auth/signup" element={<SignupPage />} />

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

                {/* Default redirect */}
                <Route path="/" element={<Navigate to="/dashboard" replace />} />

                {/* Catch all route */}
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Routes>
            </Suspense>
          </NavigationSetup>
        </Router>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
