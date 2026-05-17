/**
 * Speaker Status Lanes Component (Story 5.4 + Story 11.D.2 + Story 11.D.3)
 *
 * Kanban-board style interface with drag-and-drop status lanes.
 * Each card surfaces a single state-aware primary-action button along its bottom edge,
 * plus a colour-coded time-in-state chip on the organizer row. Lane order follows
 * ADR-009 §0.1. Column headers carry a "needs attention" sub-line that filters the
 * column when clicked (Story 11.D.3 — see `./kanbanThresholds.ts` + plan §§8.3, 8.7).
 *
 * The primary-action mapping is delegated to `./getPrimaryAction.ts` (the source of truth
 * is `docs/plans/speaker-workflow-refactor.md` §8.2).
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
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
import {
  CheckCircleOutline as CheckCircleOutlineIcon,
  Lock as LockIcon,
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
import { useOrganizers } from '@/components/shared/OrganizerSelect';
import { StatusChangeDialog } from './StatusChangeDialog';
import {
  computeSlotCapacity,
  getPrimaryAction,
  type PrimaryActionCallbacks,
  type SlotCapacityState,
} from './getPrimaryAction';
import { classifyDrop, getRejectionExplanation, isLegalTransition } from './speakerTransitions';
import {
  DEFAULT_KANBAN_THRESHOLDS,
  attentionMaxSeverity,
  classifyChipSeverity,
  countAttentionCards,
  countInvitedSplit,
  getStatusChangedAt,
  makeAttentionPredicate,
  severityToChipColor,
  type ThresholdSeverity,
} from './kanbanThresholds';
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
   * Event date (ISO string, YYYY-MM-DD or full ISO). Used by the QUALITY_REVIEWED
   * chip-colour rule (Story 11.D.3 §8.7). When omitted (or while the parent's event
   * detail query is still loading and `event?.date` is `undefined`), QUALITY_REVIEWED
   * chips remain in the default colour and the column sub-line is suppressed. The
   * chip resolves to its threshold-driven colour once `eventDate` arrives — a brief
   * "fresh → warning/error" flip is expected after the load completes. Adding a
   * skeleton chip during loading is tracked in `deferred-work.md`.
   */
  eventDate?: string;
  /**
   * Story 11.D.3 — injected `now` for deterministic testing. Production callers leave
   * it undefined and the component computes `new Date()` per render.
   */
  now?: Date;
  onStatusChange?: (speakerId: string, newStatus: SpeakerWorkflowState) => void;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  /** Opens MarkContactedModal at parent. Story 11.D.2 — IDENTIFIED card button. */
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  /** Opens PromoteSpeakerDialog at parent. Story 11.D.2 — CONTACTED card button. */
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  /** Lifted send-invitation handler. Story 11.D.4 — READY card button + READY→INVITED drop. */
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer pre-positioned at the on-behalf content form. Story 11.D.4 — ACCEPTED card button + ACCEPTED→CONTENT_SUBMITTED drop. */
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  /** Opens drawer pre-positioned at the Quality Review sub-view. Story 11.D.4 — CONTENT_SUBMITTED card button + CONTENT_SUBMITTED→QUALITY_REVIEWED drop. */
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
  /** Navigates to slot-assignment page. Story 11.D.2 — QUALITY_REVIEWED (unassigned). */
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

