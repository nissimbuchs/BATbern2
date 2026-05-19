/**
 * EventSpeakersTab Component (Story 5.6)
 *
 * Unified speaker management tab with three views:
 * - Kanban: Drag-drop status lanes (SpeakerStatusLanes)
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
  Drawer,
  Snackbar,
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
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useQuery } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { slotAssignmentService } from '@/services/slotAssignmentService/slotAssignmentService';
import { sessionApiClient } from '@/services/api/sessionApiClient';
import { speakerPoolKeys, useSendInvitation, useSpeakerPool } from '@/hooks/useSpeakerPool';
import { useEvent } from '@/hooks/useEvents';
import { useQueryClient } from '@tanstack/react-query';
import { SpeakerStatusLanes } from '@/components/organizer/SpeakerStatus/SpeakerStatusLanes';
import { computeSlotCapacity } from '@/components/organizer/SpeakerStatus/getPrimaryAction';
import { SpeakersSessionsTable } from '@/components/organizer/EventManagement/SpeakersSessionsTable';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import { SpeakerDetailDrawer } from '@/components/organizer/SpeakerDrawer';
import MarkContactedModal from '@/components/organizer/SpeakerOutreach/MarkContactedModal';
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
  // Story 11.D.4 — when the kanban dispatches `legal-input` for ACCEPTED→CONTENT_SUBMITTED
  // or CONTENT_SUBMITTED→QUALITY_REVIEWED, the drawer must open pre-positioned at the
  // matching sub-view. The drawer reads this prop on each `speakerKey` change.
  // Epic 11 bug fix 2026-05-18 — `'promote'` added (replaces the legacy PromoteSpeakerDialog);
  // `'content-submission'` now opens the drawer at the Content TAB (not a takeover view).
  const [initialDrawerView, setInitialDrawerView] = useState<
    null | 'content-submission' | 'quality-review' | 'promote'
  >(null);
  const [autoAssignLoading, setAutoAssignLoading] = useState(false);
  const [autoAssignError, setAutoAssignError] = useState<string | null>(null);
  // Story 11.D.2 — hoist modal state so kanban primary-action buttons can drive it.
  const [outreachModalState, setOutreachModalState] = useState<{
    open: boolean;
    speaker: SpeakerPoolEntry | null;
  }>({ open: false, speaker: null });
  // Story 11.D.4 — send-invitation feedback snackbar lifted to the parent.
  const [inviteSnackbar, setInviteSnackbar] = useState<{
    open: boolean;
    severity: 'success' | 'error';
    message: string;
  }>({ open: false, severity: 'success', message: '' });

  const sendInvitationMutation = useSendInvitation(eventCode);

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

  // Story 11.D.4 review patch — compute slot-capacity once at the parent so both the
  // kanban (SpeakerStatusLanes) and the drawer (SpeakerDetailDrawer) honor the same
  // gate. Prevents the drawer's READY "Send invitation" button from being enabled
  // while the kanban gate is reached.
  const slotCapacity = useMemo(
    () => computeSlotCapacity(speakers ?? [], summary?.maxSlotsAllowed),
    [speakers, summary?.maxSlotsAllowed]
  );

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
        // materialsStatus is already included in ...session from backend (Story 5.9)
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

  // Handle speaker card click (open drawer — default view, no sub-view).
  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setInitialDrawerView(null);
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.2 — IDENTIFIED card primary-action button.
  const handleLogOutreach = (speaker: SpeakerPoolEntry) => {
    setOutreachModalState({ open: true, speaker });
  };

  // Epic 11 bug fix 2026-05-18 — CONTACTED card primary-action button now opens the
  // drawer at the in-drawer Promote sub-view (with UserAutocomplete + Create-New-Speaker)
  // instead of the legacy PromoteSpeakerDialog modal.
  const handlePromoteSpeaker = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setInitialDrawerView('promote');
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.4 — READY card primary-action button + READY→INVITED drag target.
  // Lifted from SpeakerCard so the kanban-drop dispatcher, card button, and the
  // drawer's PrimaryActionSurface share the same handler.
  const handleSendInvitation = async (speaker: SpeakerPoolEntry) => {
    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 30);
    const responseDeadline = defaultDeadline.toISOString().split('T')[0];

    try {
      const result = await sendInvitationMutation.mutateAsync({
        username: speaker.id,
        options: { responseDeadline },
      });
      setInviteSnackbar({
        open: true,
        severity: 'success',
        // Review patch — include {{email}} in the fallback so the address survives a
        // missing translation key (otherwise the snackbar showed the generic string
        // and silently dropped the address).
        message: t('organizer:speakers.invitationSent', {
          email: result.email,
          defaultValue: 'Invitation sent to {{email}}',
        }),
      });
    } catch {
      setInviteSnackbar({
        open: true,
        severity: 'error',
        message: t('organizer:speakers.invitationFailed', {
          defaultValue: 'Failed to send invitation',
        }),
      });
    }
  };

  // Story 11.D.4 — ACCEPTED card primary-action button + ACCEPTED→CONTENT_SUBMITTED drag target.
  // Opens the drawer pre-positioned at the on-behalf Content sub-tab.
  const handleEnterContent = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setInitialDrawerView('content-submission');
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.4 — CONTENT_SUBMITTED card primary-action button + drag target.
  // Opens the drawer pre-positioned at the Quality Review sub-view.
  const handleReviewContent = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeaker(speaker);
    setInitialDrawerView('quality-review');
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.2 — QUALITY_REVIEWED-no-slot card primary-action button.
  // Speaker context is forwarded via `?speakerId=` so the slot-assignment page can
  // optionally focus/highlight the originating speaker. The page is non-breaking if the
  // query param is unread.
  const handleAssignSessionSlotForSpeaker = (speaker: SpeakerPoolEntry) => {
    navigate(`/organizer/events/${eventCode}/slot-assignment?speakerId=${speaker.id}`);
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

  const handleSessionDelete = async (sessionSlug: string) => {
    await sessionApiClient.deleteSession(eventCode, sessionSlug);
    queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
    queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
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
        <BATbernLoader size={96} />
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
            <ToggleButton
              value="kanban"
              aria-label={t('events:eventPage.speakers.kanbanView', 'Kanban view')}
              data-testid="kanban-view-toggle"
            >
              <KanbanIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.kanban', 'Kanban')}
            </ToggleButton>
            <ToggleButton
              value="sessions"
              aria-label={t('events:eventPage.speakers.sessionsView', 'Sessions view')}
              data-testid="sessions-view-toggle"
            >
              <SessionsIcon sx={{ mr: 1 }} />
              {t('common:labels.sessions')}
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
            maxSlots={summary?.maxSlotsAllowed}
            eventDate={event?.date}
            onStatusChange={() => {}}
            onSpeakerClick={handleSpeakerClick}
            onLogOutreach={handleLogOutreach}
            onPromoteSpeaker={handlePromoteSpeaker}
            onSendInvitation={handleSendInvitation}
            onEnterContent={handleEnterContent}
            onReviewContent={handleReviewContent}
            onAssignSessionSlot={handleAssignSessionSlotForSpeaker}
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
              onSessionDelete={handleSessionDelete}
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
              data-testid="manage-slot-assignments-button"
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

      {/* Speaker Detail Drawer — Story 11.D.4 redesigned: 2 tabs (Details + History) +
          primary-action header strip + secondary-actions list + Content sub-tab chip.
          Review patch (Resolved Q#1): rich-modal callbacks + slotCapacity threaded down
          so the drawer's override-state popover and PrimaryActionSurface honor the same
          modals + gate the kanban surfaces. */}
      <SpeakerDetailDrawer
        open={detailsDrawerOpen}
        onClose={() => setDetailsDrawerOpen(false)}
        speaker={selectedSpeaker}
        eventCode={eventCode}
        initialDrawerView={initialDrawerView}
        slotCapacity={slotCapacity}
        onLogOutreach={handleLogOutreach}
        onPromoteSpeaker={handlePromoteSpeaker}
        onSendInvitation={handleSendInvitation}
        onEnterContent={handleEnterContent}
        onReviewContent={handleReviewContent}
      />

      {/* Story 11.D.4 — invitation feedback (lifted from SpeakerCard so drag + click share). */}
      <Snackbar
        open={inviteSnackbar.open}
        autoHideDuration={4000}
        onClose={() => setInviteSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setInviteSnackbar((s) => ({ ...s, open: false }))}
          severity={inviteSnackbar.severity}
          sx={{ width: '100%' }}
        >
          {inviteSnackbar.message}
        </Alert>
      </Snackbar>

      {/* Story 11.D.2 — Hoisted modal: MarkContactedModal driven by IDENTIFIED card button */}
      {outreachModalState.speaker && (
        <MarkContactedModal
          open={outreachModalState.open}
          onClose={() => setOutreachModalState({ open: false, speaker: null })}
          onSuccess={() => {
            setOutreachModalState({ open: false, speaker: null });
            queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
            queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
          }}
          eventCode={eventCode}
          speakerId={outreachModalState.speaker.id}
          speakerName={outreachModalState.speaker.speakerName}
        />
      )}
    </Stack>
  );
};

export default EventSpeakersTab;
