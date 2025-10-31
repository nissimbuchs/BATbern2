/**
 * Event Detail/Edit Page
 * Story 2.5.3 - Integrates Tasks 9-13
 *
 * Comprehensive event management page that integrates:
 * - Task 9: EventForm (create/edit with auto-save)
 * - Task 10: Workflow & Metrics
 * - Task 11: VenueLogistics
 * - Task 12: TopicsList
 * - Task 13: SpeakersSessionsTable
 *
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1
 */

import React, { useState } from 'react';
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
  TopicsList,
  SpeakersSessionsTable,
  WorkflowProgressBar,
} from '@/components/organizer/EventManagement';
import type { Session, Topic } from '@/types/event.types';

const EventDetailEdit: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('events');

  // State for edit mode
  const [isEditFormOpen, setIsEditFormOpen] = useState(false);

  // Fetch event data with resource expansion
  const {
    data: event,
    isLoading,
    error,
  } = useEvent(eventCode, ['venue', 'topics', 'sessions', 'team', 'workflow', 'metrics']);

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
  const handleVenueUpdate = async (updates: Partial<typeof event>) => {
    console.log('Update venue:', updates);
    // TODO: Implement venue update API call
    // This would typically call useUpdateEvent mutation
  };

  // Topics handlers
  const handleViewTopic = (topicId: string) => {
    console.log('View topic:', topicId);
    // TODO: Implement topic details modal
  };

  const handleRemoveTopic = (eventCode: string, topicId: string) => {
    console.log('Remove topic:', topicId, 'from event:', eventCode);
    // TODO: Implement topic removal API call
  };

  // Speakers & Sessions handlers
  const handleViewSessionDetails = (sessionId: string) => {
    console.log('View session details:', sessionId);
    // TODO: Implement session details modal
  };

  const handleEditSlot = (sessionId: string) => {
    console.log('Edit slot:', sessionId);
    // TODO: Implement slot editor dialog
  };

  const handleViewMaterials = (sessionId: string) => {
    console.log('View materials for session:', sessionId);
    // TODO: Implement materials viewer
  };

  const handleViewFullAgenda = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/agenda`);
  };

  const handleManageSpeakerAssignments = (eventCode: string) => {
    navigate(`/organizer/events/${eventCode}/speakers`);
  };

  const handleAutoAssignSpeakers = (eventCode: string) => {
    console.log('Auto-assign speakers for event:', eventCode);
    // TODO: Implement AI auto-assign logic
  };

  const handleDeleteEvent = () => {
    console.log('Delete event:', eventCode);
    // TODO: Implement delete confirmation dialog
  };

  // Debug logging
  console.log('[EventDetailEdit] eventCode:', eventCode);
  console.log('[EventDetailEdit] isLoading:', isLoading);
  console.log('[EventDetailEdit] error:', error);
  console.log('[EventDetailEdit] event:', event);

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

  // Use real data from event or fallback to mock data
  const topics: Topic[] = event.topics || [];
  const sessions: Session[] = event.sessions || [];

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
              Event #{event.eventNumber} • {eventCode}
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
            onClick={() => console.log('Event settings')}
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
          </Grid>

          <Button variant="outlined" size="small" sx={{ mt: 2 }} onClick={handleEditEvent}>
            {t('dashboard.actions.edit', 'Edit')}
          </Button>
        </Paper>

        {/* WORKFLOW PROGRESS & KEY METRICS */}
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('dashboard.workflowProgress', 'Workflow Progress')}
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <WorkflowProgressBar
                currentStep={event.workflowStep || 1}
                totalSteps={16}
                currentState={event.workflowState || 'topic_selection'}
              />
              <Button variant="text" size="small" sx={{ mt: 2 }}>
                {t('workflow.viewDetails', 'View Workflow Details')}
              </Button>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 5 }}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('dashboard.keyMetrics', 'Key Metrics')}
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <Stack spacing={1}>
                <Typography variant="body2">
                  📊 {t('metrics.speakers', 'Speakers')}: {event.confirmedSpeakersCount || 0}/12
                  confirmed
                </Typography>
                <Typography variant="body2">
                  ✓ {t('metrics.topics', 'Topics')}: {event.assignedTopicsCount || 0} assigned
                </Typography>
                <Typography variant="body2">
                  ⚠️ {t('metrics.materials', 'Materials')}: {event.pendingMaterialsCount || 0}{' '}
                  pending
                </Typography>
                <Typography variant="body2">
                  📝 {t('metrics.registrations', 'Registrations')}: {event.currentAttendeeCount}/
                  {event.venueCapacity}
                </Typography>
                <Typography variant="body2">
                  💰 {t('metrics.budget', 'Budget')}: CHF {event.budget || '0'}
                </Typography>
              </Stack>
            </Paper>
          </Grid>
        </Grid>

        {/* VENUE & LOGISTICS - Task 11 */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            {t('venue.title', 'Venue & Logistics')}
          </Typography>
          <Divider sx={{ mb: 2 }} />
          <VenueLogistics event={event} onUpdate={handleVenueUpdate} />
        </Paper>

        {/* ASSIGNED TOPICS - Task 12 */}
        <Paper sx={{ p: 3 }}>
          <TopicsList
            topics={topics}
            eventCode={eventCode!}
            onViewTopic={handleViewTopic}
            onRemoveTopic={handleRemoveTopic}
          />
        </Paper>

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
            Lead Organizer: {event.organizerUsername}
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
