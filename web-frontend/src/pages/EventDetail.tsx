/**
 * Event Detail Page
 *
 * Displays detailed information about a single event
 * Accessed via /organizer/events/:eventCode
 */

import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  Chip,
  CircularProgress,
  Alert,
  Divider,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  ArrowBack as ArrowBackIcon,
  Edit as EditIcon,
  Event as EventIcon,
  LocationOn as LocationIcon,
  People as PeopleIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { getWorkflowStateLabel } from '@/utils/workflow/workflowState';
import { de, enUS } from 'date-fns/locale';
import { useEvent } from '@/hooks/useEvents';
import type { EventUI } from '@/types/event.types';
import { SpeakerStatusDashboard } from '@/components/organizer/SpeakerStatus/SpeakerStatusDashboard';

const EventDetail: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation('events');

  // Fetch single event by eventCode
  const { data: event, isLoading, error } = useEvent(eventCode);

  const locale = i18n.language === 'de' ? de : enUS;

  const handleBack = () => {
    navigate('/organizer/events');
  };

  const handleEdit = () => {
    // This will be implemented when the edit functionality is fully ready
    console.log('Edit event:', eventCode);
  };

  if (isLoading) {
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
  }

  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{t('errors.loadFailed')}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t('actions.back')}
        </Button>
      </Box>
    );
  }

  if (!event) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">{t('errors.notFound')}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t('actions.back')}
        </Button>
      </Box>
    );
  }

  const eventUI = event as EventUI;
  const eventDate = eventUI.eventDate || event.date;
  const formattedDate = format(new Date(eventDate), 'dd MMMM yyyy', { locale });
  const formattedTime = format(new Date(eventDate), 'HH:mm', { locale });

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack}>
          {t('actions.back')}
        </Button>
        <Button startIcon={<EditIcon />} variant="contained" onClick={handleEdit}>
          {t('dashboard.actions.edit')}
        </Button>
      </Stack>

      {/* Event Details */}
      <Paper sx={{ p: 0, overflow: 'hidden' }}>
        {/* Theme Image Banner (Story 2.5.3a) */}
        {event.themeImageUrl && (
          <Box
            component="img"
            src={event.themeImageUrl}
            alt={`${event.title} theme`}
            sx={{
              width: '100%',
              height: 240,
              objectFit: 'cover',
            }}
          />
        )}

        <Stack spacing={3} sx={{ p: 3 }}>
          {/* Title and Status */}
          <Box>
            <Stack direction="row" spacing={2} alignItems="center" mb={1}>
              <Chip
                label={getWorkflowStateLabel(event.workflowState || 'CREATED', t)}
                color="primary"
              />
              <Typography variant="caption" color="text.secondary">
                {event.eventCode}
              </Typography>
            </Stack>
            <Typography variant="h4" component="h1" gutterBottom>
              {event.title}
            </Typography>
            {event.description && (
              <Typography variant="body1" color="text.secondary" sx={{ mb: 0 }}>
                {event.description}
              </Typography>
            )}
          </Box>

          <Divider />

          {/* Event Details Grid */}
          <Grid container spacing={3}>
            {/* Date & Time */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <EventIcon color="action" />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.eventDate')}
                  </Typography>
                  <Typography variant="body1">
                    {formattedDate} • {formattedTime}
                  </Typography>
                  {eventUI.eventType && (
                    <Typography variant="body2" color="text.secondary">
                      {t(`eventTypes.${eventUI.eventType}`)}
                    </Typography>
                  )}
                </Box>
              </Stack>
            </Grid>

            {/* Venue */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <LocationIcon color="action" />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('venue.title')}
                  </Typography>
                  <Typography variant="body1">{event.venueName}</Typography>
                  {event.venueAddress && (
                    <Typography variant="body2" color="text.secondary">
                      {event.venueAddress}
                    </Typography>
                  )}
                </Box>
              </Stack>
            </Grid>

            {/* Capacity & Attendees */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <PeopleIcon color="action" />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.capacity')}
                  </Typography>
                  <Typography variant="body1">
                    {event.currentAttendeeCount} / {event.venueCapacity}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {Math.round((event.currentAttendeeCount / event.venueCapacity) * 100)}% filled
                  </Typography>
                </Box>
              </Stack>
            </Grid>

            {/* Registration Deadline */}
            {event.registrationDeadline && (
              <Grid size={{ xs: 12, md: 6 }}>
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.registrationDeadline')}
                  </Typography>
                  <Typography variant="body1">
                    {format(new Date(event.registrationDeadline), 'dd MMMM yyyy', { locale })}
                  </Typography>
                </Box>
              </Grid>
            )}
          </Grid>

          <Divider />

          {/* Organizer */}
          <Box>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('team.leadOrganizer')}
            </Typography>
            <Typography variant="body1">{event.organizerUsername}</Typography>
          </Box>

          {/* Theme (if available) */}
          {eventUI.theme && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  {t('form.theme')}
                </Typography>
                <Typography variant="body1">{eventUI.theme}</Typography>
              </Box>
            </>
          )}
        </Stack>
      </Paper>

      {/* Speaker Status Dashboard - Story 5.4 */}
      {eventCode && (
        <Box sx={{ mt: 3 }}>
          <SpeakerStatusDashboard eventCode={eventCode} />
        </Box>
      )}
    </Box>
  );
};

export default EventDetail;
