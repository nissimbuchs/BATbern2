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
import { AutoAwesome, ClearAll } from '@mui/icons-material';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
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

  console.log('[DragDropSlotAssignment] Event data:', {
    eventCode,
    eventType: event?.eventType,
    sessionsCount: event?.sessions?.length,
  });

  // Fetch event type configuration to get slot definitions
  const { data: eventTypeConfig, isLoading: eventTypeLoading } = useEventType(
    event?.eventType || 'FULL_DAY'
  );

  console.log('[DragDropSlotAssignment] Event type config:', eventTypeConfig);

  // State must be declared before useMemo that depends on it
  const [selectedSpeaker, setSelectedSpeaker] = useState<string | null>(null);
  const [autoAssignModalOpen, setAutoAssignModalOpen] = useState(false);
  const [clearAllModalOpen, setClearAllModalOpen] = useState(false);
  const [draggedSession, setDraggedSession] = useState<Session | null>(null);
  const [hoveredSlot, setHoveredSlot] = useState<{ time: string; room: string } | null>(null);
  const [speakerFilter, setSpeakerFilter] = useState<'all' | 'assigned' | 'unassigned'>(
    'unassigned'
  );

  // Extract assigned sessions from event data
  const assignedSessions = useMemo(() => {
    if (!event?.sessions) return [];
    // Filter sessions that have timing assigned (startTime and endTime are set)
    return event.sessions.filter((session) => session.startTime && session.endTime && session.room);
  }, [event?.sessions]);

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

  // Generate time slots dynamically from event type configuration PLUS assigned sessions
  // TIME_SLOTS are in LOCAL time for display to the user
  const TIME_SLOTS = useMemo(() => {
    console.log('[TIME_SLOTS] Building slots, eventTypeConfig:', eventTypeConfig);

    const slots: string[] = [];

    // First, add times from assigned sessions converted to LOCAL time (so they're visible in the grid)
    if (assignedSessions.length > 0) {
      assignedSessions.forEach((session) => {
        if (session.startTime) {
          const sessionStart = new Date(session.startTime);
          // Use getHours/getMinutes (LOCAL time) for display
          const sessionHours = sessionStart.getHours().toString().padStart(2, '0');
          const sessionMinutes = sessionStart.getMinutes().toString().padStart(2, '0');
          const timeSlot = `${sessionHours}:${sessionMinutes}`;
          if (!slots.includes(timeSlot)) {
            slots.push(timeSlot);
          }
        }
      });
    }

    // Then, add predefined slots from event type config (also in local time)
    if (eventTypeConfig) {
      const startTime = eventTypeConfig.typicalStartTime || '09:00';
      const endTime = eventTypeConfig.typicalEndTime || '17:00';
      const slotDurationMinutes = eventTypeConfig.slotDuration || 45;
      const breakCount = eventTypeConfig.breakSlots || 0;
      const lunchCount = eventTypeConfig.lunchSlots || 0;
      const totalSlots = eventTypeConfig.maxSlots || 8;

      // Parse start time and create local date
      const [startHour, startMinute] = startTime.split(':').map(Number);
      const currentTime = new Date();
      currentTime.setHours(startHour, startMinute, 0, 0);

      // Generate slots including session slots + breaks + lunch
      const totalSlotsWithBreaks = totalSlots + breakCount + lunchCount;
      for (let i = 0; i < totalSlotsWithBreaks; i++) {
        const hours = currentTime.getHours().toString().padStart(2, '0');
        const minutes = currentTime.getMinutes().toString().padStart(2, '0');
        const timeSlot = `${hours}:${minutes}`;

        // Only add if before end time and not already added from assigned sessions
        const [endHour, endMinute] = endTime.split(':').map(Number);
        if (
          (currentTime.getHours() < endHour ||
            (currentTime.getHours() === endHour && currentTime.getMinutes() < endMinute)) &&
          !slots.includes(timeSlot)
        ) {
          slots.push(timeSlot);
        }

        // Move to next slot
        currentTime.setMinutes(currentTime.getMinutes() + slotDurationMinutes);
      }
    } else {
      // Fallback to default slots if config not loaded and no assigned sessions
      if (slots.length === 0) {
        console.warn(
          '[TIME_SLOTS] No eventTypeConfig and no assigned sessions, using fallback slots'
        );
        slots.push('09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00');
      }
    }

    // Sort slots chronologically
    slots.sort((a, b) => {
      const [aH, aM] = a.split(':').map(Number);
      const [bH, bM] = b.split(':').map(Number);
      return aH * 60 + aM - (bH * 60 + bM);
    });

    console.log('[TIME_SLOTS generated (local time)]', slots);
    return slots;
  }, [eventTypeConfig, assignedSessions]);

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

      console.log('[getSessionForSlot]', {
        sessionSlug: session.sessionSlug,
        startTime: session.startTime,
        parsedTimeLocal: sessionTime,
        lookingFor: time,
        match: sessionTime === time,
        room: session.room,
        matchingRoom: session.room === room,
      });

      return sessionTime === time;
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
                        Time
                      </Typography>
                    </Grid>
                    {ROOMS.map((room) => (
                      <Grid size={3.33} key={room}>
                        <Typography variant="caption" fontWeight="bold" noWrap>
                          {room}
                        </Typography>
                      </Grid>
                    ))}
                  </Grid>

                  {/* Time Slot Rows */}
                  {TIME_SLOTS.map((time) => (
                    <Grid container spacing={1} key={time} sx={{ mb: 1 }}>
                      <Grid size={2}>
                        <Typography variant="body2">{time}</Typography>
                      </Grid>
                      {ROOMS.map((room) => {
                        const slotId = `slot-${time}-${room.replace(/\s+/g, '-')}`;
                        const isHovered = hoveredSlot?.time === time && hoveredSlot?.room === room;
                        const matchClass = isHovered ? getPreferenceMatchClass(time) : '';
                        const matchPercent = isHovered ? getPreferenceMatchPercentage(time) : 0;
                        const assignedSession = getSessionForSlot(time, room);

                        return (
                          <Grid size={3.33} key={room}>
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
                      })}
                    </Grid>
                  ))}
                </Box>
              </Box>
            </Paper>
          </Grid>

          {/* Right Sidebar: Quick Actions */}
          <Grid size={{ xs: 12, md: 3 }}>
            <Paper data-testid="quick-actions-panel" sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Quick Actions
              </Typography>

              {/* Session Summary */}
              <Box sx={{ mb: 3 }}>
                <Typography variant="body2" color="text.secondary">
                  {totalSessions} Total Sessions
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {assignedCount} Assigned
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {unassignedSessions.length} Pending
                </Typography>
              </Box>

              {/* Action Buttons */}
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
                    All timings assigned!
                  </Typography>
                  <Link href="#" underline="hover">
                    Go to Publishing Tab
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
        {assignedCount > 0 && `${assignedCount} sessions assigned successfully`}
      </Box>
    </Box>
  );
};
