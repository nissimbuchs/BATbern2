/**
 * Main App Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 */

import React, { useEffect, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline, Box, CircularProgress } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configureAmplify } from '@/config/amplify';
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

// Authentication wrapper component
const AuthWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
        }}
      >
        Loading...
      </Box>
    );
  }

  if (!isAuthenticated) {
    return <LoginForm />;
  }

  return <AuthenticatedLayout>{children}</AuthenticatedLayout>;
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
  useEffect(() => {
    // Configure AWS Amplify on app initialization
    try {
      configureAmplify();
    } catch (error) {
      console.error('Failed to configure Amplify:', error);
    }
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router>
          <NavigationSetup>
            <Suspense fallback={<PageLoader />}>
              <Routes>
                {/* Public routes */}
                <Route path="/login" element={<LoginForm />} />
                <Route path="/auth/forgot-password" element={<ForgotPasswordForm />} />

                {/* Protected routes with lazy-loaded components */}
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <AuthWrapper>
                        <Dashboard />
                      </AuthWrapper>
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/events"
                  element={
                    <ProtectedRoute>
                      <AuthWrapper>
                        <Events />
                      </AuthWrapper>
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/speakers"
                  element={
                    <SpeakerRoute>
                      <AuthWrapper>
                        <Speakers />
                      </AuthWrapper>
                    </SpeakerRoute>
                  }
                />

                <Route
                  path="/partners"
                  element={
                    <PartnerRoute>
                      <AuthWrapper>
                        <Partners />
                      </AuthWrapper>
                    </PartnerRoute>
                  }
                />

                <Route
                  path="/analytics"
                  element={
                    <PartnerRoute>
                      <AuthWrapper>
                        <Analytics />
                      </AuthWrapper>
                    </PartnerRoute>
                  }
                />

                <Route
                  path="/content"
                  element={
                    <AttendeeRoute>
                      <AuthWrapper>
                        <Content />
                      </AuthWrapper>
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
