/**
 * DragDropSlotAssignment Component (Story 5.7 - Task 4b GREEN Phase)
 *
 * Main slot assignment interface with drag-and-drop
 * AC5: Drag-and-drop UI to drag speaker cards to time slots
 * AC6: Visual timeline showing all slots and assignments
 * AC7: Display speaker time preferences
 * AC11: Highlight when slot matches speaker preference
 * AC12: Show unassigned speakers list with real-time updates
 */

import React, { useState, useMemo } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Skeleton,
  Link,
  IconButton,
  Tooltip,
} from '@mui/material';
import { AutoAwesome, ClearAll, CalendarMonth, Tune } from '@mui/icons-material';
import CoffeeIcon from '@mui/icons-material/Coffee';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import MicIcon from '@mui/icons-material/Mic';
import LocalBarIcon from '@mui/icons-material/LocalBar';
import CloseIcon from '@mui/icons-material/Close';
import { AxiosError } from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
import { useTapToAssign } from '@/hooks/useTapToAssign/useTapToAssign';
import {
  slotAssignmentService,
  type SlotAssignmentMode,
} from '@/services/slotAssignmentService/slotAssignmentService';
import { useEvent } from '@/hooks/useEvents';
import { useTimetable } from '@/hooks/useTimetable/useTimetable';
import type { TimetableSlot } from '@/services/timetableService/timetableService';
import { EventTypeConfigurationForm } from '@/components/organizer/EventTypeConfigurationForm/EventTypeConfigurationForm';
import { useUpdateAgendaConfig } from '@/hooks/useAgendaConfig/useAgendaConfig';
import { UnassignedSpeakersList } from '../UnassignedSpeakersList/UnassignedSpeakersList';
import { SpeakerPreferencePanel } from '../SpeakerPreferencePanel/SpeakerPreferencePanel';
import { ConflictDetectionAlert } from '../ConflictDetectionAlert/ConflictDetectionAlert';
import type { Session } from '@/types/event.types';

export interface DragDropSlotAssignmentProps {
  eventCode: string;
  /**
   * 14.C.5: optional speaker (username) to focus on arrival. When set, the
   * matching unassigned session in the tray is highlighted and scrolled into
   * view. Best-effort — silently ignored when no unassigned session matches.
   */
  focusSpeakerId?: string;
}

// Story 5.7: Single conference room (Main Hall)
const ROOMS = ['Main Hall'];

const STRUCTURAL_TYPES = ['moderation', 'break', 'lunch', 'aperitif'] as const;
type StructuralType = (typeof STRUCTURAL_TYPES)[number];

const STRUCTURAL_STYLES: Record<
  StructuralType,
  { bgcolor: string; borderColor: string; icon: React.ReactNode; labelKey: string }
> = {
  moderation: {
    bgcolor: 'grey.100',
    borderColor: 'grey.400',
    icon: <MicIcon fontSize="small" sx={{ color: 'text.secondary' }} />,
    labelKey: 'slotAssignment.structuralSessions.moderation',
  },
  break: {
    bgcolor: 'warning.50',
    borderColor: 'warning.main',
    icon: <CoffeeIcon fontSize="small" sx={{ color: 'warning.main' }} />,
    labelKey: 'slotAssignment.structuralSessions.break',
  },
  lunch: {
    bgcolor: 'success.50',
    borderColor: 'success.main',
    icon: <RestaurantIcon fontSize="small" sx={{ color: 'success.main' }} />,
    labelKey: 'slotAssignment.structuralSessions.lunch',
  },
  aperitif: {
    bgcolor: 'secondary.50',
    borderColor: 'secondary.main',
    icon: <LocalBarIcon fontSize="small" sx={{ color: 'secondary.main' }} />,
    labelKey: 'slotAssignment.structuralSessions.aperitif',
  },
};

