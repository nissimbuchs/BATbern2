/**
 * EventDetailsTab (Epic 14, Phase A — interim)
 *
 * Interim "Details" tab: a read-only identity summary (title, description,
 * selected topic, when & where) plus an "Edit event details" button that opens
 * the EXISTING event-edit modal (`openEditModal`) already mounted by EventPage.
 * This recomposes existing data + the existing edit affordance — no new editing
 * surface is built here (NFR9, recompose-not-rewrite).
 *
 * Phase F (Story 14.F.2) replaces this with the full in-tab identity editor
 * (theme image, AI-generate, the Topic overlay, etc.).
 */

import React from 'react';
import { Box, Paper, Stack, Typography, Chip, Button, Divider } from '@mui/material';
import { Edit as EditIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import type { Event, EventDetailUI } from '@/types/event.types';

interface EventDetailsTabProps {
  event: Event | EventDetailUI;
  onEdit: () => void;
}

export const EventDetailsTab: React.FC<EventDetailsTabProps> = ({ event, onEdit }) => {
  const { t } = useTranslation('events');

  // Topic comes from the base Event type as an expanded object (event.topic?.name);
  // fall back to topicCode if the expansion isn't present.
  const topicName =
    (event as { topic?: { name?: string } }).topic?.name ||
    (event as { topicCode?: string }).topicCode ||
    '';
  const eventDate =
    (event as { date?: string; eventDate?: string }).date ||
    (event as { eventDate?: string }).eventDate;
  const venueName = (event as { venueName?: string }).venueName;

  let formattedDate = '';
  if (eventDate) {
    const d = new Date(eventDate);
    if (!Number.isNaN(d.getTime())) formattedDate = format(d, 'dd MMM yyyy');
  }

  return (
    <Paper variant="outlined" sx={{ p: 3 }} data-testid="event-details-tab">
      <Stack spacing={2}>
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: 2,
            flexWrap: 'wrap',
          }}
        >
          <Box>
            <Typography variant="h6">{t('eventPage.details.heading', 'Event details')}</Typography>
            <Typography variant="body2" color="text.secondary">
              {t(
                'eventPage.details.intro',
                "The event's identity, topic, and schedule. Set these once early — they rarely change."
              )}
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={<EditIcon />}
            onClick={onEdit}
            data-testid="event-details-edit"
          >
            {t('eventPage.details.editButton', 'Edit event details')}
          </Button>
        </Box>

        <Divider />

        <Box>
          <Typography variant="h5" component="p">
            {event.title}
          </Typography>
          {event.description && (
            <Typography variant="body1" color="text.secondary" sx={{ mt: 1 }}>
              {event.description}
            </Typography>
          )}
        </Box>

        <Box>
          <Typography variant="overline" color="text.secondary">
            {t('eventPage.overview.selectedTopic', 'Selected Topic')}
          </Typography>
          <Box sx={{ mt: 0.5 }}>
            {topicName ? (
              <Chip label={topicName} color="primary" variant="outlined" />
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.details.noTopic', 'No topic selected yet')}
              </Typography>
            )}
          </Box>
        </Box>

        {(formattedDate || venueName) && (
          <Stack direction="row" spacing={3} flexWrap="wrap">
            {formattedDate && (
              <Typography variant="body2">
                <strong>📅</strong> {formattedDate}
              </Typography>
            )}
            {venueName && (
              <Typography variant="body2">
                <strong>📍</strong> {venueName}
              </Typography>
            )}
          </Stack>
        )}
      </Stack>
    </Paper>
  );
};

export default EventDetailsTab;
