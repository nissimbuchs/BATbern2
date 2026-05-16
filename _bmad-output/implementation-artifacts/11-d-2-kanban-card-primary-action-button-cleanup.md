# Story 11.D.2: Kanban card primary-action button + cleanup

Status: ready-for-dev

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As an** organizer scanning the speaker kanban,
**I want** every speaker card to surface its single most-likely next action as a visible button (plus a time-in-state chip on the organizer row, with legacy "pending"-style indicators removed),
**So that** I can act on each card with one click without remembering the workflow rules or opening the drawer first.

## Phase / Dependencies / Requirements Covered

- **Phase:** D — Workflow semantics update + organizer UX (second of four D stories).
- **Depends on:**
  1. **Story 11.D.1** — Delivers the `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` endpoint and the `PromoteSpeakerDialog` modal that this story's CONTACTED button must open. The plan's §8.9 explicitly notes "Story 11.D.1 for the promote-to-READY modal that the `CONTACTED` button opens" (PRD lines 904-905). **Strict prerequisite — 11.D.1 must merge first.**
  2. **Story 11.B.2** (already landed on `feature/speaker-workflow-refactor` — commit `c53d02c8` + `6b7a01bc`) — `SpeakerWorkflowService.transition()` is the sole status writer; slot-capacity precondition is enforced inside the INVITED hook and surfaces as HTTP 409 with `details.code = 'SLOT_CAPACITY_REACHED'`.
  3. **Story 11.B.1** (already landed — commit `6f61c99a`) — `SpeakerWorkflowState` is the 8-state enum (`CONFIRMED` / `SLOT_ASSIGNED` / `OVERFLOW` / `WITHDREW` / `TENTATIVE` removed).
  4. **Story 11.B.3** (in `review` at story-creation time — see "Branch state at story start" below) — drops the legacy `is_tentative`/`tentative_reason` columns and the derived flags. This story can land before or after 11.B.3 lands to `develop`, but the dev MUST resolve the `speaker.isTentative` / `speaker.tentativeReason` references in `SpeakerStatusLanes.tsx` (lines 711-717 today) as part of this story's "remove legacy indicators" sweep — they are dead code post-11.B.3 regardless.
