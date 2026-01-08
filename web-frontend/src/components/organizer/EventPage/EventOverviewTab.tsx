/**
 * EventOverviewTab Component (Story 5.6)
 *
 * Overview tab showing event summary, metrics, and quick actions
 * Consolidates content from EventDetail.tsx
 */

import React, { useEffect, useState } from 'react';
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
  People as PeopleIcon,
  Edit as EditIcon,
  Visibility as PreviewIcon,
  Email as EmailIcon,
  CalendarMonth as TimelineIcon,
  Topic as TopicIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { WorkflowProgressBar } from '@/components/organizer/EventManagement';
import type { Event, EventDetailUI, WorkflowStep } from '@/types/event.types';
import { isEarlyStage, getWorkflowStateLabel } from '@/utils/workflow/workflowState';
import { topicService } from '@/services/topicService';
import type { Topic } from '@/types/topic.types';

interface EventOverviewTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
  onEdit?: () => void;
}

export const EventOverviewTab: React.FC<EventOverviewTabProps> = ({ event, eventCode, onEdit }) => {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation('events');
  const locale = i18n.language === 'de' ? de : enUS;

  // Type assertion for extended properties
  const eventUI = event as EventDetailUI;

  // State for topic details
  const [topic, setTopic] = useState<Topic | null>(null);

  // Fetch topic details if topicCode is available
  useEffect(() => {
    const fetchTopic = async () => {
      // ADR-003: Event now includes topicCode (meaningful identifier) for frontend use
      const topicCode = event.topicCode;
      if (topicCode) {
        try {
          const topicData = await topicService.getTopicById(topicCode);
          setTopic(topicData);
        } catch (error) {
          console.error('Failed to fetch topic:', error);
        }
      }
    };

    fetchTopic();
  }, [event.topicCode]);

  // Format dates
  const eventDate = eventUI.eventDate || event.date;
  const formattedDate = eventDate
    ? format(new Date(eventDate), 'EEEE, dd MMMM yyyy', { locale })
    : '-';
  // Use typicalStartTime from event type (not event.date hour)
  const formattedTime = event.typicalStartTime || '-';

  // Calculate capacity percentage
  const capacityPercent =
    event.venueCapacity && event.venueCapacity > 0
      ? Math.round((event.currentAttendeeCount / event.venueCapacity) * 100)
      : 0;

  // Calculate speaker progress
  const confirmedSpeakers = eventUI.confirmedSpeakersCount || 0;
  const totalSpeakers = eventUI.maxSpeakerSlots || 4; // Use event type's max slots or default to 4
  const speakerPercent =
    totalSpeakers > 0 ? Math.round((confirmedSpeakers / totalSpeakers) * 100) : 0;

  // Handle edit
  const handleEditDetails = () => {
    if (onEdit) {
      onEdit();
    } else {
      // Default behavior: navigate to edit mode
      navigate(`/organizer/events/${eventCode}?tab=overview&edit=true`);
    }
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

  const handleSelectTopic = () => {
    navigate(`/organizer/topics?eventCode=${eventCode}`);
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
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">
                {t('eventPage.overview.eventDetails', 'Event Details')}
              </Typography>
              <Button size="small" startIcon={<EditIcon />} onClick={handleEditDetails}>
                {t('common.edit', 'Edit')}
              </Button>
            </Stack>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2}>
              {/* Title and Topic (side by side) */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: topic ? 6 : 12 }}>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.title', 'Title')}
                  </Typography>
                  <Typography variant="body1">{event.title}</Typography>
                </Grid>
                {topic && (
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <TopicIcon color="action" fontSize="small" />
                      <Typography variant="subtitle2" color="text.secondary">
                        {t('eventPage.overview.selectedTopic', 'Selected Topic')}
                      </Typography>
                    </Stack>
                    <Typography variant="body1">{topic.title}</Typography>
                  </Grid>
                )}
              </Grid>

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

              {/* Date & Registration Deadline (side by side) */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                    <EventIcon color="action" fontSize="small" />
                    <Typography variant="subtitle2" color="text.secondary">
                      {t('form.eventDate', 'Event Date')}
                    </Typography>
                  </Stack>
                  <Typography variant="body1">
                    {formattedDate} • {formattedTime}
                  </Typography>
                  {eventUI.eventType && (
                    <Typography variant="body2" color="text.secondary">
                      {t(`dashboard.eventType.${eventUI.eventType}`, eventUI.eventType)}
                    </Typography>
                  )}
                </Grid>
                {event.registrationDeadline && (
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="subtitle2" color="text.secondary">
                      {t('form.registrationDeadline', 'Registration Deadline')}
                    </Typography>
                    <Typography variant="body1">
                      {format(new Date(event.registrationDeadline), 'dd MMM yyyy', {
                        locale,
                      })}
                    </Typography>
                  </Grid>
                )}
              </Grid>

              {/* Venue Information */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('form.venue', 'Venue')}
                  </Typography>
                  <Typography variant="body1">{event.venueName || '-'}</Typography>
                </Grid>
                {event.venueAddress && (
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="subtitle2" color="text.secondary">
                      {t('form.address', 'Address')}
                    </Typography>
                    <Typography variant="body1">{event.venueAddress}</Typography>
                  </Grid>
                )}
              </Grid>
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
                  <Typography variant="subtitle2">{t('form.capacity', 'Capacity')}</Typography>
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

              {/* Materials - Speakers with complete info */}
              <Box>
                <Typography variant="subtitle2">
                  📋 {t('eventPage.overview.materials', 'Materials')}
                </Typography>
                <Typography variant="body1">
                  {eventUI.speakersWithCompleteInfoCount || 0}/{confirmedSpeakers}{' '}
                  {t('eventPage.overview.materialsComplete', 'complete')}
                </Typography>
                {(eventUI.pendingMaterialsCount || 0) > 0 && (
                  <Typography variant="body2" color="text.secondary">
                    {eventUI.pendingMaterialsCount} {t('eventPage.overview.pending', 'pending')}
                  </Typography>
                )}
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
            startIcon={<TopicIcon />}
            onClick={handleSelectTopic}
            data-testid="select-topic-button"
          >
            {t('eventPage.overview.selectTopic', 'Select Topic')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<EmailIcon />}
            onClick={handleSendNotification}
            data-testid="send-notification-button"
          >
            {t('eventPage.overview.sendNotification', 'Send Notification')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<PreviewIcon />}
            onClick={handlePreviewPublic}
            data-testid="preview-public-button"
          >
            {t('eventPage.overview.previewPublic', 'Preview Public Page')}
          </Button>
          <Button variant="outlined" startIcon={<TimelineIcon />} onClick={handleViewTimeline}>
            {t('eventPage.overview.viewTimeline', 'View Timeline')}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
};

export default EventOverviewTab;
