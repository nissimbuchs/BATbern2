/**
 * Speaker Outreach Page (Story 5.3)
 *
 * Page wrapper for Speaker Outreach Dashboard
 * Route: /organizer/events/:eventCode/speakers/outreach
 */

import React from 'react';
import { useParams } from 'react-router-dom';
import { Container, Box, Alert } from '@mui/material';
import { SpeakerOutreachDashboard } from '@/components/organizer/SpeakerOutreach';

const SpeakerOutreachPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();

  if (!eventCode) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">Event code is required</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Box data-testid="speaker-outreach-page">
        <SpeakerOutreachDashboard eventCode={eventCode} />
      </Box>
    </Container>
  );
};

export default SpeakerOutreachPage;