- **NO hard backend dependencies.** This story is frontend-only. It calls existing endpoints (the new `/promote` from 11.D.1, the existing `/send-invitation` from 11.B.2, the existing speaker-status PUT, the existing outreach-record endpoint).
- **Requirements covered (PRD lines 894-964):** UX-DR1 (state-aware primary-action button per card), UX-DR2 (time-in-state chip moved to organizer row), UX-DR3 (remove legacy "pending"/email-sent indicators), UX-DR4 (`⋯` secondary menu — **nice-to-have**, included only if scope permits, see AC8). Maps to plan §8.1 "Card changes — additive only", §8.2 "Primary action per state" (full state→button table), §8.6 slot-capacity gating (for the READY button's disabled tooltip).
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.D.2" lines 894-965 — the AC source-of-truth.
  - `docs/plans/speaker-workflow-refactor.md` §8.1 (card additive changes), §8.2 (state-action table), §8.6 (slot-capacity tooltip text), §8.9 story #1 ("State-aware card + cleanup").
  - `docs/architecture/06a-workflow-state-machines.md` §"Speaker Workflow Management" — 8-state model + transition allow-list.
  - ADR-009 §0.2 — `CONTACTED → READY` provisioning gate; `READY → INVITED` slot-capacity precondition.

---

## Branch state at story start

The current `feature/speaker-workflow-refactor` branch has uncommitted edits from 11.B.3 (in `review`). Critical for this story:

- `web-frontend/src/types/speakerPool.types.ts` — `isTentative?` and `tentativeReason?` are **removed** from `SpeakerPoolEntry`. The dev should treat this as the canonical post-11.B.3 type even if 11.B.3 has not yet merged to `develop`; this story does not re-add those fields.
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java` — derived `isSlotAssigned`, `isPublishable` flags are emitted as part of the response. This story consumes those flags (for the QUALITY_REVIEWED button — read-only "Publishable ✓" chip when `isSlotAssigned`).
- The backend's `/send-invitation` endpoint already throws `SlotCapacityReachedException` → HTTP 409 with `details.acceptedCount`, `details.invitedCount`, `details.maxSlots`. The READY button needs to consume this 409 for its tooltip text.

The dev should **start from `feature/speaker-workflow-refactor` HEAD** (rebasing on top of whatever has merged from 11.B.3 / 11.C.1 / 11.C.2 / 11.D.1 by the time this story begins). The story file structure references file paths as they exist on that branch.

---

## Acceptance Criteria

The AC are pinned to PRD lines 894-964 and plan §8.2. Each AC names the exact file under change. Per project-context.md "Enum Value Flow", the frontend `SpeakerPoolEntry.status` is the lowercase form on the wire (`'identified'`, `'contacted'`, …) but the existing `SpeakerStatusLanes.tsx` already mixes `'IDENTIFIED'` literals (the `STATUS_LANES` constant uses UPPER_CASE). The dev should match the existing local convention in `SpeakerStatusLanes.tsx` (UPPER_CASE comparisons) — do not introduce a new normalisation pattern in this story.

### AC1 — Primary-action button rendered per card, state-aware, full-width along card bottom (UX-DR1)

**Given** the file `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx`,
**When** the `SpeakerCard` component renders a card,
**Then** a single MUI `<Button variant="contained" fullWidth>` is rendered along the bottom edge of the card content (after all info sections, before the closing `</Card>` tag),
**And** the button is wrapped in a `<Box>` with `sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid', borderColor: 'divider' }}` (or equivalent — the visual intent is a clear separator between info and action),
**And** the button label is derived from a single `getPrimaryAction(status)` helper that returns `{ label, onClick, disabled?, tooltip?, isInfoChip? }`,
**And** the button is **omitted entirely** for states with `isInfoChip: true` (only QUALITY_REVIEWED-with-slot per AC1.7).

The state→button mapping per plan §8.2 (the source of truth — do **not** drift):

| Card status | Button label (i18n key in `organizer:speakerCard.primaryAction`) | onClick behaviour |
|---|---|---|
| `IDENTIFIED` | "Log outreach" (`logOutreach`) | Opens existing `MarkContactedModal` (lifted up to `EventSpeakersTab.tsx` — see AC2) |
| `CONTACTED` | "Promote to speaker" (`promoteToSpeaker`) | Opens 11.D.1's `PromoteSpeakerDialog` (lifted up to `EventSpeakersTab.tsx`) |
| `READY` | "Send invitation" (`sendInvitation`) | Opens the existing send-invitation flow (uses `useSendInvitation` mutation from `useSpeakerPool.ts`); **disabled** when slot capacity reached (AC4) |
| `INVITED` | "View response status" (`viewResponseStatus`) | Opens drawer (`onSpeakerClick`) |
| `ACCEPTED` | "Enter content" (`enterContent`) | **Stubbed** — opens drawer (`onSpeakerClick`) which surfaces the existing `ContentSubmissionSubView`. Full on-behalf modal lands in 11.D.4. The stub is functionally adequate: the existing drawer view already drives the same backend write path. |
| `CONTENT_SUBMITTED` | "Review content" (`reviewContent`) | Opens drawer (`onSpeakerClick`) which surfaces the existing `QualityReviewSubView` |
| `QUALITY_REVIEWED` (slot **not** assigned) | "Assign session slot" (`assignSessionSlot`) | Navigates to existing slot-assignment page: `navigate('/organizer/events/{eventCode}/slot-assignment')` (the same route `EventSpeakersTab.handleManageSlotAssignments` already uses) |
| `QUALITY_REVIEWED` (slot **assigned**) | **NOT a button** — render an MUI `<Chip>` with icon `<CheckCircleOutline>`, label "Publishable ✓" (`publishable`), `color="success"`, `variant="outlined"`, **no click handler** | n/a (info chip) |
| `DECLINED` | "View details" (`viewDetails`) | Opens drawer (`onSpeakerClick`) — drawer is read-only for DECLINED speakers (no transitions out per ADR-009 §0.2 "DECLINED is terminal") |

**And** `getPrimaryAction(status)` is implemented as a pure function colocated in the same file (or extracted to `SpeakerStatusLanes/getPrimaryAction.ts` — dev's call, but if extracted, the unit tests in AC9 must cover it directly without rendering the component).

**And** the QUALITY_REVIEWED slot-assigned check reads the new derived flag `speaker.isSlotAssigned` (emitted by `SpeakerPoolResponse` post-11.B.3). If the frontend type does not yet include this flag at story-start time, the dev:
1. Regenerates frontend types via `cd web-frontend && npm run generate:api-types` after confirming the backend spec includes the field.
2. If the spec is missing the field, surfaces this as a Critical Issue in the PR description and resolves it by either updating the spec in this PR (then regenerating types) or referencing the existing fallback (`speaker.sessionId != null && session.startTime != null`) — match what `SpeakerStatusLanes.tsx` line 449 already does for session lookup.

**And** the button click event uses `e.stopPropagation()` so it does NOT trigger the card-level `onClick` (which opens the drawer). The existing `handleClick` at line 417 already calls `e.stopPropagation()` on its own path — the new primary-action button must do the same in its own handler.

**And** the button respects the existing drag-state pattern: when `isDragging === true`, the button is **not interactive** (it inherits `opacity: 0.5` from the parent Card sx; clicks are still suppressed because `transform` is non-null inside `handleClick`, but the button's own `onClick` must additionally check `!transform` if it is a separate handler).

---

### AC2 — Modal-hosting lift to `EventSpeakersTab.tsx` for IDENTIFIED + CONTACTED buttons

**Given** the existing `EventSpeakersTab.tsx` already hosts `SpeakerStatusLanes` (line 333+) and `SpeakerDetailDrawer`,
**When** I open the file,
**Then** new modal hosting state is added at the parent level (NOT inside `SpeakerCard`):

```typescript
const [outreachModalState, setOutreachModalState] = useState<{ open: boolean; speaker: SpeakerPoolEntry | null }>({ open: false, speaker: null });
const [promoteModalState, setPromoteModalState] = useState<{ open: boolean; speaker: SpeakerPoolEntry | null }>({ open: false, speaker: null });
```

**And** new callback props are added to `SpeakerStatusLanesProps`:

```typescript
onLogOutreach?: (speaker: SpeakerPoolEntry) => void;       // For IDENTIFIED button
onPromoteSpeaker?: (speaker: SpeakerPoolEntry) => void;    // For CONTACTED button (delivered by 11.D.1)
```

**And** `EventSpeakersTab` passes these callbacks down with handlers that open the corresponding modals:

```typescript
onLogOutreach={(speaker) => setOutreachModalState({ open: true, speaker })}
onPromoteSpeaker={(speaker) => setPromoteModalState({ open: true, speaker })}
```

**And** `EventSpeakersTab` renders the modals near the bottom of its JSX (alongside the existing `SpeakerDetailDrawer`):

```tsx
{outreachModalState.speaker && (
  <MarkContactedModal
    open={outreachModalState.open}
    onClose={() => setOutreachModalState({ open: false, speaker: null })}
    onSuccess={() => {
      setOutreachModalState({ open: false, speaker: null });
      queryClient.invalidateQueries({ queryKey: ['speakerPool', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
    }}
    eventCode={eventCode}
    speakerId={outreachModalState.speaker.id}
    speakerName={outreachModalState.speaker.speakerName}
  />
)}
{promoteModalState.speaker && (
  <PromoteSpeakerDialog
    open={promoteModalState.open}
    onClose={() => setPromoteModalState({ open: false, speaker: null })}
    speaker={promoteModalState.speaker}
    eventCode={eventCode}
  />
)}
```

**And** the existing `onIdentifiedToContacted={handleIdentifiedToContacted}` prop (line 338, fired on **drag** IDENTIFIED → CONTACTED) **continues to work unchanged**. The new `onLogOutreach` is the **button-click** equivalent. They share the underlying `MarkContactedModal` but are wired through different event paths.

**And** the `PromoteSpeakerDialog` is the component delivered by Story 11.D.1 (file: `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` per 11.D.1 Task 7.2 — verify path at implementation time). It accepts `speaker` + `eventCode` props and internally drives the `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` endpoint via the `usePromoteSpeakerToReady` hook delivered by 11.D.1.

**And** the existing `handleSpeakerClick` (line 130) and `handleIdentifiedToContacted` (line 136) callbacks are untouched (they continue to open the drawer on row-click and drag-to-CONTACTED respectively).

---

### AC3 — Time-in-state chip rendered on the organizer-row, right-aligned, using existing relative-time formatting (UX-DR2)

**Given** the existing `SpeakerCard` content layout in `SpeakerStatusLanes.tsx`,
**When** the card renders,
**Then** a new `<Chip size="small" variant="outlined">` appears on the **organizer-row** (the same row that already shows the assigned organizer `Chip` at lines 590-614), positioned to the **right** of the existing organizer chip using `sx={{ ml: 'auto' }}`. If no organizer is assigned, the time-in-state chip is alone on the row, still right-aligned.

**And** the chip label is rendered using `date-fns` `formatDistanceToNow` (already used by `TeamActivityFeed.tsx:46+261` — reuse the locale wiring pattern from that file):

```typescript
import { formatDistanceToNow } from 'date-fns';
import { de, enUS } from 'date-fns/locale';

const locale = i18n.language === 'de' ? de : enUS;
const stateChangedAt = speaker.statusChangedAt ?? speaker.updatedAt ?? speaker.createdAt;  // see AC3 timestamp-source resolution below
const timeInState = formatDistanceToNow(new Date(stateChangedAt), { locale, addSuffix: false });
```

The label format is e.g. `"2 days"`, `"1 week"`, `"3 months"` — without the "ago" suffix (`addSuffix: false`), matching plan §8.1 example "2 days", "1 week".

**And** the chip has a tooltip showing the absolute date: `<Tooltip title={new Date(stateChangedAt).toLocaleString(i18n.language)}>`.

**And** the chip currently does **not** colour-code by threshold (yellow/red) — that is Story 11.D.3's scope (UX-DR7). For this story the chip uses `color="default"`. This is documented in the file as a `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` comment.

**Timestamp source resolution (AC3) — decided 2026-05-16 per Resolved Q#1:** the current `SpeakerPoolEntry` type has `createdAt`, `updatedAt`, `invitedAt`, `acceptedAt`, `declinedAt`, `contentSubmittedAt`. It does NOT have a single `statusChangedAt` field. The plan §8.1 says "the time-in-state indicator (e.g. '2 days', '1 week')" — i.e., time **in the current state**, not time since the row was created. The PM accepted the imperfect-but-shippable resolution; **NO backend `status_changed_at` column is added in this story**. Use this resolution order:
1. If `speaker.status === 'INVITED'` and `speaker.invitedAt`: use `invitedAt`.
2. Else if `speaker.status === 'ACCEPTED'` and `speaker.acceptedAt`: use `acceptedAt`.
3. Else if `speaker.status === 'CONTENT_SUBMITTED'` and `speaker.contentSubmittedAt`: use `contentSubmittedAt`.
4. Else if `speaker.status === 'DECLINED'` and `speaker.declinedAt`: use `declinedAt`.
5. Else: fall back to `speaker.updatedAt ?? speaker.createdAt`.

This is **imperfect** (the fall-back means `updatedAt` is overwritten by any field update, not just status changes) and the chip may briefly show "moments ago" if the row is touched by an unrelated field update. The dev adds a one-line code comment at the helper: `// TODO: switch to a dedicated status_changed_at column if organizers report the fallback is misleading.` No further work required.

---

### AC4 — READY card's "Send invitation" button disabled with i18n tooltip when slot capacity reached (UX-DR1 + plan §8.6)

**Given** plan §8.6 ("**Send invitation' is disabled when `count(ACCEPTED) + count(INVITED) >= max_slots` for the event**") and PRD line 922-925 (the verbatim tooltip text),
**When** the kanban renders a READY card,
**Then** the "Send invitation" button computes capacity locally from the already-loaded `speakers` array (no new API call):

```typescript
const event = useEvent(eventCode).data;                            // already used elsewhere in this tab
const maxSlots = event?.slotConfiguration?.maxSlots ?? 0;
const acceptedCount = speakers.filter(s => s.status === 'ACCEPTED').length;
const invitedCount = speakers.filter(s => s.status === 'INVITED').length;
const slotCapacityReached = maxSlots > 0 && (acceptedCount + invitedCount) >= maxSlots;
```

**And** when `slotCapacityReached === true` and the card status is READY:
- The button has `disabled={true}`.
- The button is wrapped in `<Tooltip title={t('organizer:speakerCard.slotCapacityTooltip', { invited: invitedCount, accepted: acceptedCount, slots: maxSlots })}>` with the i18n value: `"Slot capacity reached. {{invited}} invitations outstanding + {{accepted}} acceptances for {{slots}} slots. Wait or decline an accepted speaker to free a slot."`.
- MUI's `<Tooltip>` does not show on a disabled button by default — wrap the disabled button in a `<span>` (the standard MUI workaround; see the existing pattern at `SpeakerStatusLanes.tsx:558` for the disabled invite IconButton tooltip).

**And** the capacity values are passed down from `SpeakerStatusLanes` (which has access to the full `speakers` array) to each `SpeakerCard` via new props `slotCapacityReached: boolean` + `slotCapacityTooltipValues: { invited: number; accepted: number; slots: number }` so the per-card computation does NOT re-walk the array per render.

**And** (per Resolved Q#4 — defensive note required) at the slot-capacity computation site in `SpeakerStatusLanes.tsx`, add this comment:

```typescript
// Slot capacity is derived in-page from the loaded speakers array — this is correct as long
// as `speakers` is the complete event-scoped pool (it is today). If this query ever paginates,
// switch to a server-side count endpoint to avoid undercounting INVITED/ACCEPTED off-page.
```

This makes the invariant explicit so a future contributor adding pagination notices the dependency.

**And** the same i18n tooltip key (`speakerCard.slotCapacityTooltip`) is later reused by Story 11.D.3 for the READY-column "⚠ slot capacity reached" sub-line and by Story 11.D.4 for the drag-drop rejection toast — the key is defined ONCE in `organizer:speakerCard.*` namespace in this story.

**And** the disabled-button branch is exercised by both Vitest (AC9 frontend tests) and Playwright (AC9 `should_disableSendInvitation_when_slotCapacityReached`).

---

### AC5 — Removal of legacy "pending"/"send-invite" indicators on cards (UX-DR3 + plan §8.1)

The PRD lines 949-953 say "the existing 'pending' indicator/badge is removed (per UX-DR3)". The current code has multiple legacy indicators that conflate states under the pre-ADR-009 model. This AC enumerates each and the disposition:

**A. The IDENTIFIED card's inline "Send invite" `IconButton` (`SpeakerStatusLanes.tsx:550-575`)**
- **Disposition:** **REMOVE.** The new primary-action button (AC1) replaces it. Inviting from IDENTIFIED was a Story 6.1c shortcut for the legacy magic-link flow; under ADR-009 §0.2 the only path is `IDENTIFIED → CONTACTED → READY → INVITED`, with the formal invitation coming at READY.
- Code lines to delete: the `canInvite`/`hasEmail` checks (lines 444-446), the `{canInvite && (...)}` block (lines 550-575), the related `handleInviteClick` function (lines 426-442), the `useSendInvitation` import (line 47), the `sendInvitationMutation` (line 406) — **BUT** `useSendInvitation` IS still used by the new READY primary-action button. **Keep the import**; remove only the IDENTIFIED-specific wiring. The READY button re-uses the same hook through `OverviewTabPanel`'s existing pattern.

**B. The CONTACTED card's `<EmailIcon>` "invitation sent" tooltip (`SpeakerStatusLanes.tsx:576-581`)**
- **Disposition:** **REMOVE.** Under ADR-009 CONTACTED means "outreach in progress, no user provisioned, no formal invitation sent" — the badge's "Email sent" message is now misleading. The new primary-action button "Promote to speaker" is the right surface.

**C. The legacy `speaker.isTentative` + `speaker.tentativeReason` block (`SpeakerStatusLanes.tsx:711-717`)**
- **Disposition:** **REMOVE.** Story 11.B.1 dropped TENTATIVE from the state model; Story 11.B.3 drops the legacy columns. The conditional already evaluates to `false` always (the fields are removed from `SpeakerPoolEntry`). Remove the dead block plus the i18n key `organizer:speakers.tentativeReason` (line 329 of `en/organizer.json`) and `speakers.tentative`/`speakers.tentativeDetails`/`speakers.tentativeReason` in **all 10 locale files** (`web-frontend/public/locales/{locale}/organizer.json`). Verify no other component references those keys via `grep -rn "speakers.tentative" web-frontend/src` — must be zero matches after removal.

**D. The `STATUS_LANES` legacy entries (`SpeakerStatusLanes.tsx:78-93`)**
- **Background:** The current code lists `CONFIRMED` in `POST_ACCEPTANCE_LANES` and has `INVITED` in the wrong position (between IDENTIFIED and CONTACTED in `OUTREACH_LANES`). Story 11.B.1 removed CONFIRMED from the enum; ADR-009 §0.1 specifies the order `IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → DECLINED`.
- **Disposition (decided 2026-05-16 per Resolved Q#2 — clean up now):** **Reorder + remove CONFIRMED in this story.** Update the constants to:
  ```typescript
  const OUTREACH_LANES: SpeakerWorkflowState[] = ['IDENTIFIED', 'CONTACTED', 'READY', 'INVITED'];
  const POST_ACCEPTANCE_LANES: SpeakerWorkflowState[] = ['ACCEPTED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED', 'DECLINED'];
  ```
  Rationale: an empty/never-populated CONFIRMED lane wastes screen space; the wrong INVITED position is actively confusing under the new state model. The kanban file is being heavily edited by this story anyway — fixing 4 lines of constants while we're here is cheaper than a separate cleanup story.
- **STATUS_COLORS map**: remove the `CONFIRMED: '#2e7d32'` entry (line 71). The map at the top of `SpeakerStatusDashboard.tsx` (lines 39-48) also has the same `CONFIRMED` entry — remove it there too.
- **NOTE:** If 11.D.3 (column-header triage) is being worked in parallel, this lane reordering MUST land in 11.D.2 first (11.D.3's per-column logic indexes by state name).

**Invariant after this AC:** `grep -n "CONFIRMED" web-frontend/src/components/organizer/SpeakerStatus/` returns **zero** matches (or only inside comments referencing the legacy state).

---

### AC6 — Layout: the primary-action button does NOT push other card content off-screen on the smallest viewport

**Given** plan §8.1 specifies the primary-action button is "full-width along the bottom edge of the card",
**When** I view the kanban on a mobile viewport (`Grid size={{ xs: 12, sm: 6, md: 'grow' }}` per the existing lane layout — so a single lane spans the full row on `xs`),
**Then** the card height is allowed to grow to accommodate the button without truncating the speaker name, company, expertise, content-status chip, or organizer chip,
**And** the existing `flexGrow / overflow: 'auto'` inside `StatusLane` (line 364) handles vertical overflow per-lane — verify it still works post-change.

**And** the visual contract for the button is:
- Min height: 36px (MUI default `<Button>` size).
- Full width inside the card (`fullWidth` prop or `sx={{ width: '100%' }}`).
- Margin-top: 12px (`sx={{ mt: 1.5 }}`).
- Border-top separator above the button (1px divider) for visual distinction from the card content.

---

### AC7 — i18n keys added to ALL 10 locale files under `organizer:speakerCard.*` namespace

**Given** the project standard "All 10 locales … no locale lags behind" (project-context.md + Story 10-9 conventions),
**Then** the following keys are added under a new `speakerCard` section in `web-frontend/public/locales/{locale}/organizer.json` for each locale `de, en, es, fi, fr, gsw-BE, it, ja, nl, rm`:

```json
"speakerCard": {
  "primaryAction": {
    "logOutreach": "Log outreach",
    "promoteToSpeaker": "Promote to speaker",
    "sendInvitation": "Send invitation",
    "viewResponseStatus": "View response status",
    "enterContent": "Enter content",
    "reviewContent": "Review content",
    "assignSessionSlot": "Assign session slot",
    "viewDetails": "View details"
  },
  "publishable": "Publishable",
  "publishableTooltip": "Speaker is approved and has a session slot",
  "slotCapacityTooltip": "Slot capacity reached. {{invited}} invitations outstanding + {{accepted}} acceptances for {{slots}} slots. Wait or decline an accepted speaker to free a slot.",
  "timeInStateTooltip": "Current state since {{date}}"
}
```

**And** the en/de values are authored by the dev (canonical translations); the 8 non-EN/DE locales receive machine-translated baselines (matches Story 10-9 + Story 11.D.1 AC7 pattern). PR description flags the locales that need a follow-up native-speaker review.

**And** the locale-key linter (whatever Vitest test or script asserts key-parity — search for `i18n-unused.py` or the `npm run` task that validates locale parity; project-context.md notes `Story 10-9 Phase 4 script: web-frontend/scripts/i18n/analyze-unused.py`) passes after the keys are added.

**And** legacy keys removed in AC5 (C) — `organizer:speakers.tentative`, `speakers.tentativeDetails`, `speakers.tentativeReason` — are removed from all 10 locale files.

---

### AC8 — `⋯` secondary menu — DEFERRED to Story 11.D.4 (per Resolved Q#3)

**Given** PRD lines 955-959 mark the `⋯` secondary menu as "nice-to-have for first iteration",
**And** PM decision 2026-05-16: defer the menu to Story 11.D.4 (which redesigns the drawer anyway, so the four secondary actions land alongside a coherent drawer-and-card menu design),
**Then** **this story does NOT implement the `⋯` menu**. The four actions (reassign organizer, edit details, override state, decline with reason) remain reachable through the existing surfaces:
- "reassign organizer" — drawer → DetailsTabPanel (existing `AssignedOrganizerField`).
- "edit details" — drawer → DetailsTabPanel.
- "override state" — drag-drop to any lane (existing) + drawer's status dropdown (existing).
- "decline with reason" — drag-drop to DECLINED lane → triggers `StatusChangeDialog` with reason field (existing — `StatusChangeDialog.tsx`).

**And** the dev does **not** add a `<MoreHorizIcon>` IconButton, `<Menu>`, or `<MenuItem>` components to the card in this story. Save the bandwidth for the unified drawer-and-card design in 11.D.4.

---

### AC9 — Test coverage: Vitest + Playwright

**Frontend unit tests (Vitest + RTL)** — extend `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusLanes.test.tsx`:

1. `should_renderLogOutreachButton_when_speakerIsIdentified` — assert button with `data-testid="primary-action-button-{speakerId}"` exists and label matches `t('organizer:speakerCard.primaryAction.logOutreach')`.
2. `should_renderPromoteToSpeakerButton_when_speakerIsContacted`.
3. `should_renderSendInvitationButton_when_speakerIsReady` — happy path, capacity NOT reached.
4. `should_disableSendInvitation_when_slotCapacityReached` — set up speakers array with `ACCEPTED + INVITED >= maxSlots`, assert the button is disabled and the tooltip text (via `screen.getByLabelText` or `data-testid` on the wrapping span) contains the parameterised value.
5. `should_renderViewResponseStatusButton_when_speakerIsInvited`.
6. `should_renderEnterContentButton_when_speakerIsAccepted`.
7. `should_renderReviewContentButton_when_speakerIsContentSubmitted`.
8. `should_renderAssignSessionSlotButton_when_speakerIsQualityReviewed_andNoSlotAssigned`.
9. `should_renderPublishableChip_when_speakerIsQualityReviewed_andSlotAssigned` — assert it's a `<Chip>` (not a `<Button>`), has the success colour, has no click handler that opens a modal.
10. `should_renderViewDetailsButton_when_speakerIsDeclined`.
11. `should_callOnLogOutreach_when_logOutreachButtonClicked` — assert the new callback prop is invoked with the speaker entity.
12. `should_callOnPromoteSpeaker_when_promoteToSpeakerButtonClicked`.
13. `should_callOnSpeakerClick_when_viewResponseStatusButtonClicked` — wraps the existing drawer callback.
14. `should_renderTimeInStateChip_onOrganizerRow_rightAligned` — assert the chip exists, the formatted label matches a mocked `formatDistanceToNow` result.
15. `should_notRenderLegacySendInviteIconButton_onIdentifiedCards` — regression guard for AC5(A); assert the old `data-testid="invite-button-{speakerId}"` is **not** present on any card.
16. `should_notRenderLegacyEmailSentBadge_onContactedCards` — regression guard for AC5(B); assert `data-testid="invite-sent-badge"` is **not** present on any CONTACTED card.
17. `should_notRenderConfirmedLane` — regression guard for AC5(D); assert `data-testid="status-lane-confirmed"` is **not** rendered.
18. `should_renderLanesInAdr009Order` — assert lane order: IDENTIFIED, CONTACTED, READY, INVITED in outreach group; ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED in post-acceptance group.

**Frontend unit tests** — extend `SpeakerStatusDashboard.test.tsx`:
- Update any test referencing CONFIRMED lane (currently the test imports `statusCounts: { CONFIRMED: N, ... }` etc. — verify with grep before changing). Remove CONFIRMED rows from the `pendingCount` mock setups if they exist (lines 120, 212, 254, 290, 318 use `pendingCount` which is the summary endpoint's field, NOT a card badge — those are kept as-is).

**Playwright E2E** — extend `web-frontend/e2e/organizer/speaker-status-tracking.spec.ts` (or add new file `speaker-card-primary-action.spec.ts` if the existing file is getting unwieldy):

1. `it('should open MarkContactedModal when Log-outreach button is clicked on IDENTIFIED card', ...)`.
2. `it('should open PromoteSpeakerDialog when Promote-to-speaker button is clicked on CONTACTED card', ...)`.
3. `it('should disable Send-invitation button and show tooltip when slot capacity is reached', ...)` — seed an event with `maxSlots = 2`, two `ACCEPTED` speakers, one `READY` speaker; assert the disabled button + tooltip text on the READY card.
4. `it('should open drawer when View-response-status is clicked on INVITED card', ...)`.
5. `it('should render Publishable info chip (not button) on QUALITY_REVIEWED card with assigned slot', ...)`.

**Test data setup**: use the existing Playwright fixtures from `e2e/organizer/speaker-status-tracking.spec.ts` (the file already manages event/speaker seeding). For the slot-capacity test, the test must seed three speakers in the right states — the simplest path is to drive transitions through the backend API in `beforeEach` (the spec already uses the project's `apiHelpers` or equivalent — match what's there).

**Backend tests**: this story has **no backend changes**, so no new Testcontainers tests. The existing `SpeakerWorkflowServiceTest` already covers the slot-capacity precondition (Story 11.B.2 AC) and `SpeakerInvitationServiceIntegrationTest` (added by 11.D.1 AC9) covers the 409 surface. Do **not** add duplicate backend tests in this story.

---

### AC10 — Documentation + commit hygiene

**Given** CLAUDE.md §"Doc Drift Prevention" + `.github/doc-drift-mappings.yml`,
**Then** the same commit/PR that lands code includes:

1. **No update** to `docs/architecture/06a-workflow-state-machines.md` — the state machine and transitions are unchanged.
2. **No update** to `docs/architecture/05-frontend-architecture.md` for now — the existing high-level frontend architecture doc does not enumerate per-component conventions at the granularity of "primary-action button on each kanban card". If the doc-drift auditor flags this story, add a one-line entry to the `05-frontend-architecture.md` §"Role-Specific Component Specifications" mentioning the kanban card's primary-action pattern (1-2 sentences max — not a full re-write).
3. **No update** to ADR-009 — the ADR is the source of truth this story implements; the implementation does not change the ADR.
4. **Optional** update to `docs/architecture/06a-workflow-state-machines.md` §"Speaker Workflow Management" §"State Definitions" table: if the dev finds that the table currently includes obsolete fields (e.g., references to `isTentative`), reconcile them in this PR. **Verify first** — Story 11.A.1 already aligned this doc to ADR-009 per its AC11; do not duplicate work.

**And** the commit message uses Conventional Commits with the marker `[Story 11.D.2]`:
- Suggested form: `feat(web-frontend): add state-aware primary-action button on speaker kanban cards [Story 11.D.2]`.
- **NO `[no-doc]` marker** unless the dev confirms that no doc files were touched (the i18n locale-file changes are not "docs" for doc-drift purposes — they are translations).

---

### AC11 — Verification of cross-cutting invariants

**Given** the full frontend test suites run,
**Then**:

1. `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck.log` — passes (no `isTentative` / `tentativeReason` errors after AC5(C); no `CONFIRMED` errors after AC5(D)).
2. `cd web-frontend && npm run lint 2>&1 | tee /tmp/fe-lint.log` — passes with `--max-warnings 50` per project-context.md.
3. `cd web-frontend && npm test -- SpeakerStatus 2>&1 | tee /tmp/fe-test.log` — all existing + new tests pass.
4. `cd web-frontend && npx playwright test --project=chromium e2e/organizer/speaker-status-tracking.spec.ts 2>&1 | tee /tmp/playwright.log` — passes locally.
5. **Grep invariants** (run after all edits, capture to `/tmp/grep-invariants.log`):
   - `grep -rn "CONFIRMED" web-frontend/src/components/organizer/SpeakerStatus/` returns **zero** matches outside comments.
   - `grep -rn "isTentative\|tentativeReason" web-frontend/src/components/` returns **zero** matches.
   - `grep -rn "invite-button-\|invite-sent-badge" web-frontend/src/components/organizer/SpeakerStatus/` returns **zero** matches.
   - `grep -rn "speakers\.tentative" web-frontend/` returns **zero** matches across both `src/` and `public/locales/`.
   - `grep -rn "speakerCard.primaryAction" web-frontend/public/locales/` returns matches in **all 10** locale files (en, de, es, fi, fr, gsw-BE, it, ja, nl, rm).
6. **Backend regression guard**: run the existing event-management suite `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/em-test.log` — passes. This story does NOT modify backend code; the run is purely a regression guard to confirm the frontend changes (esp. the i18n key removal in AC5 C) did not break any backend test that asserts an i18n key string (unlikely but possible).
7. **Bruno tests pass**: `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log` — passes (no Bruno tests change in this story; smoke regression only).

---

## Tasks / Subtasks

Tasks ordered to compile + test incrementally. Each task names the AC it satisfies and the test to run after to lock it in.

### Task 1 — Frontend: card-level primary-action button + button mapping (AC1, AC2, AC6)

1.1. Open `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx`. Add a `getPrimaryAction(speaker, callbacks, slotCapacityState)` helper near the bottom of the file (or extract to `getPrimaryAction.ts`). Return shape: `{ label: string; onClick: () => void; disabled?: boolean; tooltip?: string; isInfoChip?: boolean }`.
1.2. Modify `SpeakerCardProps` to accept new optional callbacks (`onLogOutreach`, `onPromoteSpeaker`) and slot-capacity state (`slotCapacityReached`, `slotCapacityTooltipValues`).
1.3. Inside `SpeakerCard`, after the existing card content (after the post-acceptance details block) but **before** the closing `</Card>`, render:
   - A `<Box>` separator (`borderTop: 1px solid divider`, `mt: 1.5`, `pt: 1.5`).
   - If `getPrimaryAction(...).isInfoChip`, render a `<Chip>` (for the QUALITY_REVIEWED slot-assigned case).
   - Else render a `<Button fullWidth variant="contained" disabled={action.disabled} onClick={(e) => { e.stopPropagation(); action.onClick(); }}>` wrapped in `<Tooltip>` when `action.tooltip` is non-empty (use the disabled-button span-wrap pattern from line 558).
1.4. Modify `StatusLane` to accept + pass through `slotCapacityReached` + `slotCapacityTooltipValues` + the two new callbacks.
1.5. Modify `SpeakerStatusLanes` to:
   - Accept new props `onLogOutreach`, `onPromoteSpeaker` on its `Props` interface.
   - Compute `slotCapacityReached` + `slotCapacityTooltipValues` once at the top of the component (from `speakers` + `event.slotConfiguration.maxSlots` — fetch the event via the existing `useEvent(eventCode, ['sessions'])` already used in `SpeakerStatusDashboard.tsx` line 74; lift it to `SpeakerStatusLanes` if needed, or pass `maxSlots` as a prop — dev's call).
   - Pass slot-capacity state + callbacks down to each `StatusLane` → each `SpeakerCard`.
1.6. **Verify**: `cd web-frontend && npm run type-check` succeeds.

### Task 2 — Frontend: lift outreach + promote modals to `EventSpeakersTab` (AC2)

2.1. Open `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx`. Import `MarkContactedModal` and `PromoteSpeakerDialog` (the latter is the component from Story 11.D.1 — verify path at story-start time).
2.2. Add two new `useState` hooks at the top of the component for `outreachModalState` and `promoteModalState`.
2.3. Add handlers `handleLogOutreach(speaker)` and `handlePromoteSpeaker(speaker)` that open the corresponding modals.
2.4. Pass `onLogOutreach={handleLogOutreach}` and `onPromoteSpeaker={handlePromoteSpeaker}` to `SpeakerStatusLanes` (around the existing line 333+).
2.5. Render the two modal components near the bottom of the JSX (alongside `SpeakerDetailDrawer`).
2.6. The existing `handleIdentifiedToContacted` + `handleSpeakerClick` paths are untouched.
2.7. **Verify**: `cd web-frontend && npm run type-check` and `npm run lint`. Run `npm test -- EventSpeakers` if any tests exist for the tab.

### Task 3 — Frontend: time-in-state chip + AC3 timestamp resolution (AC3)

3.1. Import `formatDistanceToNow` + `de, enUS` locales from `date-fns/locale` (reuse the pattern from `TeamActivityFeed.tsx`).
3.2. Implement the AC3 timestamp-resolution helper inside `SpeakerCard` (or extract as `getStatusChangedAt(speaker)`).
3.3. Render the `<Tooltip><Chip variant="outlined" size="small" sx={{ ml: 'auto' }}>...</Chip></Tooltip>` on the organizer row. If `speaker.assignedOrganizerId` is null/undefined, the organizer chip is absent and the time-in-state chip is alone on the row — keep `ml: 'auto'` for right-alignment.
3.4. Add a `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` comment for future.
3.5. **Verify**: `cd web-frontend && npm test -- SpeakerStatusLanes`.

### Task 4 — Frontend: slot-capacity disable + tooltip for READY button (AC4)

4.1. Inside `SpeakerStatusLanes`, fetch `event.slotConfiguration.maxSlots` (via `useEvent(eventCode, ['sessions'])` — note this hook is currently called in `SpeakerStatusDashboard.tsx` line 74; lift it up or duplicate-query, but TanStack Query will dedupe identical query keys).
4.2. Compute `slotCapacityReached` + `slotCapacityTooltipValues = { invited, accepted, slots }` at the top.
4.3. Pass `{ slotCapacityReached, slotCapacityTooltipValues }` down to `StatusLane` → `SpeakerCard`.
4.4. Inside `getPrimaryAction(speaker, callbacks, { slotCapacityReached, slotCapacityTooltipValues })`, the READY branch returns `{ disabled: slotCapacityReached, tooltip: slotCapacityReached ? t('speakerCard.slotCapacityTooltip', slotCapacityTooltipValues) : undefined, ... }`.
4.5. Wire the tooltip via MUI's disabled-button pattern (`<Tooltip><span><Button disabled>...</Button></span></Tooltip>`).
4.6. **Verify**: `cd web-frontend && npm test -- SpeakerStatusLanes` (add the new disabled-state test from AC9 item 4).

### Task 5 — Frontend: legacy-indicator removal (AC5)

5.1. Delete `handleInviteClick` (lines 426-442), the `sendInvitationMutation` initialisation (line 406), and the `canInvite`/`hasEmail` block (lines 444-446) **only as far as they uniquely served the IDENTIFIED-card `IconButton`**. Verify the `useSendInvitation` import is still needed (yes — for the new READY primary-action button). Re-wire the READY action to call `useSendInvitation` through whatever pattern Task 1 chose (lift to parent or per-card hook — dev's call, but per-card hook keeps the change localised).
5.2. Delete the `{canInvite && (...)}` block (lines 550-575) entirely.
5.3. Delete the `{speaker.status === 'CONTACTED' && (...)}` Email-sent badge block (lines 576-581).
5.4. Delete the `{speaker.isTentative && speaker.tentativeReason && (...)}` block (lines 711-717).
5.5. Update `STATUS_LANES` constants per AC5(D): outreach `[IDENTIFIED, CONTACTED, READY, INVITED]`, post-acceptance `[ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED]`.
5.6. Remove `CONFIRMED` from `STATUS_COLORS` in `SpeakerStatusLanes.tsx` (line 71) AND `SpeakerStatusDashboard.tsx` (line 46).
5.7. Remove `speakers.tentative`, `speakers.tentativeDetails`, `speakers.tentativeReason` from all 10 locale files (AC7).
5.8. **Verify** via grep invariants (AC11 item 5) — pipe to `/tmp/grep-invariants.log`.

### Task 6 — i18n: add 10 keys × 10 locales (AC7)

6.1. Add the new `speakerCard` namespace block to `web-frontend/public/locales/en/organizer.json` and `web-frontend/public/locales/de/organizer.json` (author canonical translations).
6.2. Machine-translate to the 8 other locales: `es, fi, fr, gsw-BE, it, ja, nl, rm`. Use the project's translation flow (Story 10-9 Phase 2 / 11.D.1 Task 8.2 pattern).
6.3. Remove `tentative*` keys from all 10 locale files.
6.4. **Verify**: `cd web-frontend && npm run type-check && npm run lint`. If a key-parity check script exists (`scripts/i18n/`), run it.

### Task 7 — Frontend tests: extend Vitest suites (AC9)

7.1. Open `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusLanes.test.tsx`. Add the 18 test cases from AC9 (items 1-18).
7.2. Use `screen.getByRole('button', { name: t('organizer:speakerCard.primaryAction.logOutreach') })` patterns. Mock `formatDistanceToNow` if necessary to assert exact chip text.
7.3. Update existing tests that assert on the removed IconButton / Email-sent badge (lines 458, 564, 579 of the current file have the `data-testid`s that will disappear).
7.4. **Verify**: `cd web-frontend && npm test -- SpeakerStatus 2>&1 | tee /tmp/fe-test.log` — green.

### Task 8 — Playwright E2E (AC9)

8.1. Open `web-frontend/e2e/organizer/speaker-status-tracking.spec.ts`. Add the 5 cases from AC9. If the file is too large, split into `speaker-card-primary-action.spec.ts` co-located in the same folder.
8.2. Use the project's existing seed/auth fixtures (`global-setup.ts` writes `.playwright-auth-organizer.json`).
8.3. The slot-capacity test seeds three speakers via API in `beforeEach` (or uses pre-baked test data — match what the existing spec does).
8.4. **Verify**: `cd web-frontend && npx playwright test --project=chromium e2e/organizer/ 2>&1 | tee /tmp/playwright.log` — green.

### Task 9 — Full verification + commit (AC10, AC11)

9.1. Run the AC11 grep invariants, capture to `/tmp/grep-invariants.log`.
9.2. Run `cd web-frontend && npm run type-check && npm run lint && npm test 2>&1 | tee /tmp/fe-full.log`. Confirm green.
9.3. Run `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/em-test.log` (regression guard).
9.4. Run `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log` (smoke).
9.5. Stage + commit. Suggested message: `feat(web-frontend): add state-aware primary-action button on speaker kanban cards [Story 11.D.2]`.

---

## Dev Notes

### Why this story is the right Phase D scope to deliver immediately after 11.D.1

Story 11.D.1 closes the backend gap (the `/promote` endpoint exists; the slot-capacity gate is documented; the brainstorm panel has a "Promote to speaker" button). But the kanban itself — the primary surface organizers stare at all day — still shows the legacy magic-link "Invite" IconButton on IDENTIFIED cards and the misleading "Email sent" badge on CONTACTED cards. Story 11.D.2 brings the kanban into ADR-009 alignment in **one self-contained frontend story** that the dev can ship without coordinating across backend changes.

Plan §8.9 explicitly says "Story 1 alone closes 80% of the UX gap — primary-action button on every card." This is that story.

### Strict sequencing: 11.D.1 must merge first

Per the plan §8.9 (and PRD lines 904-905), the CONTACTED card's "Promote to speaker" button opens 11.D.1's `PromoteSpeakerDialog`. If 11.D.2 is implemented before 11.D.1 lands, the CONTACTED button will reference a component that doesn't exist. The dev should rebase 11.D.2 on top of `feature/speaker-workflow-refactor` HEAD after 11.D.1 merges to that branch (the long-lived feature branch per §9.1 of the plan).

If 11.D.2 implementation begins before 11.D.1 merges, the dev's two options:
1. **Wait** — preferred; 11.D.1 is small/surgical and should merge quickly.
2. **Stub the import** — temporarily wire `onPromoteSpeaker` to a `console.warn('PromoteSpeakerDialog not yet available')` placeholder. Remove the stub in a follow-up commit when 11.D.1 merges. NOT recommended; introduces a transient broken state.

### Reuse, don't recreate — the modals already exist

This story does NOT introduce new modals. It **lifts existing modals up** to `EventSpeakersTab`:
- `MarkContactedModal` (`web-frontend/src/components/organizer/SpeakerOutreach/MarkContactedModal.tsx`) — already exists. Used today only when the user drags IDENTIFIED → CONTACTED (the drawer auto-opens at the outreach form). The new IDENTIFIED button opens this same modal directly, skipping the drawer.
- `PromoteSpeakerDialog` (`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` per 11.D.1 Task 7.2) — delivered by 11.D.1. Used today only inside the brainstorm panel. The new CONTACTED button opens the same modal from the kanban.
- The READY button reuses `useSendInvitation` (`web-frontend/src/hooks/useSpeakerPool.ts`) — the existing hook called by both the legacy IDENTIFIED IconButton (line 47 of the current SpeakerStatusLanes) AND the drawer's `OverviewTabPanel.tsx` (line 14). The story removes the IDENTIFIED-side wiring and adds the READY-side wiring — net change: one consumer added, one removed.
- The INVITED + CONTENT_SUBMITTED + DECLINED + ACCEPTED + QUALITY_REVIEWED buttons all delegate to the existing `onSpeakerClick` drawer-opening callback. The drawer (`SpeakerDetailDrawer.tsx`) already has the correct default-tab behaviour per `getDefaultTab.ts`.

### What this story is NOT doing (scope guard)

- **No column-header triage chips.** That is Story 11.D.3 (UX-DR5-7).
- **No threshold-driven time-in-state colour coding.** That is Story 11.D.3 (UX-DR7); this story renders the chip in `color="default"` always.
- **No guided drag-drop, drop halos, drop-validity toasts.** That is Story 11.D.4 (UX-DR8-10).
- **No unified drawer redesign.** That is Story 11.D.4 (UX-DR13).
- **No on-behalf content form.** That is Story 11.D.4 (UX-DR12) — this story's ACCEPTED button is **stubbed** to open the existing drawer's `ContentSubmissionSubView`.
- **No `⋯` secondary menu** unless dev bandwidth permits — AC8 is non-blocking.
- **No backend changes.** The slot-capacity gate is already wired (11.B.2); the `/promote` endpoint is delivered by 11.D.1; the `isSlotAssigned` derived flag is delivered by 11.B.3.
- **No new modals.** All four interactive primary-actions reuse existing modal/drawer components.
- **No endpoint rename** (`/send-invitation` → `/invite` — explicitly out of scope per 11.D.1's resolved Q#3).
- **No speaker-portal frontend changes** (`web-frontend/src/pages/speaker/**`). Scope is the organizer kanban only per plan §8's scope statement.

### Project Structure Notes

- Primary file under change: `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (the kanban + card components).
- Hosting file: `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` (where the modal lifts land).
- Companion-cleanup file: `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx` (CONFIRMED removed from `STATUS_COLORS` per AC5 D).
- Test files: `__tests__/SpeakerStatusLanes.test.tsx` (extend), `e2e/organizer/speaker-status-tracking.spec.ts` (extend or split).
- i18n: all 10 locale files under `web-frontend/public/locales/{locale}/organizer.json`.
- No new component directories.

### Testing Standards (frontend-only UI story)

- **Vitest + RTL**: use `screen.getByRole('button', { name: ... })` for the primary-action button assertions. Use `userEvent.click` (project-context.md: "Prefer `userEvent` over `fireEvent`"). Use `waitFor()` for any tooltip-render assertion (MUI tooltips render asynchronously).
- **Mock layer**: mock `useSendInvitation` + `usePromoteSpeakerToReady` + `MarkContactedModal` only when asserting the button wiring; for the slot-capacity disabled-state test, mock `useEvent` to control `maxSlots`.
- **Playwright**: organizer auth project (`chromium`). Seed test data via the existing `apiHelpers` (find in `e2e/helpers/`) — do NOT duplicate seeding logic.
- **Test naming**: project-context.md "Pattern: `should_expectedBehavior_when_condition`".
- **i18n in tests**: use the project's test-i18n setup — most existing tests mock `useTranslation` to return a key passthrough. Verify the existing pattern in `SpeakerStatusLanes.test.tsx` (project-context.md: "Test mocks use namespace-stripped keys: `'wizard.buttons.cancel'` not `'registration.wizard.buttons.cancel'`").

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 894-964] — Story 11.D.2 AC list (this story's primary spec).
- [Source: docs/plans/speaker-workflow-refactor.md §8.1] — Card changes (additive only).
- [Source: docs/plans/speaker-workflow-refactor.md §8.2] — Primary action per state (the state→button table).
- [Source: docs/plans/speaker-workflow-refactor.md §8.6] — Slot-capacity gating (replaces overflow management).
- [Source: docs/plans/speaker-workflow-refactor.md §8.9 story 1] — "State-aware card + cleanup" — the scoping rationale.
- [Source: docs/architecture/06a-workflow-state-machines.md §"Speaker Workflow Management"] — 8-state model + transition allow-list.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.1, §0.2] — state ordering, provisioning gate, slot-capacity precondition.
- [Source: web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx] — primary file under change.
- [Source: web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx:39-48] — CONFIRMED entry to remove in `STATUS_COLORS`.
- [Source: web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx:130-139] — existing modal-hosting patterns to extend.
- [Source: web-frontend/src/components/organizer/SpeakerOutreach/MarkContactedModal.tsx] — existing modal to lift.
- [Source: web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx] — delivered by Story 11.D.1; lifted to kanban by this story.
- [Source: web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDetailDrawer.tsx] — drawer surface that INVITED/ACCEPTED/CONTENT_SUBMITTED/DECLINED buttons delegate to.
- [Source: web-frontend/src/hooks/useSpeakerPool.ts] — `useSendInvitation`, `usePromoteSpeakerToReady` (the latter from 11.D.1).
- [Source: web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx:46, 261-267] — existing `formatDistanceToNow` + locale wiring to reuse.
- [Source: _bmad-output/implementation-artifacts/11-d-1-promote-endpoint-brainstorm-tightening-slot-gate.md] — the immediate predecessor; defines `PromoteSpeakerDialog` and the `/promote` endpoint.
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — slot-capacity gate at INVITED.
- [Source: _bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md] — drops `isTentative`/`tentativeReason` columns; derives `isSlotAssigned`/`isPublishable`. **In `review` at story-creation time** — confirm landed before this story merges.
- [Source: CLAUDE.md §"Critical Development Standards"] — TDD, type safety, OpenAPI-first.
- [Source: _bmad-output/project-context.md §"Enum Value Flow"] — JSON UPPER_CASE matches the `STATUS_LANES` literals used in the current `SpeakerStatusLanes.tsx`.
- [Source: _bmad-output/project-context.md §"React Patterns"] — `useTranslation()` for all user-facing strings; presigned uploads (n/a here — no file uploads).
- [Source: _bmad-output/project-context.md §"Frontend Testing"] — Vitest + RTL; `screen.getByRole`; `userEvent` over `fireEvent`; `waitFor()` for async.

---

## Dev Agent Record

### Agent Model Used

_To be filled in by the dev agent._

### Debug Log References

_To be filled in by the dev agent — e.g., `/tmp/fe-typecheck.log`, `/tmp/fe-lint.log`, `/tmp/fe-test.log`, `/tmp/playwright.log`, `/tmp/em-test.log`, `/tmp/bruno.log`, `/tmp/grep-invariants.log`._

### Completion Notes List

_To be filled in by the dev agent — one short paragraph per AC._

### File List

_To be filled in by the dev agent. Expected scope: ~6-8 source files (SpeakerStatusLanes.tsx, SpeakerStatusDashboard.tsx, EventSpeakersTab.tsx, 1-2 test files, 10 locale files) + the optional `⋯` menu component if delivered._

### Change Log

| Date | Change |
|------|--------|
| 2026-05-16 | Story 11.D.2 drafted via `bmad-create-story`. |
| 2026-05-16 | Resolved all 4 Open Questions with PM (Nissim). Q1 → accept the imperfect timestamp-source resolution (no `status_changed_at` column in this story; add TODO comment). Q2 → reorder lanes + remove CONFIRMED now (AC5 D locked in). Q3 → defer `⋯` secondary menu to Story 11.D.4 (AC8 narrowed to "do not implement"). Q4 → add defensive comment about pagination dependency at the slot-capacity computation site (AC4 amended). |

---

## Open Questions (resolved 2026-05-16)

All four questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above already reflect the decisions. Listed here for traceability.

1. ✅ **Accept the imperfect timestamp-source resolution; do NOT add a `status_changed_at` column.** The time-in-state chip uses per-state timestamps (`invitedAt`, `acceptedAt`, `contentSubmittedAt`, `declinedAt`) where they exist and falls back to `updatedAt` / `createdAt` otherwise. The fallback is imperfect — `updatedAt` is bumped by any field update, not just status changes — but matches the data the frontend already has and ships from the existing model. The dev adds a one-line `// TODO: switch to a dedicated status_changed_at column if organizers report the fallback is misleading.` at the helper. The clean fix (backend column + migration + DTO regen) is revisited only if organizer feedback flags it. AC3 reflects this.

2. ✅ **Reorder lanes + remove CONFIRMED in this story.** The kanban file is being heavily edited anyway, so the 4-line constants cleanup lands here. New lane order: `[IDENTIFIED, CONTACTED, READY, INVITED]` (outreach) + `[ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED]` (post-acceptance). CONFIRMED removed from both `STATUS_LANES` and `STATUS_COLORS` in `SpeakerStatusLanes.tsx` AND `SpeakerStatusDashboard.tsx`. AC5(D) captures the full disposition.

3. ✅ **Defer `⋯` secondary menu to Story 11.D.4.** Story 11.D.4 redesigns the drawer (which is where reassign-organizer / edit-details / override-state / decline-with-reason already live), so the `⋯` menu lands alongside a unified drawer-and-card design rather than as a stand-alone surface in 11.D.2. The four actions remain reachable in 11.D.2 via drag-drop + the existing drawer. AC8 reflects "do not implement in this story".

4. ✅ **Add the defensive note about in-page derivation.** The slot-capacity computation in AC4 walks the already-loaded `speakers` array — correct as long as that array is the complete event-scoped pool (it is today). A code comment at the computation site documents the dependency so a future contributor adding pagination notices it. AC4 captures the verbatim comment text.

---

_Story created via `bmad-create-story` skill on 2026-05-16. Authored with comprehensive context-engine analysis. All 4 Open Questions resolved with PM the same day. Depends on Story 11.D.1 (must merge first) + builds on 11.B.1, 11.B.2 (landed), 11.B.3 (in review). Ready for `bmad-dev-story` execution once 11.D.1 merges to `feature/speaker-workflow-refactor`._
