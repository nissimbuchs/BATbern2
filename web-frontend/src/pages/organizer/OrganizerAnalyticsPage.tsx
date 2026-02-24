import React from 'react';
import { Box, Typography, Paper } from '@mui/material';
import { BarChart as BarChartIcon } from '@mui/icons-material';

const OrganizerAnalyticsPage: React.FC = () => {
  return (
    <Box sx={{ p: 3 }}>
      <Paper sx={{ p: 4, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 8 }}>
        <BarChartIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
        <Typography variant="h5" gutterBottom>
          Organizer Analytics
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Event analytics and reporting will be available here in a future release.
        </Typography>
      </Paper>
    </Box>
  );
};

export default OrganizerAnalyticsPage;
