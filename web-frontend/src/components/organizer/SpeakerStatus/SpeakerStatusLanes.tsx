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
import { Grid, Card, Typography, Avatar, Box, Chip, Paper } from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  closestCenter,
  useSensor,
  useSensors,
  PointerSensor,
} from '@dnd-kit/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { StatusChangeDialog } from './StatusChangeDialog';
import type { components } from '@/types/generated/speakers-api.types';

type SpeakerWorkflowState = components['schemas']['SpeakerWorkflowState'];

export interface Speaker {
  id: string;
  speakerName: string;
  company?: string;
  expertise?: string;
  status: SpeakerWorkflowState;
  assignedOrganizerId?: string;
}

export interface SpeakerStatusLanesProps {
  eventCode: string;
  speakers: Speaker[];
  onStatusChange?: (speakerId: string, newStatus: SpeakerWorkflowState) => void;
}

// Status color mapping
const STATUS_COLORS: Record<string, string> = {
  open: '#9e9e9e',
  contacted: '#ffc107',
  ready: '#ff9800',
  accepted: '#4caf50',
  declined: '#f44336',
};

// Status lanes to display
const STATUS_LANES: SpeakerWorkflowState[] = ['open', 'contacted', 'ready', 'accepted', 'declined'];

export const SpeakerStatusLanes: React.FC<SpeakerStatusLanesProps> = ({
  eventCode,
  speakers,
  onStatusChange,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();

  const [dialogState, setDialogState] = useState<{
    open: boolean;
    speaker?: Speaker;
    newStatus?: SpeakerWorkflowState;
  }>({ open: false });

  const [activeSpeaker, setActiveSpeaker] = useState<Speaker | null>(null);

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
      // Invalidate queries to refetch data
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['speakerPool', eventCode] });

      if (onStatusChange) {
        onStatusChange(variables.speakerId, variables.newStatus);
      }
    },
  });

  const handleDragStart = (event: { active: { id: string } }) => {
    const speakerId = event.active.id;
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
      // Open confirmation dialog
      setDialogState({
        open: true,
        speaker,
        newStatus,
      });
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

  // Group speakers by status
  const speakersByStatus = STATUS_LANES.reduce(
    (acc, status) => {
      acc[status] = speakers.filter((s) => s.status === status);
      return acc;
    },
    {} as Record<SpeakerWorkflowState, Speaker[]>
  );

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('organizer:speakerStatus.dragToChange')}
      </Typography>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <Grid container spacing={2}>
          {STATUS_LANES.map((status) => (
            <Grid item xs={12} md={2.4} key={status}>
              <StatusLane
                status={status}
                speakers={speakersByStatus[status] || []}
                color={STATUS_COLORS[status]}
              />
            </Grid>
          ))}
        </Grid>

        <DragOverlay>
          {activeSpeaker ? <SpeakerCard speaker={activeSpeaker} isDragging /> : null}
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
    </>
  );
};

// Status Lane Component
interface StatusLaneProps {
  status: SpeakerWorkflowState;
  speakers: Speaker[];
  color: string;
}

const StatusLane: React.FC<StatusLaneProps> = ({ status, speakers, color }) => {
  const { t } = useTranslation(['organizer']);

  return (
    <Paper
      id={status}
      sx={{
        p: 2,
        minHeight: '500px',
        backgroundColor: 'background.default',
        borderTop: `4px solid ${color}`,
      }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6" sx={{ color }}>
          {t(`organizer:speakerStatus.${status}`)}
        </Typography>
        <Chip
          label={speakers.length}
          size="small"
          sx={{ backgroundColor: color, color: 'white' }}
        />
      </Box>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {speakers.map((speaker) => (
          <SpeakerCard key={speaker.id} speaker={speaker} />
        ))}
      </Box>
    </Paper>
  );
};

// Speaker Card Component
interface SpeakerCardProps {
  speaker: Speaker;
  isDragging?: boolean;
}

const SpeakerCard: React.FC<SpeakerCardProps> = ({ speaker, isDragging = false }) => {
  return (
    <Card
      id={speaker.id}
      sx={{
        p: 2,
        cursor: 'grab',
        opacity: isDragging ? 0.5 : 1,
        '&:hover': {
          boxShadow: 3,
        },
      }}
    >
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
    </Card>
  );
};

export default SpeakerStatusLanes;
