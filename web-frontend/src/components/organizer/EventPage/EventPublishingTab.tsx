/**
 * EventPublishingTab Component (Story 5.7 - Updated)
 *
 * Progressive publishing controls with validation, timeline, and version control
 */

import React from 'react';
import { Stack, Alert, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { Event, EventDetailUI, PublishingPhase } from '@/types/event.types';
import { ValidationDashboard } from '@/components/Publishing/ValidationDashboard/ValidationDashboard';
import { PublishingControls } from '@/components/Publishing/PublishingControls/PublishingControls';
import { LivePreview } from '@/components/Publishing/LivePreview/LivePreview';
import { PublishingTimeline } from '@/components/Publishing/PublishingTimeline/PublishingTimeline';
import { VersionControl } from '@/components/Publishing/VersionControl/VersionControl';

interface EventPublishingTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

export const EventPublishingTab: React.FC<EventPublishingTabProps> = ({ event, eventCode }) => {
  const { t } = useTranslation('events');

  // TODO: Replace with real API call to get publishing status
  // For now, use mock data based on event state
  const currentPhase: PublishingPhase = 'speakers'; // TODO: Get from event.currentPublishedPhase
  const publishingMode = 'progressive' as const;

  // TODO: Replace with real validation data from API
  const validationData = {
    topic: {
      isValid: true,
      errors: [],
    },
    speakers: {
      isValid: true,
      errors: [],
    },
    sessions: {
      isValid: false,
      errors: ['Some sessions do not have timings assigned'],
      assignedCount: 3,
      totalCount: 5,
      unassignedSessions: [
        { sessionSlug: 'session-4', title: 'Pending Session 1' },
        { sessionSlug: 'session-5', title: 'Pending Session 2' },
      ],
    },
  };

  // TODO: Replace with real data from event
  const publishedPhases: PublishingPhase[] = ['topic', 'speakers'];
  const eventDate = event.date || new Date().toISOString();

  return (
    <Stack spacing={3}>
      {/* Backend Integration Notice */}
      <Alert severity="info">
        <Typography variant="body2" fontWeight="bold">
          {t('publishing.tab.title')}
        </Typography>
        <Typography variant="body2">{t('publishing.tab.notice')}</Typography>
      </Alert>

      {/* Validation Dashboard - Shows content validation status */}
      <ValidationDashboard eventCode={eventCode} phase={currentPhase} validation={validationData} />

      {/* Publishing Controls - Phase publishing buttons */}
      <PublishingControls eventCode={eventCode} currentPhase={currentPhase} validationErrors={[]} />

      {/* Publishing Timeline - Visual timeline of phases */}
      <PublishingTimeline
        eventCode={eventCode}
        currentPhase={currentPhase}
        publishedPhases={publishedPhases}
        eventDate={eventDate}
      />

      {/* Live Preview - Preview published content */}
      <LivePreview eventCode={eventCode} phase={currentPhase} mode={publishingMode} />

      {/* Version Control - Publishing history and rollback */}
      <VersionControl eventCode={eventCode} />
    </Stack>
  );
};

export default EventPublishingTab;
