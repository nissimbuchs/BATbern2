/**
 * Speaker Status Board (Story 5.4 + Story 11.D.2–4 + Epic 14 Story 14.C.2 / 14.C.3)
 *
 * 4-phase kanban: the eight workflow states are grouped into four phase columns —
 * `Sourcing` (IDENTIFIED·CONTACTED), `Inviting` (READY·INVITED), `Content`
 * (ACCEPTED·CONTENT_SUBMITTED), `Confirmed` (QUALITY_REVIEWED). Each card keeps its
 * exact 8-state chip. Within a column, "your move" cards (error/warning severity)
 * carry an accent left border and sort above a faint divider; "waiting on speaker"
 * (normal severity) cards sit below it (FR16). The Confirmed column surfaces the slot
 * tie-in (FR17). DECLINED is a collapsible bottom strip, not a column (FR18).
 *
 * Drag is workflow-safe (FR19/FR20/AR6): a forward cross-column drop resolves to the
 * card's single forward successor and is dispatched through the SAME `classifyDrop`
 * path as the card's primary-action button — opening the relevant confirm/modal,
 * never auto-committing, advancing exactly one transition. Same-phase / backward
 * drops snap back. Within-pair advances (e.g. IDENTIFIED→CONTACTED) happen via the
 * card button, not a drag.
 *
 * The primary-action mapping is delegated to `./getPrimaryAction.ts`; the transition
 * allow-list lives in `./speakerTransitions.ts`; the phase model in `./phaseColumns.ts`.
 */

import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
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
  Collapse,
  Link,
} from '@mui/material';
import {
  CheckCircleOutline as CheckCircleOutlineIcon,
  Lock as LockIcon,
  ExpandMore as ExpandMoreIcon,
  ChevronRight as ChevronRightIcon,
  WarningAmber as WarningAmberIcon,
  CheckCircle as CheckCircleIcon,
} from '@mui/icons-material';
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
import { usePublicUser } from '@/hooks/useUserPortrait';
import { useOrganizers } from '@/components/shared/OrganizerSelect';
import { StatusChangeDialog } from './StatusChangeDialog';
import {
  computeSlotCapacity,
  getPrimaryAction,
  type PrimaryActionCallbacks,
  type SlotCapacityState,
} from './getPrimaryAction';
import { classifyDrop, getRejectionExplanation, type KanbanState } from './speakerTransitions';
import {
  DEFAULT_KANBAN_THRESHOLDS,
  classifyChipSeverity,
  getStatusChangedAt,
  severityToChipColor,
  type ThresholdSeverity,
} from './kanbanThresholds';
import { PHASE_COLUMNS, resolveColumnDrop, type PhaseColumn, type PhaseKey } from './phaseColumns';
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
  /**
   * Event date (ISO string). Used by the QUALITY_REVIEWED chip-colour rule. When
   * omitted, QUALITY_REVIEWED chips remain in the default colour.
   */
  eventDate?: string;
  /** Injected `now` for deterministic testing. Production callers leave it undefined. */
  now?: Date;
  onStatusChange?: (speakerId: string, newStatus: SpeakerWorkflowState) => void;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  /** Opens MarkContactedModal at parent — IDENTIFIED card button. */
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  /** Opens PromoteSpeakerDialog at parent — CONTACTED card button. */
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  /** Lifted send-invitation handler — READY card button + READY→INVITED drop. */
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer at the on-behalf content form — ACCEPTED card button + drop. */
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer at the Quality Review sub-view — CONTENT_SUBMITTED card button + drop. */
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
  /** Switches to the in-tab Slots sub-view — QUALITY_REVIEWED (unassigned) slot tie-in. */
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

// Status color mapping (per ADR-009 §0.1).
const STATUS_COLORS: Record<string, string> = {
  IDENTIFIED: '#9e9e9e',
  CONTACTED: '#ffc107',
  READY: '#ff9800',
  INVITED: '#2196f3',
  ACCEPTED: '#4caf50',
  CONTENT_SUBMITTED: '#fbc02d',
  QUALITY_REVIEWED: '#7cb342',
  DECLINED: '#f44336',
};

// Phase-column accent colours (header top border).
const PHASE_COLORS: Record<PhaseKey, string> = {
  sourcing: '#9e9e9e',
  inviting: '#2196f3',
  content: '#4caf50',
  confirmed: '#7cb342',
};

