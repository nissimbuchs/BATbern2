/**
 * Speaker Status Lanes Component (Story 5.4)
 *
 * Kanban-board style interface with drag-and-drop status lanes
 * Features:
 * - Drag speakers between status lanes (OPEN, CONTACTED, READY, ACCEPTED, DECLINED)
 * - @dnd-kit for drag-and-drop functionality
 * - Status change confirmation dialog
 * - Color-coded status indicators
 * - i18n support (German/English)
 * - Real-time updates via React Query
 */

import React, { useState } from 'react';
import {
  Grid,
  Card,
  Typography,
  Avatar,
  Box,
  Chip,
  Paper,
  Stack,
  Alert,
  Snackbar,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { UserAvatar } from '@/components/shared/UserAvatar';
import {
  DndContext,
  DragEndEvent,
  DragStartEvent,
  DragOverlay,
  closestCenter,
  useSensor,
  useSensors,
  PointerSensor,
  useDraggable,
  useDroppable,
} from '@dnd-kit/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import { StatusChangeDialog } from './StatusChangeDialog';
import { ContentSubmissionDrawer } from './ContentSubmissionDrawer';
import { QualityReviewDrawer } from './QualityReviewDrawer';
import { SendInvitationModal } from '@/components/organizer/SendInvitation';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { components } from '@/types/generated/speakers-api.types';
import type { SessionUI } from '@/types/event.types';

type SpeakerWorkflowState = components['schemas']['SpeakerWorkflowState'];

export interface SpeakerStatusLanesProps {
  eventCode: string;
  speakers: SpeakerPoolEntry[];
  sessions: SessionUI[];
  onStatusChange?: (speakerId: string, newStatus: SpeakerWorkflowState) => void;
  onIdentifiedToContacted?: (speaker: SpeakerPoolEntry) => void; // Callback for IDENTIFIED → CONTACTED transition
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void; // Callback for speaker card clicks (for statuses without specific drawers)
}

// Status color mapping (Story 5.5 - Extended to 7 lanes)
const STATUS_COLORS: Record<string, string> = {
  IDENTIFIED: '#9e9e9e', // Grey
  CONTACTED: '#ffc107', // Amber
  READY: '#ff9800', // Orange
  ACCEPTED: '#4caf50', // Green
  CONTENT_SUBMITTED: '#fbc02d', // Yellow (NEW - Story 5.5)
  QUALITY_REVIEWED: '#7cb342', // Light Green (NEW - Story 5.5)
  CONFIRMED: '#2e7d32', // Dark Green (NEW - Story 5.5)
  DECLINED: '#f44336', // Red
};

// Status lanes to display (Story 5.5 - Extended from 5 to 7 lanes)
const STATUS_LANES: SpeakerWorkflowState[] = [
  'IDENTIFIED',
  'CONTACTED',
  'READY',
  'ACCEPTED',
  'CONTENT_SUBMITTED', // NEW - Story 5.5
  'QUALITY_REVIEWED', // NEW - Story 5.5
  'CONFIRMED', // NEW - Story 5.5
  'DECLINED',
];

export const SpeakerStatusLanes: React.FC<SpeakerStatusLanesProps> = ({
  eventCode,
  speakers,
  sessions,
  onStatusChange,
  onIdentifiedToContacted,
  onSpeakerClick,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();

  const [dialogState, setDialogState] = useState<{
    open: boolean;
    speaker?: SpeakerPoolEntry;
    newStatus?: SpeakerWorkflowState;
  }>({ open: false });

  const [activeSpeaker, setActiveSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Content submission drawer state (Story 5.5)
  const [contentDrawerOpen, setContentDrawerOpen] = useState(false);
  const [currentSpeaker, setCurrentSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Quality review drawer state (Story 5.5 Phase 4)
  const [reviewDrawerOpen, setReviewDrawerOpen] = useState(false);
  const [reviewSpeaker, setReviewSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Send invitation modal state (Story 6.3)
  const [invitationModalOpen, setInvitationModalOpen] = useState(false);
  const [invitationSpeaker, setInvitationSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Error snackbar state
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Drag-and-drop sensors
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8, // 8px movement required to activate drag
      },
    })
  );

  // Mutation for updating speaker status
  const updateStatusMutation = useMutation({
    mutationFn: ({
      speakerId,
      newStatus,
      reason,
    }: {
      speakerId: string;
      newStatus: SpeakerWorkflowState;
      reason?: string;
    }) => speakerStatusService.updateStatus(eventCode, speakerId, newStatus, reason),
    onSuccess: (_, variables) => {
      // Invalidate queries to refetch data (using correct query keys)
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      // Invalidate event cache to update sessions (Story 5.6)
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });

      if (onStatusChange) {
        onStatusChange(variables.speakerId, variables.newStatus);
      }
    },
  });

  const handleDragStart = (event: DragStartEvent) => {
    const speakerId = event.active.id as string;
    const speaker = speakers.find((s) => s.id === speakerId);
    setActiveSpeaker(speaker || null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveSpeaker(null);

    if (!over || active.id === over.id) {
      return;
    }

    const speakerId = active.id as string;
    const newStatus = over.id as SpeakerWorkflowState;
    const speaker = speakers.find((s) => s.id === speakerId);

    if (speaker && speaker.status !== newStatus) {
      // Special handling for IDENTIFIED → CONTACTED - trigger callback (Story 5.6)
      if (speaker.status === 'IDENTIFIED' && newStatus === 'CONTACTED') {
        // Validate that speaker has an email address before contacting
        if (!speaker.email?.trim()) {
          setErrorMessage(t('organizer:speakerStatus.emailRequiredForContact'));
          return;
        }
        // First update the status
        updateStatusMutation.mutate(
          {
            speakerId: speaker.id,
            newStatus,
          },
          {
            onSuccess: () => {
              // Then trigger the callback to open drawer
              if (onIdentifiedToContacted) {
                onIdentifiedToContacted({ ...speaker, status: newStatus });
              }
            },
          }
        );
      }
      // Special handling for CONTENT_SUBMITTED - open drawer instead of dialog
      else if (newStatus === 'CONTENT_SUBMITTED') {
        setCurrentSpeaker(speaker);
        setContentDrawerOpen(true);
      }
      // Special handling for QUALITY_REVIEWED - open quality review drawer (Story 5.5 Phase 4)
      else if (newStatus === 'QUALITY_REVIEWED') {
        setReviewSpeaker(speaker);
        setReviewDrawerOpen(true);
      } else {
        // Open confirmation dialog for other status changes
        setDialogState({
          open: true,
          speaker,
          newStatus,
        });
      }
    }
  };

  const handleConfirmStatusChange = (reason?: string) => {
    if (dialogState.speaker && dialogState.newStatus) {
      updateStatusMutation.mutate({
        speakerId: dialogState.speaker.id,
        newStatus: dialogState.newStatus,
        reason,
      });
    }
    setDialogState({ open: false });
  };

  const handleCancelStatusChange = () => {
    setDialogState({ open: false });
  };

  // Handle speaker card click (Story 5.5 + 5.6 + 6.3)
  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    // Open content submission drawer for ACCEPTED speakers
    if (speaker.status === 'ACCEPTED') {
      setCurrentSpeaker(speaker);
      setContentDrawerOpen(true);
    }
    // Open quality review drawer for CONTENT_SUBMITTED speakers (Story 5.5 Phase 4)
    else if (speaker.status === 'CONTENT_SUBMITTED') {
      setReviewSpeaker(speaker);
      setReviewDrawerOpen(true);
    }
    // Open send invitation modal for IDENTIFIED, CONTACTED, READY speakers (Story 6.3)
    else if (
      speaker.status === 'IDENTIFIED' ||
      speaker.status === 'CONTACTED' ||
      speaker.status === 'READY'
    ) {
      setInvitationSpeaker(speaker);
      setInvitationModalOpen(true);
    }
    // For other statuses, use callback if provided
    else if (onSpeakerClick) {
      onSpeakerClick(speaker);
    }
  };

  // Group speakers by status
  const speakersByStatus = STATUS_LANES.reduce(
    (acc, status) => {
      acc[status] = speakers.filter((s) => s.status === status);
      return acc;
    },
    {} as Record<SpeakerWorkflowState, SpeakerPoolEntry[]>
  );

  return (
    <Box sx={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h6" gutterBottom>
        {t('organizer:speakerStatus.lanes')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('organizer:speakerStatus.dragToChange')}
      </Typography>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <Grid container spacing={2} sx={{ width: '100%', flex: 1, minHeight: 0 }}>
          {STATUS_LANES.map((status) => (
            <Grid size={{ xs: 12, sm: 6, md: 3, lg: 1.5 }} key={status} sx={{ display: 'flex' }}>
              <StatusLane
                status={status}
                speakers={speakersByStatus[status] || []}
                sessions={sessions}
                color={STATUS_COLORS[status]}
                onSpeakerClick={handleSpeakerClick}
              />
            </Grid>
          ))}
        </Grid>

        <DragOverlay>
          {activeSpeaker ? (
            <SpeakerCard speaker={activeSpeaker} sessions={sessions} isDragging />
          ) : null}
        </DragOverlay>
      </DndContext>

      {/* Status Change Confirmation Dialog */}
      {dialogState.speaker && dialogState.newStatus && (
        <StatusChangeDialog
          open={dialogState.open}
          speakerName={dialogState.speaker.speakerName}
          currentStatus={dialogState.speaker.status}
          newStatus={dialogState.newStatus}
          onConfirm={handleConfirmStatusChange}
          onCancel={handleCancelStatusChange}
        />
      )}

      {/* Content Submission Drawer (Story 5.5) */}
      <ContentSubmissionDrawer
        open={contentDrawerOpen}
        onClose={() => setContentDrawerOpen(false)}
        speaker={currentSpeaker}
        eventCode={eventCode}
      />

      {/* Quality Review Drawer (Story 5.5 Phase 4) */}
      <QualityReviewDrawer
        open={reviewDrawerOpen}
        onClose={() => setReviewDrawerOpen(false)}
        speaker={reviewSpeaker}
        eventCode={eventCode}
      />

      {/* Send Invitation Modal (Story 6.3) */}
      <SendInvitationModal
        open={invitationModalOpen}
        onClose={() => setInvitationModalOpen(false)}
        onSuccess={() => setInvitationModalOpen(false)}
        speaker={invitationSpeaker}
        eventCode={eventCode}
      />

      {/* Error Snackbar */}
      <Snackbar
        open={!!errorMessage}
        autoHideDuration={6000}
        onClose={() => setErrorMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setErrorMessage(null)} severity="error" sx={{ width: '100%' }}>
          {errorMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
};

// Status Lane Component
interface StatusLaneProps {
  status: SpeakerWorkflowState;
  speakers: SpeakerPoolEntry[];
  sessions: SessionUI[];
  color: string;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
}

const StatusLane: React.FC<StatusLaneProps> = ({
  status,
  speakers,
  sessions,
  color,
  onSpeakerClick,
}) => {
  const { t } = useTranslation(['organizer']);
  const { setNodeRef } = useDroppable({
    id: status,
  });

  return (
    <Paper
      ref={setNodeRef}
      data-testid={`status-lane-${status.toLowerCase()}`}
      sx={{
        p: 2,
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'background.default',
        borderTop: `4px solid ${color}`,
      }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography
          variant="h6"
          sx={{ color }}
          data-testid={`status-lane-heading-${status.toLowerCase()}`}
        >
          {t(`organizer:speakerStatus.${status}`)}
        </Typography>
        <Chip
          label={speakers.length}
          size="small"
          sx={{ backgroundColor: color, color: 'white' }}
        />
      </Box>

      <Box sx={{ flexGrow: 1, overflow: 'auto', minHeight: 0 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {speakers.map((speaker) => (
            <SpeakerCard
              key={speaker.id}
              speaker={speaker}
              sessions={sessions}
              onSpeakerClick={onSpeakerClick}
            />
          ))}
        </Box>
      </Box>
    </Paper>
  );
};

// Speaker Card Component
interface SpeakerCardProps {
  speaker: SpeakerPoolEntry;
  sessions: SessionUI[];
  isDragging?: boolean;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
}

const SpeakerCard: React.FC<SpeakerCardProps> = ({
  speaker,
  sessions,
  isDragging = false,
  onSpeakerClick,
}) => {
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: speaker.id,
  });

  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      }
    : undefined;

  const handleClick = (e: React.MouseEvent) => {
    // Only trigger click if not dragging
    if (!transform && onSpeakerClick) {
      e.stopPropagation();
      onSpeakerClick(speaker);
    }
  };

  // Find the session if speaker has sessionId (Story 5.6)
  const session = speaker.sessionId ? sessions.find((s) => s.id === speaker.sessionId) : null;

  return (
    <Card
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      onClick={handleClick}
      sx={{
        p: 2,
        cursor: 'grab',
        opacity: isDragging ? 0.5 : 1,
        '&:hover': {
          boxShadow: 3,
        },
        ...style,
      }}
    >
      {session && session.speakers && session.speakers.length > 0 ? (
        // Display session details when speaker has sessionId
        <Box>
          <Typography variant="subtitle2" fontWeight="bold" sx={{ mb: 1 }}>
            {session.title}
          </Typography>
          <Stack direction="column" spacing={0.5}>
            {session.speakers.map((spk) => (
              <UserAvatar
                key={spk.username}
                firstName={spk.firstName}
                lastName={spk.lastName}
                company={spk.company}
                profilePictureUrl={spk.profilePictureUrl}
                size={32}
                showCompany={true}
                horizontal={true}
              />
            ))}
          </Stack>
        </Box>
      ) : (
        // Display speaker pool info when no session
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}>
              {speaker.speakerName.charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Typography variant="subtitle2">{speaker.speakerName}</Typography>
              {speaker.company && (
                <Typography variant="caption" color="text.secondary">
                  {speaker.company}
                </Typography>
              )}
            </Box>
          </Box>
          {speaker.expertise && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              {speaker.expertise}
            </Typography>
          )}
          {speaker.proposedPresentationTitle && (
            <Typography
              variant="caption"
              color="primary.main"
              sx={{ mt: 0.5, display: 'block', fontStyle: 'italic' }}
            >
              📋 {speaker.proposedPresentationTitle}
            </Typography>
          )}
        </Box>
      )}
    </Card>
  );
};

export default SpeakerStatusLanes;
