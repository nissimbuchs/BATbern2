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
import { Box, Typography, Chip, Stack, LinearProgress } from '@mui/material';
import { People as PeopleIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Event } from '../../../types/event.types';
import EventParticipantList from './EventParticipantList';
import WaitlistSection from './WaitlistSection';

interface EventParticipantsTabProps {
  event: Event;
}

const EventParticipantsTab: React.FC<EventParticipantsTabProps> = ({ event }) => {
  const { t } = useTranslation('events');

  const capacity = (event as { registrationCapacity?: number | null }).registrationCapacity ?? null;
  const confirmedCount = (event as { confirmedCount?: number }).confirmedCount ?? 0;
  const waitlistCount = (event as { waitlistCount?: number }).waitlistCount ?? 0;
  // Badge: total active registrations (registered + confirmed + waitlist, no cancelled)
  const activeTotal = confirmedCount + waitlistCount;

  const fillPct =
    capacity != null && capacity > 0 ? Math.min(100, (confirmedCount / capacity) * 100) : 0;
  const isFull = capacity != null && confirmedCount >= capacity;

  return (
    <Box sx={{ py: 3 }}>
      {/* Header with participant count */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <PeopleIcon sx={{ fontSize: 32, color: 'primary.main' }} />
          <Typography variant="h5" component="h2">
            {t('eventPage.participantsTab.title')}
          </Typography>
          <Chip label={activeTotal} color="primary" size="small" sx={{ fontWeight: 'bold' }} />
        </Stack>
      </Stack>

      {/* Story 10.11: Capacity progress bar (only when registrationCapacity is set) */}
      {capacity != null && (
        <Box sx={{ mb: 3 }}>
          <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
            <Typography variant="body2" color="text.secondary">
              {t('eventPage.participantsTab.capacityBar', {
                confirmed: confirmedCount,
                capacity,
                waitlist: waitlistCount,
              })}
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={fillPct}
            color={isFull ? 'error' : 'primary'}
            sx={{ height: 8, borderRadius: 4 }}
          />
        </Box>
      )}

      {/* Participant List */}
      <EventParticipantList eventCode={event.eventCode} />

      {/* Story 10.11: Waitlist section (only when registrationCapacity is set) */}
      {capacity != null && (
        <WaitlistSection eventCode={event.eventCode} waitlistCount={waitlistCount} />
      )}
    </Box>
  );
};

export default EventParticipantsTab;
