/**
 * Speaker Status Lanes Component (Story 5.4 + Story 11.D.2)
 *
 * Kanban-board style interface with drag-and-drop status lanes.
 * Each card surfaces a single state-aware primary-action button along its bottom edge,
 * plus a time-in-state chip on the organizer row. Lane order follows ADR-009 §0.1.
 *
 * The primary-action mapping is delegated to `./getPrimaryAction.ts` (the source of truth
 * is `docs/plans/speaker-workflow-refactor.md` §8.2).
 */

import React, { useMemo, useState } from 'react';
import {
  Grid,
  Card,
  Typography,
  Avatar,
  Box,
  Chip,
  Paper,
  Stack,
  Tooltip,
  Button,
  Snackbar,
  Alert,
} from '@mui/material';
import { CheckCircleOutline as CheckCircleOutlineIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { formatDistanceToNow } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
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
import { speakerPoolKeys, useSendInvitation } from '@/hooks/useSpeakerPool';
import { useOrganizers } from '@/components/shared/OrganizerSelect';
import { StatusChangeDialog } from './StatusChangeDialog';
import {
  getPrimaryAction,
  type PrimaryActionCallbacks,
  type SlotCapacityState,
} from './getPrimaryAction';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';
import type { SessionUI } from '@/types/event.types';

export interface SpeakerStatusLanesProps {
  eventCode: string;
  speakers: SpeakerPoolEntry[];
  sessions: SessionUI[];
  /**
   * Slot-capacity gate (READY → INVITED). Source: `speakerStatusSummary.maxSlotsAllowed`.
   * When 0 or undefined, the slot-capacity check is treated as "no max enforced".
   */
  maxSlots?: number;
  onStatusChange?: (speakerId: string, newStatus: SpeakerWorkflowState) => void;
  onIdentifiedToContacted?: (speaker: SpeakerPoolEntry) => void;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  /** Opens MarkContactedModal at parent. Story 11.D.2 — IDENTIFIED card button. */
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  /** Opens PromoteSpeakerDialog at parent. Story 11.D.2 — CONTACTED card button. */
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  /** Navigates to slot-assignment page. Story 11.D.2 — QUALITY_REVIEWED (unassigned). */
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

// Status color mapping. Story 11.D.2 (AC5 D) — CONFIRMED removed per ADR-009 §0.1.
const STATUS_COLORS: Record<string, string> = {
  IDENTIFIED: '#9e9e9e', // Grey
  CONTACTED: '#ffc107', // Amber
  READY: '#ff9800', // Orange
  INVITED: '#2196f3', // Blue
  ACCEPTED: '#4caf50', // Green
  CONTENT_SUBMITTED: '#fbc02d', // Yellow
  QUALITY_REVIEWED: '#7cb342', // Light Green
  DECLINED: '#f44336', // Red
};

// Lane order — ADR-009 §0.1: IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED →
// CONTENT_SUBMITTED → QUALITY_REVIEWED → DECLINED. Story 11.D.2 reordered to match.
const OUTREACH_LANES: SpeakerWorkflowState[] = ['IDENTIFIED', 'CONTACTED', 'READY', 'INVITED'];

const POST_ACCEPTANCE_LANES: SpeakerWorkflowState[] = [
  'ACCEPTED',
  'CONTENT_SUBMITTED',
  'QUALITY_REVIEWED',
  'DECLINED',
];

const STATUS_LANES: SpeakerWorkflowState[] = [...OUTREACH_LANES, ...POST_ACCEPTANCE_LANES];

/**
 * Resolves the "time in current state" timestamp per AC3. Uses per-state timestamps
 * where they exist, falling back to `updatedAt` / `createdAt`. The fallback is
 * imperfect (any field update bumps `updatedAt`) — see story 11.D.2 AC3 for rationale.
 */
// TODO: switch to a dedicated status_changed_at column if organizers report the fallback is misleading.
function getStatusChangedAt(speaker: SpeakerPoolEntry): string | undefined {
  switch (speaker.status) {
    case 'INVITED':
      if (speaker.invitedAt) return speaker.invitedAt;
      break;
    case 'ACCEPTED':
      if (speaker.acceptedAt) return speaker.acceptedAt;
      break;
    case 'CONTENT_SUBMITTED':
      if (speaker.contentSubmittedAt) return speaker.contentSubmittedAt;
      break;
    case 'DECLINED':
      if (speaker.declinedAt) return speaker.declinedAt;
      break;
  }
  return speaker.updatedAt ?? speaker.createdAt;
}

export const SpeakerStatusLanes: React.FC<SpeakerStatusLanesProps> = ({
  eventCode,
  speakers,
  sessions,
  maxSlots,
  onStatusChange,
  onIdentifiedToContacted,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onAssignSessionSlot,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();
  const { organizers } = useOrganizers();

  const [dialogState, setDialogState] = useState<{
    open: boolean;
    speaker?: SpeakerPoolEntry;
    newStatus?: SpeakerWorkflowState;
  }>({ open: false });

  const [activeSpeaker, setActiveSpeaker] = useState<SpeakerPoolEntry | null>(null);

  // Drag-and-drop sensors
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    })
  );

  // Slot-capacity gate (AC4). The READY → INVITED transition is blocked server-side
  // by `SpeakerWorkflowService.transition()` (Story 11.B.2). The same value is also
  // surfaced as a disabled-button tooltip here for fast feedback.
  // Slot capacity is derived in-page from the loaded speakers array — this is correct as long
  // as `speakers` is the complete event-scoped pool (it is today). If this query ever paginates,
  // switch to a server-side count endpoint to avoid undercounting off-page.
  // Post-acceptance states (CONTENT_SUBMITTED, QUALITY_REVIEWED) still occupy slots per ADR-009.
  const slotCapacity: SlotCapacityState = useMemo(() => {
    const acceptedCount = speakers.filter((s) =>
      ['ACCEPTED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED'].includes(s.status)
    ).length;
    const invitedCount = speakers.filter((s) => s.status === 'INVITED').length;
    const max = maxSlots ?? 0;
    return {
      reached: max > 0 && acceptedCount + invitedCount >= max,
      invited: invitedCount,
      accepted: acceptedCount,
      slots: max,
    };
  }, [speakers, maxSlots]);

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
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
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
      if (speaker.status === 'IDENTIFIED' && newStatus === 'CONTACTED') {
        updateStatusMutation.mutate(
          {
            speakerId: speaker.id,
            newStatus,
          },
          {
            onSuccess: () => {
              if (onIdentifiedToContacted) {
                onIdentifiedToContacted({ ...speaker, status: newStatus });
              }
            },
          }
        );
      } else if (newStatus === 'CONTENT_SUBMITTED' || newStatus === 'QUALITY_REVIEWED') {
        if (onSpeakerClick) onSpeakerClick(speaker);
      } else {
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

  const handleSpeakerClick = (speaker: SpeakerPoolEntry) => {
    if (onSpeakerClick) {
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
        <Stack spacing={2} sx={{ flex: 1, minHeight: 0 }}>
          {/* Outreach pipeline (IDENTIFIED → INVITED) */}
          <Grid container spacing={2}>
            {OUTREACH_LANES.map((status) => (
              <Grid size={{ xs: 12, sm: 6, md: 'grow' }} key={status} sx={{ display: 'flex' }}>
                <StatusLane
                  status={status}
                  speakers={speakersByStatus[status] || []}
                  sessions={sessions}
                  eventCode={eventCode}
                  color={STATUS_COLORS[status]}
                  organizers={organizers}
                  slotCapacity={slotCapacity}
                  onSpeakerClick={handleSpeakerClick}
                  onLogOutreach={onLogOutreach}
                  onPromoteSpeaker={onPromoteSpeaker}
                  onAssignSessionSlot={onAssignSessionSlot}
                />
              </Grid>
            ))}
          </Grid>

          {/* Post-acceptance pipeline (ACCEPTED → DECLINED) */}
          <Grid container spacing={2}>
            {POST_ACCEPTANCE_LANES.map((status) => (
              <Grid size={{ xs: 12, sm: 6, md: 'grow' }} key={status} sx={{ display: 'flex' }}>
                <StatusLane
                  status={status}
                  speakers={speakersByStatus[status] || []}
                  sessions={sessions}
                  eventCode={eventCode}
                  color={STATUS_COLORS[status]}
                  organizers={organizers}
                  slotCapacity={slotCapacity}
                  onSpeakerClick={handleSpeakerClick}
                  onLogOutreach={onLogOutreach}
                  onPromoteSpeaker={onPromoteSpeaker}
                  onAssignSessionSlot={onAssignSessionSlot}
                />
              </Grid>
            ))}
          </Grid>
        </Stack>

        <DragOverlay>
          {activeSpeaker ? (
            <SpeakerCard
              speaker={activeSpeaker}
              sessions={sessions}
              eventCode={eventCode}
              organizers={organizers}
              slotCapacity={slotCapacity}
              isDragging
            />
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
    </Box>
  );
};

// Status Lane Component
interface StatusLaneProps {
  status: SpeakerWorkflowState;
  speakers: SpeakerPoolEntry[];
  sessions: SessionUI[];
  eventCode: string;
  color: string;
  organizers: { id: string; name: string }[];
  slotCapacity: SlotCapacityState;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

const StatusLane: React.FC<StatusLaneProps> = ({
  status,
  speakers,
  sessions,
  eventCode,
  color,
  organizers,
  slotCapacity,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onAssignSessionSlot,
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
              eventCode={eventCode}
              organizers={organizers}
              slotCapacity={slotCapacity}
              onSpeakerClick={onSpeakerClick}
              onLogOutreach={onLogOutreach}
              onPromoteSpeaker={onPromoteSpeaker}
              onAssignSessionSlot={onAssignSessionSlot}
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
  eventCode: string;
  organizers?: { id: string; name: string }[];
  slotCapacity: SlotCapacityState;
  isDragging?: boolean;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

const SpeakerCard: React.FC<SpeakerCardProps> = ({
  speaker,
  sessions,
  eventCode,
  organizers = [],
  slotCapacity,
  isDragging = false,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onAssignSessionSlot,
}) => {
  const { t, i18n } = useTranslation(['organizer']);
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: speaker.id,
  });

  // Send-invitation flow (READY → INVITED) — used by the primary-action button.
  const sendInvitationMutation = useSendInvitation(eventCode);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarSeverity, setSnackbarSeverity] = useState<'success' | 'error'>('success');

  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      }
    : undefined;

  const handleClick = (e: React.MouseEvent) => {
    if (!transform && onSpeakerClick) {
      e.stopPropagation();
      onSpeakerClick(speaker);
    }
  };

  const handleSendInvitation = async (speakerForInvite: SpeakerPoolEntry) => {
    // Default response deadline = today + 30 days (matches OverviewTabPanel pattern).
    // SendInvitationRequest.responseDeadline is @NotNull @Future on the backend.
    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 30);
    const responseDeadline = defaultDeadline.toISOString().split('T')[0];

    try {
      await sendInvitationMutation.mutateAsync({
        username: speakerForInvite.id,
        options: { responseDeadline },
      });
      setSnackbarMessage(t('organizer:speakerCard.inviteSent'));
      setSnackbarSeverity('success');
      setSnackbarOpen(true);
    } catch {
      setSnackbarMessage(t('organizer:speakerCard.inviteFailed'));
      setSnackbarSeverity('error');
      setSnackbarOpen(true);
    }
  };

  // Find the session if speaker has sessionId (Story 5.6)
  const session = speaker.sessionId ? sessions.find((s) => s.id === speaker.sessionId) : null;

  // Time-in-state chip — AC3
  const statusChangedAt = getStatusChangedAt(speaker);
  const locale = i18n.language === 'de' ? de : enUS;
  // Guard against unparseable timestamps from the API — Invalid Date would otherwise
  // crash formatDistanceToNow with RangeError and unmount the kanban.
  const statusChangedDate = statusChangedAt ? new Date(statusChangedAt) : null;
  const isValidStatusChangedDate =
    statusChangedDate !== null && !Number.isNaN(statusChangedDate.getTime());
  const timeInState = isValidStatusChangedDate
    ? formatDistanceToNow(statusChangedDate, { locale, addSuffix: false })
    : null;
  const statusChangedAbsolute = isValidStatusChangedDate
    ? statusChangedDate.toLocaleString(i18n.language)
    : '';

  // Primary action mapping — AC1
  const callbacks: PrimaryActionCallbacks = {
    onLogOutreach: onLogOutreach ?? (() => undefined),
    onPromoteSpeaker: onPromoteSpeaker ?? (() => undefined),
    onSendInvitation: handleSendInvitation,
    onSpeakerClick: onSpeakerClick ?? (() => undefined),
    onAssignSessionSlot: onAssignSessionSlot ?? (() => undefined),
  };
  const primaryAction = getPrimaryAction(speaker, callbacks, slotCapacity, t);

  const assignedOrg = speaker.assignedOrganizerId
    ? organizers.find((o) => o.id === speaker.assignedOrganizerId)
    : null;

  return (
    <>
      <Card
        ref={setNodeRef}
        {...listeners}
        {...attributes}
        onClick={handleClick}
        data-testid={`speaker-card-${speaker.id}`}
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
          // Session view (speaker has assigned session)
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

            {(speaker.submittedTitle || speaker.contentStatus) && (
              <Box sx={{ mt: 1, pt: 1, borderTop: '1px dashed', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                  <Chip
                    label={speaker.contentStatus || 'SUBMITTED'}
                    size="small"
                    color={
                      speaker.contentStatus === 'APPROVED'
                        ? 'success'
                        : speaker.contentStatus === 'REVISION_NEEDED'
                          ? 'warning'
                          : 'info'
                    }
                    sx={{ height: 18, '& .MuiChip-label': { fontSize: '0.65rem', px: 1 } }}
                  />
                </Box>
                {speaker.submittedTitle && (
                  <Typography
                    variant="caption"
                    color="success.dark"
                    sx={{ display: 'block', fontWeight: 600 }}
                  >
                    {speaker.submittedTitle}
                  </Typography>
                )}
                {speaker.submittedAbstract && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      mt: 0.5,
                    }}
                  >
                    {speaker.submittedAbstract}
                  </Typography>
                )}
              </Box>
            )}
          </Box>
        ) : (
          // Pool view (no assigned session)
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

            {/* Organizer + time-in-state row (AC3) */}
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                mt: 0.5,
              }}
              data-testid={`organizer-row-${speaker.id}`}
            >
              {assignedOrg && (
                <Chip
                  size="small"
                  label={assignedOrg.name}
                  avatar={
                    <Avatar sx={{ width: 18, height: 18, fontSize: '0.6rem' }}>
                      {assignedOrg.name
                        .split(' ')
                        .map((n) => n[0])
                        .join('')
                        .slice(0, 2)}
                    </Avatar>
                  }
                  variant="outlined"
                  sx={{
                    height: 20,
                    '& .MuiChip-label': { fontSize: '0.65rem', px: 0.5 },
                  }}
                  data-testid={`assigned-organizer-chip-${speaker.id}`}
                />
              )}
              {/* TODO(11.D.3): apply threshold-driven colour coding per §8.7 */}
              {timeInState && (
                <Tooltip
                  title={t('organizer:speakerCard.timeInStateTooltip', {
                    date: statusChangedAbsolute,
                  })}
                >
                  <Chip
                    size="small"
                    variant="outlined"
                    label={timeInState}
                    color="default"
                    sx={{
                      ml: 'auto',
                      height: 20,
                      '& .MuiChip-label': { fontSize: '0.65rem', px: 0.5 },
                    }}
                    data-testid={`time-in-state-chip-${speaker.id}`}
                  />
                </Tooltip>
              )}
            </Box>

            {/* Speaker Response Details (Story 6.2a) */}
            {speaker.acceptedAt &&
              (speaker.preferredTimeSlot ||
                speaker.travelRequirements ||
                speaker.initialPresentationTitle) && (
                <Box sx={{ mt: 1, pt: 1, borderTop: '1px dashed', borderColor: 'divider' }}>
                  {speaker.initialPresentationTitle && (
                    <Typography
                      variant="caption"
                      color="primary.main"
                      sx={{ display: 'block', fontWeight: 500 }}
                    >
                      {speaker.initialPresentationTitle}
                    </Typography>
                  )}
                  {speaker.preferredTimeSlot && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {t('organizer:speakers.preferredTimeSlot')}: {speaker.preferredTimeSlot}
                    </Typography>
                  )}
                  {speaker.travelRequirements && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {t('organizer:speakers.travelRequirements')}: {speaker.travelRequirements}
                    </Typography>
                  )}
                  {speaker.technicalRequirements && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {t('organizer:speakers.technicalRequirements')}:{' '}
                      {speaker.technicalRequirements}
                    </Typography>
                  )}
                  {speaker.preferenceComments && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', fontStyle: 'italic', mt: 0.5 }}
                    >
                      "{speaker.preferenceComments}"
                    </Typography>
                  )}
                </Box>
              )}

            {/* Submitted Content Display (Story 6.3) */}
            {(speaker.submittedTitle || speaker.contentStatus) && (
              <Box sx={{ mt: 1, pt: 1, borderTop: '1px dashed', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                  <Chip
                    label={speaker.contentStatus || 'SUBMITTED'}
                    size="small"
                    color={
                      speaker.contentStatus === 'APPROVED'
                        ? 'success'
                        : speaker.contentStatus === 'REVISION_NEEDED'
                          ? 'warning'
                          : 'info'
                    }
                    sx={{ height: 18, '& .MuiChip-label': { fontSize: '0.65rem', px: 1 } }}
                  />
                </Box>
                {speaker.submittedTitle && (
                  <Typography
                    variant="caption"
                    color="success.dark"
                    sx={{ display: 'block', fontWeight: 600 }}
                  >
                    {speaker.submittedTitle}
                  </Typography>
                )}
                {speaker.submittedAbstract && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      mt: 0.5,
                    }}
                  >
                    {speaker.submittedAbstract}
                  </Typography>
                )}
              </Box>
            )}

            {speaker.status === 'DECLINED' && speaker.declineReason && (
              <Box sx={{ mt: 1, pt: 1, borderTop: '1px dashed', borderColor: 'divider' }}>
                <Typography variant="caption" color="error.main" sx={{ display: 'block' }}>
                  {t('organizer:speakers.declineReason')}: {speaker.declineReason}
                </Typography>
              </Box>
            )}
          </Box>
        )}

        {/* Primary-action button or info chip (AC1, AC6) — full-width along card bottom */}
        {primaryAction.kind !== 'none' && !isDragging && (
          <Box
            sx={{
              mt: 1.5,
              pt: 1.5,
              borderTop: '1px solid',
              borderColor: 'divider',
            }}
          >
            {primaryAction.kind === 'chip' ? (
              <Tooltip title={primaryAction.tooltip ?? ''}>
                <Chip
                  label={primaryAction.label}
                  icon={<CheckCircleOutlineIcon fontSize="small" />}
                  color="success"
                  variant="outlined"
                  size="small"
                  sx={{ width: '100%' }}
                  data-testid={`primary-action-chip-${speaker.id}`}
                />
              </Tooltip>
            ) : primaryAction.tooltip ? (
              <Tooltip title={primaryAction.tooltip}>
                <span data-testid={`primary-action-tooltip-${speaker.id}`}>
                  <Button
                    variant="contained"
                    fullWidth
                    disabled={
                      primaryAction.disabled ||
                      !!transform ||
                      (primaryAction.testIdSuffix === 'sendInvitation' &&
                        sendInvitationMutation.isPending)
                    }
                    onClick={(e) => {
                      e.stopPropagation();
                      if (!transform) primaryAction.onClick();
                    }}
                    data-testid={`primary-action-button-${speaker.id}`}
                    data-action={primaryAction.testIdSuffix}
                  >
                    {primaryAction.label}
                  </Button>
                </span>
              </Tooltip>
            ) : (
              <Button
                variant="contained"
                fullWidth
                disabled={
                  primaryAction.disabled ||
                  !!transform ||
                  (primaryAction.testIdSuffix === 'sendInvitation' &&
                    sendInvitationMutation.isPending)
                }
                onClick={(e) => {
                  e.stopPropagation();
                  if (!transform) primaryAction.onClick();
                }}
                data-testid={`primary-action-button-${speaker.id}`}
                data-action={primaryAction.testIdSuffix}
              >
                {primaryAction.label}
              </Button>
            )}
          </Box>
        )}
      </Card>

      {/* Invitation Feedback Snackbar */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={4000}
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbarOpen(false)}
          severity={snackbarSeverity}
          sx={{ width: '100%' }}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </>
  );
};

export default SpeakerStatusLanes;
