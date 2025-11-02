/**
 * Dashboard Page Component
 * Story 1.17: React Frontend Foundation - Task 13b (Performance Optimization)
 *
 * Redirects to role-specific dashboards
 */

import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, CircularProgress } from '@mui/material';
import { useAuth } from '@/hooks/useAuth';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  useEffect(() => {
    // Redirect to role-specific dashboard
    if (user?.role === 'organizer') {
      navigate('/organizer/events', { replace: true });
    }
    // Add other role-specific redirects here as needed
    // else if (user?.role === 'speaker') { navigate('/speakers', { replace: true }); }
    // else if (user?.role === 'partner') { navigate('/partners', { replace: true }); }
    // else if (user?.role === 'attendee') { navigate('/content', { replace: true }); }
  }, [user, navigate]);

  // Show loading while redirecting
  return (
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
};

export default Dashboard;
