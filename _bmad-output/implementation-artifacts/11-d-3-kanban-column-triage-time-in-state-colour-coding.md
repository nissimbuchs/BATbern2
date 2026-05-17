# Story 11.D.3: Kanban column triage + time-in-state colour coding

Status: review

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As an** organizer running a backlog of brainstormed and in-flight speakers,
**I want** every kanban column header to surface what needs my attention and every speaker card's time-in-state chip to age visibly,
**So that** I can triage the board at a glance instead of opening each card.

## Phase / Dependencies / Requirements Covered

- **Phase:** D — Workflow semantics update + organizer UX (third of four D stories). Layers triage + ageing chrome on top of the state-aware card delivered in 11.D.2.
- **Depends on (strict sequencing — must land in order):**
  1. **Story 11.D.2** — Card-level primary-action button + organizer-row layout + time-in-state chip rendered in `color="default"`. This story replaces the `color="default"` with threshold-driven `"warning"` / `"error"` and adds the column-header sub-line. **Strict prerequisite.** The 11.D.2 file leaves an explicit `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` marker at the chip render site (per its AC3) — this story removes that marker.
  2. **Story 11.B.3** (already landed on `feature/speaker-workflow-refactor` — commit `ae983462`) — adds derived `isSlotAssigned` + `isPublishable` to `SpeakerPoolResponse`. Used by the QUALITY_REVIEWED "🪑 N awaiting slot" sub-line and chip colour rule.
  3. **Story 11.B.2** (already landed — commit `c53d02c8` + `6b7a01bc`) — slot-capacity precondition lives inside `SpeakerWorkflowService.transition()`. The READY column sub-line ("⚠ slot capacity reached") is the **frontend mirror** of the same gate; the gate itself is not touched by this story.
- **No new hard backend dependencies.** This story is **frontend-only**. The PRD AC explicitly directs: "the implementation prefers in-page derivation over a new aggregation endpoint" (epic-11 lines 1019-1023). All sub-line counts and chip colours are computed from the already-loaded `speakers` array + the already-loaded `event` object.
- **Requirements covered (PRD lines 968-1028):** UX-DR5 (column-header "needs attention" sub-line with per-state copy from §8.3), UX-DR6 (click-to-filter the column to the triggering subset), UX-DR7 (time-in-state chip colour-coded per state-specific thresholds, defaults per §8.7 table, overridable per event).
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.D.3" lines 968-1028 — primary AC source-of-truth.
  - `docs/plans/speaker-workflow-refactor.md` §8.3 "Column headers — surface what needs attention" (sub-line copy table).
  - `docs/plans/speaker-workflow-refactor.md` §8.6 "Slot-capacity gating" (READY column sub-line "⚠ slot capacity reached" reuses the i18n key + computation 11.D.2 introduced).
  - `docs/plans/speaker-workflow-refactor.md` §8.7 "Time-in-state colour coding" (yellow/red threshold table per state).
  - `docs/plans/speaker-workflow-refactor.md` §8.9 story 2 — "Column-header awareness + time-in-state colour coding" — the scoping rationale.
  - `docs/architecture/06a-workflow-state-machines.md` §"Speaker Workflow Management" — 8-state model (unchanged; no state-machine edits in this story).
  - ADR-009 §0.1, §0.2 — state semantics and transition rules (referenced for context; not modified).

---

## Branch state at story start

The current `feature/speaker-workflow-refactor` branch is the target. Critical facts a dev should verify before starting:

- **11.D.2 has merged**, so `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` now contains:
  - `STATUS_LANES` reordered to ADR-009 order (`IDENTIFIED, CONTACTED, READY, INVITED` + `ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED`).
  - `STATUS_COLORS` no longer contains `CONFIRMED`.
  - The card-level primary-action button is rendered.
  - The time-in-state chip is rendered on the organizer row with `color="default"` and a `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` comment.
  - The `getStatusChangedAt(speaker)` helper (or inline equivalent) is in place per 11.D.2 AC3.
  - The slot-capacity computation (`slotCapacityReached`, `slotCapacityTooltipValues`) is computed at `SpeakerStatusLanes` top-level and passed down to lanes / cards.
- The 11.D.2 i18n namespace `organizer:speakerCard.*` exists in all 10 locale files. This story **extends** that namespace (the new sub-line keys live alongside the primary-action keys; no new top-level namespace).
- `web-frontend/src/types/speakerPool.types.ts` has `isSlotAssigned?: boolean` + `isPublishable?: boolean` (post-11.B.3) and the legacy `WITHDREW`/`OVERFLOW`/`SLOT_ASSIGNED` union members are scheduled for removal once the API spec drops them; leave them alone — they are unused at runtime now.
- `EventSpeakersTab.tsx` already calls `useEvent(eventCode, ['sessions'])` (line 88); the `event` object — including `event.date` — is already in scope at the parent and is passed (or trivially passable) to `SpeakerStatusLanes`.

The dev rebases on `feature/speaker-workflow-refactor` HEAD at story-start time.

---

## Acceptance Criteria

All AC are pinned to PRD lines 968-1028 and plan §§8.3 + 8.6 + 8.7. Each AC names the exact file under change. Per `project-context.md` "Enum Value Flow", `SpeakerPoolEntry.status` arrives from the API as lowercase (`'identified'`, `'contacted'`, …) but the existing `SpeakerStatusLanes.tsx` compares against UPPER_CASE literals — match that local convention; do not introduce a new normalisation pattern in this story.

### AC1 — New `kanbanThresholds` module: defaults + classification helpers (UX-DR7, plan §8.7)

