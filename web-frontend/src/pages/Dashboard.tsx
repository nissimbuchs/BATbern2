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
  const { user } = useAuth();

  useEffect(() => {
    // Redirect to role-specific dashboard
    if (user?.role === 'organizer') {
      navigate('/organizer/events', { replace: true });
    } else if (user?.role === 'partner') {
      navigate('/partners/company', { replace: true });
    } else if (user?.role === 'speaker') {
      // Epic 11 bug fix 2026-05-19 — speaker dashboard is mounted at
      // `/speaker-portal/dashboard` (Story 11.E.3 Cognito-flow speaker portal).
      // The prior `/speaker/dashboard` target didn't exist; speakers landed
      // on a blank page with no console error.
      navigate('/speaker-portal/dashboard', { replace: true });
    } else if (user?.role === 'attendee') {
      navigate('/attendee', { replace: true });
    }
  }, [user, navigate]);

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
