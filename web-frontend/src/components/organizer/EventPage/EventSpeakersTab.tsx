/**
 * EventSpeakersTab Component (Story 5.6 · Epic 14 Phase C)
 *
 * Unified Speakers & Agenda tab with three sub-views (Epic 14 Phase C, 14.C.1):
 * - Pool:   4-phase speaker kanban (SpeakerStatusLanes)
 * - Agenda: editable session table + "N of M need a slot" summary (SpeakersSessionsTable)
 * - Slots:  in-tab slot assignment (DragDropSlotAssignment) — replaces the retired
 *           /slot-assignment route (14.C.5)
 *
 * URL params: ?tab=speakers&view=pool|agenda|slots  (legacy kanban→pool, sessions→agenda).
 * The keys pool|agenda|slots are the stable Cockpit deep-link contract (cockpitCards.ts).
 */

import React, { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  ViewKanban as PoolIcon,
  CalendarMonth as AgendaIcon,
  Add as AddIcon,
  Close as CloseIcon,
  Schedule as SlotsIcon,
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
import { DragDropSlotAssignment } from '@/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import { SpeakerDetailDrawer } from '@/components/organizer/SpeakerDrawer';
import MarkContactedModal from '@/components/organizer/SpeakerOutreach/MarkContactedModal';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { SessionUI, SessionSpeaker } from '@/types/event.types';
import type { SessionUpdateData } from '@/components/organizer/EventManagement/SessionEditModal';

type ViewMode = 'pool' | 'agenda' | 'slots';

// Map the URL ?view= param to a sub-view. The stable keys are pool|agenda|slots
// (the Cockpit deep-link contract). Legacy values are tolerated so old links/bookmarks
// don't dead-end: kanban→pool, sessions→agenda.
const resolveViewMode = (viewParam: string | null): ViewMode => {
  if (viewParam === 'agenda' || viewParam === 'sessions') return 'agenda';
  if (viewParam === 'slots') return 'slots';
  return 'pool'; // 'pool', 'kanban', and absent all land on the kanban
};

interface EventSpeakersTabProps {
  eventCode: string;
}

export const EventSpeakersTab: React.FC<EventSpeakersTabProps> = ({ eventCode }) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { t } = useTranslation(['events', 'organizer']);

  // Get view mode from URL, default to 'pool' (the kanban). 14.C.1.
  const currentView: ViewMode = resolveViewMode(searchParams.get('view'));
  // Speaker context carried into the Slots sub-view (FR17 "⚠ Needs a slot →" jump and
  // the retired-route redirect). Matched best-effort against session.speakers[].username.
  const focusSpeakerId = searchParams.get('speakerId') ?? undefined;

  // Local state
  const [addSpeakerDrawerOpen, setAddSpeakerDrawerOpen] = useState(false);
  // Track the SELECTED speaker by id, not by snapshot — Epic 11 bug fix 2026-05-19.
  // Storing the full SpeakerPoolEntry object captured the value at click-time and went
  // stale after edits/promotions invalidated the speakerPool query; the live entry
  // (drawer header, Details tab, Content tab) silently rendered the pre-mutation
  // snapshot. The drawer now reads `selectedSpeaker` derived from the live `speakers`
  // list, so cache invalidations propagate automatically.
  const [selectedSpeakerId, setSelectedSpeakerId] = useState<string | null>(null);
  const [detailsDrawerOpen, setDetailsDrawerOpen] = useState(false);
  // Story 11.D.4 — when the kanban dispatches `legal-input` for ACCEPTED→CONTENT_SUBMITTED
  // or CONTENT_SUBMITTED→QUALITY_REVIEWED, the drawer must open pre-positioned at the
  // matching sub-view. The drawer reads this prop on each `speakerKey` change.
  // Epic 11 bug fix 2026-05-18 — `'promote'` added (replaces the legacy PromoteSpeakerDialog);
  // `'content-submission'` now opens the drawer at the Content TAB (not a takeover view).
  const [initialDrawerView, setInitialDrawerView] = useState<
    null | 'content-submission' | 'quality-review' | 'promote'
  >(null);
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

  // Derive the live drawer-selected speaker from the speakerPool query (Epic 11 bug fix
  // 2026-05-19). Looking up by id at every render means cache invalidations from
  // patchSpeakerPool / promoteSpeakerToReady / updateStatus propagate into the drawer's
  // header chip + Details + Content tabs immediately — no manual refresh after edits.
  const selectedSpeaker = useMemo(
    () => (selectedSpeakerId ? (speakers?.find((s) => s.id === selectedSpeakerId) ?? null) : null),
    [speakers, selectedSpeakerId]
  );

  // Fetch event data for sessions view
  const { data: event } = useEvent(eventCode, ['sessions']);

  // Story 11.E.8 follow-up — invalidate the event query whenever the speaker detail
  // drawer opens. The drawer's primary use is "I'm about to act on this speaker"
  // (review content, enter on behalf, send invitation), so the organizer expects to
  // see the latest data. Without this, the Sessions sub-tab kept showing pre-submit
  // titles until a quality-review approval invalidated the cache. Pairs with
  // refetchOnWindowFocus on useEvent.
  useEffect(() => {
    if (detailsDrawerOpen) {
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
    }
  }, [detailsDrawerOpen, eventCode, queryClient]);

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

  // Switch sub-view in-tab via the ?view= param — no route change (14.C.1/14.C.5).
  // When switching to Slots with a speaker context, carry it as ?speakerId= so the
  // tray can highlight that speaker's unassigned session (FR17); otherwise clear it.
  const switchToView = (view: ViewMode, speakerContext?: string) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('tab', 'speakers');
    newParams.set('view', view);
    if (view === 'slots' && speakerContext) {
      newParams.set('speakerId', speakerContext);
    } else {
      newParams.delete('speakerId');
    }
    setSearchParams(newParams, { replace: true });
  };

  // Handle view change from the Pool/Agenda/Slots toggle.
  const handleViewChange = (_event: React.MouseEvent, newView: ViewMode | null) => {
    if (newView) {
      switchToView(newView);
    }
  };

  // Handle speaker card click (open drawer — default view, no sub-view).
  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeakerId(speaker.id);
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
    setSelectedSpeakerId(speaker.id);
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
    setSelectedSpeakerId(speaker.id);
    setInitialDrawerView('content-submission');
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.4 — CONTENT_SUBMITTED card primary-action button + drag target.
  // Opens the drawer pre-positioned at the Quality Review sub-view.
  const handleReviewContent = (speaker: SpeakerPoolEntry) => {
    setSelectedSpeakerId(speaker.id);
    setInitialDrawerView('quality-review');
    setDetailsDrawerOpen(true);
  };

  // Story 11.D.2 · Epic 14 14.C.5 — QUALITY_REVIEWED-no-slot card primary-action.
  // Switches to the in-tab Slots sub-view (no route change) carrying the speaker's
  // username so the tray highlights their unassigned session (FR17). Falls back to the
  // pool id if the username isn't resolved; the tray match is best-effort (no-op on miss).
  const handleAssignSessionSlotForSpeaker = (speaker: SpeakerPoolEntry) => {
    switchToView('slots', speaker.username ?? speaker.id);
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

  // Epic 14 14.C.4 — Agenda sub-view "needs a slot" summary (a session is slotted when
  // it has a startTime). Drives the "N of M need a slot" line + the "Arrange slots →" jump.
  const totalSessions = sessions.length;
  const needsSlotCount = sessions.filter((s) => !s.startTime).length;

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
            data-testid="add-speakers-button"
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
              value="pool"
              aria-label={t('events:eventPage.speakers.poolView', 'Speaker pool view')}
              data-testid="pool-view-toggle"
            >
              <PoolIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.pool', 'Pool')}
            </ToggleButton>
            <ToggleButton
              value="agenda"
              aria-label={t('events:eventPage.speakers.agendaView', 'Agenda view')}
              data-testid="agenda-view-toggle"
            >
              <AgendaIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.agenda', 'Agenda')}
            </ToggleButton>
            <ToggleButton
              value="slots"
              aria-label={t('events:eventPage.speakers.slotsView', 'Slots view')}
              data-testid="slots-view-toggle"
            >
              <SlotsIcon sx={{ mr: 1 }} />
              {t('events:eventPage.speakers.slots', 'Slots')}
            </ToggleButton>
          </ToggleButtonGroup>
        </Stack>
      </Paper>

      {/* View Content */}
      <Box sx={{ flex: 1, minHeight: 0 }}>
        {currentView === 'pool' && speakers && (
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

        {currentView === 'agenda' && (
          <Paper sx={{ p: 2 }}>
            {/* 14.C.4 — "N of M need a slot" summary + jump to Slots */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'stretch', sm: 'center' }}
              spacing={1}
              sx={{ mb: 2 }}
            >
              <Typography
                variant="subtitle2"
                color="text.secondary"
                data-testid="needs-slot-summary"
              >
                {totalSessions === 0
                  ? t('events:eventPage.speakers.noSessionsYet', 'No sessions yet')
                  : needsSlotCount === 0
                    ? t('events:eventPage.speakers.allSlotted', 'All sessions slotted')
                    : t('events:eventPage.speakers.needsSlotSummary', {
                        count: needsSlotCount,
                        total: totalSessions,
                        defaultValue: '{{count}} of {{total}} sessions need a slot',
                      })}
              </Typography>
              <Button
                variant="outlined"
                size="small"
                startIcon={<SlotsIcon />}
                onClick={() => switchToView('slots')}
                data-testid="arrange-slots-button"
              >
                {t('events:eventPage.speakers.arrangeSlots', 'Arrange slots')}
              </Button>
            </Stack>
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

        {currentView === 'slots' && (
          <DragDropSlotAssignment eventCode={eventCode} focusSpeakerId={focusSpeakerId} />
        )}
      </Box>

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
        onAssignSessionSlot={handleAssignSessionSlotForSpeaker}
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
