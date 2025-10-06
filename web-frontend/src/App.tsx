/**
 * Main App Component
 * Story 1.2: React Application with Authentication
 */

import React, { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline, Box } from '@mui/material';
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

// Placeholder components for different pages
const Dashboard = () => (
  <Box>
    <h1>Dashboard</h1>
    <p>Welcome to your dashboard</p>
  </Box>
);

const Events = () => (
  <Box>
    <h1>Events</h1>
    <p>Event management interface</p>
  </Box>
);

const Speakers = () => (
  <Box>
    <h1>Speakers</h1>
    <p>Speaker coordination interface</p>
  </Box>
);

const Partners = () => (
  <Box>
    <h1>Partners</h1>
    <p>Partner analytics interface</p>
  </Box>
);

const Content = () => (
  <Box>
    <h1>Content Search</h1>
    <p>Content discovery interface</p>
  </Box>
);

const Analytics = () => (
  <Box>
    <h1>Analytics</h1>
    <p>Analytics dashboard</p>
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
          <Routes>
            {/* Public routes */}
            <Route path="/login" element={<LoginForm />} />
            <Route path="/auth/forgot-password" element={<ForgotPasswordForm />} />

            {/* Protected routes */}
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
        </Router>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