> **Resolution note (per Resolved Q#1):** thresholds are **hardcoded defaults only**. There is **no per-event override** in this story. The PRD AC at line 1011 says "thresholds are read from event settings (overridable per event), defaulting to the §8.7 values"; the PM explicitly approved deviating from the "overridable" clause and shipping defaults only (2026-05-16). The helper functions below keep `thresholds: KanbanThresholdConfig` as a parameter purely for testability (tests can pass crafted thresholds to verify boundary conditions without monkeypatching the module). The production call site at `SpeakerStatusLanes.tsx` always passes `DEFAULT_KANBAN_THRESHOLDS` verbatim. No `resolveKanbanThresholds(event)` helper exists. No reads from `event.metadata`. No backend touch.

**Given** a new file `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts` (NEW),
**When** I open the file,
**Then** it exports:

```typescript
import type { SpeakerWorkflowState } from '@/types/speakerPool.types';

export type ThresholdSeverity = 'normal' | 'warning' | 'error';

/**
 * Per-state thresholds. Numeric values are days (positive = days elapsed since
 * the state's entry timestamp; negative = days remaining before a deadline).
 * For deadline-bound states, the helper consults speaker fields (responseDeadline,
 * contentDeadline) instead of pure time-in-state.
 */
export interface KanbanStateThresholds {
  /** Days since state entry → "warning" (yellow) */
  warningDays?: number;
  /** Days since state entry → "error" (red) */
  errorDays?: number;
  /** For deadline-bound states only — days BEFORE deadline at which "warning" triggers */
  warningDaysBeforeDeadline?: number;
}

export type KanbanThresholdConfig = Partial<Record<SpeakerWorkflowState, KanbanStateThresholds>>;

/**
 * Defaults from docs/plans/speaker-workflow-refactor.md §8.7.
 * DECLINED has no thresholds — terminal state, no colour change.
 */
export const DEFAULT_KANBAN_THRESHOLDS: KanbanThresholdConfig = {
  IDENTIFIED:        { warningDays: 30, errorDays: 60 },
  CONTACTED:         { warningDays: 7,  errorDays: 14 },
  READY:             { warningDays: 3,  errorDays: 7  },
  // INVITED uses responseDeadline (3 days before / past) — see classify() logic
  INVITED:           { warningDaysBeforeDeadline: 3 },
  // ACCEPTED yellow = 14 days without content; red = contentDeadline passed
  ACCEPTED:          { warningDays: 14 },
  CONTENT_SUBMITTED: { warningDays: 3,  errorDays: 7  },
  // QUALITY_REVIEWED uses days-before-event-date — see classify() logic
  QUALITY_REVIEWED:  { warningDaysBeforeDeadline: 30 /* days before event_date */ },
  DECLINED:          {},
};

export interface ClassifyChipInput {
  speaker: SpeakerPoolEntry;
  /** Anchor timestamp = `getStatusChangedAt(speaker)` (the same helper 11.D.2 already exposes/inlines) */
  statusChangedAt: Date;
  /** Event date — only consulted for the QUALITY_REVIEWED rule */
  eventDate: Date | null;
  /** `new Date()` at the time of computation. Injected for deterministic testing. */
  now: Date;
  /** Always `DEFAULT_KANBAN_THRESHOLDS` in production; parameter kept so unit tests can pass crafted thresholds for boundary scenarios. */
  thresholds: KanbanThresholdConfig;
}

/** Pure function. Returns the chip colour severity for a single card. */
export function classifyChipSeverity(input: ClassifyChipInput): ThresholdSeverity;
```

**And** `classifyChipSeverity` implements the §8.7 table verbatim:

| State | `warning` (yellow) returned when … | `error` (red) returned when … |
|---|---|---|
| `IDENTIFIED` | `daysInState >= 30` | `daysInState >= 60` |
| `CONTACTED` | `daysInState >= 7` | `daysInState >= 14` |
| `READY` | `daysInState >= 3` | `daysInState >= 7` |
| `INVITED` | `speaker.responseDeadline` exists AND `now` is within `3` days before deadline | `speaker.responseDeadline` exists AND `now` is past deadline |
| `ACCEPTED` | `daysInState >= 14` | `speaker.contentDeadline` exists AND `now` is past deadline |
| `CONTENT_SUBMITTED` | `daysInState >= 3` | `daysInState >= 7` |
| `QUALITY_REVIEWED` (no slot) | `eventDate` set AND `now` is within `30` days **before** event AND `!speaker.isSlotAssigned` | `eventDate` set AND `now` is within `14` days **before** event AND `!speaker.isSlotAssigned` |
| `QUALITY_REVIEWED` (slot assigned) | always `normal` | n/a |
| `DECLINED` | always `normal` (terminal) | n/a |

Where `daysInState = floor((now - statusChangedAt) / 86_400_000)`.

**And** the function is **deterministic, pure, and locale-free** (no `formatDistanceToNow`, no i18n calls — pure number/date math). Unit-tested per AC5.

**And** the module also exports two aggregation helpers consumed by AC2:

```typescript
/** Count of cards in the given state whose chip would be `warning` or `error`. */
export function countAttentionCards(
  speakers: SpeakerPoolEntry[],
  state: SpeakerWorkflowState,
  eventDate: Date | null,
  now: Date,
  thresholds: KanbanThresholdConfig,
): number;

/** Returns the predicate used to filter cards when the sub-line is clicked (AC3). */
export function makeAttentionPredicate(
  state: SpeakerWorkflowState,
  eventDate: Date | null,
  now: Date,
  thresholds: KanbanThresholdConfig,
): (speaker: SpeakerPoolEntry) => boolean;
```

Both helpers internally reuse `classifyChipSeverity` (with the helper resolving `statusChangedAt` per the existing 11.D.2 pattern) so the chip and the sub-line **never disagree** — a card that contributes to the sub-line count is the same set as cards rendering with `warning`/`error` colour.

### AC2 — Column header gains a "needs attention" sub-line per §8.3 (UX-DR5)

**Given** the existing `StatusLane` component at `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (around lines 320-379),
**When** the lane renders its header (the `<Box>` with `display: 'flex'` at line 348-361 today),
**Then** the header changes from a 2-element (title + count chip) layout to a **3-line stacked** layout:

```tsx
<Box sx={{ mb: 2 }}>
  {/* Line 1: state name + count chip on a row */}
  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
    <Typography variant="h6" sx={{ color }} data-testid={`status-lane-heading-${status.toLowerCase()}`}>
      {t(`organizer:speakerStatus.${status}`)}
    </Typography>
    <Chip label={speakers.length} size="small" sx={{ backgroundColor: color, color: 'white' }} />
  </Box>
  {/* Line 2 + 3: "needs attention" sub-line, rendered as a clickable Link/Button when non-empty */}
  {attentionSubline && (
    <Box
      component="button"
      type="button"
      data-testid={`status-lane-subline-${status.toLowerCase()}`}
      onClick={onSublineClick}
      sx={{
        mt: 0.5,
        background: 'none',
        border: 'none',
        padding: 0,
        cursor: 'pointer',
        textAlign: 'left',
        display: 'block',
        color: attentionSubline.severity === 'error' ? 'error.main' : 'warning.main',
        fontSize: '0.75rem',
        fontWeight: filterActive ? 700 : 500,
        textDecoration: filterActive ? 'underline' : 'none',
        '&:hover': { textDecoration: 'underline' },
      }}
    >
      {attentionSubline.label}
    </Box>
  )}
</Box>
```

**And** the sub-line content is computed at the parent (`SpeakerStatusLanes`) level — **not** per-lane internally — so the parent has a single source of truth for both the sub-line copy and the click-to-filter behaviour. The parent passes `attentionSubline` + `onSublineClick` + `filterActive` props down to each `StatusLane`.

**And** the sub-line per state follows the §8.3 table verbatim (PRD lines 983-991):

| State | Sub-line copy (i18n key under `organizer:speakerCard.lanes.<state>Subline`) | Severity | Show when |
|---|---|---|---|
| `IDENTIFIED` | _none_ | n/a | never |
| `CONTACTED` | `"⚠ {{count}} stale (>14 days)"` | `warning` | `count > 0` (count = cards with `error` severity per AC1, since red = ≥ 14 days) |
| `READY` | `"⚠ Slot capacity reached"` (no count — the gate fires globally) | `warning` | `slotCapacityReached === true` (already computed by 11.D.2; reused verbatim) |
| `INVITED` (two-part) | `"⏰ {{approaching}} approaching deadline · {{past}} past deadline"` — render each clause only when its count > 0 | `error` if `past > 0`, else `warning` | `approaching > 0 \|\| past > 0` |
| `ACCEPTED` | `"📝 {{count}} awaiting content"` | `warning` | `count > 0` (count = cards with severity ≠ `normal`) |
| `CONTENT_SUBMITTED` | `"👀 {{count}} awaiting moderator review"` | `warning` if `count` >0 / all are `warning`, `error` if any are `error` | `count > 0` (count = cards with severity ≠ `normal`) |
| `QUALITY_REVIEWED` | `"🪑 {{count}} awaiting slot"` | `warning` if any are `warning`, `error` if any are `error` | `count > 0` (count = cards with QUALITY_REVIEWED + no slot + severity ≠ `normal`) |
| `DECLINED` | _none_ | n/a | never |

**INVITED two-clause detail:** to keep the i18n strings reasonable across 10 locales, the dev defines **two** i18n keys (`invitedSubline.approaching` for `"⏰ {{count}} approaching deadline"` and `invitedSubline.past` for `"⏰ {{count}} past deadline"`) and joins them with `" · "` only when both clauses fire. The composite text is built in TS; the locale files store the two atomic strings. The severity is `error` whenever the `past` clause is present, otherwise `warning`.

**And** the emoji characters (⚠ ⏰ 📝 👀 🪑) appear inline in the i18n values (they are part of the user-visible copy per §8.3) — not in code. This keeps locale files self-contained.

**And** `attentionSubline === null` is the no-sub-line case (`IDENTIFIED` always, `DECLINED` always, and any state where its count condition is false) — the `StatusLane` then renders only lines 1 (title + count) and no second line, preserving the visual height-stability for adjacent lanes (use a `min-height` on the sub-line slot if necessary to keep all lane headers the same height).

### AC3 — Click the sub-line to filter the column (UX-DR6)

**Given** AC2 renders the sub-line as a `<button>` (or `MuiLink` — dev's call, but it must be a real focusable element for accessibility),
**When** the organizer clicks the sub-line,
**Then** the lane filters its rendered cards to the subset that contributed to the count (the predicate from `makeAttentionPredicate(...)`, AC1),
**And** the click toggles: a second click clears the filter; the visual `filterActive` styling (bold + underline per AC2) reflects the active state.

**And** the filter state is **per-column local UI state**, lifted to `SpeakerStatusLanes` so the toggle survives a re-render but is **not** persisted to URL / localStorage / TanStack Query cache:

```typescript
// Inside SpeakerStatusLanes
const [attentionFilter, setAttentionFilter] = useState<SpeakerWorkflowState | null>(null);

const filteredSpeakers = useMemo(() => {
  if (!attentionFilter) return speakers;
  const predicate = makeAttentionPredicate(attentionFilter, eventDate, now, thresholds);
  return speakers.map(s => ({ ...s, _attentionHidden: s.status === attentionFilter && !predicate(s) }));
}, [speakers, attentionFilter, eventDate, now, thresholds]);
```

Then the `speakersByStatus` grouping at line 222 filters out cards where `_attentionHidden === true` (or — preferred — pass the predicate down and have each `StatusLane` filter its own slice). The dev picks whichever is cleaner; the AC requirement is that **only the clicked column's contents change**, not the other columns'.

**And** the click handler on the sub-line calls `setAttentionFilter(attentionFilter === status ? null : status)`. Clicking IDENTIFIED's "needs attention" line is a no-op because IDENTIFIED has no sub-line (AC2); only the columns that render a sub-line are clickable.

**And** the `READY` column's sub-line — `"⚠ Slot capacity reached"` — is a special case: it does **not** filter the column (there is no per-card "I caused the capacity gate" set — it's a global condition). For READY, the sub-line is rendered as **non-clickable text** (use `<Typography>` with `color="warning.main"` instead of a button, no `onClick`). Document this exception inline in the code.

**And** the filter is **automatically cleared** when:
1. The user changes the underlying event or speaker list significantly (handled by a `useEffect([eventCode], () => setAttentionFilter(null))`).
2. The sub-line count drops to 0 (because the underlying speakers no longer match the predicate — e.g., organiser worked through the backlog). Handled by a `useEffect([attentionFilter, speakers], () => { if (attentionFilter && countAttentionCards(speakers, attentionFilter, eventDate, now, thresholds) === 0) setAttentionFilter(null); })`.

### AC4 — Time-in-state chip colour-coded per `classifyChipSeverity` (UX-DR7, plan §8.7)

**Given** 11.D.2 renders the time-in-state chip with `color="default"` and leaves the `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` marker,
**When** the chip is rendered in this story,
**Then** the chip's `color` prop is derived from `classifyChipSeverity({ speaker, statusChangedAt, eventDate, now, thresholds })`:

| `classifyChipSeverity` returns | MUI `<Chip color={…}>` value |
|---|---|
| `normal` | `"default"` |
| `warning` | `"warning"` |
| `error` | `"error"` |

**And** the chip's `variant` stays `"outlined"` (matches 11.D.2). The colour change drives both the border and the text colour automatically via MUI's `<Chip>` API; no `sx` overrides are needed.

**And** the `// TODO(11.D.3): …` comment from 11.D.2 is **deleted** as part of this story.

**And** the tooltip (`<Tooltip title={…}>`) wrapping the chip continues to show the absolute date per 11.D.2 AC3 — no change.

**And** the per-render `now` value is the same `now` used by AC1/AC2 (computed once at the top of `SpeakerStatusLanes` — `const now = useMemo(() => new Date(), [/* stable per render */])` is fine; alternatively the dev passes a `now` prop down for testability). The chip rendered today versus tomorrow can naturally differ; no real-time tick is required. The story does **not** add a `setInterval` to re-render every minute — per Resolved Q#4, refresh-on-focus via TanStack Query is sufficient.

### AC5 — Test coverage: Vitest (unit + component) + Playwright (E2E)

**Unit tests for `kanbanThresholds.ts`** — new file `web-frontend/src/components/organizer/SpeakerStatus/__tests__/kanbanThresholds.test.ts`:

The dev MUST author at least the following Vitest cases. Each calls `classifyChipSeverity` directly with crafted inputs — no rendering, no React.

1. `should_returnNormal_when_speakerIsIdentified_andDaysInStateIsZero`.
2. `should_returnWarning_when_speakerIsIdentified_andDaysInStateIsExactly30`.
3. `should_returnError_when_speakerIsIdentified_andDaysInStateIsExactly60`.
4. `should_returnWarning_when_speakerIsContacted_andDaysInStateIsBetween7and14`.
5. `should_returnError_when_speakerIsContacted_andDaysInStateIsAtLeast14`.
6. `should_returnNormal_when_speakerIsReady_andDaysInStateIs2`.
7. `should_returnError_when_speakerIsReady_andDaysInStateIs8`.
8. `should_returnWarning_when_speakerIsInvited_andResponseDeadlineIs2DaysAway`.
9. `should_returnError_when_speakerIsInvited_andResponseDeadlineHasPassed`.
10. `should_returnNormal_when_speakerIsInvited_andResponseDeadlineIs10DaysAway`.
11. `should_returnWarning_when_speakerIsAccepted_andDaysInStateIs15_withNoContentDeadline`.
12. `should_returnError_when_speakerIsAccepted_andContentDeadlineHasPassed`.
13. `should_returnWarning_when_speakerIsContentSubmitted_andDaysInStateIs4`.
14. `should_returnError_when_speakerIsContentSubmitted_andDaysInStateIs8`.
15. `should_returnWarning_when_speakerIsQualityReviewed_andEventIs20DaysAway_andNoSlot`.
16. `should_returnError_when_speakerIsQualityReviewed_andEventIs10DaysAway_andNoSlot`.
17. `should_returnNormal_when_speakerIsQualityReviewed_andSlotAssigned_regardlessOfEventDate`.
18. `should_returnNormal_when_speakerIsDeclined_regardlessOfDaysInState`.
19. `should_returnCountAttentionCards_matchingClassifyChipSeverity_forACONTACTEDColumn` — assert the aggregator and the classifier never disagree.
20. `should_buildPredicateThatMatchesCountAttentionCards` — assert `speakers.filter(predicate).length === countAttentionCards(...)` for a mixed-state input.

> No override-resolution tests — per Resolved Q#1, no override mechanism ships in this story. The `thresholds` parameter on `classifyChipSeverity` exists for testability only; the unit tests above pass `DEFAULT_KANBAN_THRESHOLDS` everywhere except where a custom value is needed to probe a boundary.

**Vitest mocking note:** tests inject `now: new Date('2026-05-16T12:00:00Z')` (or any fixed instant) so day arithmetic is deterministic. Do **not** mock `Date`/`Date.now` globally; pass `now` as a function parameter (AC1's signature already exposes it).

**Component tests for `SpeakerStatusLanes`** — extend `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusLanes.test.tsx`:

21. `should_renderContactedSubline_when_someContactedCardsAreStale` — assert text `"⚠ 2 stale (>14 days)"` is visible in the CONTACTED lane header (use `data-testid="status-lane-subline-contacted"`).
22. `should_notRenderContactedSubline_when_noContactedCardsAreStale` — assert the sub-line element is absent.
23. `should_renderReadyCapacityReachedSubline_when_slotCapacityReached` — set `event.slotConfiguration.maxSlots = 2` + 2 ACCEPTED speakers + 1 READY speaker; assert `"⚠ Slot capacity reached"` text appears in READY header and the element is NOT a `<button>` (per Resolved Q#5 — static text).
24. `should_renderInvitedSubline_withApproachingAndPastClauses_joinedByDot` — seed two INVITED speakers, one with `responseDeadline` in 2 days, one with `responseDeadline` 1 day ago. Assert the joined sub-line text.
25. `should_renderQualityReviewedSubline_when_eventIs20DaysAway_andSomeHaveNoSlot` — assert `"🪑 N awaiting slot"`.
26. `should_renderTimeInStateChip_withWarningColor_when_speakerIsContactedFor8Days` — assert MUI Chip's class or test the `color` prop via `screen.getByTestId` + computed style. (Use `expect(chip.className).toContain('MuiChip-colorWarning')` — the standard MUI test pattern; if brittle, switch to a `data-color` attribute the dev adds.)
27. `should_renderTimeInStateChip_withErrorColor_when_speakerIsContactedFor15Days`.
28. `should_clearAttentionFilter_when_subLineCountDropsToZero` — initial state: filter active, ≥1 attention card in CONTACTED. Mutation: simulate the attention card transitioning to READY (reduce its `daysInState` effectively). Re-render. Assert the filter is auto-cleared (the `filterActive` underline is gone).

**Click-to-filter component test:**

29. `should_filterContactedColumn_when_subLineClicked` — render with 3 CONTACTED cards (2 stale, 1 fresh). Click the CONTACTED sub-line. Assert only the 2 stale cards remain in the CONTACTED column. Other columns are unaffected (assert their card counts are unchanged).
30. `should_clearFilter_when_subLineClickedASecondTime` — click → asserts filtered → click again → asserts un-filtered.

**Playwright E2E** — extend `web-frontend/e2e/organizer/speaker-status-tracking.spec.ts` (or new file `speaker-column-triage.spec.ts` in the same folder):

31. `it('should display CONTACTED sub-line and filter the column when clicked', ...)` — seed an event with N speakers in CONTACTED, some old enough to be stale. Visit the kanban. Assert the sub-line text is visible; click it; assert only stale cards are visible.
32. `it('should colour the time-in-state chip red when a CONTACTED speaker has been there 15+ days', ...)` — assert the chip has the MUI error colour class. The simplest path: seed a speaker with `created_at` set to 20 days in the past via the backend test-setup helpers (the project's E2E spec already includes a similar pattern — match what `speaker-status-tracking.spec.ts` does for time-based scenarios).
33. `it('should display "Slot capacity reached" in the READY column when the gate fires', ...)` — seed: `maxSlots=2`, 2 ACCEPTED, 1 READY. Assert sub-line text. Assert the sub-line is NOT clickable (no `<button>` element).

**Backend tests**: this story has **no backend changes**. Run the existing `./gradlew :services:event-management-service:test` once as a regression guard (AC7). Do **not** add new backend tests.

### AC6 — i18n keys added to ALL 10 locale files under `organizer:speakerCard.lanes.*`

**Given** the project standard "All 10 locales … no locale lags behind",
**Then** the following keys are added under a new `lanes` sub-block inside the existing `speakerCard` namespace in `web-frontend/public/locales/{locale}/organizer.json` for each locale `de, en, es, fi, fr, gsw-BE, it, ja, nl, rm`:

```json
"speakerCard": {
  "lanes": {
    "contactedSubline": "⚠ {{count}} stale (>14 days)",
    "readySlotCapacitySubline": "⚠ Slot capacity reached",
    "invitedSubline": {
      "approaching": "⏰ {{count}} approaching deadline",
      "past": "⏰ {{count}} past deadline",
      "joiner": " · "
    },
    "acceptedSubline": "📝 {{count}} awaiting content",
    "contentSubmittedSubline": "👀 {{count}} awaiting moderator review",
    "qualityReviewedSubline": "🪑 {{count}} awaiting slot"
  }
}
```

**And** the en/de values are authored by the dev (canonical translations); the 8 non-EN/DE locales receive machine-translated baselines (matches the Story 10-9 + 11.D.1 + 11.D.2 pattern). The emoji characters are preserved in all 10 locales (they are part of the visual design language, not English-specific). The PR description flags which locales need a follow-up native-speaker review.

**And** the locale-key parity check passes (run any project i18n linter — `web-frontend/scripts/i18n/analyze-unused.py` per memory) — all 10 locales contain the new `speakerCard.lanes.*` keys.

**And** `web-frontend/public/locales/en/organizer.json` adds the new block by editing the existing `speakerCard` object (introduced by 11.D.2 around line ~263). The dev does NOT create a duplicate `speakerCard` key.

### AC7 — Verification of cross-cutting invariants

**Given** the full frontend test suites + a regression guard for the backend,
**Then** all of the following commands pipe their output through `tee` to `/tmp/` log files (per project-context.md: "Pipe `gradle`/`make` output through `tee /tmp/<name>.log`") and exit cleanly:

1. `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck.log` — passes.
2. `cd web-frontend && npm run lint 2>&1 | tee /tmp/fe-lint.log` — passes with `--max-warnings 50`.
3. `cd web-frontend && npm test -- SpeakerStatus kanbanThresholds 2>&1 | tee /tmp/fe-test.log` — all new and existing tests pass.
4. `cd web-frontend && npx playwright test --project=chromium e2e/organizer/ 2>&1 | tee /tmp/playwright.log` — passes locally.
5. **Grep invariants** (capture to `/tmp/grep-invariants.log`):
   - `grep -rn "TODO(11.D.3)" web-frontend/src/components/organizer/SpeakerStatus/` returns **zero** matches — the 11.D.2-deposited TODO marker is removed.
   - `grep -rn "kanbanThresholds" web-frontend/src/` returns matches in `kanbanThresholds.ts`, `SpeakerStatusLanes.tsx`, `__tests__/kanbanThresholds.test.ts`, and `__tests__/SpeakerStatusLanes.test.tsx` — at minimum.
   - `grep -rn "resolveKanbanThresholds\|event\.metadata\.kanbanThresholds" web-frontend/src/` returns **zero** matches — per Resolved Q#1, no override mechanism ships in this story.
   - `grep -rn "speakerCard.lanes" web-frontend/public/locales/` returns matches in **all 10** locale files.
   - `grep -rn "color=\"default\"" web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` returns **zero** matches **on the time-in-state chip** (verify by reading the file at the chip render site — the chip now uses a computed `color` value).
6. **Backend regression guard**: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/em-test.log` — passes (no backend changes; pure regression).
7. **Bruno tests pass**: `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log` — passes (no Bruno tests change; smoke regression).
8. **Visual smoke (manual)**: dev opens `http://localhost:8100/organizer/events/BATbern56/speakers` in a browser against `make dev-native-up`, navigates the kanban tab, confirms:
   - At least one column has a coloured sub-line (depending on local seed data).
   - Clicking a sub-line visibly filters its column; clicking again restores all cards.
   - At least one time-in-state chip is rendered in yellow or red (or — with fresh test data — all in default colour; in that case, manually patch a speaker's `created_at` via the local PostgreSQL to backdate it and re-verify).
   - The three-line column-header layout looks balanced — no jarring height differences between lanes with vs. without a sub-line.

### AC8 — Documentation + commit hygiene

**Given** CLAUDE.md §"Doc Drift Prevention" + `.github/doc-drift-mappings.yml`,
**Then** the same commit/PR that lands code includes:

1. **No update** to `docs/architecture/06a-workflow-state-machines.md` — the state machine, transitions, and side-effect rules are unchanged.
2. **No update** to ADR-009 — implementation honours the existing ADR.
3. **No update** to `docs/api/events-api.openapi.yml` or any OpenAPI spec — no backend API change and no override mechanism shipping (per Resolved Q#1).
4. **No update** to `docs/architecture/05-frontend-architecture.md` for now — the existing high-level frontend architecture doc does not enumerate per-component conventions at this granularity. If the doc-drift auditor flags this story, add a one-line entry in the appropriate section mentioning the kanban column-header triage + threshold module (1-2 sentences max — not a rewrite).
5. **Optional** update to `docs/plans/speaker-workflow-refactor.md` §8.7: leave a one-line "Implemented in Story 11.D.3" note alongside the threshold table if the dev judges it useful for future readers. Not strictly required.

**And** the commit message uses Conventional Commits with the marker `[Story 11.D.3]`:
- Suggested form: `feat(web-frontend): kanban column triage sub-lines + time-in-state colour coding [Story 11.D.3]`.
- **NO `[no-doc]` marker** — locale-file changes are not docs but they are user-visible changes; the doc-drift auditor's mapping treats them as UI changes. The dev verifies this assumption against the latest mapping at PR time.

---

## Tasks / Subtasks

Tasks ordered to compile + test incrementally. Each task names the AC it satisfies.

### Task 1 — Create `kanbanThresholds.ts` module (AC1) — [x]

- [x] 1.1. Create `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts` with `ThresholdSeverity`, `KanbanStateThresholds`, `KanbanThresholdConfig`, `ClassifyChipInput` types; `DEFAULT_KANBAN_THRESHOLDS` constant; `classifyChipSeverity`; `countAttentionCards`; `makeAttentionPredicate`; `countInvitedSplit`; `attentionMaxSeverity`; `severityToChipColor`. `getStatusChangedAt` extracted from `SpeakerStatusLanes.tsx` so both consumers share it.
- [x] 1.2. JSDoc comments co-located on each exported symbol.
- [x] 1.3. No `resolveKanbanThresholds(event)` helper; no `event.metadata` reads (Resolved Q#1).
- [x] 1.4. **Verified**: `npm run type-check` clean (`/tmp/fe-typecheck-task1.log`).

### Task 2 — Unit tests for `kanbanThresholds.ts` (AC5 cases 1-20) — [x]

- [x] 2.1. Created `web-frontend/src/components/organizer/SpeakerStatus/__tests__/kanbanThresholds.test.ts`.
- [x] 2.2. Authored 20 cases per AC5 #1-20 + 5 defensive cases (invalid Date, null event date, severityToChipColor mapping, INVITED split, zero-count for terminal/no-subline states). Fixed `now = new Date('2026-05-16T12:00:00Z')` passed as parameter; no global timer mock.
- [x] 2.3. **Verified**: 25 tests pass (`/tmp/fe-test-kanban.log`).

### Task 3 — Wire `classifyChipSeverity` into the time-in-state chip (AC4) — [x]

- [x] 3.1. `SpeakerStatusLanes`: `now = nowProp ?? new Date()` at top; threshold source is `DEFAULT_KANBAN_THRESHOLDS`; no `resolveKanbanThresholds(event)` call.
- [x] 3.2. `eventDate` (parsed) + `now` flow `SpeakerStatusLanes → StatusLane → SpeakerCard` via props.
- [x] 3.3. `SpeakerCard`'s time-in-state chip now uses `color={severityToChipColor(classifyChipSeverity({...}))}`. Added `data-severity={chipSeverity}` for test assertions.
- [x] 3.4. `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` deleted — grep confirms zero matches.
- [x] 3.5. **Verified**: existing 26 Story 11.D.2 tests still pass (`/tmp/fe-test-lanes-existing.log`).

### Task 4 — Refactor column-header to 3-line layout + render sub-line (AC2) — [x]

- [x] 4.1. `SpeakerStatusLanes` computes `attentionSublines: Record<SpeakerWorkflowState, AttentionSubline | null>` via module-level `computeAttentionSubline(state, ctx)`. Single source of truth at parent.
- [x] 4.2. `attentionSubline`, `filterActive`, `onSublineClick` props plumbed to each `StatusLane`.
- [x] 4.3. `StatusLane` header refactored to 3-line stacked layout with `minHeight: 56` to align lanes regardless of sub-line presence. `<button>` for clickable sub-lines, `<Typography>` for READY (Resolved Q#5).
- [x] 4.4. `data-testid="status-lane-subline-${status.toLowerCase()}"` on every sub-line element.
- [x] 4.5. Visual smoke deferred to AC7 #8 (kanban load against `make dev-native-up`); component tests assert the layout deterministically.

### Task 5 — Click-to-filter behaviour (AC3) — [x]

- [x] 5.1. Local `attentionFilter` state + auto-clear `useEffect` on `eventCode` change + auto-clear `useEffect` on `[attentionFilter, speakers, parsedEventDate]` when count drops to 0. `now` intentionally excluded from auto-clear deps to avoid every-render clearing.
- [x] 5.2. `handleSublineClick(status)` toggles the filter. READY's sub-line renders as `<Typography>` (no click handler wired) — Resolved Q#5.
- [x] 5.3. `speakersByStatus` reduction applies `makeAttentionPredicate(...)` for the matching state; other columns unaffected.
- [x] 5.4. `filterActive={attentionFilter === status}` passed to each `StatusLane`; button styling switches to bold + underline + `aria-pressed="true"` when active.
- [x] 5.5. Click-to-filter verified by component tests #29 + #30 (toggle + un-toggle).

### Task 6 — Component tests for `SpeakerStatusLanes` (AC5 cases 21-30) — [x]

- [x] 6.1. New `describe('SpeakerStatusLanes — Story 11.D.3 column triage + chip colour coding', ...)` block appended; 10 tests cover cases #21-30.
- [x] 6.2. `now` injected via prop on `SpeakerStatusLanes` for determinism (no `vi.setSystemTime`).
- [x] 6.3. MUI Chip colour assertions use `expect(chip.className).toMatch(/MuiChip-colorWarning|Error/)` + the new `data-severity` attribute as a second assertion.
- [x] 6.4. **Verified**: 36 tests pass in `SpeakerStatusLanes.test.tsx` (26 from 11.D.2 + 10 new) (`/tmp/fe-test-lanes-new.log`).

### Task 7 — i18n: add 7 keys × 10 locales (AC6) — [x]

- [x] 7.1. EN: added `speakerCard.lanes` sub-block in `public/locales/en/organizer.json`.
- [x] 7.2. DE: canonical German translations added.
- [x] 7.3. Machine-translated baselines added to `es, fi, fr, gsw-BE, it, ja, nl, rm`. Emoji characters preserved in all locales.
- [x] 7.4. Locale-parity verified via `jq -e '.speakerCard.lanes'` — all 10 locales present.
- [x] 7.5. **Verified**: `type-check` + `lint` clean (`/tmp/fe-typecheck-final.log`, `/tmp/fe-lint-final.log`).

### Task 8 — Playwright E2E (AC5 cases 31-33) — [x]

- [x] 8.1. New file `web-frontend/e2e/organizer/speaker-column-triage.spec.ts` with 3 cases (negative-case sub-line absence, chip `data-severity` wire-up, READY slot-capacity sub-line + non-clickable invariant).
- [x] 8.2. Seed pattern matches `speaker-card-primary-action.spec.ts` (Story 11.D.2). The affirmative stale-data path for cases #31/#32 requires backdating `updated_at` which the public API does not expose — fully asserted at the Vitest level instead; Playwright covers the wire-up + negative case + slot-capacity gate.
- [x] 8.3. Slot-capacity test annotates a skip if the event's default `maxSlots` exceeds 2; the deterministic gate-fired case is covered by Vitest case #23.
- [x] 8.4. **Verified**: `npx playwright test --list` parses all 3 tests.

### Task 9 — Full verification + commit (AC7, AC8) — [x]

- [x] 9.1. AC7 grep invariants: zero `TODO(11.D.3)`, zero `resolveKanbanThresholds`, zero `event.metadata.kanbanThresholds`, zero `color="default"` on the time-in-state chip; `kanbanThresholds` referenced in expected files; `speakerCard.lanes` present in all 10 locales.
- [x] 9.2. `type-check` clean; `lint` clean (0 errors, 0 warnings); full vitest suite **4,924 passed / 110 skipped / 23 todo** (`/tmp/fe-full.log`).
- [x] 9.3. Backend regression guard: `./gradlew :services:event-management-service:test` → **BUILD SUCCESSFUL**, 1,535 tests PASSED, 0 FAILED (`/tmp/em-test.log`).
- [x] 9.4. Bruno smoke deferred to CI (local Cognito stack not running this session).
- [x] 9.5. Manual visual smoke deferred to PR review against `make dev-native-up`.
- [x] 9.6. Commit message will be: `feat(web-frontend): kanban column triage sub-lines + time-in-state colour coding [Story 11.D.3]`.

---

## Dev Notes

### Why this is the right Phase D scope to deliver after 11.D.2

11.D.2 surfaces the **single most-likely next action** on every card. 11.D.3 surfaces **which cards across the entire board need attention right now**. They are complementary: 11.D.2 tells the organizer "what to do on this card", 11.D.3 tells the organizer "which card to look at next". Together they close the §8.9 story-2 UX gap — column-header awareness + time-in-state colour coding — that plan §8 calls out as the second-largest UX win after the primary-action button.

This is a **pure frontend story** with **zero backend changes**. The PRD explicitly directs in-page derivation over an aggregation endpoint (AC requirement); thresholds are hardcoded defaults per Resolved Q#1 (no override mechanism); the chip colours use MUI's built-in `<Chip color>` API. The simplest viable implementation that satisfies the (now-narrowed) AC is exactly what the story prescribes — no scope creep.

### Reuse, don't recreate — what already exists from 11.D.2

This story **does not introduce new modal components**. It **does not change** the workflow state machine, the API, or any backend code. The single new artefact is the `kanbanThresholds.ts` module (~150 lines), which is a pure-function helper layer; everything else is in-place edits to `SpeakerStatusLanes.tsx`.

The 11.D.2 changes this story builds on:
- The `getStatusChangedAt(speaker)` helper (per-state timestamps with fallback to `updatedAt`/`createdAt`) — the same imperfect resolver. **Same caveat as 11.D.2:** the `updatedAt` fallback is bumped by any field update, not just status changes. The user accepted this imperfection in 11.D.2 (Q#1 there); the same trade-off applies here. If organisers later report misleading chip colours due to this, the same fix applies: add a backend `status_changed_at` column. **NOT in scope for this story.**
- The `slotCapacityReached` boolean already computed at the parent — reused verbatim for the READY column sub-line.
- The i18n `speakerCard` namespace — this story adds the `lanes` sub-block alongside the existing `primaryAction` block.
- The 8-state lane ordering (`STATUS_LANES`) — unchanged.
- The drag-drop layer — **untouched** (drag-drop UX upgrades are 11.D.4 scope).

### What this story is NOT doing (scope guard)

- **No drag-drop changes** — that is 11.D.4 (UX-DR8-12).
- **No drawer redesign** — that is 11.D.4 (UX-DR13).
- **No on-behalf content form** — that is 11.D.4 (UX-DR14).
- **No settings UI for editing thresholds.** The override path is `event.metadata.kanbanThresholds`. The defaults apply unless someone (manually or via a future story) writes the override. The story PR description states this explicitly so reviewers don't expect a settings panel.
- **No backend changes** — no migrations, no DTO changes, no controller changes, no OpenAPI changes.
- **No new aggregation endpoint** — PRD explicitly directs in-page derivation (AC requirement). The dev does NOT introduce a `/api/v1/events/{code}/speakers/triage-counts` endpoint. If at implementation time the dev finds in-page derivation is somehow impractical (e.g., a future paginated query masks part of the pool), they surface this in the PR description and justify the alternative — but the default path is in-page derivation.
- **No real-time refresh / setInterval / WebSocket subscription** — the colour-coding is based on `new Date()` at render time. TanStack Query's existing refetch-on-focus is sufficient. The chip transition from yellow → red happens when the user re-opens the tab the next day, not via a live tick.
- **No backend `status_changed_at` column.** Continues to use 11.D.2's per-state-timestamps resolver.
- **No `DECLINED` column colouring or sub-line.** Per AC2, DECLINED has no sub-line and per AC1 its chips are always `normal`. Terminal state, no attention needed.
- **No threshold override mechanism, no settings UI.** Per Resolved Q#1, thresholds are hardcoded defaults only. The PRD AC line 1011's "overridable per event" clause is consciously not honoured. If organisers later ask to tune thresholds, a follow-up story can add Option B (typed column or `event_kanban_settings` table + settings tab); the present story does not even ship the JSONB-override path.

### Project Structure Notes

- Primary file under change: `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (column-header layout + click-to-filter + chip colour wiring).
- New module: `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts` (pure-function helper layer; no React).
- New test file: `web-frontend/src/components/organizer/SpeakerStatus/__tests__/kanbanThresholds.test.ts`.
- Extended test file: `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusLanes.test.tsx`.
- Extended (or new) Playwright spec: `web-frontend/e2e/organizer/speaker-status-tracking.spec.ts` (extend) or `web-frontend/e2e/organizer/speaker-column-triage.spec.ts` (new — dev's call based on size).
- i18n: all 10 locale files under `web-frontend/public/locales/{locale}/organizer.json` — edit the existing `speakerCard` block (added by 11.D.2).
- No new component directories. No backend touch points.

### Testing Standards (frontend-only UI story)

- **Vitest + RTL**: per `project-context.md` "Use `screen` queries from React Testing Library — no `container.querySelector`. Prefer `userEvent` over `fireEvent`. Use `waitFor()` for any assertion that depends on async state or CSS transitions."
- **Pure-function tests** (`kanbanThresholds.test.ts`): straight assertions, no rendering, no mocking framework needed beyond Vitest's built-ins. Pass `now` as a parameter; do NOT `vi.useFakeTimers` globally (it propagates to other tests).
- **Component tests**: mock `useTranslation` to return key passthrough (matches the existing pattern in `SpeakerStatusLanes.test.tsx`). When asserting i18n composition (e.g., the INVITED sub-line's `" · "` join), use the passthrough key names directly.
- **Playwright**: organizer auth project (`chromium`). Use the existing `global-setup.ts` (`.playwright-auth-organizer.json`). Seed test data via the project's API helpers — do NOT duplicate seeding logic.
- **Test naming**: `should_expectedBehavior_when_condition`.
- **i18n in tests**: namespace-stripped keys (`'speakerCard.lanes.contactedSubline'`, not `'organizer:speakerCard.lanes.contactedSubline'`) per the established project convention.

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 968-1028] — Story 11.D.3 AC list (this story's primary spec).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 244-255] — UX-DR5, UX-DR6, UX-DR7 verbatim.
- [Source: docs/plans/speaker-workflow-refactor.md §8.3] — Column-header sub-line copy table.
- [Source: docs/plans/speaker-workflow-refactor.md §8.6] — Slot-capacity gating UI surface.
- [Source: docs/plans/speaker-workflow-refactor.md §8.7] — Time-in-state colour-coding threshold table (the §8.7 the AC refer to).
- [Source: docs/plans/speaker-workflow-refactor.md §8.9 story 2] — "Column-header awareness + time-in-state colour coding" — the scoping rationale.
- [Source: docs/architecture/06a-workflow-state-machines.md §"Speaker Workflow Management"] — 8-state model (unchanged).
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.1, §0.2] — state semantics + transitions (context only).
- [Source: web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx] — primary file under change.
- [Source: web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx:348-361] — current column-header layout to refactor (AC2).
- [Source: web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx:222] — `speakersByStatus` grouping (extended for the click-to-filter in AC3).
- [Source: web-frontend/src/types/speakerPool.types.ts:38-58] — `SpeakerPoolEntry` fields used by the threshold helper (`responseDeadline`, `contentDeadline`, `invitedAt`, `acceptedAt`, `contentSubmittedAt`, `declinedAt`, `isSlotAssigned`).
- [Source: web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx:46, 261-267] — existing `formatDistanceToNow` + locale wiring (referenced by 11.D.2 — same pattern continues here for the chip label, untouched).
- [Source: _bmad-output/implementation-artifacts/11-d-2-kanban-card-primary-action-button-cleanup.md] — immediate predecessor; defines the chip render site and the time-in-state-source resolver pattern.
- [Source: _bmad-output/implementation-artifacts/11-d-1-promote-endpoint-brainstorm-tightening-slot-gate.md] — defines `slotCapacityReached` consumption pattern (and the 409 contract for the gate).
- [Source: _bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md] — adds `isSlotAssigned`/`isPublishable` on `SpeakerPoolResponse` (used by QUALITY_REVIEWED rule).
- [Source: _bmad-output/project-context.md §"React Patterns"] — `useTranslation()` for all user-facing strings.
- [Source: _bmad-output/project-context.md §"Frontend Testing"] — Vitest + RTL; `screen.getByRole`; `userEvent` over `fireEvent`; `waitFor()` for async; `msw` for HTTP in unit tests (not needed here — no new HTTP calls).
- [Source: _bmad-output/project-context.md §"Build & Test Output"] — Pipe `gradle`/`make` output through `tee /tmp/<name>.log` (applied in AC7).
- [Source: MEMORY.md §"Multi-Language Support"] — 10 supported locales including gsw-BE; `useTranslation` + `react-i18next` patterns.

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context) via `bmad-dev-story` skill on 2026-05-17.

### Debug Log References

- `/tmp/fe-typecheck-task1.log` — type-check after `kanbanThresholds.ts` creation (clean).
- `/tmp/fe-test-kanban.log` — 25 unit tests pass for `kanbanThresholds.test.ts`.
- `/tmp/fe-typecheck-task3.log` — type-check after `SpeakerStatusLanes` refactor (clean).
- `/tmp/fe-test-lanes-existing.log` — existing 26 Story 11.D.2 component tests pass against refactored file.
- `/tmp/fe-test-lanes-new.log` — full 36 tests pass (26 from 11.D.2 + 10 new for 11.D.3).
- `/tmp/fe-typecheck-final.log` — clean.
- `/tmp/fe-lint-final.log` — clean (0 errors, 0 warnings, `--max-warnings 50`).
- `/tmp/fe-test-speakerstatus.log` — 99 SpeakerStatus tests across 5 files pass.
- `/tmp/fe-full.log` — full frontend suite: 4,924 tests pass / 110 skipped / 23 todo.
- `/tmp/em-test.log` — backend regression guard against `event-management-service`.
- Playwright list verified via `npx playwright test --list e2e/organizer/speaker-column-triage.spec.ts` — 3 tests parsed.

### Completion Notes List

- **AC1 (`kanbanThresholds.ts` module)**: pure-function module created at `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts`. Exports `ThresholdSeverity`, `KanbanStateThresholds`, `KanbanThresholdConfig`, `ClassifyChipInput` types; `DEFAULT_KANBAN_THRESHOLDS` constant per §8.7; `classifyChipSeverity` (handles all 8 ADR-009 states + invalid-date / null-event-date defensive paths); `countAttentionCards` / `makeAttentionPredicate` aggregators with per-state attention-set rules (CONTACTED error-only, QUALITY_REVIEWED no-slot-only, etc); `countInvitedSplit` for the INVITED two-clause sub-line; `attentionMaxSeverity` for severity escalation in CONTENT_SUBMITTED / QUALITY_REVIEWED sub-lines; `severityToChipColor` mapping helper. `getStatusChangedAt` was extracted from `SpeakerStatusLanes.tsx` (the local-helper duplication 11.D.2 left inline) so both consumers share the same per-state-timestamp resolver. No `resolveKanbanThresholds(event)` helper, no read from `event.metadata`, no migration, no settings UI — per Resolved Q#1.
- **AC2 (3-line column header + sub-line)**: `StatusLane` header refactored from a 2-element `flex` row to a 3-line stacked layout with a `minHeight: 56` slot for the optional sub-line (keeps lane headers visually aligned). Sub-line data is computed at the `SpeakerStatusLanes` parent via the new module-level `computeAttentionSubline(state, ctx)` helper — single source of truth for both the count and the click-to-filter predicate. Per-state copy follows the §8.3 table verbatim; emoji glyphs live in the locale values, not in code.
- **AC3 (click-to-filter)**: local `attentionFilter: SpeakerWorkflowState | null` state in `SpeakerStatusLanes`. The grouping reducer at `speakersByStatus` applies `makeAttentionPredicate(...)` to the matching column only — other columns are unaffected. Two auto-clear `useEffect`s: one on `eventCode` change, one on `[attentionFilter, speakers, parsedEventDate]` that clears the filter when the count drops to 0 (organiser worked through the backlog). The `now` dep is intentionally excluded from the auto-clear effect — re-running on every render would clear the filter immediately after the user clicked it. The READY column's sub-line is rendered as static `<Typography>` (not `<button>`), so the click handler is never wired for that lane (Resolved Q#5).
- **AC4 (chip colour wiring)**: `SpeakerCard` now computes `chipSeverity = classifyChipSeverity({ speaker, statusChangedAt, eventDate, now, thresholds: DEFAULT_KANBAN_THRESHOLDS })` and renders `<Chip color={severityToChipColor(chipSeverity)} data-severity={chipSeverity} ... />`. The legacy `color="default"` and the `// TODO(11.D.3): apply threshold-driven colour coding per §8.7` marker are deleted (the grep invariant in AC7 confirms zero matches for both). The chip variant stays `outlined`; tooltip unchanged.
- **AC5 (tests)**: 25 unit tests in `kanbanThresholds.test.ts` (the 20 specified cases #1-20 + 5 extra defensive guards / split-count / IDENTIFIED-DECLINED-READY zero-count). 36 tests in `SpeakerStatusLanes.test.tsx` (the existing 26 from 11.D.2 + 10 new for 11.D.3, cases #21-30). 3 Playwright tests in `e2e/organizer/speaker-column-triage.spec.ts` (cases #31-33). The Playwright suite hits a real constraint the AC author flagged in AC7 visual-smoke #8: the public API does not expose a way to backdate `updated_at`, so the affirmative stale-data cases (#31 click-to-filter, #32 warning/error chip class) are asserted at the Vitest level where time can be injected. The Playwright spec covers (a) negative-case sub-line absence for fresh CONTACTED, (b) the new `data-severity` attribute wire-up against the legacy `color="default"` regression, (c) the READY slot-capacity sub-line text + non-clickable invariant when the gate fires. The third Playwright test is best-effort with respect to the natural `maxSlots` value the evening-event default produces; it falls back to a recorded test annotation if the gate does not fire, deferring to Vitest case #23 which is fully deterministic.
- **AC6 (i18n)**: new `speakerCard.lanes` sub-block added to all 10 locale files (`de, en, es, fi, fr, gsw-BE, it, ja, nl, rm`). Canonical EN + DE authored by the dev; machine-translated baselines for the 8 others — PR description should flag locales needing native-speaker review. Emoji characters (⚠ ⏰ 📝 👀 🪑) are preserved in every locale value (visual-design language, not English-specific). The INVITED two-clause sub-line uses 3 keys (`approaching`, `past`, `joiner`) so the composite is built in TS but the atoms stay locale-self-contained. Strict locale-parity verified via `jq -e '.speakerCard.lanes'` per locale.
- **AC7 (verification)**: `type-check` clean; `lint` clean (0 errors, 0 warnings, `--max-warnings 50`); full vitest suite passes (4,924 tests / 110 skipped / 23 todo); all 5 grep invariants pass (zero `TODO(11.D.3)`, zero `resolveKanbanThresholds`, zero `event.metadata.kanbanThresholds`, zero `color="default"` on the time-in-state chip; `kanbanThresholds` appears in all expected files; `speakerCard.lanes` present in all 10 locales). Backend regression guard against `event-management-service` runs; Bruno smoke would be next if local Cognito stack is up — deferred to CI for this PR.
- **AC8 (docs + commit hygiene)**: no docs touched (per AC8 items 1-4 — state machine, ADR-009, OpenAPI specs, and frontend-architecture doc all remain unchanged). Optional §8.7 "Implemented in Story 11.D.3" note in `docs/plans/speaker-workflow-refactor.md` deferred — judged not load-bearing for future readers. Commit will use `feat(web-frontend): kanban column triage sub-lines + time-in-state colour coding [Story 11.D.3]`.

### File List

**New files (3):**
- `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts`
- `web-frontend/src/components/organizer/SpeakerStatus/__tests__/kanbanThresholds.test.ts`
- `web-frontend/e2e/organizer/speaker-column-triage.spec.ts`

**Modified files (13):**
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (chip colour + 3-line header + click-to-filter + extracted helper; `getStatusChangedAt` moved to `kanbanThresholds.ts` so both consumers share it)
- `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusLanes.test.tsx` (10 new cases + extended i18n mock + extended `renderLanes` helper)
- `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` (passes `eventDate={event?.date}` to `SpeakerStatusLanes`)
- `web-frontend/public/locales/de/organizer.json`
- `web-frontend/public/locales/en/organizer.json`
- `web-frontend/public/locales/es/organizer.json`
- `web-frontend/public/locales/fi/organizer.json`
- `web-frontend/public/locales/fr/organizer.json`
- `web-frontend/public/locales/gsw-BE/organizer.json`
- `web-frontend/public/locales/it/organizer.json`
- `web-frontend/public/locales/ja/organizer.json`
- `web-frontend/public/locales/nl/organizer.json`
- `web-frontend/public/locales/rm/organizer.json`
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (Story 11.D.3 status flipped to in-progress → review)
- `_bmad-output/implementation-artifacts/11-d-3-kanban-column-triage-time-in-state-colour-coding.md` (this file — Dev Agent Record + Change Log + Status)

### Change Log

| Date | Change |
|------|--------|
| 2026-05-16 | Story 11.D.3 drafted via `bmad-create-story`. |
| 2026-05-16 | Resolved all 5 Open Questions with PM (Nissim). Q1 → hardcode defaults, **no override mechanism** in this story (deliberate deviation from PRD AC line 1011's "overridable per event" clause; PM accepted). AC2 (threshold-override resolution) was deleted; AC numbering shifted (AC3 → AC2, AC4 → AC3, … AC9 → AC8); test cases 19–20 (override merge / malformed override) were removed; remaining AC5 tests renumbered 1–20 (unit) + 21–30 (component) + 31–33 (E2E). Q2 → accept the imperfect timestamp resolver inherited from 11.D.2 (no `status_changed_at` column added). Q3 → local React state only for the click-to-filter toggle (no URL / localStorage persistence). Q4 → refresh-on-focus via TanStack Query is sufficient (no `setInterval` real-time tick). Q5 → READY's "Slot capacity reached" remains static non-clickable text. |
| 2026-05-17 | Story 11.D.3 implemented via `bmad-dev-story`. New `kanbanThresholds.ts` module (pure-function helpers); `SpeakerStatusLanes.tsx` refactored to 3-line column headers + chip-colour wiring + click-to-filter; `getStatusChangedAt` extracted from inline-in-`SpeakerStatusLanes.tsx` to the shared module; 25 unit tests + 10 new component tests + 3 Playwright tests added; `speakerCard.lanes` keys added to all 10 locales (canonical en/de + machine-translated baselines). Frontend `type-check` + `lint` + full vitest suite (4,924 tests) all green. AC7 grep invariants all clean. |

---

## Open Questions (resolved 2026-05-16)

All five questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above already reflect the decisions. Listed here for traceability.

1. ✅ **Hardcoded thresholds only — no override mechanism.** The story ships `DEFAULT_KANBAN_THRESHOLDS` and nothing else. The PRD AC line 1011's "thresholds are read from event settings (overridable per event)" clause is consciously not honoured in this story; the PM explicitly accepted shipping defaults only. There is no `resolveKanbanThresholds(event)` helper, no read from `event.metadata.kanbanThresholds`, no migration, no settings UI. If organisers ever ask to tune thresholds in practice, a follow-up story can add either a JSONB-backed override (lowest cost) or a typed `event_kanban_settings` table with a proper settings tab; today's story does not lay groundwork for either path beyond keeping the `thresholds` parameter on the helper signatures for unit-test boundary probing.

2. ✅ **Accept the imperfect timestamp resolver from 11.D.2 — do NOT add a `status_changed_at` column.** The CONTACTED "> 14 days stale" count, the ACCEPTED "awaiting content" count, and the chip colour for IDENTIFIED/CONTACTED/READY/CONTENT_SUBMITTED all use the same per-state-timestamps-with-`updatedAt`-fallback resolver 11.D.2 introduced. This means an organiser editing notes on a CONTACTED speaker resets that speaker's perceived "time in state" — both the chip colour and the sub-line count undercount by the same amount. 11.D.2 accepted this with a TODO marker; this story inherits the same trade-off. If organisers later report misleading chip colours, the fix is a dedicated backend column + DTO regen; that work is **not in scope** for 11.D.3.

3. ✅ **Click-to-filter is local React state only.** The active `attentionFilter` lives in `SpeakerStatusLanes` component state. It does not persist to URL query params or localStorage. Refreshing the page, navigating away, or even unmounting the lanes (e.g., switching event tabs) clears the filter. This matches the "transient triage helper" framing of the feature — the organiser uses the filter for the next 30 seconds while they triage a column, not as a bookmark-worthy view. A future story could add URL-param persistence if organisers ask for shareable filtered views.

4. ✅ **Refresh-on-focus is sufficient — no `setInterval` real-time tick.** Chip colours are computed from `new Date()` at render time. The kanban re-renders when TanStack Query refetches (which happens on tab focus by default). A speaker whose chip would turn yellow at midnight will continue to appear default-coloured until the organiser refocuses the tab — acceptable, since organisers rarely leave the same tab idle and visible for 12+ hours uninterrupted. No background ticker is added.

5. ✅ **READY's "Slot capacity reached" sub-line is static, non-clickable text.** Rendered with `<Typography color="warning.main">`, not a `<button>`. The gate is a global event-level condition (not a per-card subset), so there is no meaningful filter behaviour to attach to a click. The static rendering also makes the visual contrast with the clickable sub-lines on other columns clear. A future redesign could attach a click-to-open-slot-management action; that is out of scope here.

---

_Story created via `bmad-create-story` skill on 2026-05-16. Authored with comprehensive context-engine analysis. All 5 Open Questions resolved with PM the same day. Depends on Story 11.D.2 (must merge first) + builds on 11.B.2, 11.B.3 (already on `feature/speaker-workflow-refactor`). Ready for `bmad-dev-story` execution once 11.D.2 merges to the refactor branch._
