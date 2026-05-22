/**
 * Speaker detail drawer — Story 11.D.4 redesigned (AC7).
 *
 * Layout (top-to-bottom):
 *   1. <SpeakerDrawerHeader>                — name, company, status chip, close.
 *   2. <PrimaryActionSurface>               — the same primary-action button the kanban
 *                                             card surfaces, at `size="large"` (AC7.1).
 *   3. Secondary actions <List>             — Decline / Reassign organizer / Edit details
 *                                             / Override state (AC7.2).
 *   4. (optional) Content sub-tab <Chip>    — visible only for READY / ACCEPTED /
 *                                             CONTENT_SUBMITTED / QUALITY_REVIEWED (AC7.4).
 *   5. <Tabs> Details · History             — 2-tab structure (AC7.5).
 *   6. Sub-views (ContentSubmissionSubView / QualityReviewSubView) take over the body
 *                                             when `drawerView !== null` (AC7.6).
 */
import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Snackbar,
  Tab,
  Tabs,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  Edit as EditIcon,
  Person as PersonIcon,
  SwapHoriz as SwapHorizIcon,
} from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { SpeakerDrawerHeader } from './SpeakerDrawerHeader';
import { DetailsTabPanel } from './DetailsTabPanel';
import { ContentSubmissionSubView } from './ContentSubmissionSubView';
import { PromoteSpeakerSubView } from './PromoteSpeakerSubView';
import { QualityReviewSubView } from './QualityReviewSubView';
import { PrimaryActionSurface } from './PrimaryActionSurface';
import { UnifiedHistoryPanel } from './UnifiedHistoryPanel';
import { getDefaultTab } from './getDefaultTab';
import type { DrawerTabKey } from './getDefaultTab';
import { StatusChangeDialog } from '@/components/organizer/SpeakerStatus/StatusChangeDialog';
import {
  ALLOWED_TRANSITIONS,
  classifyDrop,
  type KanbanState,
} from '@/components/organizer/SpeakerStatus/speakerTransitions';
import { speakerStatusService } from '@/services/speakerStatusService';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import type {
  PrimaryActionCallbacks,
  SlotCapacityState,
} from '@/components/organizer/SpeakerStatus/getPrimaryAction';
import type { SpeakerPoolEntry, SpeakerWorkflowState } from '@/types/speakerPool.types';

export type DrawerView = null | 'content-submission' | 'quality-review' | 'promote';

/**
 * States where the Content tab is rendered alongside Details + History (Epic 11 bug fix
 * 2026-05-18 — previously a Chip that opened a takeover view). In READY the speaker has
 * not accepted yet, so the Content tab body shows the form with a banner + a disabled
 * submit button; ACCEPTED+ behaves as before (required fields, active submit).
 */
const CONTENT_TAB_STATES: ReadonlySet<KanbanState> = new Set([
  'READY',
  'ACCEPTED',
  'CONTENT_SUBMITTED',
  'QUALITY_REVIEWED',
]);

interface SpeakerDetailDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
  /**
   * Story 11.D.4 — When set, the drawer opens directly at the named sub-view (instead
   * of the default Details tab). Used by the kanban dispatcher for
   * `ACCEPTED → CONTENT_SUBMITTED` ('content-submission') and
   * `CONTENT_SUBMITTED → QUALITY_REVIEWED` ('quality-review') drops.
   */
  initialDrawerView?: DrawerView;
  /**
   * Story 11.D.4 review patch — kanban-derived slot-capacity state, threaded from
   * `EventSpeakersTab` so the drawer's PrimaryActionSurface and override-state popover
   * honor the same gate as the kanban. When omitted (e.g. drawer mounted in
   * isolation for tests), defaults to a no-cap state.
   */
  slotCapacity?: SlotCapacityState;
  /**
   * Story 11.D.4 review patch (Resolved Q#1) — rich-modal callbacks lifted from the
   * kanban so the drawer's "Override state" popover dispatches to the SAME modals as
   * the card click + drag-drop. Without these, override falls back to the generic
   * StatusChangeDialog and bypasses the modal flows (e.g. PromoteSpeakerDialog email
   * pre-fill). When omitted (drawer used standalone), the legal-input override path
   * still opens StatusChangeDialog as a graceful degradation.
   */
  onLogOutreach?: (speaker: SpeakerPoolEntry) => void;
  onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;
  onSendInvitation?: (speaker: SpeakerPoolEntry) => void;
  onEnterContent?: (speaker: SpeakerPoolEntry) => void;
  onReviewContent?: (speaker: SpeakerPoolEntry) => void;
  /**
   * 2026-05-20 (Q#6) — threaded from EventSpeakersTab so the drawer's PrimaryActionSurface
   * "Assign session slot" button (QUALITY_REVIEWED with no slot) actually navigates to the
   * slot-assignment page. Before this, the callback was a no-op default and the button
   * appeared functional but did nothing.
   */
  onAssignSessionSlot?: (speaker: SpeakerPoolEntry) => void;
}