interface AttentionSubline {
  label: string;
  severity: 'warning' | 'error';
  clickable: boolean;
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
// `KanbanLane` narrows `SpeakerWorkflowState` to the 8 ADR-009 states the kanban renders;
// legacy union members (`SLOT_ASSIGNED`, `WITHDREW`, `OVERFLOW`) are NOT valid lanes.
type OutreachLane = 'IDENTIFIED' | 'CONTACTED' | 'READY' | 'INVITED';
type PostAcceptanceLane = 'ACCEPTED' | 'CONTENT_SUBMITTED' | 'QUALITY_REVIEWED' | 'DECLINED';
type KanbanLane = OutreachLane | PostAcceptanceLane;

const OUTREACH_LANES: OutreachLane[] = ['IDENTIFIED', 'CONTACTED', 'READY', 'INVITED'];

const POST_ACCEPTANCE_LANES: PostAcceptanceLane[] = [
  'ACCEPTED',
  'CONTENT_SUBMITTED',
  'QUALITY_REVIEWED',
  'DECLINED',
];

const STATUS_LANES: KanbanLane[] = [...OUTREACH_LANES, ...POST_ACCEPTANCE_LANES];

/**
 * Story 11.D.4 — drag-state context broadcast to every lane during a drag (AC2).
 *
 * - `activeSourceStatus`: the source lane the active card came from (null when no drag is in progress).
 * - `validTargets`: the legal destinations for the current drag, derived once at `handleDragStart`.
 *
 * Lanes consume both: `validTargets.has(status)` enables the green halo; absence + a
 * non-source lane enables the dim + lock icon.
 */
interface KanbanDragContextValue {
  activeSourceStatus: KanbanLane | null;
  validTargets: ReadonlySet<KanbanLane>;
}
// Review patch — freeze the default value's `validTargets` so a future contributor
// can't accidentally `.add()` into the shared singleton at module scope.
const EMPTY_VALID_TARGETS: ReadonlySet<KanbanLane> = Object.freeze(
  new Set<KanbanLane>()
) as ReadonlySet<KanbanLane>;
const KanbanDragContext = createContext<KanbanDragContextValue>({
  activeSourceStatus: null,
  validTargets: EMPTY_VALID_TARGETS,
});

/**
 * Story 11.D.3 — Build the "needs attention" sub-line metadata for a single column.
 * Returns null when the column has no sub-line to render (IDENTIFIED / DECLINED / a
 * gated column whose count is 0 / READY without slot-capacity reached).
 *
 * The label uses i18n keys under `organizer:speakerCard.lanes.*`. Translations are
 * provided in all 10 supported locales; emoji glyphs live in the locale values per
 * AC6 (not hardcoded here).
 *
 * Severity rules (AC2 table):
 * - CONTACTED → warning (count = error-severity / "stale > 14 days")
 * - READY → warning (global slot-capacity gate, non-clickable)
 * - INVITED → error if any past-deadline, else warning
 * - ACCEPTED → warning
 * - CONTENT_SUBMITTED / QUALITY_REVIEWED → error if any matching card is error-severity, else warning
 */
function computeAttentionSubline(
  state: KanbanLane,
  ctx: {
    speakers: SpeakerPoolEntry[];
    slotCapacity: SlotCapacityState;
    parsedEventDate: Date | null;
    now: Date;
    t: (key: string, options?: Record<string, unknown>) => string;
  }
): AttentionSubline | null {
  const { speakers, slotCapacity, parsedEventDate, now, t } = ctx;

  switch (state) {
    case 'IDENTIFIED':
    case 'DECLINED':
      return null;

    case 'READY':
      if (!slotCapacity.reached) return null;
      return {
        label: t('organizer:speakerCard.lanes.readySlotCapacitySubline'),
        severity: 'warning',
        clickable: false,
      };

    case 'CONTACTED': {
      const count = countAttentionCards(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      if (count === 0) return null;
      return {
        label: t('organizer:speakerCard.lanes.contactedSubline', { count }),
        severity: 'warning',
        clickable: true,
      };
    }

    case 'INVITED': {
      const { approaching, past } = countInvitedSplit(speakers, now, DEFAULT_KANBAN_THRESHOLDS);
      if (approaching === 0 && past === 0) return null;
      const parts: string[] = [];
      if (approaching > 0) {
        parts.push(
          t('organizer:speakerCard.lanes.invitedSubline.approaching', { count: approaching })
        );
      }
      if (past > 0) {
        parts.push(t('organizer:speakerCard.lanes.invitedSubline.past', { count: past }));
      }
      const joiner = t('organizer:speakerCard.lanes.invitedSubline.joiner');
      return {
        label: parts.join(joiner),
        severity: past > 0 ? 'error' : 'warning',
        clickable: true,
      };
    }

    case 'ACCEPTED': {
      const count = countAttentionCards(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      if (count === 0) return null;
      return {
        label: t('organizer:speakerCard.lanes.acceptedSubline', { count }),
        severity: 'warning',
        clickable: true,
      };
    }

    case 'CONTENT_SUBMITTED': {
      const count = countAttentionCards(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      if (count === 0) return null;
      const maxSeverity = attentionMaxSeverity(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      return {
        label: t('organizer:speakerCard.lanes.contentSubmittedSubline', { count }),
        severity: maxSeverity === 'error' ? 'error' : 'warning',
        clickable: true,
      };
    }

    case 'QUALITY_REVIEWED': {
      const count = countAttentionCards(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      if (count === 0) return null;
      const maxSeverity = attentionMaxSeverity(
        speakers,
        state,
        parsedEventDate,
        now,
        DEFAULT_KANBAN_THRESHOLDS
      );
      return {
        label: t('organizer:speakerCard.lanes.qualityReviewedSubline', { count }),
        severity: maxSeverity === 'error' ? 'error' : 'warning',
        clickable: true,
      };
    }

    default:
      return null;
  }
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

  // Story 11.D.4 — drag-state context value. Recomputed only on drag start/end.
  const [dragContext, setDragContext] = useState<KanbanDragContextValue>({
    activeSourceStatus: null,
    validTargets: new Set<KanbanLane>(),
  });

  // Story 11.D.4 — drop-toast snackbar (illegal drops + slot-capacity rejection).
  // Reuses the existing Snackbar surface from invitation feedback (lines below).
  const [dropToast, setDropToast] = useState<{ open: boolean; message: string }>({
    open: false,
    message: '',
  });

  // Story 11.D.3 — sub-line click-to-filter (per-column local UI state). Narrowed to
  // `KanbanLane` so legacy `SpeakerWorkflowState` members can't slip through.
  const [attentionFilter, setAttentionFilter] = useState<KanbanLane | null>(null);

  // Story 11.D.3 — `now` is memoised to a per-day key so all chip-colour + sub-line
  // computations within the same calendar day reuse the same `Date` instance. A new
  // day naturally refreshes `now` (and the entire downstream chain) on the next render.
  // TanStack Query refetch-on-focus drives daily re-evaluation; no setInterval needed
  // (Resolved Q#4). Tests inject a fixed `now` via prop.
  const liveNowRef = useRef<Date>(nowProp ?? new Date());
  if (!nowProp) {
    const fresh = new Date();
    // Refresh `liveNowRef` only when the calendar day flips — `Math.floor(ts/MS_PER_DAY)`
    // is stable for the duration of a UTC day.
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

  // Anchor date-only ISO strings to local noon to avoid the UTC-midnight off-by-hours
  // pitfall in non-UTC regions (e.g. CET / CEST). Full ISO datetimes are unaffected
  // because `new Date(iso)` returns the same instant regardless of local offset.
  const parsedEventDate = useMemo<Date | null>(() => {
    if (!eventDate) return null;
    const isDateOnly = /^\d{4}-\d{2}-\d{2}$/.test(eventDate);
    const d = isDateOnly ? new Date(`${eventDate}T12:00:00`) : new Date(eventDate);
    return Number.isNaN(d.getTime()) ? null : d;
  }, [eventDate]);

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
  const slotCapacity: SlotCapacityState = useMemo(
    () => computeSlotCapacity(speakers, maxSlots),
    [speakers, maxSlots]
  );

  // Story 11.D.3 — Per-state "needs attention" sub-line data. Computed at the parent
  // so a single source of truth drives both the visible count + the click-to-filter
  // predicate (AC2 + AC3). Memoised on `now` (stable per UTC day per the ref above),
  // so reclassification only happens when the underlying inputs change. `t` and
  // `i18n.language` are intentionally not in the deps — i18n changes trigger a full
  // re-render through useTranslation, and the language won't flip mid-day.
  const attentionSublines = useMemo<Partial<Record<KanbanLane, AttentionSubline | null>>>(
    () =>
      STATUS_LANES.reduce<Partial<Record<KanbanLane, AttentionSubline | null>>>((acc, state) => {
        acc[state] = computeAttentionSubline(state, {
          speakers,
          slotCapacity,
          parsedEventDate,
          now,
          t,
        });
        return acc;
      }, {}),
    [speakers, slotCapacity, parsedEventDate, now, t]
  );

  // Story 11.D.3 — Auto-clear filter when the event changes (a new pool, a new context).
  useEffect(() => {
    setAttentionFilter(null);
  }, [eventCode]);

  // Story 11.D.3 — Auto-clear filter when its underlying count drops to 0 (organiser
  // worked through the backlog; no rows left to focus on).
  useEffect(() => {
    if (!attentionFilter) return;
    const count = countAttentionCards(
      speakers,
      attentionFilter,
      parsedEventDate,
      now,
      DEFAULT_KANBAN_THRESHOLDS
    );
    if (count === 0) {
      setAttentionFilter(null);
    }
    // `now` is intentionally excluded — re-running on every render would clear the
    // filter immediately after the user clicks it. Speaker / event / filter changes
    // are the legitimate triggers.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attentionFilter, speakers, parsedEventDate]);

  // Story 11.D.3 — `useCallback` stabilises the handler so per-lane `useMemo`'d
  // children don't re-render purely because the parent re-rendered.
  const handleSublineClick = useCallback((state: KanbanLane) => {
    setAttentionFilter((prev) => (prev === state ? null : state));
  }, []);

  // Story 11.D.3 — visually-hidden aria-live region announces filter-state changes
  // to screen-reader users. Updated by the effect below when `attentionFilter` flips.
  const [filterAnnouncement, setFilterAnnouncement] = useState('');
  useEffect(() => {
    if (attentionFilter === null) {
      setFilterAnnouncement(t('organizer:speakerCard.lanes.filterClearedAnnouncement'));
      return;
    }
    const count = countAttentionCards(
      speakers,
      attentionFilter,
      parsedEventDate,
      now,
      DEFAULT_KANBAN_THRESHOLDS
    );
    setFilterAnnouncement(
      t('organizer:speakerCard.lanes.filterAppliedAnnouncement', {
        state: t(`organizer:speakerStatus.${attentionFilter}`),
        count,
      })
    );
    // `now` is intentionally excluded — see auto-clear effect rationale below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attentionFilter, speakers, parsedEventDate, t]);

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
    // Review patch — surface backend rejections (e.g. SLOT_CAPACITY_REACHED 409 on
    // INVITED → ACCEPTED legal-direct drops). Previously the legal-direct branch fired
    // the mutation with no error handler; a 409 closed silently and the kanban
    // didn't refresh. Reuses the same `dropToast` Snackbar AC3/AC6 use.
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

    // Story 11.D.4 — broadcast legal destinations to every lane.
    if (speaker) {
      const sourceStatus = speaker.status as KanbanLane;
      const validTargets = new Set<KanbanLane>(
        STATUS_LANES.filter((target) => isLegalTransition(speaker.status, target))
      );
      setDragContext({ activeSourceStatus: sourceStatus, validTargets });
    }
  };

  // Story 11.D.4 — Reset drag context on every drop / cancel path (the four return
  // points in `handleDragEnd` plus the explicit cancel).
  const resetDragState = useCallback(() => {
    setActiveSpeaker(null);
    setDragContext({ activeSourceStatus: null, validTargets: new Set<KanbanLane>() });
  }, []);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    resetDragState();

    if (!over || active.id === over.id) {
      return;
    }

    const speakerId = active.id as string;
    const newStatus = over.id as SpeakerWorkflowState;
    const speaker = speakers.find((s) => s.id === speakerId);

    if (!speaker || speaker.status === newStatus) {
      return;
    }

    // Story 11.D.4 — single dispatcher driven by `classifyDrop` (AC4).
    const intent = classifyDrop(speaker.status, newStatus, slotCapacity.reached);
    switch (intent.kind) {
      case 'illegal':
        // AC3 — invalid-drop toast with state-machine explanation.
        setDropToast({
          open: true,
          message: getRejectionExplanation(speaker.status, newStatus, t),
        });
        return;

      case 'legal-blocked-slot':
        // AC6 — slot-gate consistency. Reuses the 11.D.2 i18n key verbatim.
        // Slot-capacity is mirrored from the in-page derivation at SpeakerStatusLanes;
        // the authoritative gate is SpeakerWorkflowService.transition(INVITED) on the
        // backend (Story 11.B.2). A future contributor adding pagination must also
        // surface backend 409s here.
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
        // AC5 — open StatusChangeDialog; required-reason guard applies inside dialog.
        setDialogState({ open: true, speaker, newStatus });
        return;

      case 'legal-input': {
        // AC4 — open the same rich modal the primary-action button opens.
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
        // Today only INVITED → ACCEPTED. Fire the mutation; no reason needed.
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

  // Group speakers by status. Story 11.D.3 — when the user clicks a column's "needs
  // attention" sub-line, that lane filters to the attention predicate's matches.
  const speakersByStatus = STATUS_LANES.reduce<Record<KanbanLane, SpeakerPoolEntry[]>>(
    (acc, status) => {
      const inState = speakers.filter((s) => s.status === status);
      if (attentionFilter === status) {
        const predicate = makeAttentionPredicate(
          status,
          parsedEventDate,
          now,
          DEFAULT_KANBAN_THRESHOLDS
        );
        acc[status] = inState.filter(predicate);
      } else {
        acc[status] = inState;
      }
      return acc;
    },
    {} as Record<KanbanLane, SpeakerPoolEntry[]>
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
                    attentionSubline={attentionSublines[status] ?? null}
                    filterActive={attentionFilter === status}
                    onSublineClick={handleSublineClick}
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
                    attentionSubline={attentionSublines[status] ?? null}
                    filterActive={attentionFilter === status}
                    onSublineClick={handleSublineClick}
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
          </Stack>

          {/* Story 11.D.3 — visually-hidden aria-live region for filter-toggle SR feedback */}
          <Box
            aria-live="polite"
            aria-atomic="true"
            data-testid="speaker-lanes-filter-announcement"
            sx={{
              position: 'absolute',
              width: 1,
              height: 1,
              padding: 0,
              margin: -1,
              overflow: 'hidden',
              clip: 'rect(0, 0, 0, 0)',
              whiteSpace: 'nowrap',
              border: 0,
            }}
          >
            {filterAnnouncement}
          </Box>

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

      {/* Story 11.D.4 — invalid-drop / slot-capacity toast surface (AC3, AC6). */}
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

// Status Lane Component
interface StatusLaneProps {
  status: KanbanLane;
  speakers: SpeakerPoolEntry[];
  sessions: SessionUI[];
  eventCode: string;
  color: string;
  organizers: { id: string; name: string }[];
  slotCapacity: SlotCapacityState;
  /** Story 11.D.3 — "needs attention" sub-line for this column, or null when none. */
  attentionSubline: AttentionSubline | null;
  /** Story 11.D.3 — whether this column's filter is active (drives sub-line styling). */
  filterActive: boolean;
  /** Story 11.D.3 — handler for clickable sub-lines. Receives the lane's state so a
   *  single stable callback can serve every lane (no inline-arrow re-renders). No-op
   *  for READY (static text). */
  onSublineClick: (state: KanbanLane) => void;
  /** Story 11.D.3 — event date (parsed). Drives QUALITY_REVIEWED chip-colour rule. */
  eventDate: Date | null;
  /** Story 11.D.3 — `now` propagated for deterministic chip-colour computation. */
  now: Date;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — primary-action for READY card + drop-target callback. */
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — primary-action for ACCEPTED card + drop-target callback. */
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — primary-action for CONTENT_SUBMITTED card + drop-target callback. */
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
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
  attentionSubline,
  filterActive,
  onSublineClick,
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
  const { setNodeRef } = useDroppable({
    id: status,
  });

  // Story 11.D.4 (AC2) — drag-state context drives halo / dim / lock styles.
  const { activeSourceStatus, validTargets } = useContext(KanbanDragContext);
  const isDragActive = activeSourceStatus !== null;
  const isSourceLane = activeSourceStatus === status;
  const isValidTarget = validTargets.has(status);
  const showInvalidLock = isDragActive && !isValidTarget && !isSourceLane;
  const showValidHalo = isDragActive && isValidTarget;

  // Story 11.D.3 — READY's "Slot capacity reached" sub-line is a global event-level
  // gate (not a per-card subset), so it renders as static, non-clickable text per
  // Resolved Q#5. All other sub-lines are clickable buttons (AC2 + AC3).
  const sublineColor = attentionSubline?.severity === 'error' ? 'error.main' : 'warning.main';

  return (
    <Paper
      ref={setNodeRef}
      data-testid={`status-lane-${status.toLowerCase()}`}
      data-drop-state={
        showValidHalo ? 'valid' : showInvalidLock ? 'invalid' : isSourceLane ? 'source' : 'idle'
      }
      sx={{
        p: 2,
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'background.default',
        borderTop: `4px solid ${color}`,
        // Story 11.D.4 AC2 — green halo on legal destinations during drag.
        ...(showValidHalo && {
          outline: '2px solid',
          outlineColor: 'success.main',
          outlineOffset: '-2px',
          transition: 'outline-color 120ms ease',
        }),
        // Story 11.D.4 AC2 — dim + not-allowed cursor on invalid destinations.
        ...(showInvalidLock && {
          opacity: 0.4,
          cursor: 'not-allowed',
        }),
      }}
    >
      {/* Story 11.D.3 — Three-line header: title+count row, then optional sub-line.
          A min-height keeps lane headers aligned across columns with vs. without sub-lines. */}
      <Box sx={{ mb: 2, minHeight: 56 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography
            variant="h6"
            sx={{ color }}
            data-testid={`status-lane-heading-${status.toLowerCase()}`}
          >
            {t(`organizer:speakerStatus.${status}`)}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {/* Story 11.D.4 AC2 — lock icon on invalid destinations during drag. */}
            {showInvalidLock && (
              <Tooltip
                title={t('organizer:kanbanDrag.invalidDestinationTooltip', {
                  from: activeSourceStatus
                    ? t(`organizer:speakerStatus.${activeSourceStatus}`)
                    : '',
                  to: t(`organizer:speakerStatus.${status}`),
                })}
              >
                <LockIcon
                  fontSize="small"
                  color="action"
                  data-testid={`status-lane-lock-${status.toLowerCase()}`}
                />
              </Tooltip>
            )}
            <Chip
              label={speakers.length}
              size="small"
              sx={{ backgroundColor: color, color: 'white' }}
            />
          </Box>
        </Box>
        {attentionSubline &&
          (attentionSubline.clickable ? (
            <Box
              component="button"
              type="button"
              data-testid={`status-lane-subline-${status.toLowerCase()}`}
              data-severity={attentionSubline.severity}
              onClick={() => onSublineClick(status)}
              aria-pressed={filterActive}
              aria-label={t('organizer:speakerCard.lanes.sublineFilterAriaLabel', {
                state: t(`organizer:speakerStatus.${status}`),
                description: attentionSubline.label,
              })}
              sx={{
                mt: 0.5,
                background: 'none',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                textAlign: 'left',
                display: 'block',
                color: sublineColor,
                fontSize: '0.75rem',
                fontWeight: filterActive ? 700 : 500,
                textDecoration: filterActive ? 'underline' : 'none',
                '&:hover': { textDecoration: 'underline' },
              }}
            >
              {attentionSubline.label}
            </Box>
          ) : (
            <Typography
              variant="caption"
              role="status"
              data-testid={`status-lane-subline-${status.toLowerCase()}`}
              data-severity={attentionSubline.severity}
              sx={{ mt: 0.5, display: 'block', color: sublineColor, fontWeight: 500 }}
            >
              {attentionSubline.label}
            </Typography>
          ))}
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
              eventDate={eventDate}
              now={now}
              onSpeakerClick={onSpeakerClick}
              onLogOutreach={onLogOutreach}
              onPromoteSpeaker={onPromoteSpeaker}
              onSendInvitation={onSendInvitation}
              onEnterContent={onEnterContent}
              onReviewContent={onReviewContent}
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
  /** Story 11.D.3 — drives the QUALITY_REVIEWED chip-colour rule (§8.7). */
  eventDate?: Date | null;
  /** Story 11.D.3 — `now` for deterministic chip-colour math. */
  now?: Date;
  isDragging?: boolean;
  onSpeakerClick?: (speaker: SpeakerPoolEntry) => void;
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — READY card primary-action (lifted to parent for drag-end reuse). */
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — ACCEPTED card primary-action; opens drawer at Content sub-tab. */
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  /** Story 11.D.4 — CONTENT_SUBMITTED card primary-action; opens drawer at Quality Review. */
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
  onSpeakerClick,
  onLogOutreach,
  onPromoteSpeaker,
  onSendInvitation,
  onEnterContent,
  onReviewContent,
  onAssignSessionSlot,
}) => {
  const { t, i18n } = useTranslation(['organizer']);
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: speaker.id,
    // Story 11.D.4 AC2 — DECLINED is terminal; card is not draggable.
    disabled: speaker.status === 'DECLINED',
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
    // Default response deadline = today + 30 days (project convention).
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

  // Time-in-state chip — AC3 (Story 11.D.2) + threshold-driven colour (Story 11.D.3, AC4).
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

  // Story 11.D.3 (AC4) — colour the chip per §8.7 thresholds. `now` is supplied by the
  // parent (stable per UTC day); falls back to a fresh Date for safety when the card
  // renders outside a SpeakerStatusLanes context (e.g. unit-test isolation).
  // When the underlying timestamp is unparseable, `chipSeverity` stays `'normal'` (so
  // MUI renders the default chip colour), but `data-severity="unknown"` surfaces the
  // failure mode in the DOM — QA can distinguish "fresh data" from "bad data".
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

  // Primary action mapping — AC1.
  // Story 11.D.4 — prefer the lifted `onSendInvitation` / `onEnterContent` / `onReviewContent`
  // props (drag-end and card-click converge on the same handlers). Fall back to local
  // `handleSendInvitation` / `onSpeakerClick` for back-compat with the legacy callers.
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
