/**
 * EventOverviewTab Component (Story 5.6)
 *
 * Overview tab showing event summary, metrics, and quick actions
 * Consolidates content from EventDetail.tsx
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  Chip,
  Divider,
  LinearProgress,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  Event as EventIcon,
  LocationOn as LocationIcon,
  People as PeopleIcon,
  Edit as EditIcon,
  Visibility as PreviewIcon,
  Email as EmailIcon,
  CalendarMonth as TimelineIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { WorkflowProgressBar } from '@/components/organizer/EventManagement';
import type { Event, EventDetailUI, WorkflowStep } from '@/types/event.types';
import { isEarlyStage, getWorkflowStateLabel } from '@/utils/workflow/workflowState';

interface EventOverviewTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

export const EventOverviewTab: React.FC<EventOverviewTabProps> = ({ event, eventCode }) => {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation('events');
  const locale = i18n.language === 'de' ? de : enUS;

  // Type assertion for extended properties
  const eventUI = event as EventDetailUI;

  // Format dates
  const eventDate = eventUI.eventDate || event.date;
  const formattedDate = eventDate
    ? format(new Date(eventDate), 'EEEE, dd MMMM yyyy', { locale })
    : '-';
  const formattedTime = eventDate ? format(new Date(eventDate), 'HH:mm', { locale }) : '-';

  // Calculate capacity percentage
  const capacityPercent =
    event.venueCapacity && event.venueCapacity > 0
      ? Math.round((event.currentAttendeeCount / event.venueCapacity) * 100)
      : 0;

  // Calculate speaker progress
  const confirmedSpeakers = eventUI.confirmedSpeakersCount || 0;
  const totalSpeakers = 12; // Default for full-day event
  const speakerPercent = Math.round((confirmedSpeakers / totalSpeakers) * 100);

  // Handle navigation
  const handleEditDetails = () => {
    // Open edit form - for now navigate to same page, could open modal
    navigate(`/organizer/events/${eventCode}?tab=overview&edit=true`);
  };

  const handlePreviewPublic = () => {
    // Open public page preview in new tab
    window.open(`/events/${eventCode}`, '_blank');
  };

  const handleViewTimeline = () => {
    navigate(`/organizer/events/timeline?highlight=${eventCode}`);
  };

  const handleSendNotification = () => {
    // TODO: Open notification modal
    console.log('Send notification for:', eventCode);
  };

  return (
    <Stack spacing={3}>
      {/* Workflow Status Bar */}
      <Paper sx={{ p: 2 }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'stretch', md: 'center' }}
          spacing={2}
        >
          <Box sx={{ flex: 1 }}>
            <Stack direction="row" spacing={2} alignItems="center" mb={1}>
              <Chip
                label={getWorkflowStateLabel(event.workflowState || 'CREATED', t)}
                color="primary"
                size="small"
              />
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.overview.step', 'Step')} {eventUI.workflowStep || 1}/16
              </Typography>
            </Stack>
            <WorkflowProgressBar
              workflow={{
                currentStep: (eventUI.workflowStep || 1) as WorkflowStep,
                totalSteps: 16,
                completionPercentage: ((eventUI.workflowStep || 1) / 16) * 100,
                steps: [],
                blockers: [],
              }}
              eventCode={eventCode}
              workflowState={event.workflowState || 'CREATED'}
              compact
            />
          </Box>
          {isEarlyStage(event.workflowState || 'CREATED') && (
            <Button variant="outlined" size="small">
              {t('workflow.advanceWorkflow', 'Advance Workflow')} →
            </Button>
          )}
        </Stack>
      </Paper>

      {/* Theme Image (if available) */}
      {event.themeImageUrl && (
        <Paper sx={{ p: 0, overflow: 'hidden' }}>
          <Box
            component="img"
            src={event.themeImageUrl}
            alt={`${event.title} theme`}
            sx={{
              width: '100%',
              height: { xs: 200, md: 300 },
              objectFit: 'cover',
            }}
          />
        </Paper>
      )}

      {/* Main Content Grid */}
      <Grid container spacing={3}>
        {/* Event Details Card */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="center"
              mb={2}
            >
              <Typography variant="h6">
                {t('eventPage.overview.eventDetails', 'Event Details')}
              </Typography>
              <Button
                size="small"
                startIcon={<EditIcon />}
                onClick={handleEditDetails}
              >
                {t('common.edit', 'Edit')}
              </Button>
            </Stack>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2}>
              {/* Title */}
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  {t('form.title', 'Title')}
                </Typography>
                <Typography variant="body1">{event.title}</Typography>
              </Box>

              {/* Description */}
              {event.description && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.description', 'Description')}
                  </Typography>
                  <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                    {event.description}
                  </Typography>
                </Box>
              )}

              {/* Date & Time */}
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <EventIcon color="action" sx={{ mt: 0.5 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.eventDate', 'Event Date')}
                  </Typography>
                  <Typography variant="body1">
                    {formattedDate} • {formattedTime}
                  </Typography>
                  {eventUI.eventType && (
                    <Typography variant="body2" color="text.secondary">
                      {t(`dashboard.eventType.${eventUI.eventType}`, eventUI.eventType)}
                    </Typography>
                  )}
                </Box>
              </Stack>

              {/* Venue */}
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <LocationIcon color="action" sx={{ mt: 0.5 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('venue.title', 'Venue')}
                  </Typography>
                  <Typography variant="body1">{event.venueName || '-'}</Typography>
                  {event.venueAddress && (
                    <Typography variant="body2" color="text.secondary">
                      {event.venueAddress}
                    </Typography>
                  )}
                </Box>
              </Stack>

              {/* Theme (if available) */}
              {eventUI.theme && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.theme', 'Theme')}
                  </Typography>
                  <Typography variant="body1">{eventUI.theme}</Typography>
                </Box>
              )}
            </Stack>
          </Paper>
        </Grid>

        {/* Key Metrics Card */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              {t('eventPage.overview.keyMetrics', 'Key Metrics')}
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={3}>
              {/* Capacity */}
              <Box>
                <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                  <PeopleIcon fontSize="small" color="action" />
                  <Typography variant="subtitle2">
                    {t('form.capacity', 'Capacity')}
                  </Typography>
                </Stack>
                <Typography variant="body1" gutterBottom>
                  {event.currentAttendeeCount || 0} / {event.venueCapacity || 0}{' '}
                  {t('eventPage.overview.registered', 'registered')}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(capacityPercent, 100)}
                  sx={{ height: 8, borderRadius: 1 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {capacityPercent}% {t('eventPage.overview.filled', 'filled')}
                </Typography>
              </Box>

              {/* Speakers */}
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  🎤 {t('eventPage.overview.speakers', 'Speakers')}
                </Typography>
                <Typography variant="body1" gutterBottom>
                  {confirmedSpeakers}/{totalSpeakers}{' '}
                  {t('eventPage.overview.confirmed', 'confirmed')}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(speakerPercent, 100)}
                  color={speakerPercent >= 100 ? 'success' : 'primary'}
                  sx={{ height: 8, borderRadius: 1 }}
                />
              </Box>

              {/* Materials */}
              <Box>
                <Typography variant="subtitle2">
                  📋 {t('eventPage.overview.materials', 'Materials')}
                </Typography>
                <Typography variant="body1">
                  {eventUI.pendingMaterialsCount || 0}{' '}
                  {t('eventPage.overview.pending', 'pending')}
                </Typography>
              </Box>

              {/* Budget (if available) */}
              {eventUI.budget && (
                <Box>
                  <Typography variant="subtitle2">
                    💰 {t('eventPage.overview.budget', 'Budget')}
                  </Typography>
                  <Typography variant="body1">
                    {eventUI.budget.currency || 'CHF'} {eventUI.budget.allocated || 0}
                  </Typography>
                </Box>
              )}

              {/* Registration Deadline */}
              {event.registrationDeadline && (
                <Box>
                  <Typography variant="subtitle2">
                    📅 {t('form.registrationDeadline', 'Registration Deadline')}
                  </Typography>
                  <Typography variant="body1">
                    {format(new Date(event.registrationDeadline), 'dd MMM yyyy', {
                      locale,
                    })}
                  </Typography>
                </Box>
              )}
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      {/* Quick Actions */}
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          {t('eventPage.overview.quickActions', 'Quick Actions')}
        </Typography>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <Button
            variant="outlined"
            startIcon={<EmailIcon />}
            onClick={handleSendNotification}
          >
            {t('eventPage.overview.sendNotification', 'Send Notification')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<PreviewIcon />}
            onClick={handlePreviewPublic}
          >
            {t('eventPage.overview.previewPublic', 'Preview Public Page')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<TimelineIcon />}
            onClick={handleViewTimeline}
          >
            {t('eventPage.overview.viewTimeline', 'View Timeline')}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
};

export default EventOverviewTab;
