/**
 * EventSpeakersTab Component (Story 5.6)
 *
 * Unified speaker management tab with three views:
 * - Kanban: Drag-drop status lanes (from SpeakerStatusDashboard)
 * - Table: List with outreach tracking (from SpeakerOutreachDashboard)
 * - Sessions: Slot-based assignment (from SpeakersSessionsTable)
 *
 * URL params: ?tab=speakers&view=kanban|table|sessions
 */

import React, { useState, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  ToggleButtonGroup,
  ToggleButton,
  LinearProgress,
  Alert,
  IconButton,
  Divider,
  CircularProgress,
  Drawer,
} from '@mui/material';
import {
  ViewKanban as KanbanIcon,
  CalendarMonth as SessionsIcon,
  Add as AddIcon,
  Close as CloseIcon,
  AutoAwesome as AutoAssignIcon,
  Schedule as SlotAssignmentIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { slotAssignmentService } from '@/services/slotAssignmentService/slotAssignmentService';
import { sessionApiClient } from '@/services/api/sessionApiClient';
import { useSpeakerPool } from '@/hooks/useSpeakerPool';
import { useEvent } from '@/hooks/useEvents';
import { useQueryClient } from '@tanstack/react-query';
import { SpeakerStatusLanes } from '@/components/organizer/SpeakerStatus/SpeakerStatusLanes';
import { SpeakersSessionsTable } from '@/components/organizer/EventManagement/SpeakersSessionsTable';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import SpeakerOutreachDetailsDrawer from '@/components/organizer/SpeakerOutreach/SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { SessionUI, SessionSpeaker } from '@/types/event.types';
import type { SessionUpdateData } from '@/components/organizer/EventManagement/SessionEditModal';

type ViewMode = 'kanban' | 'sessions';

interface EventSpeakersTabProps {
  eventCode: string;
}

export const EventSpeakersTab: React.FC<EventSpeakersTabProps> = ({ eventCode }) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { t } = useTranslation(['events', 'organizer']);

  // Get view mode from URL, default to 'kanban'
  const viewParam = searchParams.get('view');
  const currentView: ViewMode = viewParam === 'sessions' ? 'sessions' : 'kanban';

  // Local state
  const [addSpeakerDrawerOpen, setAddSpeakerDrawerOpen] = useState(false);
  const [selectedSpeaker, setSelectedSpeaker] = useState<SpeakerPoolEntry | null>(null);
  const [detailsDrawerOpen, setDetailsDrawerOpen] = useState(false);
  const [autoAssignLoading, setAutoAssignLoading] = useState(false);
  const [autoAssignError, setAutoAssignError] = useState<string | null>(null);

  // Fetch speaker status summary
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['speakerStatusSummary', eventCode],
    queryFn: () => speakerStatusService.getStatusSummary(eventCode),
    refetchInterval: 30000,
    staleTime: 15000,
  });

  // Fetch speaker pool (using hook for proper cache invalidation)
  const { data: speakers, isLoading: speakersLoading } = useSpeakerPool(eventCode);

  // Fetch event data for sessions view
  const { data: event } = useEvent(eventCode, ['sessions']);

  // Transform sessions for SpeakersSessionsTable
  const sessions: SessionUI[] = useMemo(() => {
    if (!event?.sessions) return [];
    return event.sessions.map((session) => {
      const primarySpeaker =
        session.speakers?.find((s: SessionSpeaker) => s.speakerRole === 'PRIMARY_SPEAKER') ||
        session.speakers?.[0];
      return {
        ...session,
        speaker: primarySpeaker
          ? {
              speakerSlug: primarySpeaker.username,
              name: `${primarySpeaker.firstName} ${primarySpeaker.lastName}`,
              company: primarySpeaker.company,
              email: primarySpeaker.username,
              profilePictureUrl: primarySpeaker.profilePictureUrl,
            }
          : undefined,
        materialsStatus: 'pending' as const,
      };
    });
  }, [event?.sessions]);

  // Calculate progress
  const acceptedCount = summary?.acceptedCount || 0;
  const minRequired = summary?.minSlotsRequired || 12;
  const progressPercent = minRequired > 0 ? Math.round((acceptedCount / minRequired) * 100) : 0;
  const thresholdMet = summary?.thresholdMet || false;

  // Handle view change
  const handleViewChange = (_event: React.MouseEvent, newView: ViewMode | null) => {
    if (newView) {
      const newParams = new URLSearchParams(searchParams);
      newParams.set('tab', 'speakers');
      newParams.set('view', newView);
      setSearchParams(newParams, { replace: true });
    }
  };

  // Handle speaker card click (open drawer)
  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setDetailsDrawerOpen(true);
  };

  // Handle IDENTIFIED → CONTACTED transition (auto-open drawer with form)
  const handleIdentifiedToContacted = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setDetailsDrawerOpen(true);
  };

  // Session handlers
  const handleSessionUpdate = async (sessionSlug: string, updates: SessionUpdateData) => {
    // AC3: Save button triggers two API calls
    // 1. PATCH for title/description/duration
    if (updates.title || updates.description !== undefined || updates.durationMinutes) {
      await sessionApiClient.updateSession(eventCode, sessionSlug, {
        title: updates.title,
        description: updates.description,
        durationMinutes: updates.durationMinutes,
      });
    }

    // 2. PATCH for startTime/endTime (with conflict detection)
    if (updates.startTime && updates.endTime) {
      await slotAssignmentService.assignSessionTiming(eventCode, sessionSlug, {
        startTime: updates.startTime,
        endTime: updates.endTime,
      });
    }

    // AC6: After successful save - refresh table with updated session data
    // Errors (including 409 conflicts) propagate to modal for display
    queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
  };

  const handleViewMaterials = (sessionId: string) => {
    console.log('View materials:', sessionId);
  };

  const handleAutoAssignSpeakers = async () => {
    try {
      setAutoAssignLoading(true);
      setAutoAssignError(null);
      const result = await slotAssignmentService.autoAssignTimings(eventCode);
      console.log('Auto-assigned', result.assignedCount, 'sessions');
      // Refresh event data to show updated assignments
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
    } catch (error) {
      console.error('Failed to auto-assign speakers:', error);
      setAutoAssignError(
        error instanceof Error
          ? error.message
          : t('events:speakers.autoAssignError', 'Failed to auto-assign speakers')
      );
    } finally {
      setAutoAssignLoading(false);
    }
  };

  const handleManageSlotAssignments = () => {
    navigate(`/organizer/events/${eventCode}/slot-assignment`);
  };

  // Loading state
  const isLoading = summaryLoading || speakersLoading;

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={3} sx={{ height: '100%' }}>
      {/* Summary Bar */}
      <Paper sx={{ p: 2, flexShrink: 0 }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'stretch', md: 'center' }}
          spacing={2}
        >
          <Box sx={{ flex: 1 }}>
            <Stack direction="row" spacing={2} alignItems="center" mb={1}>
              <Typography variant="subtitle1">
                {t('events:eventPage.speakers.progress', 'Progress')}:{' '}
                <strong>
                  {acceptedCount}/{minRequired}
                </strong>{' '}
                {t('events:eventPage.speakers.confirmed', 'confirmed')}
              </Typography>
              {thresholdMet ? (
                <Alert severity="success" sx={{ py: 0, px: 1 }} icon={false}>
                  ✓ {t('events:eventPage.speakers.thresholdMet', 'Threshold met')}
                </Alert>
              ) : (
                <Alert severity="warning" sx={{ py: 0, px: 1 }} icon={false}>
                  {t('events:eventPage.speakers.needMore', 'Need more speakers')}
                </Alert>
              )}
            </Stack>
            <LinearProgress
              variant="determinate"
              value={Math.min(progressPercent, 100)}
              color={thresholdMet ? 'success' : 'primary'}
              sx={{ height: 8, borderRadius: 1 }}
            />
            <Typography variant="caption" color="text.secondary">
              {summary?.acceptanceRate?.toFixed(1) || 0}%{' '}
              {t('events:eventPage.speakers.acceptanceRate', 'acceptance rate')}
            </Typography>
          </Box>

          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setAddSpeakerDrawerOpen(true)}
          >
            {t('events:eventPage.speakers.addSpeakers', 'Add Speakers')}
          </Button>
        </Stack>
      </Paper>

      {/* Add Speakers Drawer */}
      <Drawer
        anchor="right"
        open={addSpeakerDrawerOpen}
        onClose={() => setAddSpeakerDrawerOpen(false)}
        PaperProps={{
          sx: { width: { xs: '100%', sm: 500 } },
        }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Header */}
          <Box
            sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
          >
            <Typography variant="h6">
              {t('events:eventPage.speakers.addToPool', 'Add Speakers to Pool')}
            </Typography>
            <IconButton onClick={() => setAddSpeakerDrawerOpen(false)} size="small">
              <CloseIcon />
            </IconButton>
          </Box>
          <Divider />

          {/* Content */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            <SpeakerBrainstormingPanel
              eventCode={eventCode}
              showPoolList={false}
              showHeader={false}
            />
          </Box>
        </Box>
      </Drawer>

      {/* View Toggle */}
      <Paper sx={{ p: 2, flexShrink: 0 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'stretch', sm: 'center' }}
          spacing={2}
        >
          <ToggleButtonGroup
            value={currentView}
            exclusive
            onChange={handleViewChange}
            aria-label={t('events:eventPage.speakers.viewMode', 'View mode')}
          >
            <ToggleButton value="kanban" aria-label="Kanban view">
              <KanbanIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.kanban', 'Kanban')}
            </ToggleButton>
            <ToggleButton value="sessions" aria-label="Sessions view">
              <SessionsIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.sessions', 'Sessions')}
            </ToggleButton>
          </ToggleButtonGroup>
        </Stack>
      </Paper>

      {/* View Content */}
      <Box sx={{ flex: 1, minHeight: 0 }}>
        {currentView === 'kanban' && speakers && (
          <SpeakerStatusLanes
            eventCode={eventCode}
            speakers={speakers}
            sessions={sessions}
            onStatusChange={() => {}}
            onIdentifiedToContacted={handleIdentifiedToContacted}
            onSpeakerClick={handleSpeakerClick}
          />
        )}

        {currentView === 'sessions' && (
          <Paper sx={{ p: 2 }}>
            <SpeakersSessionsTable
              sessions={sessions}
              eventCode={eventCode}
              eventDate={event?.date || ''}
              onViewMaterials={handleViewMaterials}
              onSessionUpdate={handleSessionUpdate}
            />
          </Paper>
        )}
      </Box>

      {/* Action Buttons (Sessions View Only) */}
      {currentView === 'sessions' && (
        <Paper sx={{ p: 2, flexShrink: 0 }}>
          <Stack direction="row" spacing={2} justifyContent="flex-end">
            <Button
              variant="contained"
              startIcon={<SlotAssignmentIcon />}
              onClick={handleManageSlotAssignments}
            >
              {t('events:speakers.manageSlotAssignments', 'Slot Assignment')}
            </Button>
            <Button
              variant="outlined"
              startIcon={<AutoAssignIcon />}
              onClick={handleAutoAssignSpeakers}
              disabled={autoAssignLoading}
            >
              {autoAssignLoading
                ? t('events:speakers.autoAssigning', 'Auto-Assigning...')
                : t('events:speakers.autoAssignSpeakers', 'Auto-Assign Slots')}
            </Button>
          </Stack>
          {autoAssignError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {autoAssignError}
            </Alert>
          )}
        </Paper>
      )}

      {/* Speaker Details Drawer */}
      <SpeakerOutreachDetailsDrawer
        open={detailsDrawerOpen}
        onClose={() => setDetailsDrawerOpen(false)}
        speaker={selectedSpeaker}
        eventCode={eventCode}
        showMarkContactedForm={
          selectedSpeaker?.status === 'IDENTIFIED' || selectedSpeaker?.status === 'CONTACTED'
        }
      />
    </Stack>
  );
};

export default EventSpeakersTab;
