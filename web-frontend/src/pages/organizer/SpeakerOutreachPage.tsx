/**
 * Speaker Outreach Page (Story 5.3)
 *
 * Page wrapper for Speaker Outreach Dashboard
 * Route: /organizer/events/:eventCode/speakers/outreach
 */

import React, { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Container, Box, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { SpeakerOutreachDashboard } from '@/components/organizer/SpeakerOutreach';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { useEvent } from '@/hooks/useEvents';

const SpeakerOutreachPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const { t } = useTranslation(['events', 'organizer', 'common']);

  // Fetch event data to get the title for breadcrumbs
  const { data: event } = useEvent(eventCode);

  // Build breadcrumb items (memoized to prevent re-renders)
  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [
      { label: t('events:navigation.events', 'Events'), path: '/organizer/events' },
      {
        label: event?.title || t('common:loading', 'Loading...'),
        path: `/organizer/events/${eventCode}`,
      },
      { label: t('organizer:speakerOutreach.breadcrumbs.speakerOutreach') },
    ],
    [event?.title, eventCode, t]
  );

  if (!eventCode) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">Event code is required</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Breadcrumbs */}
      <Breadcrumbs items={breadcrumbItems} marginBottom={2} />

      <Box data-testid="speaker-outreach-page">
        <SpeakerOutreachDashboard eventCode={eventCode} />
      </Box>
    </Container>
  );
};

export default SpeakerOutreachPage;
