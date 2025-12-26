/**
 * Event Participants Tab Component (GREEN Phase)
 *
 * Container for the participants view within EventPage.
 * Story 3.3: Event Participants Tab - Task 8 (GREEN Phase)
 *
 * Features:
 * - Displays participant count badge
 * - Renders participant list for specific event
 * - Simple container with no complex logic
 */

import React from 'react';
import { Box, Typography, Chip, Stack } from '@mui/material';
import { People as PeopleIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Event } from '../../../types/event.types';
import EventParticipantList from './EventParticipantList';

interface EventParticipantsTabProps {
  event: Event;
}

const EventParticipantsTab: React.FC<EventParticipantsTabProps> = ({ event }) => {
  const { t } = useTranslation('events');

  const participantCount = event.registrationCount || 0;

  return (
    <Box sx={{ py: 3 }}>
      {/* Header with participant count */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <PeopleIcon sx={{ fontSize: 32, color: 'primary.main' }} />
          <Typography variant="h5" component="h2">
            {t('eventPage.participantsTab.title')}
          </Typography>
          <Chip label={participantCount} color="primary" size="small" sx={{ fontWeight: 'bold' }} />
        </Stack>
      </Stack>

      {/* Participant List */}
      <EventParticipantList eventCode={event.eventCode} />
    </Box>
  );
};

export default EventParticipantsTab;