const DEFAULT_SLOT_CAPACITY: SlotCapacityState = {
  reached: false,
  invited: 0,
  accepted: 0,
  slots: 0,
};

export const SpeakerDetailDrawer: React.FC<SpeakerDetailDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
  initialDrawerView = null,
  slotCapacity = DEFAULT_SLOT_CAPACITY,
  onLogOutreach,
  // 2026-05-20 (Q#A) — `onPromoteSpeaker`, `onEnterContent`, `onReviewContent` are
  // accepted in the prop type for backwards compatibility with the kanban-side
  // wiring, but the drawer ignores them in favour of internal handlers. See the
  // comment on `primaryActionCallbacks` below.
  onPromoteSpeaker: _onPromoteSpeaker,
  onSendInvitation,
  onEnterContent: _onEnterContent,
  onReviewContent: _onReviewContent,
  onAssignSessionSlot,
}) => {
  void _onPromoteSpeaker;
  void _onEnterContent;
  void _onReviewContent;
  const { t } = useTranslation(['organizer']);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<DrawerTabKey>('details');
  const [drawerView, setDrawerView] = useState<DrawerView>(null);
  const [isEditingDetails, setIsEditingDetails] = useState(false);
  const [statusDialogState, setStatusDialogState] = useState<{
    open: boolean;
    newStatus: SpeakerWorkflowState | null;
  }>({ open: false, newStatus: null });
  const [overrideMenuAnchor, setOverrideMenuAnchor] = useState<HTMLElement | null>(null);
  // Story 11.D.4 review patch — drawer-level error feedback (was previously swallowed
  // by silent `catch{}` blocks). Surfaces directMutation + override-popover failures.
  const [errorSnackbar, setErrorSnackbar] = useState<{ open: boolean; message: string }>({
    open: false,
    message: '',
  });

  // Reset tab and drawerView on speaker change or drawer open (derived state pattern).
  const [prevSpeakerKey, setPrevSpeakerKey] = useState<string | null>(null);
  const speakerKey = open && speaker ? speaker.id : null;
  if (speakerKey !== prevSpeakerKey) {
    setPrevSpeakerKey(speakerKey);
    if (speaker && open) {
      // Epic 11 bug fix 2026-05-18 — `initialDrawerView='content-submission'` now
      // navigates to the Content TAB (not a takeover view); `'quality-review'` and
      // `'promote'` remain full takeover sub-views. Apply the tab mapping here so the
      // drawer opens correctly even when the parent dispatcher still uses the legacy
      // 'content-submission' enum value.
      if (initialDrawerView === 'content-submission') {
        setTab('content');
        setDrawerView(null);
      } else {
        setTab(getDefaultTab(speaker));
        setDrawerView(initialDrawerView);
      }
      setStatusDialogState({ open: false, newStatus: null });
      setOverrideMenuAnchor(null);
      setIsEditingDetails(false);
    }
  }

  const handleTabChange = (_event: React.SyntheticEvent, newValue: DrawerTabKey) => {
    setTab(newValue);
  };

  // Story 11.D.4 — direct-mutation path for the override-state popover (legal-direct
  // INVITED → ACCEPTED) and the legal-decline confirm-dialog. The drawer is its own
  // writer here; the kanban dispatcher handles the same case via the lifted
  // `updateStatusMutation` in `SpeakerStatusLanes`.
  //
  // Review patch (P0): `reason` now flows through the mutation. Previously the drawer
  // fired the mutation without a reason and then re-fired a SECOND `updateStatus(..., reason)`
  // call — the second call attempted a DECLINED→DECLINED transition (terminal, illegal
  // per ADR-009) which the backend rejected, dropping the reason from the audit row.
  const directMutation = useMutation({
    mutationFn: ({ to, reason }: { to: SpeakerWorkflowState; reason?: string }) =>
      speaker
        ? speakerStatusService.updateStatus(eventCode, speaker.id, to, reason)
        : Promise.reject(new Error('no speaker')),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
    },
    onError: (err: unknown) => {
      const message =
        err instanceof Error && err.message
          ? err.message
          : t('organizer:speakerDrawer.errors.statusUpdateFailed', {
              defaultValue: 'Could not update speaker status.',
            });
      setErrorSnackbar({ open: true, message });
    },
  });

  // Primary-action callbacks — the drawer wires the same callback set the card uses.
  // Review patch (Resolved Q#1): parent-supplied rich-modal callbacks take precedence;
  // fall back to no-op only when the drawer is mounted standalone (unit tests).
  // Memoised so PrimaryActionSurface's downstream memoization isn't defeated.
  // NOTE: declared BEFORE the `!speaker` early return so React's Rules of Hooks hold.
  const primaryActionCallbacks = useMemo<PrimaryActionCallbacks>(
    () => ({
      onLogOutreach: onLogOutreach ?? (() => undefined),
      // 2026-05-20 (Q#A) — navigation-within-drawer actions ALWAYS use internal
      // handlers, never the parent's. The parent's handlers
      // (EventSpeakersTab.handlePromoteSpeaker / handleEnterContent /
      // handleReviewContent) re-set `selectedSpeakerId` + `initialDrawerView` +
      // `setDetailsDrawerOpen(true)` — but when the drawer is ALREADY open for the
      // SAME speaker, those state writes are no-ops (no React re-render), so the
      // sub-view never opens. The internal `setDrawerView` / `setTab` writes do
      // re-render the drawer. Parent handlers stay relevant for the kanban-card
      // click that opens the drawer in the first place.
      onPromoteSpeaker: (s) => {
        void s;
        setDrawerView('promote');
      },
      onEnterContent: (s) => {
        void s;
        setTab('content');
      },
      onReviewContent: (s) => {
        void s;
        setDrawerView('quality-review');
      },
      // Parent handlers retained for actions that need cross-drawer state (snackbar,
      // navigation, modal mount points):
      onSendInvitation: onSendInvitation ?? (() => undefined),
      // 2026-05-20 (Q#6) — the drawer's primary action for INVITED ("View response
      // status") and DECLINED ("View details") previously had a no-op callback. The
      // drawer IS the speaker detail view, so the most useful default is to jump to
      // the History tab where the response/decline audit trail lives. The user can
      // then see "last reminder sent", the decline reason, etc.
      onSpeakerClick: () => setTab('history'),
      // 2026-05-20 (Q#6) — QUALITY_REVIEWED with no slot fires this callback. Wired
      // through from EventSpeakersTab (handleAssignSessionSlotForSpeaker) so the
      // button actually navigates to the slot-assignment page.
      onAssignSessionSlot: onAssignSessionSlot ?? (() => undefined),
    }),
    [onLogOutreach, onSendInvitation, onAssignSessionSlot]
  );

  const dispatchOverride = useCallback(
    (target: KanbanState) => {
      if (!speaker) return;
      setOverrideMenuAnchor(null);
      if (target === 'DECLINED') {
        setStatusDialogState({ open: true, newStatus: 'DECLINED' });
        return;
      }
      // Use the same slot-capacity gate the kanban uses so the drawer surfaces the
      // `legal-blocked-slot` branch identically (AC6 convergence — three surfaces +
      // override popover).
      const intent = classifyDrop(speaker.status, target, slotCapacity.reached);
      switch (intent.kind) {
        case 'legal-direct':
          directMutation.mutate({ to: target });
          return;
        case 'legal-decline':
        case 'legal-accept-on-behalf':
          // Both branches route through the same StatusChangeDialog. The dialog
          // computes its required-reason rule from (currentStatus, newStatus).
          setStatusDialogState({ open: true, newStatus: target });
          return;
        case 'legal-input':
          // Review patch (Resolved Q#1): dispatch to the same rich modals the kanban
          // dispatcher uses. The card-click button, drag-drop, and override popover
          // now converge on identical modal flows.
          switch (intent.modal) {
            case 'content-form':
              // Epic 11 bug fix 2026-05-18 — Content is a tab, not a takeover view.
              setTab('content');
              return;
            case 'quality-review':
              setDrawerView('quality-review');
              return;
            case 'mark-contacted':
              primaryActionCallbacks.onLogOutreach(speaker);
              return;
            case 'promote':
              primaryActionCallbacks.onPromoteSpeaker(speaker);
              return;
            case 'invitation':
              primaryActionCallbacks.onSendInvitation(speaker);
              return;
            default:
              // Defensive: a future modal kind hits this. Surface the generic dialog
              // rather than silently no-op.
              setStatusDialogState({ open: true, newStatus: target });
              return;
          }
        case 'legal-blocked-slot':
          setErrorSnackbar({
            open: true,
            message: t('organizer:speakerCard.slotCapacityTooltip', {
              invited: slotCapacity.invited,
              accepted: slotCapacity.accepted,
              slots: slotCapacity.slots,
            }),
          });
          return;
        case 'illegal':
        default:
          // Should be unreachable — the popover filters to legal targets only.
          return;
      }
    },
    [speaker, slotCapacity, directMutation, primaryActionCallbacks, t]
  );

  if (!speaker) {
    return (
      <Drawer
        anchor="right"
        open={open}
        onClose={onClose}
        PaperProps={{ sx: { width: { xs: '100%', sm: 520 } } }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <SpeakerDrawerHeader speaker={null} onClose={onClose} />
        </Box>
      </Drawer>
    );
  }

  const speakerStatus = speaker.status as KanbanState;
  const legalTargets = ALLOWED_TRANSITIONS[speakerStatus] ?? new Set<KanbanState>();
  const canDecline = legalTargets.has('DECLINED');
  // Override-state popover lists every legal target EXCEPT DECLINED (Decline has its
  // own dedicated row in the secondary actions list).
  const overrideTargets = Array.from(legalTargets).filter((s) => s !== 'DECLINED');

  const handleConfirmStatusChange = (reason?: string) => {
    if (!statusDialogState.newStatus) return;
    // Review patch (P0): pass `reason` through the single mutation. Previously the
    // drawer fired two mutations (the second a DECLINED→DECLINED illegal transition)
    // and dropped the reason from the audit row.
    directMutation.mutate({ to: statusDialogState.newStatus, reason });
    setStatusDialogState({ open: false, newStatus: null });
  };

  const drawerContent =
    drawerView === 'promote' ? (
      <PromoteSpeakerSubView
        speaker={speaker}
        eventCode={eventCode}
        onBack={() => setDrawerView(null)}
      />
    ) : drawerView === 'quality-review' ? (
      <QualityReviewSubView
        speaker={speaker}
        eventCode={eventCode}
        onBack={() => setDrawerView(null)}
        onClose={onClose}
      />
    ) : (
      <>
        <PrimaryActionSurface
          speaker={speaker}
          callbacks={primaryActionCallbacks}
          slotCapacity={slotCapacity}
        />

        {/* Secondary actions list — AC7.2 */}
        <List dense disablePadding data-testid="drawer-secondary-actions">
          {canDecline && (
            <ListItemButton
              onClick={() => setStatusDialogState({ open: true, newStatus: 'DECLINED' })}
              data-testid="drawer-action-decline"
            >
              <ListItemIcon>
                <BlockIcon color="error" />
              </ListItemIcon>
              <ListItemText primary={t('organizer:speakerDrawer.secondaryActions.decline')} />
            </ListItemButton>
          )}
          {/* Epic 11 bug fix 2026-05-19 — the legacy "Reassign organizer" row was a
              no-op `setTab('details')` and duplicated functionality that now lives in
              the Details tab edit-mode (assignedOrganizer is part of the edit form).
              Removed. */}
          <ListItemButton
            onClick={() => {
              setTab('details');
              setIsEditingDetails(true);
            }}
            data-testid="drawer-action-edit-details"
          >
            <ListItemIcon>
              <EditIcon />
            </ListItemIcon>
            <ListItemText primary={t('organizer:speakerDrawer.secondaryActions.editDetails')} />
          </ListItemButton>
          {/* Epic 11 bug fix 2026-05-19 — for CONTACTED speakers, surface an
              "Add contact entry" action that opens the existing MarkContactedModal
              for recording additional outreach attempts (kanban primary action is
              "Promote to speaker"). */}
          {speakerStatus === 'CONTACTED' && (
            <ListItemButton
              onClick={() => primaryActionCallbacks.onLogOutreach(speaker)}
              data-testid="drawer-action-add-contact-entry"
            >
              <ListItemIcon>
                <PersonIcon />
              </ListItemIcon>
              <ListItemText
                primary={t(
                  'organizer:speakerDrawer.secondaryActions.addContactEntry',
                  'Add contact entry'
                )}
              />
            </ListItemButton>
          )}
          {/* 2026-05-20 — READY → ACCEPTED on-behalf path. Same StatusChangeDialog
              the kanban drag fires; reason is required (audit). For speakers who
              confirmed off-portal (email/phone) so the organizer skips the formal
              invitation step. */}
          {speakerStatus === 'READY' && !slotCapacity.reached && (
            <ListItemButton
              onClick={() => dispatchOverride('ACCEPTED')}
              data-testid="drawer-action-accept-on-behalf"
            >
              <ListItemIcon>
                <CheckCircleIcon color="success" />
              </ListItemIcon>
              <ListItemText
                primary={t(
                  'organizer:speakerDrawer.secondaryActions.acceptOnBehalf',
                  'Accept on behalf (skip invitation)'
                )}
              />
            </ListItemButton>
          )}
          {overrideTargets.length > 0 && (
            <ListItemButton
              onClick={(e) => setOverrideMenuAnchor(e.currentTarget)}
              data-testid="drawer-action-override-state"
            >
              <ListItemIcon>
                <SwapHorizIcon />
              </ListItemIcon>
              <ListItemText primary={t('organizer:speakerDrawer.secondaryActions.overrideState')} />
            </ListItemButton>
          )}
        </List>

        {/* Review patch: DECLINED is already surfaced as the dedicated "Decline with
          reason" row in the secondary-actions list above; don't repeat it inside the
          override popover. Filter is in `overrideTargets` (line ~150). */}
        <Menu
          anchorEl={overrideMenuAnchor}
          open={!!overrideMenuAnchor}
          onClose={() => setOverrideMenuAnchor(null)}
          data-testid="drawer-override-state-menu"
        >
          {overrideTargets.map((target) => (
            <MenuItem
              key={target}
              onClick={() => dispatchOverride(target)}
              data-testid={`drawer-override-target-${target.toLowerCase()}`}
            >
              {t(`organizer:speakerStatus.${target}`)}
            </MenuItem>
          ))}
        </Menu>

        <Tabs
          value={tab}
          onChange={handleTabChange}
          variant={isMobile ? 'scrollable' : 'standard'}
          scrollButtons={isMobile ? 'auto' : false}
          sx={{ borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}
        >
          <Tab
            value="details"
            label={t('organizer:speakerDrawer.tabs.details')}
            data-testid="drawer-tab-details"
          />
          {CONTENT_TAB_STATES.has(speakerStatus) && (
            <Tab
              value="content"
              label={t('organizer:speakerDrawer.tabs.content', 'Content')}
              data-testid="drawer-tab-content"
            />
          )}
          <Tab
            value="history"
            label={t('organizer:speakerDrawer.tabs.history')}
            data-testid="drawer-tab-history"
          />
        </Tabs>

        <Box sx={{ flex: 1, overflow: 'auto' }}>
          {tab === 'details' && (
            <DetailsTabPanel
              speaker={speaker}
              eventCode={eventCode}
              isEditing={isEditingDetails}
              onExitEditMode={() => setIsEditingDetails(false)}
            />
          )}
          {tab === 'content' && CONTENT_TAB_STATES.has(speakerStatus) && (
            <ContentSubmissionSubView
              speaker={speaker}
              eventCode={eventCode}
              onBack={() => setTab('details')}
              onClose={onClose}
              embedded
            />
          )}
          {tab === 'history' && <UnifiedHistoryPanel speaker={speaker} eventCode={eventCode} />}
        </Box>

        <StatusChangeDialog
          open={statusDialogState.open}
          speakerName={speaker.speakerName}
          currentStatus={speaker.status}
          newStatus={statusDialogState.newStatus ?? 'DECLINED'}
          onConfirm={handleConfirmStatusChange}
          onCancel={() => setStatusDialogState({ open: false, newStatus: null })}
        />
      </>
    );

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 520 } } }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <SpeakerDrawerHeader speaker={speaker} onClose={onClose} />
        {drawerContent}
      </Box>
      {/* Review patch — drawer-level error feedback. Surfaces directMutation +
          override-popover failures that were previously swallowed. */}
      <Snackbar
        open={errorSnackbar.open}
        autoHideDuration={6000}
        onClose={() => setErrorSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        data-testid="drawer-error-snackbar"
      >
        <Alert
          onClose={() => setErrorSnackbar((s) => ({ ...s, open: false }))}
          severity="warning"
          sx={{ width: '100%' }}
        >
          {errorSnackbar.message}
        </Alert>
      </Snackbar>
    </Drawer>
  );
};