/**
 * Story 11.D.4 — drag-state context broadcast to every column during a drag.
 * `validTargets` is the set of phase keys that accept the active card (legal forward
 * cross-column drops). Columns consume it for the halo / dim / lock styling.
 */
interface KanbanDragContextValue {
  activeSourcePhase: PhaseKey | null;
  validTargets: ReadonlySet<PhaseKey>;
}
const EMPTY_VALID_TARGETS: ReadonlySet<PhaseKey> = Object.freeze(
  new Set<PhaseKey>()
) as ReadonlySet<PhaseKey>;
const KanbanDragContext = createContext<KanbanDragContextValue>({
  activeSourcePhase: null,
  validTargets: EMPTY_VALID_TARGETS,
});

/**
 * "Whose move" classification for within-column sort (FR16). error/warning ⇒
 * organizer's move; normal ⇒ waiting on speaker. Reuses `classifyChipSeverity` — the
 * same signal the time-in-state chip uses — so the two surfaces never disagree.
 */
function isYourMove(speaker: SpeakerPoolEntry, eventDate: Date | null, now: Date): boolean {
  const anchorIso = getStatusChangedAt(speaker);
  if (!anchorIso) return false;
  const anchor = new Date(anchorIso);
  if (Number.isNaN(anchor.getTime())) return false;
  const severity = classifyChipSeverity({
    speaker,
    statusChangedAt: anchor,
    eventDate,
    now,
    thresholds: DEFAULT_KANBAN_THRESHOLDS,
  });
  return severity !== 'normal';
}

