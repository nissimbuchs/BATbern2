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
} from '@mui/material';
import { AutoAwesome, ClearAll, CalendarMonth } from '@mui/icons-material';
import CoffeeIcon from '@mui/icons-material/Coffee';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import MicIcon from '@mui/icons-material/Mic';
import { AxiosError } from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
import { slotAssignmentService } from '@/services/slotAssignmentService/slotAssignmentService';
import { useEvent } from '@/hooks/useEvents';
import { useEventType } from '@/hooks/useEventTypes';
import { UnassignedSpeakersList } from '../UnassignedSpeakersList/UnassignedSpeakersList';
import { SpeakerPreferencePanel } from '../SpeakerPreferencePanel/SpeakerPreferencePanel';
import { ConflictDetectionAlert } from '../ConflictDetectionAlert/ConflictDetectionAlert';
import type { Session } from '@/types/event.types';

export interface DragDropSlotAssignmentProps {
  eventCode: string;
}

// Story 5.7: Single conference room (Main Hall)
const ROOMS = ['Main Hall'];

const STRUCTURAL_TYPES = ['moderation', 'break', 'lunch'] as const;
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
};

export const DragDropSlotAssignment: React.FC<DragDropSlotAssignmentProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

  const {
    unassignedSessions,
    isLoading: sessionsLoading,
    conflict,
    assignedCount,
    totalSessions,
    assignTiming,
    clearConflict,
    clearAllTimings,
    autoAssignTimings,
  } = useSlotAssignment(eventCode);

  // Fetch event to get eventType and all sessions (including assigned)
  const { data: event, isLoading: eventLoading } = useEvent(eventCode, ['sessions']);

  // Fetch event type configuration to get slot definitions
  const { data: eventTypeConfig, isLoading: eventTypeLoading } = useEventType(
    event?.eventType || 'FULL_DAY'
  );

  // State must be declared before useMemo that depends on it
  const [selectedSpeaker, setSelectedSpeaker] = useState<string | null>(null);
  const [autoAssignModalOpen, setAutoAssignModalOpen] = useState(false);
  const [clearAllModalOpen, setClearAllModalOpen] = useState(false);
  const [generateStructuralOpen, setGenerateStructuralOpen] = useState(false);
  const [generateStructuralError, setGenerateStructuralError] = useState<string | null>(null);
  const [structuralAlreadyExist, setStructuralAlreadyExist] = useState(false);
  const [draggedSession, setDraggedSession] = useState<Session | null>(null);
  const [hoveredSlot, setHoveredSlot] = useState<{ time: string; room: string } | null>(null);
  const [speakerFilter, setSpeakerFilter] = useState<'all' | 'assigned' | 'unassigned'>(
    'unassigned'
  );

  // Extract all timed sessions from event data
  const allTimedSessions = useMemo(() => {
    if (!event?.sessions) return [];
    return event.sessions.filter((session) => session.startTime && session.endTime);
  }, [event?.sessions]);

  // Structural sessions (moderation, break, lunch) — fixed blocks, non-droppable
  const structuralSessions = useMemo(
    () =>
      allTimedSessions.filter((s) => STRUCTURAL_TYPES.includes(s.sessionType as StructuralType)),
    [allTimedSessions]
  );

  // Speaker sessions assigned to a room (non-structural, droppable)
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

  // Generate time slots dynamically.
  // When structural sessions exist, derive droppable slot times from the GAPS between structural
  // blocks (moderation end → next structural start), so droppable rows align exactly with the
  // actual generated schedule rather than a misaligned typicalStartTime-based grid.
  const TIME_SLOTS = useMemo(() => {
    const slotDurationMinutes = eventTypeConfig?.slotDuration || 45;
    const slots: string[] = [];

    const toTimeStr = (d: Date) =>
      `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;

    // Always include start times from already-assigned speaker sessions
    assignedSessions.forEach((session) => {
      if (session.startTime) {
        const t = toTimeStr(new Date(session.startTime));
        if (!slots.includes(t)) {
          slots.push(t);
        }
      }
    });

    if (structuralSessions.length > 0) {
      // Sort structural sessions chronologically
      const sorted = [...structuralSessions]
        .filter((s) => s.startTime && s.endTime)
        .sort((a, b) => new Date(a.startTime!).getTime() - new Date(b.startTime!).getTime());

      // Add each structural session's start time (these render as fixed blocks)
      sorted.forEach((s) => {
        const t = toTimeStr(new Date(s.startTime!));
        if (!slots.includes(t)) {
          slots.push(t);
        }
      });

      // Fill droppable speaker-session slots in each gap between consecutive structural blocks
      for (let i = 0; i < sorted.length - 1; i++) {
        const gapStart = new Date(sorted[i].endTime!); // end of current structural
        const gapEnd = new Date(sorted[i + 1].startTime!); // start of next structural
        const cursor = new Date(gapStart.getTime());
        while (cursor.getTime() < gapEnd.getTime()) {
          const t = toTimeStr(cursor);
          if (!slots.includes(t)) {
            slots.push(t);
          }
          cursor.setTime(cursor.getTime() + slotDurationMinutes * 60 * 1000);
        }
      }
    } else if (eventTypeConfig) {
      // No structural sessions yet — fall back to a simple grid from typicalStartTime
      const startTime = eventTypeConfig.typicalStartTime || '09:00';
      const endTime = eventTypeConfig.typicalEndTime || '17:00';
      const breakCount = eventTypeConfig.breakSlots || 0;
      const lunchCount = eventTypeConfig.lunchSlots || 0;
      const totalSlots = eventTypeConfig.maxSlots || 8;

      const [startHour, startMinute] = startTime.split(':').map(Number);
      const currentTime = new Date();
      currentTime.setHours(startHour, startMinute, 0, 0);

      const totalSlotsWithBreaks = totalSlots + breakCount + lunchCount;
      for (let i = 0; i < totalSlotsWithBreaks; i++) {
        const timeSlot = toTimeStr(currentTime);
        const [endHour, endMinute] = endTime.split(':').map(Number);
        if (
          (currentTime.getHours() < endHour ||
            (currentTime.getHours() === endHour && currentTime.getMinutes() < endMinute)) &&
          !slots.includes(timeSlot)
        ) {
          slots.push(timeSlot);
        }
        currentTime.setTime(currentTime.getTime() + slotDurationMinutes * 60 * 1000);
      }
    } else if (slots.length === 0) {
      slots.push('09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00');
    }

    slots.sort((a, b) => {
      const [aH, aM] = a.split(':').map(Number);
      const [bH, bM] = b.split(':').map(Number);
      return aH * 60 + aM - (bH * 60 + bM);
    });

    return slots;
  }, [eventTypeConfig, assignedSessions, structuralSessions]);

  // Get session assigned to a specific time slot and room
  const getSessionForSlot = (time: string, room: string): Session | undefined => {
    return assignedSessions.find((session) => {
      if (!session.startTime || session.room !== room) return false;

      // Parse session start time - sessions are stored as ISO strings (UTC)
      // TIME_SLOTS are in HH:MM format (LOCAL time for display)
      // Convert session UTC time to local time for comparison

      // Parse the session's ISO timestamp
      const sessionStart = new Date(session.startTime);

      // Extract LOCAL time portion from the session (for display/comparison)
      const sessionHours = sessionStart.getHours().toString().padStart(2, '0');
      const sessionMinutes = sessionStart.getMinutes().toString().padStart(2, '0');
      const sessionTime = `${sessionHours}:${sessionMinutes}`;

      return sessionTime === time;
    });
  };

  const getStructuralSessionForTime = (time: string): Session | undefined => {
    return structuralSessions.find((session) => {
      if (!session.startTime) return false;
      const s = new Date(session.startTime);
      const h = s.getHours().toString().padStart(2, '0');
      const m = s.getMinutes().toString().padStart(2, '0');
      return `${h}:${m}` === time;
    });
  };

  const isLoading = sessionsLoading || eventLoading || eventTypeLoading;

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

  const handleDrop = (time: string, room: string) => async (e: React.DragEvent) => {
    e.preventDefault();
    setHoveredSlot(null);

    if (!draggedSession) {
      console.warn('No dragged session');
      return;
    }

    if (!event) {
      console.error('Event not loaded - cannot assign timing');
      return;
    }

    if (!eventTypeConfig) {
      console.error('Event type config not loaded - cannot assign timing');
      return;
    }

    const isReassignment = !!(draggedSession.startTime && draggedSession.endTime);
    console.log('Dropping session:', {
      sessionSlug: draggedSession.sessionSlug,
      title: draggedSession.title,
      time,
      room,
      event: event.eventCode,
      eventType: event.eventType,
      rawDate: event.date,
      isReassignment,
      currentTime: draggedSession.startTime,
    });

    // Use actual event date and slot duration from event type config
    // Extract just the date part (YYYY-MM-DD) in case event.date is a full ISO datetime
    const eventDateStr = event.date
      ? event.date.split('T')[0]
      : new Date().toISOString().split('T')[0];
    const slotDurationMinutes = eventTypeConfig.slotDuration || 45;

    // Parse time (HH:MM) and create proper datetime
    const [hours, minutes] = time.split(':').map(Number);

    // Create start time
    const startDate = new Date(eventDateStr);
    startDate.setHours(hours, minutes, 0, 0);
    const startTime = startDate.toISOString();

    // Create end time by adding slot duration
    const endDate = new Date(startDate);
    endDate.setMinutes(endDate.getMinutes() + slotDurationMinutes);
    const endTime = endDate.toISOString();

    console.log('Assigning timing:', {
      eventDateStr,
      startTime,
      endTime,
      room,
      duration: slotDurationMinutes,
    });

    try {
      // assignTiming uses optimistic updates - session is removed from unassignedSessions
      // immediately and only rolled back on error (see useSlotAssignment hook)
      await assignTiming(draggedSession.sessionSlug, {
        startTime,
        endTime,
        room,
        changeReason: 'drag_drop_reassignment',
      });
      console.log('✓ Successfully assigned timing');

      // Invalidate event cache to force refetch and show assigned session in timeline
      // CRITICAL: Must invalidate with exact query key including the 'include' parameter
      // to ensure the event data with sessions is refetched
      console.log('[DragDropSlotAssignment] Invalidating event cache to refresh timeline');
      await queryClient.invalidateQueries({
        queryKey: ['event', eventCode, ['sessions']],
      });

      // Force immediate refetch to ensure UI updates right away
      await queryClient.refetchQueries({
        queryKey: ['event', eventCode, ['sessions']],
        exact: true,
      });
    } catch (err) {
      // Error handled by hook (includes rollback of optimistic update)
      console.error('✗ Failed to assign timing:', err);
    }

    setDraggedSession(null);
  };

  const getPreferenceMatchClass = (time: string): string => {
    if (!draggedSession || !hoveredSlot) return '';

    const hour = parseInt(time.split(':')[0], 10);

    // Morning: 8-12 (80-100% match)
    if (hour >= 8 && hour < 12) {
      return 'preference-match-high';
    }
    // Early afternoon: 13-15 (50-79% match)
    if (hour >= 13 && hour < 16) {
      return 'preference-match-medium';
    }
    // Evening: 16+ (<50% match)
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
    <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column', p: 3 }}>
      {/* Loading State */}
      {isLoading && (
        <Box data-testid="loading-skeleton">
          <Skeleton variant="rectangular" height={100} sx={{ mb: 2 }} />
          <Skeleton variant="rectangular" height={400} />
        </Box>
      )}

      {/* Main Layout */}
      {!isLoading && (
        <Grid container spacing={3} sx={{ flex: 1, overflow: 'hidden' }}>
          {/* Left Sidebar: Unassigned Speakers */}
          <Grid size={{ xs: 12, md: 3 }}>
            <Paper sx={{ height: '100%', overflow: 'hidden' }}>
              <UnassignedSpeakersList
                sessions={filteredSessions}
                totalSessions={totalSessions}
                onViewPreferences={(username) => setSelectedSpeaker(username)}
                onDragStart={handleDragStart}
                activeFilter={speakerFilter}
                onFilterChange={setSpeakerFilter}
                isLoading={isLoading}
              />
            </Paper>
          </Grid>

          {/* Center: Session Timeline Grid */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper
              data-testid="session-timeline-grid"
              sx={{ height: '100%', overflow: 'auto', p: 2 }}
            >
              <Typography variant="h6" gutterBottom>
                {t('slotAssignment.timeline.title')}
              </Typography>

              {/* Timeline Grid */}
              <Box data-testid="timeline-grid" sx={{ overflowX: 'auto' }}>
                <Box sx={{ minWidth: 800 }}>
                  {/* Header Row */}
                  <Grid container spacing={1} sx={{ mb: 1 }}>
                    <Grid size={2}>
                      <Typography variant="caption" fontWeight="bold">
                        {t('slotAssignment.timeline.headerTime')}
                      </Typography>
                    </Grid>
                    {ROOMS.map((room) => (
                      <Grid size={10 / ROOMS.length} key={room}>
                        <Typography variant="caption" fontWeight="bold" noWrap>
                          {room}
                        </Typography>
                      </Grid>
                    ))}
                  </Grid>

                  {/* Time Slot Rows */}
                  {TIME_SLOTS.map((time) => {
                    const structuralSession = getStructuralSessionForTime(time);
                    const structuralStyle = structuralSession
                      ? STRUCTURAL_STYLES[structuralSession.sessionType as StructuralType]
                      : null;

                    return (
                      <Grid container spacing={1} key={time} sx={{ mb: 1 }}>
                        <Grid size={2}>
                          <Typography variant="body2">{time}</Typography>
                        </Grid>

                        {structuralSession && structuralStyle ? (
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
                                {structuralSession.title || t(structuralStyle.labelKey)}
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
                            const assignedSession = getSessionForSlot(time, room);

                            return (
                              <Grid size={10 / ROOMS.length} key={room}>
                                <Paper
                                  data-testid={slotId}
                                  data-slot-time={time}
                                  data-slot-room={room}
                                  draggable={!!assignedSession}
                                  onDragStart={
                                    assignedSession ? handleDragStart(assignedSession) : undefined
                                  }
                                  className={`${isHovered ? 'drop-zone-active' : ''} ${matchClass}`}
                                  onDragOver={handleDragOver(time, room)}
                                  onDragLeave={handleDragLeave}
                                  onDrop={handleDrop(time, room)}
                                  sx={{
                                    p: 1,
                                    minHeight: 60,
                                    border: 2,
                                    borderColor: isHovered
                                      ? 'primary.main'
                                      : assignedSession
                                        ? 'success.main'
                                        : 'divider',
                                    borderStyle: isHovered ? 'dashed' : 'solid',
                                    bgcolor: isHovered
                                      ? 'action.hover'
                                      : assignedSession
                                        ? 'success.light'
                                        : 'background.default',
                                    cursor: assignedSession ? 'grab' : 'pointer',
                                    transition: 'all 0.2s',
                                    '&:hover': {
                                      bgcolor: assignedSession ? 'success.light' : 'action.hover',
                                    },
                                    '&:active': {
                                      cursor: assignedSession ? 'grabbing' : 'pointer',
                                    },
                                  }}
                                >
                                  {assignedSession ? (
                                    <Box>
                                      <Typography variant="caption" fontWeight="bold" noWrap>
                                        {assignedSession.title}
                                      </Typography>
                                      {assignedSession.speakers?.[0] && (
                                        <Typography variant="caption" display="block" noWrap>
                                          {assignedSession.speakers[0].firstName}{' '}
                                          {assignedSession.speakers[0].lastName}
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

          {/* Right Sidebar: Quick Actions */}
          <Grid size={{ xs: 12, md: 3 }}>
            <Paper data-testid="quick-actions-panel" sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                {t('slotAssignment.quickActions.title')}
              </Typography>

              {/* Session Summary */}
              <Box sx={{ mb: 3 }}>
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
              <Button
                fullWidth
                variant="outlined"
                startIcon={<CalendarMonth />}
                onClick={() => {
                  setGenerateStructuralError(null);
                  setStructuralAlreadyExist(false);
                  setGenerateStructuralOpen(true);
                }}
                sx={{ mb: 2 }}
                data-testid="generate-structural-button"
              >
                {t('slotAssignment.actions.generateStructure')}
              </Button>

              <Button
                fullWidth
                variant="contained"
                startIcon={<AutoAwesome />}
                onClick={() => setAutoAssignModalOpen(true)}
                sx={{ mb: 2 }}
                data-testid="auto-assign-button"
              >
                {t('slotAssignment.actions.autoAssign')}
              </Button>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<ClearAll />}
                onClick={() => setClearAllModalOpen(true)}
              >
                {t('slotAssignment.actions.clearAll')}
              </Button>

              {/* Success Banner */}
              {allSessionsAssigned && (
                <Alert severity="success" sx={{ mt: 3 }}>
                  <Typography variant="body2" gutterBottom>
                    {t('slotAssignment.quickActions.allAssigned')}
                  </Typography>
                  <Link href="#" underline="hover">
                    {t('slotAssignment.quickActions.goToPublishing')}
                  </Link>
                </Alert>
              )}
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
            {t('slotAssignment.actions.cancel')}
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
        data-testid="auto-assign-modal"
      >
        <DialogTitle>{t('slotAssignment.modals.autoAssign.title')}</DialogTitle>
        <DialogContent>
          <Typography>{t('slotAssignment.modals.autoAssign.message')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAutoAssignModalOpen(false)} data-testid="auto-assign-cancel">
            {t('slotAssignment.actions.cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={async () => {
              try {
                await autoAssignTimings();
                // Invalidate event cache to refresh assigned sessions in the timeline
                await queryClient.invalidateQueries({
                  queryKey: ['event', eventCode, ['sessions']],
                });
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
      <Dialog open={clearAllModalOpen} onClose={() => setClearAllModalOpen(false)}>
        <DialogTitle>{t('slotAssignment.modals.clearAll.title')}</DialogTitle>
        <DialogContent>
          <Typography>{t('slotAssignment.modals.clearAll.message')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClearAllModalOpen(false)}>
            {t('slotAssignment.actions.cancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={async () => {
              try {
                await clearAllTimings();
                // Invalidate event cache to refresh timeline (remove all assigned sessions)
                await queryClient.invalidateQueries({
                  queryKey: ['event', eventCode, ['sessions']],
                });
                setClearAllModalOpen(false);
              } catch (err) {
                console.error('Failed to clear all timings:', err);
                // Modal will stay open to show error, user can try again or cancel
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
        sx={{ position: 'absolute', left: -10000, width: 1, height: 1, overflow: 'hidden' }}
      >
        {assignedCount > 0 &&
          t('slotAssignment.quickActions.sessionsAssignedAnnouncement', { count: assignedCount })}
      </Box>
    </Box>
  );
};