const toTimeStr = (d: Date) =>
  `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;

/** Resolve the structural session type from a TimetableSlot.type string. */
const timetableTypeToStructural = (type: TimetableSlot['type']): StructuralType | null => {
  const lower = type.toLowerCase();
  if (lower === 'moderation' || lower === 'break' || lower === 'lunch' || lower === 'aperitif') {
    return lower as StructuralType;
  }
  return null;
};

export const DragDropSlotAssignment: React.FC<DragDropSlotAssignmentProps> = ({
  eventCode,
  focusSpeakerId,
}) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const queryClient = useQueryClient();

  const {
    unassignedSessions,
    isLoading: sessionsLoading,
    conflict,
    assignedCount,
    totalSessions,
    assignToSlot,
    unassignTiming,
    clearConflict,
    clearAllTimings,
    autoAssignTimings,
  } = useSlotAssignment(eventCode);

  // Fetch event to get all sessions (including assigned speaker sessions for display)
  const { data: event, isLoading: eventLoading } = useEvent(eventCode, ['sessions']);

  // Fetch authoritative timetable from backend — drives the slot grid
  const { data: timetable, isLoading: timetableLoading } = useTimetable(eventCode);

  // Story 15.2: per-event "Edit event type" (reuses the shared EventTypeConfigurationForm).
  const updateAgendaConfig = useUpdateAgendaConfig(eventCode);
  const hasAssignments =
    timetable?.slots.some((s) => s.type === 'SPEAKER_SLOT' && !!s.assignedSessionSlug) ?? false;

  // State must be declared before useMemo that depends on it
  const [selectedSpeaker, setSelectedSpeaker] = useState<string | null>(null);
  const [autoAssignModalOpen, setAutoAssignModalOpen] = useState(false);
  const [clearAllModalOpen, setClearAllModalOpen] = useState(false);
  const [generateStructuralOpen, setGenerateStructuralOpen] = useState(false);
  const [editEventTypeOpen, setEditEventTypeOpen] = useState(false);
  const [generateStructuralError, setGenerateStructuralError] = useState<string | null>(null);
  const [structuralAlreadyExist, setStructuralAlreadyExist] = useState(false);
  const [draggedSession, setDraggedSession] = useState<Session | null>(null);
  const [hoveredSlot, setHoveredSlot] = useState<{ time: string; room: string } | null>(null);
  const [speakerFilter, setSpeakerFilter] = useState<'all' | 'assigned' | 'unassigned'>(
    'unassigned'
  );

  // Extract all timed sessions from event data (for speaker session display)
  const allTimedSessions = useMemo(() => {
    if (!event?.sessions) return [];
    return event.sessions.filter((session) => session.startTime && session.endTime);
  }, [event?.sessions]);

  // Assigned (non-structural) speaker sessions — for display in timeline cells
  const assignedSessions = useMemo(
    () =>
      allTimedSessions.filter(
        (s) => s.room && !STRUCTURAL_TYPES.includes(s.sessionType as StructuralType)
      ),
    [allTimedSessions]
  );

  // Filter sessions for speaker pool based on active filter
  const filteredSessions = useMemo(() => {
    const allSessions = event?.sessions || [];

    switch (speakerFilter) {
      case 'all':
        return allSessions;
      case 'assigned':
        return assignedSessions;
      case 'unassigned':
        return unassignedSessions;
      default:
        return unassignedSessions;
    }
  }, [speakerFilter, event?.sessions, assignedSessions, unassignedSessions]);

  // 14.C.5: resolve the focus speaker (username) to a session in the current
  // tray so the list can highlight + scroll it. Best-effort: null when no match.
  const focusSessionSlug = useMemo(() => {
    if (!focusSpeakerId) return null;
    const match = filteredSessions.find((s) =>
      s.speakers?.some((sp) => sp.username === focusSpeakerId)
    );
    return match?.sessionSlug ?? null;
  }, [focusSpeakerId, filteredSessions]);

  // TIME_SLOTS and structural slot lookup are now derived from the backend timetable.
  // This guarantees the grid displays exactly the same positions as the backend algorithm.
  const TIME_SLOTS = useMemo(() => {
    if (!timetable) return [];
    const times = timetable.slots.map((s) => toTimeStr(new Date(s.startTime)));
    return [...new Set(times)].sort((a, b) => {
      const [aH, aM] = a.split(':').map(Number);
      const [bH, bM] = b.split(':').map(Number);
      return aH * 60 + aM - (bH * 60 + bM);
    });
  }, [timetable]);

  // Structural slots from timetable (non-SPEAKER_SLOT) — indexed by time string for O(1) lookup
  const structuralSlotsByTime = useMemo(() => {
    const map = new Map<string, TimetableSlot>();
    if (!timetable) return map;
    timetable.slots
      .filter((s) => s.type !== 'SPEAKER_SLOT')
      .forEach((s) => {
        map.set(toTimeStr(new Date(s.startTime)), s);
      });
    return map;
  }, [timetable]);

  // Story 15.3: SPEAKER_SLOT indexed by its time-row label — carries the stable `slotKey`
  // and the current occupant (`assignedSessionSlug`). The grid keeps rendering one row per
  // time for layout, but slot IDENTITY for assignment is now the slotKey, not the HH:MM string.
  const speakerSlotByTime = useMemo(() => {
    const map = new Map<string, TimetableSlot>();
    if (!timetable) return map;
    timetable.slots
      .filter((s) => s.type === 'SPEAKER_SLOT')
      .forEach((s) => map.set(toTimeStr(new Date(s.startTime)), s));
    return map;
  }, [timetable]);

  // Resolve an assigned session slug → the full Session (for cell display).
  const sessionBySlug = useMemo(() => {
    const map = new Map<string, Session>();
    (event?.sessions ?? []).forEach((s) => map.set(s.sessionSlug, s));
    return map;
  }, [event?.sessions]);

  // Story 15.3: while dragging over a SPEAKER_SLOT, compute the agenda as it WOULD look after
  // the drop (ASSIGN / INSERT-shift / SWAP) — Map<slotKey, occupantSlug | null>. Null when not
  // dragging, the hovered slot isn't a speaker slot, or an INSERT would overflow the agenda.
  // Mirrors the backend SlotReorderService so the grid can preview the reflow before committing.
  const previewBySlotKey = useMemo<Map<string, string | null> | null>(() => {
    if (!draggedSession || !hoveredSlot || !timetable) {
      return null;
    }
    const target = speakerSlotByTime.get(hoveredSlot.time);
    if (!target?.slotKey) {
      return null;
    }
    const ordered = timetable.slots.filter((s) => s.type === 'SPEAKER_SLOT');
    const keys = ordered.map((s) => s.slotKey as string);
    const occ = ordered.map((s) => s.assignedSessionSlug ?? null);
    const targetIdx = keys.indexOf(target.slotKey);
    if (targetIdx < 0) {
      return null;
    }
    const dragged = draggedSession.sessionSlug;
    const targetOcc = occ[targetIdx];
    const detach = () => {
      const i = occ.indexOf(dragged);
      if (i >= 0) {
        occ[i] = null;
      }
    };
    if (!targetOcc || targetOcc === dragged) {
      // ASSIGN
      detach();
      occ[targetIdx] = dragged;
    } else if (draggedSession.startTime) {
      // SWAP (dragged is already assigned)
      const di = occ.indexOf(dragged);
      if (di >= 0) {
        occ[di] = targetOcc;
      }
      occ[targetIdx] = dragged;
    } else {
      // INSERT (pool speaker) — shift the block into the first free slot at/after target
      detach();
      let free = -1;
      for (let i = targetIdx; i < occ.length; i++) {
        if (occ[i] == null) {
          free = i;
          break;
        }
      }
      if (free < 0) {
        return null; // agenda full — nothing to preview
      }
      for (let i = free; i > targetIdx; i--) {
        occ[i] = occ[i - 1];
      }
      occ[targetIdx] = dragged;
    }
    const map = new Map<string, string | null>();
    keys.forEach((k, i) => map.set(k, occ[i]));
    return map;
  }, [draggedSession, hoveredSlot, timetable, speakerSlotByTime]);

  const isLoading = sessionsLoading || eventLoading || timetableLoading;

  // Mock speaker data for preferences panel
  const getSpeakerData = (username: string) => {
    return {
      username,
      displayName: username
        .split('.')
        .map((n) => n.charAt(0).toUpperCase() + n.slice(1))
        .join(' '),
      companyName: 'Mock Company',
      preferences: {
        preferredTimeOfDay: 'morning' as const,
        avRequirements: {
          microphone: true,
          projector: true,
          recording: false,
          whiteboard: true,
        },
        roomSetupNotes: 'Prefer standing desk and natural light',
      },
    };
  };

  const handleGenerateStructural = async (overwrite: boolean) => {
    setGenerateStructuralError(null);
    try {
      await slotAssignmentService.generateStructuralSessions(eventCode, overwrite);
      setGenerateStructuralOpen(false);
      setStructuralAlreadyExist(false);
      await queryClient.invalidateQueries({ queryKey: ['event', eventCode, ['sessions']] });
      await queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
      await queryClient.refetchQueries({
        queryKey: ['event', eventCode, ['sessions']],
        exact: true,
      });
    } catch (err) {
      if (err instanceof AxiosError && err.response?.status === 409) {
        setStructuralAlreadyExist(true);
      } else {
        const msg = err instanceof Error ? err.message : 'Failed to generate structural sessions';
        setGenerateStructuralError(msg);
      }
    }
  };

  const handleDragStart = (session: Session) => (e: React.DragEvent) => {
    setDraggedSession(session);
    if (e.dataTransfer) {
      e.dataTransfer.effectAllowed = 'move';
    }
  };

  const handleDragOver = (time: string, room: string) => (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'move';
    }
    setHoveredSlot({ time, room });
  };

  const handleDragLeave = () => {
    setHoveredSlot(null);
  };

  // Story 15.3: single commit path for BOTH desktop drag and mobile tap. Addresses the
  // target slot by its stable `slotKey` and dispatches ASSIGN / INSERT / SWAP. The backend
  // recomputes and persists the affected sessions' times; we then refresh the grid.
  const commitSlotAssignment = async (
    sessionSlug: string,
    targetSlotKey: string,
    mode: SlotAssignmentMode
  ) => {
    if (!targetSlotKey) {
      return;
    }
    try {
      await assignToSlot(sessionSlug, { targetSlotKey, mode });
      await queryClient.invalidateQueries({ queryKey: ['event', eventCode, ['sessions']] });
      await queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
      await queryClient.refetchQueries({
        queryKey: ['event', eventCode, ['sessions']],
        exact: true,
      });
    } catch (err) {
      // 409 (agenda-full / conflict) is surfaced via the hook's `conflict` modal.
      console.error('✗ Failed to assign slot:', err);
    }
  };

  // Story 15.3: remove an assigned session from its slot → back to the unassigned pool.
  const handleUnassign = async (sessionSlug: string) => {
    try {
      await unassignTiming(sessionSlug);
      await queryClient.invalidateQueries({ queryKey: ['event', eventCode, ['sessions']] });
      await queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
      await queryClient.refetchQueries({
        queryKey: ['event', eventCode, ['sessions']],
        exact: true,
      });
    } catch (err) {
      console.error('✗ Failed to remove slot assignment:', err);
    }
  };

  // Mobile tap-to-assign / tap-to-swap (14.G.3 → extracted, Story 15.3).
  const tap = useTapToAssign({ commit: commitSlotAssignment });

  // Drop onto an empty slot → ASSIGN; a pool speaker onto an occupied slot → INSERT;
  // an already-assigned speaker onto an occupied slot → SWAP.
  const resolveDropMode = (
    draggedIsAssigned: boolean,
    occupantSlug: string | null,
    draggedSlug: string
  ): SlotAssignmentMode => {
    if (!occupantSlug || occupantSlug === draggedSlug) {
      return 'ASSIGN';
    }
    return draggedIsAssigned ? 'SWAP' : 'INSERT';
  };

  const handleDrop = (time: string) => async (e: React.DragEvent) => {
    e.preventDefault();
    setHoveredSlot(null);
    if (!draggedSession) {
      return;
    }
    const slot = speakerSlotByTime.get(time);
    if (!slot?.slotKey) {
      setDraggedSession(null);
      return;
    }
    const occupant = slot.assignedSessionSlug ?? null;
    const mode = resolveDropMode(!!draggedSession.startTime, occupant, draggedSession.sessionSlug);
    await commitSlotAssignment(draggedSession.sessionSlug, slot.slotKey, mode);
    setDraggedSession(null);
  };

  // Mobile: tap a slot. If a session is armed, place/swap it here; otherwise arm an
  // occupied slot's session for a subsequent move/swap.
  const handleSlotTap = (time: string) => async () => {
    const slot = speakerSlotByTime.get(time);
    if (!slot?.slotKey) {
      return;
    }
    const occupant = slot.assignedSessionSlug ?? null;
    if (tap.hasArmed) {
      await tap.tapSlot(slot.slotKey, occupant);
    } else if (occupant) {
      tap.armFromSlot(occupant, slot.slotKey);
    }
  };

  // Get the session occupying a SPEAKER_SLOT (by the backend-resolved occupant slug).
  const getSessionForSlot = (time: string): Session | undefined => {
    const occupant = speakerSlotByTime.get(time)?.assignedSessionSlug;
    return occupant ? sessionBySlug.get(occupant) : undefined;
  };

  const getPreferenceMatchClass = (time: string): string => {
    if (!draggedSession || !hoveredSlot) return '';
    const hour = parseInt(time.split(':')[0], 10);
    if (hour >= 8 && hour < 12) return 'preference-match-high';
    if (hour >= 13 && hour < 16) return 'preference-match-medium';
    return 'preference-match-low';
  };

  const getPreferenceMatchPercentage = (time: string): number => {
    const hour = parseInt(time.split(':')[0], 10);
    if (hour >= 8 && hour < 12) return 90;
    if (hour >= 13 && hour < 16) return 65;
    return 20;
  };

  const allSessionsAssigned = totalSessions > 0 && assignedCount === totalSessions;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        p: { xs: 1, md: 3 },
      }}
    >
      {/* Loading State */}
      {isLoading && (
        <Box data-testid="loading-skeleton">
          <Skeleton variant="rectangular" height={100} sx={{ mb: 2 }} />
          <Skeleton variant="rectangular" height={400} />
        </Box>
      )}

      {/* Top Action Bar — summary + bulk actions (14.C.5) */}
      {!isLoading && (
        <Paper
          data-testid="slot-action-bar"
          role="toolbar"
          aria-label={t('slotAssignment.actionBar.label')}
          sx={{
            p: 2,
            mb: 3,
            display: 'flex',
            flexDirection: { xs: 'column', md: 'row' },
            alignItems: { xs: 'stretch', md: 'center' },
            gap: 2,
            flexWrap: 'wrap',
          }}
        >
          {/* Session Summary */}
          <Box
            sx={{
              display: 'flex',
              gap: { xs: 1, md: 3 },
              flexWrap: 'wrap',
              flexGrow: 1,
            }}
          >
            <Typography variant="body2" color="text.secondary">
              {t('slotAssignment.quickActions.total', { count: totalSessions })}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('slotAssignment.quickActions.assigned', { count: assignedCount })}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('slotAssignment.quickActions.pending', { count: unassignedSessions.length })}
            </Typography>
          </Box>

          {/* Action Buttons */}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button
              variant="outlined"
              startIcon={<Tune />}
              onClick={() => setEditEventTypeOpen(true)}
              data-testid="edit-event-type-button"
            >
              {t('slotAssignment.actions.editEventType')}
            </Button>

            <Button
              variant="outlined"
              startIcon={<CalendarMonth />}
              onClick={() => {
                setGenerateStructuralError(null);
                setStructuralAlreadyExist(false);
                setGenerateStructuralOpen(true);
              }}
              data-testid="generate-structural-button"
            >
              {t('slotAssignment.actions.generateStructure')}
            </Button>

            <Button
              variant="contained"
              startIcon={<AutoAwesome />}
              onClick={() => setAutoAssignModalOpen(true)}
              data-testid="auto-assign-button"
            >
              {t('slotAssignment.actions.autoAssign')}
            </Button>

            <Button
              variant="outlined"
              startIcon={<ClearAll />}
              onClick={() => setClearAllModalOpen(true)}
              data-testid="clear-all-button"
            >
              {t('slotAssignment.actions.clearAll')}
            </Button>
          </Box>
        </Paper>
      )}

      <Dialog
        open={editEventTypeOpen}
        onClose={() => setEditEventTypeOpen(false)}
        maxWidth="md"
        fullWidth
        fullScreen={isMobile}
        data-testid="edit-event-type-modal"
      >
        <DialogTitle>{t('slotAssignment.actions.editEventType')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <EventTypeConfigurationForm
              eventCode={eventCode}
              warning={
                hasAssignments ? (
                  <Alert severity="warning" data-testid="edit-event-type-assignment-warning">
                    {t('slotAssignment.editEventType.assignmentWarning')}
                  </Alert>
                ) : undefined
              }
              onSave={async (config) => {
                await updateAgendaConfig.mutateAsync({
                  minSlots: config.minSlots,
                  maxSlots: config.maxSlots,
                  slotDuration: config.slotDuration,
                  theoreticalSlotsAM: config.theoreticalSlotsAM,
                  breakSlots: config.breakSlots,
                  lunchSlots: config.lunchSlots,
                  defaultCapacity: config.defaultCapacity,
                  moderationStartDuration: config.moderationStartDuration ?? 5,
                  moderationEndDuration: config.moderationEndDuration ?? 5,
                  breakDuration: config.breakDuration ?? 20,
                  lunchDuration: config.lunchDuration ?? 60,
                  aperitifSlots: config.aperitifSlots ?? 0,
                  aperitifDuration: config.aperitifDuration ?? 90,
                  aperitifPosition: config.aperitifPosition ?? 'end',
                  typicalStartTime: config.typicalStartTime ?? undefined,
                  typicalEndTime: config.typicalEndTime ?? undefined,
                });
                setEditEventTypeOpen(false);
              }}
              onCancel={() => setEditEventTypeOpen(false)}
            />
          </Box>
        </DialogContent>
      </Dialog>

      {/* Success Banner (above the two columns) */}
      {!isLoading && allSessionsAssigned && (
        <Alert severity="success" sx={{ mb: 3 }}>
          <Typography variant="body2" gutterBottom>
            {t('slotAssignment.quickActions.allAssigned')}
          </Typography>
          <Link href="#" underline="hover">
            {t('slotAssignment.quickActions.goToPublishing')}
          </Link>
        </Alert>
      )}

      {/* Main Layout — 2 columns: tray (left) + timeline (right) */}
      {!isLoading && (
        <Grid container spacing={3} sx={{ overflow: 'visible' }}>
          {/* Left: Unassigned Speakers tray */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Paper>
              <UnassignedSpeakersList
                sessions={filteredSessions}
                totalSessions={totalSessions}
                onViewPreferences={(username) => setSelectedSpeaker(username)}
                onDragStart={handleDragStart}
                activeFilter={speakerFilter}
                onFilterChange={setSpeakerFilter}
                isLoading={isLoading}
                focusSessionSlug={focusSessionSlug}
                onSessionTap={isMobile ? (s) => tap.armFromPool(s.sessionSlug) : undefined}
                selectedSessionSlug={tap.armedSessionSlug}
              />
              {isMobile && tap.hasArmed && (
                <Typography
                  variant="caption"
                  color="primary"
                  sx={{ display: 'block', px: 2, pb: 1 }}
                  data-testid="tap-assign-hint"
                >
                  {t(
                    'slotAssignment.tapToAssign.placeHint',
                    '↓ Now tap a highlighted slot to place it'
                  )}
                </Typography>
              )}
            </Paper>
          </Grid>

          {/* Right: Session Timeline Grid (drop targets) */}
          <Grid size={{ xs: 12, md: 8 }}>
            <Paper data-testid="session-timeline-grid" sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                {t('slotAssignment.timeline.title')}
              </Typography>

              {/* Timeline Grid — fluid to viewport width on mobile AND desktop; the
                  proportional Grid fractions handle column sizing. overflowX wrapper
                  kept as a harmless safety net. */}
              <Box data-testid="timeline-grid" sx={{ overflowX: 'auto' }}>
                <Box>
                  {/* Header Row */}
                  <Grid container spacing={1} sx={{ mb: 1 }}>
                    <Grid size={2}>
                      <Typography variant="caption" fontWeight="bold">
                        {t('slotAssignment.timeline.headerTime')}
                      </Typography>
                    </Grid>
                    {ROOMS.map((room) => (
                      <Grid size={10 / ROOMS.length} key={room}>
                        <Typography
                          variant="caption"
                          fontWeight="bold"
                          sx={{ whiteSpace: { xs: 'normal', md: 'nowrap' } }}
                        >
                          {room}
                        </Typography>
                      </Grid>
                    ))}
                  </Grid>

                  {/* Time Slot Rows — driven entirely by backend timetable */}
                  {TIME_SLOTS.map((time) => {
                    const structuralSlot = structuralSlotsByTime.get(time);
                    const structuralType = structuralSlot
                      ? timetableTypeToStructural(structuralSlot.type)
                      : null;
                    const structuralStyle = structuralType
                      ? STRUCTURAL_STYLES[structuralType]
                      : null;

                    return (
                      <Grid container spacing={1} key={time} sx={{ mb: 1 }}>
                        <Grid size={2}>
                          <Typography variant="body2">{time}</Typography>
                        </Grid>

                        {structuralSlot && structuralStyle ? (
                          /* Non-droppable structural block spanning all room columns */
                          <Grid size={10}>
                            <Paper
                              data-testid={`structural-${time}`}
                              sx={{
                                p: 1,
                                minHeight: 44,
                                border: 1,
                                borderColor: structuralStyle.borderColor,
                                bgcolor: structuralStyle.bgcolor,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1,
                                cursor: 'default',
                                userSelect: 'none',
                              }}
                            >
                              {structuralStyle.icon}
                              <Typography variant="caption" fontWeight="medium">
                                {structuralSlot.title || t(structuralStyle.labelKey)}
                              </Typography>
                            </Paper>
                          </Grid>
                        ) : (
                          /* Normal droppable room cells */
                          ROOMS.map((room) => {
                            const slotId = `slot-${time}-${room.replace(/\s+/g, '-')}`;
                            const isHovered =
                              hoveredSlot?.time === time && hoveredSlot?.room === room;
                            const matchClass = isHovered ? getPreferenceMatchClass(time) : '';
                            const matchPercent = isHovered ? getPreferenceMatchPercentage(time) : 0;
                            const slotKey = speakerSlotByTime.get(time)?.slotKey;
                            // Actual occupant (authoritative) drives drag + mobile-tap; the
                            // preview occupant only drives what's DISPLAYED during a drag-over.
                            const actualAssignedSession = getSessionForSlot(time);
                            const previewActive = previewBySlotKey !== null;
                            const previewOccupant =
                              previewActive && slotKey
                                ? (previewBySlotKey.get(slotKey) ?? null)
                                : null;
                            const displaySession = previewActive
                              ? previewOccupant
                                ? sessionBySlug.get(previewOccupant)
                                : undefined
                              : actualAssignedSession;
                            const isPreviewMoved =
                              previewActive &&
                              previewOccupant !== (actualAssignedSession?.sessionSlug ?? null);
                            // 14.G.3 / 15.3: on mobile every slot is tappable — a target while a
                            // session is armed, or (when occupied) a source to arm for a swap.
                            const isArmedSource =
                              !!actualAssignedSession &&
                              tap.isArmed(actualAssignedSession.sessionSlug);
                            const armed = isMobile && tap.hasArmed && !isArmedSource;
                            const tappable = isMobile && (tap.hasArmed || !!actualAssignedSession);

                            return (
                              <Grid size={10 / ROOMS.length} key={room}>
                                <Paper
                                  data-testid={slotId}
                                  data-slot-time={time}
                                  data-slot-room={room}
                                  data-slot-key={slotKey}
                                  data-armed={armed ? 'true' : undefined}
                                  data-preview-moved={isPreviewMoved ? 'true' : undefined}
                                  draggable={!!actualAssignedSession}
                                  onDragStart={
                                    actualAssignedSession
                                      ? handleDragStart(actualAssignedSession)
                                      : undefined
                                  }
                                  onClick={tappable ? handleSlotTap(time) : undefined}
                                  className={`${isHovered ? 'drop-zone-active' : ''} ${matchClass}`}
                                  onDragOver={handleDragOver(time, room)}
                                  onDragLeave={handleDragLeave}
                                  onDrop={handleDrop(time)}
                                  sx={{
                                    p: 1,
                                    minHeight: 60,
                                    position: 'relative',
                                    border: 2,
                                    // Hovered target = primary; cascaded preview-moved cells =
                                    // secondary (dashed) so the reflow is visually distinct.
                                    borderColor: armed
                                      ? 'secondary.main'
                                      : isHovered
                                        ? 'primary.main'
                                        : isPreviewMoved
                                          ? 'secondary.main'
                                          : displaySession
                                            ? 'success.main'
                                            : 'divider',
                                    borderStyle:
                                      isHovered || armed || isPreviewMoved ? 'dashed' : 'solid',
                                    bgcolor: armed
                                      ? 'action.selected'
                                      : isHovered
                                        ? 'action.hover'
                                        : isPreviewMoved
                                          ? 'secondary.50'
                                          : displaySession
                                            ? 'success.light'
                                            : 'background.default',
                                    opacity: isPreviewMoved && !isHovered ? 0.92 : 1,
                                    cursor: actualAssignedSession ? 'grab' : 'pointer',
                                    transition: 'all 0.2s',
                                    '&:hover': {
                                      bgcolor: actualAssignedSession
                                        ? 'success.light'
                                        : 'action.hover',
                                    },
                                    '&:active': {
                                      cursor: actualAssignedSession ? 'grabbing' : 'pointer',
                                    },
                                  }}
                                >
                                  {/* 15.3: remove an assigned session from its slot (back to
                                      pool). Hidden during a drag-preview to avoid clutter. */}
                                  {actualAssignedSession && !previewActive && (
                                    <Tooltip title={t('slotAssignment.timeline.removeFromSlot')}>
                                      <IconButton
                                        size="small"
                                        data-testid={`remove-slot-${actualAssignedSession.sessionSlug}`}
                                        aria-label={t('slotAssignment.timeline.removeFromSlot')}
                                        draggable={false}
                                        onDragStart={(e) => e.stopPropagation()}
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          handleUnassign(actualAssignedSession.sessionSlug);
                                        }}
                                        sx={{
                                          position: 'absolute',
                                          top: 2,
                                          right: 2,
                                          p: 0.25,
                                          color: 'text.secondary',
                                          '&:hover': { color: 'error.main' },
                                        }}
                                      >
                                        <CloseIcon sx={{ fontSize: 16 }} />
                                      </IconButton>
                                    </Tooltip>
                                  )}
                                  {displaySession ? (
                                    <Box sx={{ pr: actualAssignedSession ? 2.5 : 0 }}>
                                      <Typography
                                        variant="caption"
                                        fontWeight="bold"
                                        sx={{ whiteSpace: { xs: 'normal', md: 'nowrap' } }}
                                      >
                                        {displaySession.title}
                                      </Typography>
                                      {displaySession.speakers?.[0] && (
                                        <Typography
                                          variant="caption"
                                          display="block"
                                          sx={{ whiteSpace: { xs: 'normal', md: 'nowrap' } }}
                                        >
                                          {displaySession.speakers[0].firstName}{' '}
                                          {displaySession.speakers[0].lastName}
                                        </Typography>
                                      )}
                                      {/* AC11: preference-match indicator on the hovered slot. */}
                                      {isHovered && matchPercent > 0 && (
                                        <Typography
                                          variant="caption"
                                          display="block"
                                          color="primary"
                                        >
                                          {t('slotAssignment.timeline.matchPercent', {
                                            percent: matchPercent,
                                          })}
                                        </Typography>
                                      )}
                                      {/* 15.3: on the hovered target slot, label what dropping here
                                          will do (insert-before vs swap), based on the ACTUAL
                                          occupant before the reflow. */}
                                      {isHovered &&
                                        draggedSession &&
                                        actualAssignedSession &&
                                        actualAssignedSession.sessionSlug !==
                                          draggedSession.sessionSlug && (
                                          <Typography
                                            variant="caption"
                                            display="block"
                                            color="secondary"
                                            fontWeight="bold"
                                            data-testid="drop-hint"
                                            sx={{ mt: 0.5 }}
                                          >
                                            {draggedSession.startTime
                                              ? t('slotAssignment.timeline.swapHint')
                                              : t('slotAssignment.timeline.insertHint')}
                                          </Typography>
                                        )}
                                    </Box>
                                  ) : isHovered && matchPercent > 0 ? (
                                    <Typography variant="caption" color="primary">
                                      {t('slotAssignment.timeline.matchPercent', {
                                        percent: matchPercent,
                                      })}
                                    </Typography>
                                  ) : null}
                                </Paper>
                              </Grid>
                            );
                          })
                        )}
                      </Grid>
                    );
                  })}
                </Box>
              </Box>
            </Paper>
          </Grid>
        </Grid>
      )}

      {/* Speaker Preferences Drawer */}
      <SpeakerPreferencePanel
        speaker={selectedSpeaker ? getSpeakerData(selectedSpeaker) : null}
        isOpen={!!selectedSpeaker}
        onClose={() => setSelectedSpeaker(null)}
        hoveredSlot={hoveredSlot || undefined}
      />

      {/* Conflict Alert Modal */}
      <ConflictDetectionAlert
        conflict={conflict}
        isOpen={!!conflict}
        onClose={clearConflict}
        onResolve={clearConflict}
      />

      {/* Generate Structural Sessions Modal */}
      <Dialog
        open={generateStructuralOpen}
        onClose={() => {
          setGenerateStructuralOpen(false);
          setStructuralAlreadyExist(false);
          setGenerateStructuralError(null);
        }}
        fullScreen={isMobile}
        data-testid="generate-structural-modal"
      >
        <DialogTitle>{t('slotAssignment.modals.generateStructure.title')}</DialogTitle>
        <DialogContent>
          {structuralAlreadyExist ? (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {t('slotAssignment.modals.generateStructure.alreadyExist')}
            </Alert>
          ) : (
            <Typography>{t('slotAssignment.modals.generateStructure.message')}</Typography>
          )}
          {generateStructuralError && (
            <Alert severity="error" sx={{ mt: 1 }}>
              {generateStructuralError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setGenerateStructuralOpen(false);
              setStructuralAlreadyExist(false);
              setGenerateStructuralError(null);
            }}
          >
            {t('common:actions.cancel')}
          </Button>
          {structuralAlreadyExist ? (
            <Button
              variant="contained"
              color="warning"
              onClick={() => handleGenerateStructural(true)}
              data-testid="generate-structural-overwrite-confirm"
            >
              {t('slotAssignment.modals.generateStructure.overwrite')}
            </Button>
          ) : (
            <Button
              variant="contained"
              onClick={() => handleGenerateStructural(false)}
              data-testid="generate-structural-confirm"
            >
              {t('slotAssignment.actions.confirm')}
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Auto-Assign Modal */}
      <Dialog
        open={autoAssignModalOpen}
        onClose={() => setAutoAssignModalOpen(false)}
        fullScreen={isMobile}
        data-testid="auto-assign-modal"
      >
        <DialogTitle>{t('slotAssignment.modals.autoAssign.title')}</DialogTitle>
        <DialogContent>
          <Typography>{t('slotAssignment.modals.autoAssign.message')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAutoAssignModalOpen(false)} data-testid="auto-assign-cancel">
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={async () => {
              try {
                await autoAssignTimings();
                await queryClient.invalidateQueries({
                  queryKey: ['event', eventCode, ['sessions']],
                });
                await queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
                setAutoAssignModalOpen(false);
              } catch (err) {
                console.error('Failed to auto-assign:', err);
              }
            }}
            data-testid="auto-assign-confirm"
          >
            {t('slotAssignment.actions.confirm')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Clear All Confirmation Modal */}
      <Dialog
        open={clearAllModalOpen}
        onClose={() => setClearAllModalOpen(false)}
        fullScreen={isMobile}
      >
        <DialogTitle>{t('slotAssignment.modals.clearAll.title')}</DialogTitle>
        <DialogContent>
          <Typography>{t('slotAssignment.modals.clearAll.message')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClearAllModalOpen(false)} data-testid="clear-all-cancel">
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            data-testid="clear-all-confirm"
            onClick={async () => {
              try {
                await clearAllTimings();
                await queryClient.invalidateQueries({
                  queryKey: ['event', eventCode, ['sessions']],
                });
                await queryClient.invalidateQueries({ queryKey: ['timetable', eventCode] });
                setClearAllModalOpen(false);
              } catch (err) {
                console.error('Failed to clear all timings:', err);
              }
            }}
          >
            {t('slotAssignment.actions.clearAll')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ARIA Live Region for Screen Reader Announcements */}
      <Box
        role="status"
        aria-live="polite"
        aria-atomic="true"
        sx={{
          position: 'absolute',
          left: '-10000px',
          width: '1px',
          height: '1px',
          overflow: 'hidden',
        }}
      >
        {assignedCount > 0 &&
          t('slotAssignment.quickActions.sessionsAssignedAnnouncement', { count: assignedCount })}
      </Box>
    </Box>
  );
};
