/**
 * Event Create Page
 * Story 2.5.3, Task 4: Navigation Integration & Routing
 *
 * Event creation page for organizers.
 * Will be populated with EventForm component in later tasks.
 */

import React from 'react';
import { Box, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

const EventCreate: React.FC = () => {
  const { t } = useTranslation('events');

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('form.createEvent')}
      </Typography>
      <Typography variant="body1" color="text.secondary">
        Event Create Form - Coming soon
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
        This page will display the event creation form with validation and auto-save.
      </Typography>
    </Box>
  );
};

export default EventCreate;
