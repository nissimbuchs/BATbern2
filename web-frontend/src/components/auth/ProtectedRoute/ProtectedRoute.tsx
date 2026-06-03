/**
 * ProtectedRoute Component
 * Story 1.2: Route guards based on user roles and permissions
 */

import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
// Tailwind, not MUI: ProtectedRoute is imported eagerly by App.tsx (it guards ~20 routes),
// so keeping it MUI-free is what stops @mui/material from leaking into the public entry chunk.
import { BATbernLoader } from '@components/shared/BATbernLoader';
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
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
        <h1 className="mb-1 text-3xl font-light">Loading</h1>
        <BATbernLoader size={80} />
        <p className="text-sm">Checking authentication...</p>
      </div>
    );
  }

  // Redirect to login if authentication is required but user is not authenticated
  if (requiresAuth && !isAuthenticated) {
    return <Navigate to={fallbackPath} state={{ from: location }} replace />;
  }

  // Check if user exists and has required role
  if (requiresAuth && user) {
    // Story 11.E.3 (cherry-pick 73d94688): multi-role support — check if ANY of user's roles is allowed
    const userRoles = user.roles ?? (user.role ? [user.role] : []);
    if (!userRoles.some((r) => allowedRoles.includes(r))) {
      // Redirect to dashboard instead of showing error
      return <Navigate to="/dashboard" replace />;
    }

    // Check email verification if required
    if (requiresVerification && !user.emailVerified) {
      return (
        <div className="p-6">
          <div
            role="alert"
            className="rounded-md border border-amber-400/30 bg-amber-400/15 p-4 text-amber-200"
          >
            <h6 className="text-lg font-medium">Email Verification Required</h6>
            <p className="text-sm">Please verify your email address to access this content.</p>
          </div>
        </div>
      );
    }

    // Check path-based access control
    const hasAccess = canAccess(location.pathname);
    if (!hasAccess) {
      console.warn('[ProtectedRoute] Access denied, redirecting to /dashboard');
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

// Code review 2026-05-18 (D3): tightened to SPEAKER only. The backend's
// @PreAuthorize("hasRole('SPEAKER')") on /api/v1/speaker-portal/** strict-rejects organizer-
// only tokens; admitting ORGANIZER at the frontend just mounts the page then errors with
// 403. Mirrors the backend contract; users with both ORGANIZER + SPEAKER roles still pass.
export const SpeakerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['speaker']}>{children}</ProtectedRoute>
);

export const PartnerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['organizer', 'partner']}>{children}</ProtectedRoute>
);

export const AttendeeRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ProtectedRoute allowedRoles={['attendee', 'organizer']}>{children}</ProtectedRoute>
);
