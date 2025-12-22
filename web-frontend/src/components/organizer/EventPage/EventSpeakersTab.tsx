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
  Collapse,
  IconButton,
  Divider,
  CircularProgress,
} from '@mui/material';
import {
  ViewKanban as KanbanIcon,
  TableRows as TableIcon,
  CalendarMonth as SessionsIcon,
  Add as AddIcon,
  Close as CloseIcon,
  AutoAwesome as AutoAssignIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { speakerPoolService } from '@/services/speakerPoolService';
import { useEvent } from '@/hooks/useEvents';
import { SpeakerStatusLanes } from '@/components/organizer/SpeakerStatus/SpeakerStatusLanes';
import { SpeakersSessionsTable } from '@/components/organizer/EventManagement/SpeakersSessionsTable';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import SpeakerOutreachDetailsDrawer from '@/components/organizer/SpeakerOutreach/SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { SessionUI, SessionSpeaker } from '@/types/event.types';

type ViewMode = 'kanban' | 'table' | 'sessions';

interface EventSpeakersTabProps {
  eventCode: string;
}

export const EventSpeakersTab: React.FC<EventSpeakersTabProps> = ({ eventCode }) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation(['events', 'organizer']);

  // Get view mode from URL, default to 'kanban'
  const viewParam = searchParams.get('view');
  const currentView: ViewMode =
    viewParam === 'table' || viewParam === 'sessions' ? viewParam : 'kanban';

  // Local state
  const [addPanelOpen, setAddPanelOpen] = useState(false);
  const [selectedSpeaker, setSelectedSpeaker] = useState<SpeakerPoolEntry | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Fetch speaker status summary
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['speakerStatusSummary', eventCode],
    queryFn: () => speakerStatusService.getStatusSummary(eventCode),
    refetchInterval: 30000,
    staleTime: 15000,
  });

  // Fetch speaker pool
  const { data: speakers, isLoading: speakersLoading } = useQuery({
    queryKey: ['speakerPool', eventCode],
    queryFn: () => speakerPoolService.getSpeakerPool(eventCode),
    refetchInterval: 30000,
    staleTime: 15000,
  });

  // Fetch event data for sessions view
  const { data: event } = useEvent(eventCode, ['sessions']);

  // Transform sessions for SpeakersSessionsTable
  const sessions: SessionUI[] = useMemo(() => {
    if (!event?.sessions) return [];
    return event.sessions.map((session, index) => {
      const primarySpeaker =
        session.speakers?.find((s: SessionSpeaker) => s.speakerRole === 'PRIMARY_SPEAKER') ||
        session.speakers?.[0];
      return {
        ...session,
        slotNumber: index + 1,
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
    setDrawerOpen(true);
  };

  // Session handlers
  const handleEditSlot = (sessionId: string) => {
    console.log('Edit slot:', sessionId);
  };

  const handleViewMaterials = (sessionId: string) => {
    console.log('View materials:', sessionId);
  };

  const handleViewFullAgenda = () => {
    navigate(`/organizer/events/${eventCode}/agenda`);
  };

  const handleManageSpeakerAssignments = () => {
    // Switch to kanban view
    const newParams = new URLSearchParams(searchParams);
    newParams.set('view', 'kanban');
    setSearchParams(newParams, { replace: true });
  };

  const handleManageSpeakerOutreach = () => {
    // Switch to table view
    const newParams = new URLSearchParams(searchParams);
    newParams.set('view', 'table');
    setSearchParams(newParams, { replace: true });
  };

  const handleAutoAssignSpeakers = () => {
    console.log('Auto-assign speakers for:', eventCode);
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
    <Stack spacing={3}>
      {/* Summary Bar */}
      <Paper sx={{ p: 2 }}>
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
            onClick={() => setAddPanelOpen(!addPanelOpen)}
          >
            {addPanelOpen
              ? t('events:eventPage.speakers.hideAdd', 'Hide')
              : t('events:eventPage.speakers.addSpeakers', 'Add Speakers')}
          </Button>
        </Stack>
      </Paper>

      {/* Add Speakers Panel (Collapsible) */}
      <Collapse in={addPanelOpen}>
        <Paper sx={{ p: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">
              {t('events:eventPage.speakers.addToPool', 'Add Speakers to Pool')}
            </Typography>
            <IconButton size="small" onClick={() => setAddPanelOpen(false)}>
              <CloseIcon />
            </IconButton>
          </Stack>
          <Divider sx={{ mb: 2 }} />
          <SpeakerBrainstormingPanel
            eventCode={eventCode}
            organizers={[]}
            showPoolList={false}
            showHeader={false}
          />
        </Paper>
      </Collapse>

      {/* View Toggle */}
      <Paper sx={{ p: 2 }}>
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
            <ToggleButton value="table" aria-label="Table view">
              <TableIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.table', 'Table')}
            </ToggleButton>
            <ToggleButton value="sessions" aria-label="Sessions view">
              <SessionsIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.sessions', 'Sessions')}
            </ToggleButton>
          </ToggleButtonGroup>

          {currentView === 'sessions' && (
            <Button
              variant="outlined"
              startIcon={<AutoAssignIcon />}
              onClick={handleAutoAssignSpeakers}
            >
              {t('events:speakers.autoAssignSpeakers', 'Auto-Assign')}
            </Button>
          )}
        </Stack>
      </Paper>

      {/* View Content */}
      <Box>
        {currentView === 'kanban' && speakers && (
          <SpeakerStatusLanes eventCode={eventCode} speakers={speakers} onStatusChange={() => {}} />
        )}

        {currentView === 'table' && speakers && (
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              {t('organizer:speakerOutreach.title', 'Speaker Outreach')}
            </Typography>
            <Divider sx={{ mb: 2 }} />

            {/* Simple table view of speakers with outreach info */}
            <Box sx={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th
                      style={{
                        textAlign: 'left',
                        padding: '12px 8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {t('organizer:speakerBrainstorm.form.speakerName', 'Speaker')}
                    </th>
                    <th
                      style={{
                        textAlign: 'left',
                        padding: '12px 8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {t('organizer:speakerBrainstorm.form.company', 'Company')}
                    </th>
                    <th
                      style={{
                        textAlign: 'left',
                        padding: '12px 8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {t('common:status', 'Status')}
                    </th>
                    <th
                      style={{
                        textAlign: 'left',
                        padding: '12px 8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {t('organizer:speakerOutreach.assigned', 'Assigned To')}
                    </th>
                    <th
                      style={{
                        textAlign: 'right',
                        padding: '12px 8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {t('common:actions', 'Actions')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {speakers.map((speaker) => (
                    <tr
                      key={speaker.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => handleSpeakerClick(speaker)}
                    >
                      <td style={{ padding: '12px 8px', borderBottom: '1px solid #eee' }}>
                        <Typography variant="body2" fontWeight="medium">
                          {speaker.speakerName}
                        </Typography>
                      </td>
                      <td style={{ padding: '12px 8px', borderBottom: '1px solid #eee' }}>
                        <Typography variant="body2" color="text.secondary">
                          {speaker.company || '-'}
                        </Typography>
                      </td>
                      <td style={{ padding: '12px 8px', borderBottom: '1px solid #eee' }}>
                        <Typography
                          variant="body2"
                          sx={{
                            display: 'inline-block',
                            px: 1,
                            py: 0.25,
                            borderRadius: 1,
                            bgcolor:
                              speaker.status === 'ACCEPTED'
                                ? 'success.light'
                                : speaker.status === 'DECLINED'
                                  ? 'error.light'
                                  : 'grey.200',
                            color:
                              speaker.status === 'ACCEPTED'
                                ? 'success.dark'
                                : speaker.status === 'DECLINED'
                                  ? 'error.dark'
                                  : 'text.primary',
                          }}
                        >
                          {speaker.status}
                        </Typography>
                      </td>
                      <td style={{ padding: '12px 8px', borderBottom: '1px solid #eee' }}>
                        <Typography variant="body2" color="text.secondary">
                          {speaker.assignedOrganizerId || '-'}
                        </Typography>
                      </td>
                      <td
                        style={{
                          padding: '12px 8px',
                          borderBottom: '1px solid #eee',
                          textAlign: 'right',
                        }}
                      >
                        <Button size="small" variant="text">
                          {t('common:viewDetails', 'Details')}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Box>

            {speakers.length === 0 && (
              <Alert severity="info" sx={{ mt: 2 }}>
                {t('events:eventPage.speakers.noSpeakers', 'No speakers in pool yet.')}
              </Alert>
            )}
          </Paper>
        )}

        {currentView === 'sessions' && (
          <Paper sx={{ p: 2 }}>
            <SpeakersSessionsTable
              sessions={sessions}
              eventCode={eventCode}
              onEditSlot={handleEditSlot}
              onViewMaterials={handleViewMaterials}
              onViewFullAgenda={handleViewFullAgenda}
              onManageSpeakerAssignments={handleManageSpeakerAssignments}
              onManageSpeakerOutreach={handleManageSpeakerOutreach}
              onAutoAssignSpeakers={handleAutoAssignSpeakers}
              onSessionUpdate={async () => {}}
            />
          </Paper>
        )}
      </Box>

      {/* Speaker Details Drawer */}
      <SpeakerOutreachDetailsDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        speaker={selectedSpeaker}
        eventCode={eventCode}
      />
    </Stack>
  );
};

export default EventSpeakersTab;
