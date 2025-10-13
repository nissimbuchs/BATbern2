/**
 * ProtectedRoute Component
 * Story 1.2: Route guards based on user roles and permissions
 */

import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import { useAuth } from '@hooks/useAuth';
import type { ProtectedRouteProps } from './types';

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles = ['organizer', 'speaker', 'partner', 'attendee'],
  requiresAuth = true,
  requiresVerification = false,
  fallbackPath = '/login',
}) => {
  const { isAuthenticated, isLoading, user, canAccess } = useAuth();
  const location = useLocation();

  // Show loading spinner while checking authentication
  if (isLoading) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          gap: 2,
        }}
      >
        <CircularProgress size={40} />
        <Typography variant="body2" color="text.secondary">
          Checking authentication...
        </Typography>
      </Box>
    );
  }

  // Redirect to login if authentication is required but user is not authenticated
  if (requiresAuth && !isAuthenticated) {
    return <Navigate to={fallbackPath} state={{ from: location }} replace />;
  }

  // Check if user exists and has required role
  if (requiresAuth && user) {
    // Check if user's role is in allowed roles
    if (!allowedRoles.includes(user.role)) {
      // Redirect to dashboard instead of showing error
      return <Navigate to="/dashboard" replace />;
    }

    // Check email verification if required
    if (requiresVerification && !user.emailVerified) {
      return (
        <Box sx={{ p: 3 }}>
          <Alert severity="warning">
            <Typography variant="h6">Email Verification Required</Typography>
            <Typography variant="body2">
              Please verify your email address to access this content.
            </Typography>
          </Alert>
        </Box>
      );
    }

    // Check path-based access control
    if (!canAccess(location.pathname)) {
      // Redirect to dashboard instead of showing error
      return <Navigate to="/dashboard" replace />;
    }
  }

  // Render protected content
  return <>{children}</>;
};

/**
 * Role-specific route protection components
 */
export const OrganizerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['organizer']}>{children}</ProtectedRoute>
);

export const SpeakerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['organizer', 'speaker']}>{children}</ProtectedRoute>
);

export const PartnerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['organizer', 'partner']}>{children}</ProtectedRoute>
);

export const AttendeeRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['attendee', 'organizer']}>{children}</ProtectedRoute>
);
