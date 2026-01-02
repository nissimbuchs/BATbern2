/**
 * EventPublishingTab Component (Story 5.7 - Updated)
 *
 * Progressive publishing controls with validation, timeline, and version control
 * Now uses real data from usePublishing hook instead of mocked data
 */

import React from 'react';
import { Stack, Skeleton, Box } from '@mui/material';
import type { Event, EventDetailUI, PublishingPhase } from '@/types/event.types';
import { ValidationDashboard } from '@/components/Publishing/ValidationDashboard/ValidationDashboard';
import { PublishingControls } from '@/components/Publishing/PublishingControls/PublishingControls';
import { LivePreview } from '@/components/Publishing/LivePreview/LivePreview';
import { PublishingTimeline } from '@/components/Publishing/PublishingTimeline/PublishingTimeline';
import { VersionControl } from '@/components/Publishing/VersionControl/VersionControl';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';

interface EventPublishingTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

export const EventPublishingTab: React.FC<EventPublishingTabProps> = ({ event, eventCode }) => {
  const { publishingStatus, isLoadingStatus, validationErrors } = usePublishing(eventCode);
  const { unassignedSessions } = useSlotAssignment(eventCode);

  const publishingMode = 'progressive' as const;

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
  const currentPhase: PublishingPhase =
    (publishingStatus?.currentPhase as PublishingPhase) || 'topic';
  const publishedPhases: PublishingPhase[] =
    (publishingStatus?.publishedPhases as PublishingPhase[]) || [];
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
      isValid: topicValidation.isValid && hasTopicCode,
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
      {/* Validation Dashboard - Shows content validation status */}
      <Box data-testid="validation-dashboard-container">
        <ValidationDashboard
          eventCode={eventCode}
          phase={currentPhase}
          validation={validationData}
        />
      </Box>

      {/* Publishing Controls - Phase publishing buttons */}
      <Box data-testid="publishing-controls-container">
        <PublishingControls
          eventCode={eventCode}
          currentPhase={currentPhase}
          validationErrors={validationErrors}
        />
      </Box>

      {/* Publishing Timeline - Visual timeline of phases */}
      <Box data-testid="publishing-timeline-container">
        <PublishingTimeline
          eventCode={eventCode}
          currentPhase={currentPhase}
          publishedPhases={publishedPhases}
          eventDate={eventDate}
        />
      </Box>

      {/* Live Preview - Preview published content */}
      <Box data-testid="live-preview-container">
        <LivePreview eventCode={eventCode} phase={currentPhase} mode={publishingMode} />
      </Box>

      {/* Version Control - Publishing history and rollback */}
      <Box data-testid="version-control-container">
        <VersionControl eventCode={eventCode} />
      </Box>
    </Stack>
  );
};

export default EventPublishingTab;