export const SpeakerStatusLanes: React.FC<SpeakerStatusLanesProps> = ({
  eventCode,
  speakers,
  sessions,
  maxSlots,
  eventDate,
  now: nowProp,
  onStatusChange,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onSendInvitation,
  onEnterContent,
  onReviewContent,
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

  const [dragContext, setDragContext] = useState<KanbanDragContextValue>({
    activeSourcePhase: null,
    validTargets: new Set<PhaseKey>(),
  });

  // Drop-toast snackbar (illegal drops + slot-capacity rejection).
  const [dropToast, setDropToast] = useState<{ open: boolean; message: string }>({
    open: false,
    message: '',
  });

  // FR18 — Declined strip collapse state (collapsed by default).
  const [declinedOpen, setDeclinedOpen] = useState(false);

  // `now` memoised to a per-day key (TanStack refetch-on-focus drives daily re-eval).
  const liveNowRef = useRef<Date>(nowProp ?? new Date());
  if (!nowProp) {
    const fresh = new Date();
    const MS_PER_DAY = 86_400_000;
    if (
      Math.floor(fresh.getTime() / MS_PER_DAY) !==
      Math.floor(liveNowRef.current.getTime() / MS_PER_DAY)
    ) {
      liveNowRef.current = fresh;
    }
  } else {
    liveNowRef.current = nowProp;
  }
  const now = liveNowRef.current;

  // Anchor date-only ISO strings to local noon to avoid the UTC-midnight off-by-hours pitfall.
  const parsedEventDate = useMemo<Date | null>(() => {
    if (!eventDate) return null;
    const isDateOnly = /^\d{4}-\d{2}-\d{2}$/.test(eventDate);
    const d = isDateOnly ? new Date(`${eventDate}T12:00:00`) : new Date(eventDate);
    return Number.isNaN(d.getTime()) ? null : d;
  }, [eventDate]);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    })
  );

  const slotCapacity: SlotCapacityState = useMemo(
    () => computeSlotCapacity(speakers, maxSlots),
    [speakers, maxSlots]
  );

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
    onError: (err: unknown) => {
      const message =
        err instanceof Error && err.message
          ? err.message
          : t('organizer:speakerCard.statusUpdateFailed', {
              defaultValue: 'Could not update speaker status.',
            });
      setDropToast({ open: true, message });
    },
  });

  const handleDragStart = (event: DragStartEvent) => {
    const speakerId = event.active.id as string;
    const speaker = speakers.find((s) => s.id === speakerId);
    setActiveSpeaker(speaker || null);

    if (speaker) {
      const fromState = speaker.status as KanbanState;
      // Valid targets = phase columns that resolve to a legal forward transition.
      const validTargets = new Set<PhaseKey>(
        PHASE_COLUMNS.map((c) => c.key).filter((phase) => {
          const drop = resolveColumnDrop(fromState, phase);
          if (drop.kind !== 'forward') return false;
          const intent = classifyDrop(drop.from, drop.to, slotCapacity.reached);
          return intent.kind !== 'illegal';
        })
      );
      const sourcePhase = PHASE_COLUMNS.find((c) => c.states.includes(fromState))?.key;
      setDragContext({ activeSourcePhase: sourcePhase ?? null, validTargets });
    }
  };

  const resetDragState = useCallback(() => {
    setActiveSpeaker(null);
    setDragContext({ activeSourcePhase: null, validTargets: new Set<PhaseKey>() });
  }, []);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    resetDragState();

    if (!over) {
      return;
    }

    const speakerId = active.id as string;
    const targetPhase = over.id as PhaseKey;
    const speaker = speakers.find((s) => s.id === speakerId);
    if (!speaker) {
      return;
    }

    // FR19/FR20 — resolve the column drop to a single forward transition (or snap-back).
    const columnDrop = resolveColumnDrop(speaker.status as KanbanState, targetPhase);
    if (columnDrop.kind !== 'forward') {
      // Same-phase or backward drop → snap back, no state change (FR20).
      return;
    }

    const newStatus = columnDrop.to;

    // The SAME `classifyDrop` dispatch the legacy per-lane drop used — one transition,
    // always opens the relevant modal, never auto-commits a consequential action (NFR1).
    const intent = classifyDrop(speaker.status, newStatus, slotCapacity.reached);
    switch (intent.kind) {
      case 'illegal':
        setDropToast({
          open: true,
          message: getRejectionExplanation(speaker.status, newStatus, t),
        });
        return;

      case 'legal-blocked-slot':
        setDropToast({
          open: true,
          message: t('organizer:speakerCard.slotCapacityTooltip', {
            invited: slotCapacity.invited,
            accepted: slotCapacity.accepted,
            slots: slotCapacity.slots,
          }),
        });
        return;

      case 'legal-decline':
        setDialogState({ open: true, speaker, newStatus });
        return;

      case 'legal-accept-on-behalf':
        setDialogState({ open: true, speaker, newStatus });
        return;

      case 'legal-input': {
        const cb = ((): ((s: SpeakerPoolEntry) => void) | undefined => {
          switch (intent.modal) {
            case 'mark-contacted':
              return onLogOutreach;
            case 'promote':
              return onPromoteSpeaker;
            case 'invitation':
              return onSendInvitation;
            case 'content-form':
              return onEnterContent;
            case 'quality-review':
              return onReviewContent;
            default:
              return undefined;
          }
        })();
        if (cb) {
          cb(speaker);
        }
        return;
      }

      case 'legal-direct':
        updateStatusMutation.mutate({ speakerId: speaker.id, newStatus });
        return;
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

  // Group speakers by phase column (FR15), then within each column split your-move
  // (top) from waiting-on-speaker (below the divider) (FR16). Card order within a
  // band follows the column's state order, then the input order.
  const speakersByPhase = useMemo(() => {
    const map = new Map<PhaseKey, { yourMove: SpeakerPoolEntry[]; waiting: SpeakerPoolEntry[] }>();
    for (const column of PHASE_COLUMNS) {
      const inColumn = speakers.filter((s) => column.states.includes(s.status as KanbanState));
      const stateRank = (s: SpeakerPoolEntry) => column.states.indexOf(s.status as KanbanState);
      const sorted = [...inColumn].sort((a, b) => stateRank(a) - stateRank(b));
      const yourMove = sorted.filter((s) => isYourMove(s, parsedEventDate, now));
      const waiting = sorted.filter((s) => !isYourMove(s, parsedEventDate, now));
      map.set(column.key, { yourMove, waiting });
    }
    return map;
  }, [speakers, parsedEventDate, now]);

  const declinedSpeakers = useMemo(
    () => speakers.filter((s) => s.status === 'DECLINED'),
    [speakers]
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
        onDragCancel={resetDragState}
      >
        <KanbanDragContext.Provider value={dragContext}>
          <Grid container spacing={2} sx={{ flex: 1, minHeight: 0 }}>
            {PHASE_COLUMNS.map((column) => (
              <Grid size={{ xs: 12, sm: 6, md: 'grow' }} key={column.key} sx={{ display: 'flex' }}>
                <PhaseColumnLane
                  column={column}
                  yourMove={speakersByPhase.get(column.key)?.yourMove ?? []}
                  waiting={speakersByPhase.get(column.key)?.waiting ?? []}
                  sessions={sessions}
                  eventCode={eventCode}
                  color={PHASE_COLORS[column.key]}
                  organizers={organizers}
                  slotCapacity={slotCapacity}
                  eventDate={parsedEventDate}
                  now={now}
                  onSpeakerClick={handleSpeakerClick}
                  onLogOutreach={onLogOutreach}
                  onPromoteSpeaker={onPromoteSpeaker}
                  onSendInvitation={onSendInvitation}
                  onEnterContent={onEnterContent}
                  onReviewContent={onReviewContent}
                  onAssignSessionSlot={onAssignSessionSlot}
                />
              </Grid>
            ))}
          </Grid>

          <DragOverlay>
            {activeSpeaker ? (
              <SpeakerCard
                speaker={activeSpeaker}
                sessions={sessions}
                eventCode={eventCode}
                organizers={organizers}
                slotCapacity={slotCapacity}
                eventDate={parsedEventDate}
                now={now}
                isDragging
              />
            ) : null}
          </DragOverlay>
        </KanbanDragContext.Provider>
      </DndContext>

      {/* FR18 — collapsible Declined strip (collapsed by default; hidden when empty). */}
      {declinedSpeakers.length > 0 && (
        <Box sx={{ mt: 2 }} data-testid="declined-strip">
          <Box
            component="button"
            type="button"
            onClick={() => setDeclinedOpen((prev) => !prev)}
            aria-expanded={declinedOpen}
            aria-controls="declined-strip-content"
            data-testid="declined-strip-toggle"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
              width: '100%',
              background: 'none',
              border: 'none',
              borderTop: '1px solid',
              borderColor: 'divider',
              pt: 1,
              cursor: 'pointer',
              textAlign: 'left',
              color: 'text.secondary',
              fontSize: '0.875rem',
              fontWeight: 600,
            }}
          >
            {declinedOpen ? (
              <ExpandMoreIcon fontSize="small" />
            ) : (
              <ChevronRightIcon fontSize="small" />
            )}
            {t('organizer:speakerStatus.declinedStrip', { count: declinedSpeakers.length })}
          </Box>
          <Collapse in={declinedOpen} unmountOnExit>
            <Box
              id="declined-strip-content"
              data-testid="declined-strip-content"
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
                mt: 1,
              }}
            >
              {declinedSpeakers.map((speaker) => (
                <Box key={speaker.id} sx={{ width: { xs: '100%', sm: 280 } }}>
                  <SpeakerCard
                    speaker={speaker}
                    sessions={sessions}
                    eventCode={eventCode}
                    organizers={organizers}
                    slotCapacity={slotCapacity}
                    eventDate={parsedEventDate}
                    now={now}
                    onSpeakerClick={handleSpeakerClick}
                  />
                </Box>
              ))}
            </Box>
          </Collapse>
        </Box>
      )}

      {/* invalid-drop / slot-capacity toast surface (AC3, AC6). */}
      <Snackbar
        open={dropToast.open}
        autoHideDuration={6000}
        onClose={() => setDropToast({ open: false, message: '' })}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        data-testid="kanban-drop-toast"
      >
        <Alert
          onClose={() => setDropToast({ open: false, message: '' })}
          severity="warning"
          sx={{ width: '100%' }}
        >
          {dropToast.message}
        </Alert>
      </Snackbar>

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

