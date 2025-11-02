/**
 * Event Timeline Page
 * Story 2.5.3, Task 4: Navigation Integration & Routing
 *
 * Event timeline view for organizers.
 * Will be populated with timeline visualization component in later tasks.
 */

import React from 'react';
import { Box, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

const EventTimeline: React.FC = () => {
  const { t } = useTranslation('events');

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('navigation.eventTimeline')}
      </Typography>
      <Typography variant="body1" color="text.secondary">
        Event Timeline View - Coming soon
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
        This page will display a timeline view of all events with their workflows and milestones.
      </Typography>
    </Box>
  );
};

export default EventTimeline;
