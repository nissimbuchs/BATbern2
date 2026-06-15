/**
 * EventPublishingTab Component (Story 5.7 - Updated)
 *
 * Progressive publishing controls with validation, timeline, and version control
 * Now uses real data from usePublishing hook instead of mocked data
 */

import React from 'react';
import { Stack, Skeleton, Box, Typography, Paper } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI, PublishingPhase } from '@/types/event.types';
import { ValidationDashboard } from '@/components/Publishing/ValidationDashboard/ValidationDashboard';
import { PublishingControls } from '@/components/Publishing/PublishingControls/PublishingControls';
import { LivePreview } from '@/components/Publishing/LivePreview/LivePreview';
import { PublishingTimeline } from '@/components/Publishing/PublishingTimeline/PublishingTimeline';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';

interface EventPublishingTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

export const EventPublishingTab: React.FC<EventPublishingTabProps> = ({ event, eventCode }) => {
  const { t } = useTranslation('events');
  const { publishingStatus, isLoadingStatus, validationErrors } = usePublishing(eventCode);
  const { unassignedSessions } = useSlotAssignment(eventCode);

  // Loading state
  if (isLoadingStatus) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={200} />
        <Skeleton variant="rectangular" height={150} />
        <Skeleton variant="rectangular" height={100} />
        <Skeleton variant="rectangular" height={300} />
      </Stack>
    );
  }

  // Extract data from status response, with fallbacks
  // Convert uppercase phase from API to lowercase (API returns 'SPEAKERS', we need 'speakers')
  const currentPhase: PublishingPhase =
    (publishingStatus?.currentPhase?.toLowerCase() as PublishingPhase) || 'topic';
  const publishedPhases: PublishingPhase[] =
    publishingStatus?.publishedPhases?.map((p) => p.toLowerCase() as PublishingPhase) || [];
  const eventDate = event.date || new Date().toISOString();

  // Build validation data from status response and slot assignment
  const mappedUnassignedSessions =
    unassignedSessions?.map((session) => ({
      sessionSlug: session.sessionSlug,
      title: session.title || session.sessionSlug,
    })) || [];

  // Frontend safeguard: Check if event actually has a topic
  const hasTopicCode = 'topicCode' in event && event.topicCode;
  const topicValidation = publishingStatus?.topic || { isValid: true, errors: [] };

  const validationData = {
    topic: {
      ...topicValidation,
      // Override if backend incorrectly reports valid but no topicCode exists
      isValid: !!(topicValidation.isValid && hasTopicCode),
      errors:
        !hasTopicCode && topicValidation.isValid
          ? ['Event topic must be defined']
          : topicValidation.errors,
    },
    speakers: publishingStatus?.speakers || { isValid: true, errors: [] },
    sessions: {
      ...(publishingStatus?.sessions || {
        isValid: true,
        errors: [],
        assignedCount: 0,
        totalCount: 0,
      }),
      // Override isValid: only valid if no unassigned sessions AND there are sessions total
      isValid:
        mappedUnassignedSessions.length === 0 && (publishingStatus?.sessions?.totalCount || 0) > 0,
      // Use actual unassigned sessions from slot assignment hook
      unassignedSessions: mappedUnassignedSessions,
    },
  };

  return (
    <Stack spacing={3}>
      {/* Top row: Validation · Publishing phases · Publish actions. On desktop a
          40/40/20 three-column grid (publish buttons stay beside the phases); on
          mobile the columns stack full-width (Phase G — three columns don't fit a phone). */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            md: 'minmax(0, 2fr) minmax(0, 2fr) minmax(0, 1fr)',
          },
          gap: 3,
          alignItems: 'stretch',
        }}
      >
        {/* Validation Dashboard - Shows content validation status */}
        <Box
          data-testid="validation-dashboard-container"
          sx={{ display: 'flex', flexDirection: 'column' }}
        >
          <Typography variant="subtitle2" gutterBottom>
            {t('publishing.sections.validation', 'Validation')}
          </Typography>
          <ValidationDashboard
            eventCode={eventCode}
            phase={currentPhase}
            validation={validationData}
          />
        </Box>

        {/* Publishing Timeline - Visual timeline of phases */}
        <Box
          data-testid="publishing-timeline-container"
          sx={{ display: 'flex', flexDirection: 'column' }}
        >
          <Typography variant="subtitle2" gutterBottom>
            {t('publishing.sections.phases', 'Publishing phase')}
          </Typography>
          <Paper sx={{ p: 2, flexGrow: 1 }}>
            <PublishingTimeline
              eventCode={eventCode}
              currentPhase={currentPhase}
              publishedPhases={publishedPhases}
              eventDate={eventDate}
            />
          </Paper>
        </Box>

        {/* Publishing Controls - phase publish buttons, stacked vertically */}
        <Box
          data-testid="publishing-controls-container"
          sx={{ display: 'flex', flexDirection: 'column' }}
        >
          <Typography variant="subtitle2" gutterBottom>
            {t('publishing.sections.publishNext', 'Publish next phase')}
          </Typography>
          <Paper sx={{ p: 2, flexGrow: 1 }}>
            <PublishingControls
              eventCode={eventCode}
              currentPhase={currentPhase}
              validationErrors={validationErrors}
            />
          </Paper>
        </Box>
      </Box>

      {/* Live Preview - full width below the three columns */}
      <Box data-testid="live-preview-container">
        <LivePreview eventCode={eventCode} phase={currentPhase} />
      </Box>
    </Stack>
  );
};

export default EventPublishingTab;