// Phase Column Component
interface PhaseColumnLaneProps {
  column: PhaseColumn;
  yourMove: SpeakerPoolEntry[];
  waiting: SpeakerPoolEntry[];
  sessions: SessionUI[];
  eventCode: string;
  color: string;
  organizers: { id: string; name: string }[];
  slotCapacity: SlotCapacityState;
  eventDate: Date | null;
  now: Date;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

const PhaseColumnLane: React.FC<PhaseColumnLaneProps> = ({
  column,
  yourMove,
  waiting,
  sessions,
  eventCode,
  color,
  organizers,
  slotCapacity,
  eventDate,
  now,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onSendInvitation,
  onEnterContent,
  onReviewContent,
  onAssignSessionSlot,
}) => {
  const { t } = useTranslation(['organizer']);
  const { setNodeRef } = useDroppable({ id: column.key });

  const { activeSourcePhase, validTargets } = useContext(KanbanDragContext);
  const isDragActive = activeSourcePhase !== null;
  const isSourceColumn = activeSourcePhase === column.key;
  const isValidTarget = validTargets.has(column.key);
  const showInvalidLock = isDragActive && !isValidTarget && !isSourceColumn;
  const showValidHalo = isDragActive && isValidTarget;

  const totalCount = yourMove.length + waiting.length;

  const renderCard = (speaker: SpeakerPoolEntry, yourMoveCard: boolean) => (
    <SpeakerCard
      key={speaker.id}
      speaker={speaker}
      sessions={sessions}
      eventCode={eventCode}
      organizers={organizers}
      slotCapacity={slotCapacity}
      eventDate={eventDate}
      now={now}
      yourMove={yourMoveCard}
      onSpeakerClick={onSpeakerClick}
      onLogOutreach={onLogOutreach}
      onPromoteSpeaker={onPromoteSpeaker}
      onSendInvitation={onSendInvitation}
      onEnterContent={onEnterContent}
      onReviewContent={onReviewContent}
      onAssignSessionSlot={onAssignSessionSlot}
    />
  );

  return (
    <Paper
      ref={setNodeRef}
      data-testid={`phase-column-${column.key}`}
      role="group"
      aria-label={t(`organizer:speakerStatus.phaseColumns.${column.key}`)}
      data-drop-state={
        showValidHalo ? 'valid' : showInvalidLock ? 'invalid' : isSourceColumn ? 'source' : 'idle'
      }
      sx={{
        p: 2,
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'background.default',
        borderTop: `4px solid ${color}`,
        ...(showValidHalo && {
          outline: '2px solid',
          outlineColor: 'success.main',
          outlineOffset: '-2px',
          transition: 'outline-color 120ms ease',
        }),
        ...(showInvalidLock && {
          opacity: 0.4,
          cursor: 'not-allowed',
        }),
      }}
    >
      <Box sx={{ mb: 2, minHeight: 40 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography
            variant="h6"
            sx={{ color }}
            data-testid={`phase-column-heading-${column.key}`}
          >
            {t(`organizer:speakerStatus.phaseColumns.${column.key}`)}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {showInvalidLock && (
              <Tooltip title={t('organizer:kanbanDrag.invalidColumnTooltip')}>
                <LockIcon
                  fontSize="small"
                  color="action"
                  data-testid={`phase-column-lock-${column.key}`}
                />
              </Tooltip>
            )}
            <Chip label={totalCount} size="small" sx={{ backgroundColor: color, color: 'white' }} />
          </Box>
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, overflow: 'auto', minHeight: 0 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {yourMove.map((speaker) => renderCard(speaker, true))}

          {/* FR16 — faint divider between "your move" (above) and "waiting on speaker" (below). */}
          {yourMove.length > 0 && waiting.length > 0 && (
            <Box
              data-testid={`phase-column-divider-${column.key}`}
              role="separator"
              aria-label={t('organizer:speakerStatus.waitingOnSpeakerDivider')}
              sx={{
                my: 0.5,
                borderTop: '1px dashed',
                borderColor: 'divider',
                opacity: 0.6,
              }}
            />
          )}

          {waiting.map((speaker) => renderCard(speaker, false))}
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
  eventDate?: Date | null;
  now?: Date;
  isDragging?: boolean;
  /** FR16 — whether this card sits in the "your move" band (accent left border). */
  yourMove?: boolean;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

const SpeakerCard: React.FC<SpeakerCardProps> = ({
  speaker,
  sessions,
  eventCode,
  organizers = [],
  slotCapacity,
  eventDate = null,
  now,
  isDragging = false,
  yourMove = false,
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onSendInvitation,
  onEnterContent,
  onReviewContent,
  onAssignSessionSlot,
}) => {
  const { t, i18n } = useTranslation(['organizer']);
  const { data: linkedUser } = usePublicUser(speaker.username ?? undefined);
  const linkedDisplayName =
    linkedUser?.firstName && linkedUser?.lastName
      ? `${linkedUser.firstName} ${linkedUser.lastName}`
      : null;
  const cardDisplayName = linkedDisplayName ?? speaker.speakerName;
  const showBrainstormCaption =
    linkedDisplayName !== null && speaker.speakerName !== linkedDisplayName;
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: speaker.id,
    // DECLINED is terminal; card is not draggable.
    disabled: speaker.status === 'DECLINED',
  });

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

  const session = speaker.sessionId ? sessions.find((s) => s.id === speaker.sessionId) : null;

  // Time-in-state chip.
  const statusChangedAt = getStatusChangedAt(speaker);
  const locale = i18n.language === 'de' ? de : enUS;
  const statusChangedDate = statusChangedAt ? new Date(statusChangedAt) : null;
  const isValidStatusChangedDate =
    statusChangedDate !== null && !Number.isNaN(statusChangedDate.getTime());
  const timeInState = isValidStatusChangedDate
    ? formatDistanceToNow(statusChangedDate, { locale, addSuffix: false })
    : null;
  const statusChangedAbsolute = isValidStatusChangedDate
    ? statusChangedDate.toLocaleString(i18n.language)
    : '';

  const chipSeverity: ThresholdSeverity = isValidStatusChangedDate
    ? classifyChipSeverity({
        speaker,
        statusChangedAt: statusChangedDate,
        eventDate,
        now: now ?? new Date(),
        thresholds: DEFAULT_KANBAN_THRESHOLDS,
      })
    : 'normal';
  const chipDataSeverity: ThresholdSeverity | 'unknown' = isValidStatusChangedDate
    ? chipSeverity
    : 'unknown';
  const chipColor = severityToChipColor(chipSeverity);

  // Primary action mapping (unchanged from Epic 11).
  const callbacks: PrimaryActionCallbacks = {
    onLogOutreach: onLogOutreach ?? (() => undefined),
    onPromoteSpeaker: onPromoteSpeaker ?? (() => undefined),
    onSendInvitation: onSendInvitation ?? handleSendInvitation,
    onEnterContent: onEnterContent ?? onSpeakerClick ?? (() => undefined),
    onReviewContent: onReviewContent ?? onSpeakerClick ?? (() => undefined),
    onSpeakerClick: onSpeakerClick ?? (() => undefined),
    onAssignSessionSlot: onAssignSessionSlot ?? (() => undefined),
  };
  const primaryAction = getPrimaryAction(speaker, callbacks, slotCapacity, t);

  const assignedOrg = speaker.assignedOrganizerId
    ? organizers.find((o) => o.id === speaker.assignedOrganizerId)
    : null;

  // FR17 — Confirmed-column slot tie-in. QUALITY_REVIEWED with no slot → "⚠ Needs a slot →"
  // (triggers the existing onAssignSessionSlot); slotted → ✓ + time.
  const isQualityReviewed = speaker.status === 'QUALITY_REVIEWED';
  const hasSlot = speaker.isSlotAssigned === true || speaker.sessionId != null;
  const slotTimeLabel = session?.startTime
    ? new Date(session.startTime).toLocaleTimeString(i18n.language, {
        hour: '2-digit',
        minute: '2-digit',
      })
    : null;

  return (
    <>
      <Card
        ref={setNodeRef}
        {...listeners}
        {...attributes}
        onClick={handleClick}
        data-testid={`speaker-card-${speaker.id}`}
        data-your-move={yourMove ? 'true' : 'false'}
        sx={{
          p: 2,
          cursor: speaker.status === 'DECLINED' ? 'default' : 'grab',
          opacity: isDragging ? 0.5 : 1,
          // FR16 — accent left border on "your move" cards.
          ...(yourMove && {
            borderLeft: '4px solid',
            borderLeftColor: 'warning.main',
          }),
          '&:hover': {
            boxShadow: 3,
          },
          ...style,
        }}
      >
        {/* Exact 8-state chip (FR15) — every card carries its own state chip. */}
        <Box sx={{ mb: 1 }}>
          <Chip
            size="small"
            label={t(`organizer:speakerStatus.${speaker.status}`)}
            data-testid={`state-chip-${speaker.id}`}
            data-state={speaker.status}
            sx={{
              height: 20,
              backgroundColor: STATUS_COLORS[speaker.status] ?? 'grey.400',
              color: 'white',
              '& .MuiChip-label': { fontSize: '0.65rem', px: 0.75, fontWeight: 600 },
            }}
          />
        </Box>

        {session && session.speakers && session.speakers.length > 0 ? (
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
            {/* Epic 14 — no second title/abstract block below the speaker: post-Epic-11
                normalization, speaker_pool content IS the session content, so
                `submittedTitle`/`submittedAbstract` only duplicated `session.title` above. */}
          </Box>
        ) : (
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Avatar
                src={linkedUser?.profilePictureUrl ?? undefined}
                sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}
              >
                {cardDisplayName.charAt(0).toUpperCase()}
              </Avatar>
              <Box sx={{ flex: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
                  <Typography variant="subtitle2">{cardDisplayName}</Typography>
                  {speaker.source === 'self_nomination' && (
                    <Chip
                      label={t('speakerCard.selfNominated')}
                      size="small"
                      color="info"
                      variant="outlined"
                      data-testid={`self-nominated-badge-${speaker.id}`}
                    />
                  )}
                </Box>
                {showBrainstormCaption && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: 'block', fontStyle: 'italic' }}
                  >
                    {speaker.speakerName}
                  </Typography>
                )}
                {(speaker.companyDisplayName || speaker.company) && (
                  <Typography variant="caption" color="text.secondary">
                    {speaker.companyDisplayName || speaker.company}
                  </Typography>
                )}
              </Box>
            </Box>
            {speaker.expertise && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                {speaker.expertise}
              </Typography>
            )}
            {speaker.proposedSessionTitle && (
              <Typography
                variant="caption"
                sx={{ mt: 1, display: 'block', fontWeight: 600 }}
                data-testid={`proposed-talk-title-${speaker.id}`}
              >
                {speaker.proposedSessionTitle}
              </Typography>
            )}
            {speaker.proposedAbstract && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {speaker.proposedAbstract}
              </Typography>
            )}

            <Box
              sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}
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
                  sx={{ height: 20, '& .MuiChip-label': { fontSize: '0.65rem', px: 0.5 } }}
                  data-testid={`assigned-organizer-chip-${speaker.id}`}
                />
              )}
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
                    color={chipColor}
                    sx={{
                      ml: 'auto',
                      height: 20,
                      '& .MuiChip-label': { fontSize: '0.65rem', px: 0.5 },
                    }}
                    data-testid={`time-in-state-chip-${speaker.id}`}
                    data-severity={chipDataSeverity}
                  />
                </Tooltip>
              )}
            </Box>

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
            {/* Epic 14 — `submittedTitle`/`submittedAbstract` (legacy Story 6.3 content fields)
                are no longer rendered on the card: post-normalization they equal the session's
                title/abstract, and a content-submitting speaker always has a session (shown in
                the session branch above). Content lives in the drawer Content tab. */}

            {speaker.status === 'DECLINED' && speaker.declineReason && (
              <Box sx={{ mt: 1, pt: 1, borderTop: '1px dashed', borderColor: 'divider' }}>
                <Typography variant="caption" color="error.main" sx={{ display: 'block' }}>
                  {t('organizer:speakers.declineReason')}: {speaker.declineReason}
                </Typography>
              </Box>
            )}
          </Box>
        )}

        {/* FR17 — Confirmed-column slot tie-in. */}
        {isQualityReviewed && !isDragging && (
          <Box
            sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid', borderColor: 'divider' }}
            data-testid={`slot-tie-in-${speaker.id}`}
          >
            {hasSlot ? (
              <Box
                sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'success.main' }}
                data-testid={`slot-assigned-${speaker.id}`}
              >
                <CheckCircleIcon fontSize="small" />
                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                  {slotTimeLabel
                    ? t('organizer:speakerCard.slotAssignedAt', { time: slotTimeLabel })
                    : t('organizer:speakerCard.slotAssigned')}
                </Typography>
              </Box>
            ) : (
              <Link
                component="button"
                type="button"
                underline="hover"
                onClick={(e) => {
                  e.stopPropagation();
                  if (!transform) onAssignSessionSlot?.(speaker);
                }}
                data-testid={`needs-slot-link-${speaker.id}`}
                data-action="assign-session-slot"
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.5,
                  color: 'warning.main',
                  fontWeight: 600,
                  fontSize: '0.75rem',
                }}
              >
                <WarningAmberIcon fontSize="small" />
                {t('organizer:speakerCard.needsASlot')}
              </Link>
            )}
          </Box>
        )}

        {/* Primary-action button or info chip — full-width along card bottom.
            Suppressed for QUALITY_REVIEWED (the slot tie-in above replaces it). */}
        {primaryAction.kind !== 'none' && !isDragging && !isQualityReviewed && (
          <Box sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid', borderColor: 'divider' }}>
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
