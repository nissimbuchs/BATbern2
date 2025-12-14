/**
 * Event Detail/Edit Page
 * Story 2.5.3 - Integrates Tasks 9-13
 *
 * Comprehensive event management page that integrates:
 * - Task 9: EventForm (create/edit with auto-save)
 * - Task 10: Workflow & Metrics
 * - Task 11: VenueLogistics
 * - Task 12: Topic display (moved to Event Information card)
 * - Task 13: SpeakersSessionsTable
 *
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1
 */

import React, { useState, useEffect } from 'react';
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
  Breadcrumbs,
  Link,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
  Settings as SettingsIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useEvent } from '@/hooks/useEvents';
import {
  EventForm,
  VenueLogistics,
  SpeakersSessionsTable,
  WorkflowProgressBar,
} from '@/components/organizer/EventManagement';
import type { SessionUI, SessionSpeaker, WorkflowStep } from '@/types/event.types';
import { topicService } from '@/services/topicService';
import type { TopicResponse } from '@/types/generated/events-api.types';

const EventDetailEdit: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('events');

  // State for edit mode
  const [isEditFormOpen, setIsEditFormOpen] = useState(false);

  // State for topic details
  const [topic, setTopic] = useState<TopicResponse | null>(null);
  const [topicLoading, setTopicLoading] = useState(false);

  // Fetch event data with resource expansion
  const {
    data: event,
    isLoading,
    error,
  } = useEvent(eventCode, ['venue', 'topics', 'sessions', 'team', 'workflow', 'metrics']);

  // Fetch topic details when topicId changes
  useEffect(() => {
    if (event?.topicId) {
      setTopicLoading(true);
      topicService
        .getTopicById(event.topicId)
        .then((topicData) => {
          setTopic(topicData);
        })
        .catch((err) => {
          console.error('Failed to fetch topic:', err);
          setTopic(null);
        })
        .finally(() => {
          setTopicLoading(false);
        });
    } else {
      setTopic(null);
    }
  }, [event?.topicId]);

  const handleBack = () => {
    navigate('/organizer/events');
  };

  const handleEditEvent = () => {
    setIsEditFormOpen(true);
  };

  const handleCloseEditForm = () => {
    setIsEditFormOpen(false);
  };

  const handleEventFormSuccess = () => {
    // Event saved successfully via EventForm's auto-save functionality
    // Optionally refetch the event data here
    setIsEditFormOpen(false);
  };

  // Venue & Logistics handlers
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleVenueUpdate = async (_updates: Partial<typeof event>) => {
    // TODO: Implement venue update API call
    // This would typically call useUpdateEvent mutation
  };

  // Speakers & Sessions handlers
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleViewSessionDetails = (_sessionId: string) => {
    // TODO: Implement session details modal
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleEditSlot = (_sessionId: string) => {
    // TODO: Implement slot editor dialog
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleViewMaterials = (_sessionId: string) => {
    // TODO: Implement materials viewer
  };

  const handleViewFullAgenda = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/agenda`);
  };

  const handleManageSpeakerAssignments = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/speakers`);
  };

  const handleManageSpeakerOutreach = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/speakers/outreach`);
  };

  const handleSelectTopic = (eventCode: string) => {
    navigate(`/organizer/topics?eventCode=${eventCode}`);
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleAutoAssignSpeakers = (_eventCode: string) => {
    // TODO: Implement AI auto-assign logic
  };

  const handleDeleteEvent = () => {
    // TODO: Implement delete confirmation dialog
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
        <Alert severity="error">
          {error.message || t('errors.loadFailed', 'Failed to load event')}
        </Alert>
        <Typography variant="caption" color="error" sx={{ mt: 1, display: 'block' }}>
          Error Details: {JSON.stringify(error)}
        </Typography>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t('common.back', 'Back')}
        </Button>
      </Box>
    );
  }

  if (!event) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">{t('errors.notFound', 'Event not found')}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t('common.back', 'Back')}
        </Button>
      </Box>
    );
  }

  // Transform API sessions to SessionUI format for the component
  const sessions: SessionUI[] = (event.sessions || []).map((session, index) => {
    // Map speakers array to single speaker object (take primary speaker or first speaker)
    const primarySpeaker =
      session.speakers?.find((s: SessionSpeaker) => s.speakerRole === 'PRIMARY_SPEAKER') ||
      session.speakers?.[0];

    return {
      ...session,
      slotNumber: index + 1,
      // Map speaker from API speakers array to UI speaker object
      speaker: primarySpeaker
        ? {
            speakerSlug: primarySpeaker.username,
            name: `${primarySpeaker.firstName} ${primarySpeaker.lastName}`,
            company: primarySpeaker.company,
            email: primarySpeaker.username, // username serves as email identifier per ADR-003
          }
        : undefined,
      materialsStatus: 'pending' as const,
    };
  });

  return (
    <Box sx={{ p: 3 }}>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link underline="hover" color="inherit" sx={{ cursor: 'pointer' }} onClick={handleBack}>
          {t('navigation.events', 'Events')}
        </Link>
        <Typography color="text.primary">{event.title}</Typography>
      </Breadcrumbs>

      {/* Header */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'stretch', sm: 'center' }}
        spacing={2}
        mb={3}
      >
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" mb={1}>
            <Chip
              label={t(`dashboard.status.${event.status}`, event.status)}
              color="primary"
              size="small"
            />
            <Typography variant="caption" color="text.secondary">
              {t('form.eventNumber', 'Event')} {eventCode}
            </Typography>
          </Stack>
          <Typography variant="h4" component="h1">
            {event.title}
          </Typography>
        </Box>

        <Stack direction="row" spacing={1}>
          <Button variant="contained" startIcon={<SaveIcon />} onClick={handleEditEvent}>
            {t('common.saveChanges', 'Save Changes')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<SettingsIcon />}
            onClick={() => {
              // TODO: Implement event settings modal
            }}
          >
            {t('common.settings', 'Settings')}
          </Button>
        </Stack>
      </Stack>

      {/* Main Content */}
      <Stack spacing={3}>
        {/* EVENT INFORMATION SECTION */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            {t('form.eventInformation', 'Event Information')}
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.title', 'Title')}
              </Typography>
              <Typography variant="body1">{event.title}</Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.eventDate', 'Event Date')}
              </Typography>
              <Typography variant="body1">
                {event.eventDate || event.date
                  ? new Date(event.eventDate || event.date).toLocaleDateString()
                  : '-'}
              </Typography>
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.description', 'Description')}
              </Typography>
              <Typography variant="body1">{event.description || '-'}</Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.eventType', 'Event Type')}
              </Typography>
              <Typography variant="body1">
                {event.eventType
                  ? t(`dashboard.eventType.${event.eventType}`, event.eventType)
                  : '-'}
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.capacity', 'Capacity')}
              </Typography>
              <Typography variant="body1">
                {event.currentAttendeeCount || 0} / {event.venueCapacity || 0}
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.registrationDeadline', 'Registration Deadline')}
              </Typography>
              <Typography variant="body1">
                {event.registrationDeadline
                  ? new Date(event.registrationDeadline).toLocaleDateString()
                  : '-'}
              </Typography>
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t('form.topic', 'Topic')}
              </Typography>
              {event.topicId ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {topicLoading ? (
                    <CircularProgress size={20} />
                  ) : topic ? (
                    <Typography variant="body1">{topic.title}</Typography>
                  ) : (
                    <Typography variant="body1" color="text.secondary">
                      {t('topics.loadingError', 'Error loading topic')}
                    </Typography>
                  )}
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => handleSelectTopic(eventCode!)}
                  >
                    {t('topics.change', 'Change Topic')}
                  </Button>
                </Box>
              ) : (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="body1" color="text.secondary">
                    {t('topics.noTopic', 'No topic selected')}
                  </Typography>
                  <Button
                    variant="contained"
                    size="small"
                    onClick={() => handleSelectTopic(eventCode!)}
                  >
                    {t('topics.addFromBacklog', 'Add Topic from Backlog')}
                  </Button>
                </Box>
              )}
            </Grid>
          </Grid>

          <Button variant="outlined" size="small" sx={{ mt: 2 }} onClick={handleEditEvent}>
            {t('dashboard.actions.edit', 'Edit')}
          </Button>
        </Paper>

        {/* THEME IMAGE SECTION */}
        {event.themeImageUrl && (
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              {t('form.themeImage', 'Event Theme Image')}
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Box
              component="img"
              src={event.themeImageUrl}
              alt={t('form.themeImageAlt', 'Theme image for event')}
              sx={{
                width: '100%',
                maxHeight: 400,
                objectFit: 'cover',
                borderRadius: 1,
                boxShadow: 1,
              }}
            />
          </Paper>
        )}

        {/* WORKFLOW PROGRESS & KEY METRICS */}
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('dashboard.workflowProgress', 'Workflow Progress')}
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <WorkflowProgressBar
                workflow={{
                  currentStep: (event.workflowStep || 1) as WorkflowStep,
                  totalSteps: 16,
                  completionPercentage: ((event.workflowStep || 1) / 16) * 100,
                  steps: [],
                  blockers: [],
                }}
                eventCode={eventCode || ''}
                compact
              />
              <Button variant="text" size="small" sx={{ mt: 2 }}>
                {t('workflow.viewDetails', 'View Workflow Details')}
              </Button>
              {(event.workflowState === 'CREATED' || event.workflowState === 'TOPIC_SELECTION') && (
                <Button
                  variant="outlined"
                  size="small"
                  sx={{ mt: 2, ml: 1 }}
                  onClick={() => handleSelectTopic(eventCode!)}
                >
                  {t('workflow.actions.selectTopic', 'Select Topic')}
                </Button>
              )}
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 5 }}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('metrics.keyMetrics', 'Key Metrics')}
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <Stack spacing={1}>
                <Typography variant="body2">
                  📊 {t('metrics.speakers', 'Speakers')}: {event.confirmedSpeakersCount || 0}/12{' '}
                  {t('metrics.confirmed', 'confirmed')}
                </Typography>
                <Typography variant="body2">
                  ✓ {t('metrics.topics', 'Topics')}: {event.assignedTopicsCount || 0}{' '}
                  {t('metrics.assigned', 'assigned')}
                </Typography>
                <Typography variant="body2">
                  ⚠️ {t('metrics.materials', 'Materials')}: {event.pendingMaterialsCount || 0}{' '}
                  {t('metrics.pending', 'pending')}
                </Typography>
                <Typography variant="body2">
                  📝 {t('metrics.registrations', 'Registrations')}: {event.currentAttendeeCount}/
                  {event.venueCapacity}
                </Typography>
                <Typography variant="body2">
                  💰 {t('metrics.budget', 'Budget')}: {event.budget?.currency || 'CHF'}{' '}
                  {event.budget?.allocated || '0'}
                </Typography>
              </Stack>
            </Paper>
          </Grid>
        </Grid>

        {/* VENUE & LOGISTICS - Task 11 */}
        <VenueLogistics event={event} onUpdate={handleVenueUpdate} />

        {/* SPEAKERS & SESSIONS - Task 13 */}
        <Paper sx={{ p: 3 }}>
          <SpeakersSessionsTable
            sessions={sessions}
            eventCode={eventCode!}
            onViewDetails={handleViewSessionDetails}
            onEditSlot={handleEditSlot}
            onViewMaterials={handleViewMaterials}
            onViewFullAgenda={handleViewFullAgenda}
            onManageSpeakerAssignments={handleManageSpeakerAssignments}
            onManageSpeakerOutreach={handleManageSpeakerOutreach}
            onAutoAssignSpeakers={handleAutoAssignSpeakers}
          />
        </Paper>

        {/* TEAM ASSIGNMENTS - To be implemented in Task 14 */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            {t('team.title', 'Team Assignments')}
          </Typography>
          <Divider sx={{ mb: 2 }} />
          <Typography variant="body2" color="text.secondary">
            {t('team.leadOrganizer', 'Lead Organizer')}: {event.organizerUsername}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            (Team assignment component - Task 14)
          </Typography>
        </Paper>

        {/* ACTION BUTTONS */}
        <Stack direction="row" spacing={2} justifyContent="space-between">
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteIcon />}
            onClick={handleDeleteEvent}
          >
            {t('actions.deleteEvent', 'Delete Event')}
          </Button>
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" onClick={handleBack}>
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button variant="contained" startIcon={<SaveIcon />}>
              {t('common.saveAndContinue', 'Save & Continue')}
            </Button>
          </Stack>
        </Stack>
      </Stack>

      {/* EVENT FORM MODAL - Task 9 */}
      <EventForm
        open={isEditFormOpen}
        mode="edit"
        event={event}
        onClose={handleCloseEditForm}
        onSuccess={handleEventFormSuccess}
      />
    </Box>
  );
};

export default EventDetailEdit;
