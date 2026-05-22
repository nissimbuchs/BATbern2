/**
 * Dashboard Page Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 *
 * Redirects to role-specific dashboards
 */

import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Typography } from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useAuth } from '@/hooks/useAuth';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user, isLoading } = useAuth();

  useEffect(() => {
    // Epic 11 bug fix 2026-05-19 — diagnostic log + defensive fallback. The original
    // implementation silently stayed on the spinner when `user.role` did not match
    // any of the role branches (e.g. JWT missing `custom:role` claim, role spelled
    // unexpectedly), leaving the user staring at a blank page. Log the detected
    // role so the failure mode is visible in the console, and fall back to a sane
    // destination per role-presence so the spinner never sticks forever.
    if (isLoading) return;
    if (!user) return;
    console.log('[Dashboard] redirect decision — user.role=', user.role, 'roles=', user.roles);

    // Prefer the multi-role `roles` array when the primary `role` is missing or
    // unexpected — e.g. JWT with empty `custom:role` falls back to 'attendee' but
    // the actual role array may include 'speaker' (set later by AuthContext).
    const roleSet = new Set<string>(user.roles ?? (user.role ? [user.role] : []));
    const has = (r: string) => roleSet.has(r) || user.role === r;

    if (has('organizer')) {
      navigate('/organizer/events', { replace: true });
    } else if (has('partner')) {
      navigate('/partners/company', { replace: true });
    } else if (has('speaker')) {
      // Speaker dashboard is mounted at `/speaker-portal/dashboard` (Story 11.E.3
      // Cognito-flow speaker portal). The prior `/speaker/dashboard` target did
      // not exist; speakers landed on a blank page with no console error.
      navigate('/speaker-portal/dashboard', { replace: true });
    } else if (has('attendee')) {
      navigate('/attendee', { replace: true });
    } else {
      // Defensive: no recognised role. Send the user to the public home page
      // rather than leaving them on an infinite spinner.
      console.warn('[Dashboard] No recognised role on user; redirecting to /', user);
      navigate('/', { replace: true });
    }
  }, [user, isLoading, navigate]);

  // Show loading while redirecting
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '50vh',
        gap: 2,
      }}
    >
      <Typography variant="h4" component="h1" sx={{ mb: 2 }}>
        Dashboard
      </Typography>
      <BATbernLoader size={96} />
      <Typography variant="body2" color="text.primary">
        Loading your dashboard...
      </Typography>
    </Box>
  );
};

export default Dashboard;
