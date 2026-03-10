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
  Topic as TopicIcon,
  AutoAwesome,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { WorkflowProgressBar } from '@/components/organizer/EventManagement';
import type { Event, EventDetailUI, WorkflowStep } from '@/types/event.types';
import {
  getWorkflowStateLabel,
  getWorkflowStepNumber,
  getWorkflowProgress,
  WORKFLOW_STATE_ORDER,
} from '@/utils/workflow/workflowState';
import { topicService } from '@/services/topicService';
import type { Topic } from '@/types/topic.types';
import { useFeatureFlags } from '@/hooks/useFeatureFlags';
import { AiAssistDrawer } from './AiAssistDrawer';

interface EventOverviewTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
  onEdit?: () => void;
}

export const EventOverviewTab: React.FC<EventOverviewTabProps> = ({ event, eventCode, onEdit }) => {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation('events');
  const { t: tOrg } = useTranslation('organizer');
  const locale = i18n.language === 'de' ? de : enUS;
  const { aiContentEnabled } = useFeatureFlags();

  // Type assertion for extended properties
  const eventUI = event as EventDetailUI;

  // State for topic details
  const [topic, setTopic] = useState<Topic | null>(null);
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);

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

  // Use registrationCapacity (organizer-set limit) when available; fall back to venueCapacity.
  // registrationCapacity is populated when include=registrations is requested (Story 10.11).
  const registrationCapacity = (event as { registrationCapacity?: number | null })
    .registrationCapacity;
  const displayCapacity = registrationCapacity ?? event.venueCapacity ?? 0;

  // Calculate capacity percentage
  const capacityPercent =
    displayCapacity > 0 ? Math.round((event.currentAttendeeCount / displayCapacity) * 100) : 0;

  // Calculate speaker progress
  const confirmedSpeakers = eventUI.confirmedSpeakersCount || 0;
  const totalSpeakers = eventUI.maxSpeakerSlots || 4; // Use event type's max slots or default to 4
  const speakerPercent =
    totalSpeakers > 0 ? Math.round((confirmedSpeakers / totalSpeakers) * 100) : 0;

  // Calculate session materials progress (Story 5.9 - Task 7b)
  const sessionsWithMaterials = eventUI.sessionsWithMaterialsCount || 0;
  const totalSessions = eventUI.totalSessionsCount || 0;
  const materialsPercent =
    totalSessions > 0 ? Math.round((sessionsWithMaterials / totalSessions) * 100) : 0;

  // Determine materials progress bar color based on completion percentage
  const getMaterialsProgressColor = (): 'error' | 'warning' | 'success' => {
    if (materialsPercent < 50) return 'error'; // Red for <50%
    if (materialsPercent < 100) return 'warning'; // Yellow for 50-99%
    return 'success'; // Green for 100%
  };

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
                data-testid="workflow-status-badge"
              />
              <Typography variant="body2" color="text.secondary">
                {t('eventPage.overview.step', 'Step')}{' '}
                {getWorkflowStepNumber(event.workflowState || 'CREATED')}/
                {WORKFLOW_STATE_ORDER.length}
              </Typography>
            </Stack>
            <WorkflowProgressBar
              workflow={{
                currentStep: getWorkflowStepNumber(
                  event.workflowState || 'CREATED'
                ) as WorkflowStep,
                totalSteps: WORKFLOW_STATE_ORDER.length,
                completionPercentage: getWorkflowProgress(event.workflowState || 'CREATED'),
                steps: [],
                blockers: [],
              }}
              workflowState={event.workflowState || 'CREATED'}
              compact
            />
          </Box>
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
              <Button
                size="small"
                startIcon={<EditIcon />}
                onClick={handleEditDetails}
                data-testid="edit-event-button"
              >
                <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                  {t('common.edit', 'Edit')}
                </Box>
              </Button>
            </Stack>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2}>
              {/* Title and Topic (side by side) */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: topic ? 6 : 12 }}>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('common:labels.title')}
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
                    {t('common:labels.description')}
                  </Typography>
                  <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                    {event.description}
                  </Typography>
                </Box>
              )}

              {/* AI description/image generation button (Story 10.16) */}
              {aiContentEnabled && event.topicCode && (
                <Box>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<AutoAwesome />}
                    onClick={() => setAiDrawerOpen(true)}
                  >
                    {tOrg('aiAssist.generateDescription', '✨ Generate with AI')}
                  </Button>
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
                  {event.currentAttendeeCount || 0} / {displayCapacity || 0}{' '}
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
                  🎤 {t('common:navigation.speakers')}
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

              {/* Materials - Session materials completion (Story 5.9 - Task 7b) */}
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  📋 {t('eventPage.overview.materials', 'Materials')}
                </Typography>
                <Typography variant="body1" gutterBottom>
                  {sessionsWithMaterials}/{totalSessions}{' '}
                  {t('eventPage.overview.sessionsComplete', 'sessions complete')}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(materialsPercent, 100)}
                  color={getMaterialsProgressColor()}
                  sx={{ height: 8, borderRadius: 1 }}
                />
                <Typography variant="caption" color="text.secondary">
                  {materialsPercent}% {t('eventPage.overview.complete', 'complete')}
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
            startIcon={<PreviewIcon />}
            onClick={handlePreviewPublic}
            data-testid="preview-public-button"
          >
            {t('eventPage.overview.previewPublic', 'Preview Public Page')}
          </Button>
        </Stack>
      </Paper>

      {/* AI Assist Drawer (Story 10.16) */}
      <AiAssistDrawer
        eventCode={eventCode}
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
        onDescriptionGenerated={() => {
          // text is displayed inside the drawer with a copy button; no auto-close needed
        }}
        onImageGenerated={() => {
          setAiDrawerOpen(false);
        }}
      />
    </Stack>
  );
};

export default EventOverviewTab;
