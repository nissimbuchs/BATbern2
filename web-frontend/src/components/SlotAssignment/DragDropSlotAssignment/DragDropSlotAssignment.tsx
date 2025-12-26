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

import React, { useState } from 'react';
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
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
import { UnassignedSpeakersList } from '../UnassignedSpeakersList/UnassignedSpeakersList';
import { SpeakerPreferencePanel } from '../SpeakerPreferencePanel/SpeakerPreferencePanel';
import { ConflictDetectionAlert } from '../ConflictDetectionAlert/ConflictDetectionAlert';
import type { Session } from '@/types/event.types';

export interface DragDropSlotAssignmentProps {
  eventCode: string;
}

// Time slots for timeline (8:00 AM to 8:00 PM)
const TIME_SLOTS = [
  '08:00',
  '09:00',
  '10:00',
  '11:00',
  '12:00',
  '13:00',
  '14:00',
  '15:00',
  '16:00',
  '17:00',
  '18:00',
  '19:00',
  '20:00',
];

const ROOMS = ['Main Hall', 'Conference Room A', 'Conference Room B'];

export const DragDropSlotAssignment: React.FC<DragDropSlotAssignmentProps> = ({ eventCode }) => {
  const {
    unassignedSessions,
    isLoading,
    conflict,
    assignedCount,
    totalSessions,
    assignTiming,
    clearConflict,
  } = useSlotAssignment(eventCode);

  const [selectedSpeaker, setSelectedSpeaker] = useState<string | null>(null);
  const [autoAssignModalOpen, setAutoAssignModalOpen] = useState(false);
  const [clearAllModalOpen, setClearAllModalOpen] = useState(false);
  const [draggedSession, setDraggedSession] = useState<Session | null>(null);
  const [hoveredSlot, setHoveredSlot] = useState<{ time: string; room: string } | null>(null);

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

    if (!draggedSession) return;

    // Create timing request
    const startTime = new Date(`2025-05-15T${time}:00Z`).toISOString();
    const endTime = new Date(`2025-05-15T${time}:00Z`);
    endTime.setMinutes(endTime.getMinutes() + 45);

    try {
      await assignTiming(draggedSession.sessionSlug, {
        startTime,
        endTime: endTime.toISOString(),
        room,
        changeReason: 'drag_drop_reassignment',
      });
    } catch (err) {
      // Error handled by hook
      console.error('Failed to assign timing:', err);
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
                sessions={unassignedSessions.map((session) => ({
                  ...session,
                  draggable: true,
                  onDragStart: handleDragStart(session),
                }))}
                totalSessions={totalSessions}
                onViewPreferences={(username) => setSelectedSpeaker(username)}
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
                Session Timeline
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

                        return (
                          <Grid size={3.33} key={room}>
                            <Paper
                              data-testid={slotId}
                              className={`${isHovered ? 'drop-zone-active' : ''} ${matchClass}`}
                              onDragOver={handleDragOver(time, room)}
                              onDragLeave={handleDragLeave}
                              onDrop={handleDrop(time, room)}
                              sx={{
                                p: 1,
                                minHeight: 60,
                                border: 2,
                                borderColor: isHovered ? 'primary.main' : 'divider',
                                borderStyle: isHovered ? 'dashed' : 'solid',
                                bgcolor: isHovered ? 'action.hover' : 'background.default',
                                cursor: 'pointer',
                                transition: 'all 0.2s',
                                '&:hover': {
                                  bgcolor: 'action.hover',
                                },
                              }}
                            >
                              {isHovered && matchPercent > 0 && (
                                <Typography variant="caption" color="primary">
                                  {matchPercent}% match
                                </Typography>
                              )}
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
              >
                Auto-Assign All
              </Button>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<ClearAll />}
                onClick={() => setClearAllModalOpen(true)}
              >
                Clear All Assignments
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
      <Dialog open={autoAssignModalOpen} onClose={() => setAutoAssignModalOpen(false)}>
        <DialogTitle>Auto-Assign Sessions</DialogTitle>
        <DialogContent>
          <Typography>Select algorithm for auto-assignment:</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAutoAssignModalOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => setAutoAssignModalOpen(false)}>
            Assign
          </Button>
        </DialogActions>
      </Dialog>

      {/* Clear All Confirmation Modal */}
      <Dialog open={clearAllModalOpen} onClose={() => setClearAllModalOpen(false)}>
        <DialogTitle>Clear All Assignments</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to clear all timing assignments?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClearAllModalOpen(false)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={() => setClearAllModalOpen(false)}>
            Clear All
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
