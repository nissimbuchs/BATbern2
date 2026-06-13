---
title: 'Epic 14 Phase B — Task-driven Cockpit (lifecycle spine · attention cards · metric tiles)'
type: 'feature'
created: '2026-06-13'
status: 'done'
baseline_commit: '4ff85f96'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/docs/ux/event-detail-redesign-spec.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-14-a-event-detail-shell.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Phase A built the 8-tab lifecycle-aware shell, but the Cockpit tab still renders the
interim `EventOverviewTab` (a flat details/metrics dump). A returning organizer (≈3 events/year, no
muscle memory) still has to re-derive "what's due now, what's next?" by reading everything.

**Approach:** Replace the Cockpit's interim content with the task-driven landing from the prototype:
three stacked regions — an **8-step lifecycle spine**, a **"Needs your attention"** list (real
backend tasks **blended** with data-derived virtual cards and event-day virtual cards, sorted
overdue→due-soon→upcoming, each deep-linking to the right tab/sub-view, **disappearing the moment
its work is done**), and **4 clickable metric tiles**. Frontend-only; recompose existing data hooks
(`useEvent`, `useEventTasks`/`useMyTasks`, `taskService`, `CustomTaskModal`) — no backend change.

## Boundaries & Constraints

**Always:**
- **8-state model only** (AR1) — no `AGENDA_FINALIZED`. Spine = 8 steps; the "finalize" emphasis
  (final newsletter / catering / print agenda) lives **within `AGENDA_PUBLISHED`** as due-date tasks.
- **Frontend-only / dual-serve (NFR9/AR9):** diff touches only `web-frontend/`. No new endpoints,
  fields, or migrations. Old `www` frontend keeps working against the same prod backend.
- **No silent consequential actions (NFR1):** a card's deep-link/action only **navigates**; it never
  sends mail or provisions accounts. Any consequential action is reached via the destination tab's
  own confirm/modal. Event-day cards navigate to the existing live-control/presentation surfaces.
- **Every card has a resolved completion detector** (AR3/§12.1): task-backed (`status==='completed'`),
  data-derived (named predicate), workflow-action (field/state change), or event-day (no done-state).
  **No card may be left "no signal yet."**
- i18n ×10 (NFR5), WCAG 2.1 AA keyboard+SR for spine/cards/tiles (NFR7), service-layer + generated
  types + `@/` + `useTranslation()` (NFR8).
- Reuse: `getWorkflowProgress`/`getWorkflowStepNumber`/`getWorkflowStateI18nKey` + the Phase-A
  `WORKFLOW_RELEVANCE.cockpitEmphasis`; `CustomTaskModal` for +Add task; the `isOverdue`/due logic
  pattern from `tabBadges.ts`; deep-links use EventPage's existing `?tab=`/`?view=` query-param nav.

**Ask First:**
- If any prototype card cannot be resolved to one of the four completion detectors using existing
  data (i.e. a genuinely new backend field would be required) — HALT, do not invent a backend change.
- If a "fill the pool" / "review submissions" predicate needs a session/event field that does not
  exist on the generated types — HALT and confirm the field rather than guessing.

**Never:**
- No new route, no page header buttons for event-day controls (they are attention cards).
- No event-identity block in the Cockpit (identity lives in Details; orientation = header + spine).
- Do not delete `EventOverviewTab` yet (Phase F retires the interim Overview; keep it importable).
- No backend/API/migration changes. No `WorkflowProgressBar` replacement for the spine — it is a
  %-bar, not a stepper; build a new spine component reusing the helpers.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Spine render | `workflowState` (1 of 8) | Horizontal 8-step stepper; completed=green check, current highlighted "Step N of 8" | unknown state → step 1, no crash |
| Attention list | open tasks + derived cards | One card per OPEN item, sorted overdue→due-soon→upcoming; each has colour strip, name, due chip, assignee avatar, deep-link button | — |
| Done card | task `status==='completed'` OR predicate satisfied | Card absent from list | — |
| Empty list | no open cards for state | Friendly "You're all caught up" state (not blank) | — |
| Task fetch error | tasks query fails | Inline error + retry; spine & tiles still render | non-blocking |
| +Add task success | submit `CustomTaskModal` | New task created via `taskService.createAdHocTask`, appears in list | — |
| +Add task failure | create call rejects | Inline error, modal stays open with values, no phantom card | no silent fail |
| Event-day cards | `workflowState >= AGENDA_PUBLISHED` | "🔴 Start the presentation" + "📡 Open Live Control" pinned at top, urgent, due "Now · live"; navigate to `/present/:code` and live-control route | — |
| Event-day pre-gate | state `< AGENDA_PUBLISHED` | Event-day cards absent | — |
| Metric tile click | click any of 4 tiles | Deep-link: Registrations→registrations; Speakers→speakers·pool; Materials→speakers·agenda; Agenda→speakers·slots | — |
| Tile data missing | metric field undefined | Tile shows em-dash / 0, still clickable | no crash |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` -- mount new `CockpitTab` in place of `EventOverviewTab`; expose a `navigateTo(tab, view?)` handler built on existing `setSearchParams(?tab=&?view=)` and pass to Cockpit for deep-links.
- `web-frontend/src/components/organizer/EventPage/cockpit/CockpitTab.tsx` -- NEW landing: 3 stacked regions (spine / attention / tiles); no identity block.
- `.../cockpit/LifecycleSpine.tsx` -- NEW 8-step stepper; reuses `getWorkflowStepNumber`/`getWorkflowStateI18nKey`; collapses to "Step N/8" via `compact` (mobile is Phase G, leave the prop seam).
- `.../cockpit/AttentionList.tsx` + `AttentionCard.tsx` -- NEW; render merged/sorted cards; colour strip, due chip, avatar, deep-link button; urgent event-day variant; empty + error states; "＋ Add task" → `CustomTaskModal`.
- `.../cockpit/MetricTiles.tsx` -- NEW 4 clickable tiles from `useEvent(['metrics','registrations','workflow'])` fields.
- `.../cockpit/cockpitCards.ts` -- NEW **completion-signal registry** (AR3/§12.1): the full card-by-card table (see Design Notes) — virtual-card defs (`{id,label,target,severity,statesShown,done(ctx)}`) + a `resolveTaskCard(task)` mapping backend `EventTaskResponse`→`{target, severity, done}`.
- `.../cockpit/useCockpitCards.ts` -- NEW hook: merge `useEventTasks`/`useMyTasks` + derived + event-day cards, drop done cards, sort, return list + loading/error.
- `web-frontend/src/utils/workflow/workflowState.ts` -- reuse helpers + `cockpitEmphasis`; no signature changes.
- `web-frontend/src/components/organizer/Tasks/CustomTaskModal.tsx` -- reuse as-is for +Add task.
- `web-frontend/src/hooks/useEventTasks.ts`, `src/services/taskService.ts` -- consumed (exist).
- `web-frontend/public/locales/*/events.json` -- new `eventPage.cockpit.*` keys (regions, due chips, emphasis, card labels, event-day, empty/error) in **all 10 locales**.
- Tests under `.../cockpit/__tests__/` + update `EventPage.test.tsx` (Cockpit now renders spine/attention/tiles, not Overview).

## Tasks & Acceptance

**Execution:**
- [x] `cockpit/cockpitCards.ts` -- author the **complete §12.1 card-by-card table** covering EVERY card in the prototype `STATES` map (CREATED→ARCHIVED) as typed registry entries + `resolveTaskCard`; unit-test each predicate's open AND done state. *(14.B.3, AR3, AR4)*
- [x] `cockpit/LifecycleSpine.tsx` -- 8-step stepper reusing helpers. *(14.B.1, FR8)*
- [x] `cockpit/useCockpitCards.ts` + `AttentionList.tsx` + `AttentionCard.tsx` -- merge/sort/hide-done; deep-link buttons; empty + error + retry; ＋Add task via `CustomTaskModal`. *(14.B.2, FR9–FR11, FR10)*
- [ ] event-day virtual cards in registry + list -- gated `>= AGENDA_PUBLISHED`, pinned top, urgent, navigate to present/live-control. *(14.B.4, FR12)*
- [x] `cockpit/MetricTiles.tsx` -- 4 clickable deep-link tiles. *(14.B.5, FR13)*
- [x] `cockpit/CockpitTab.tsx` + `EventPage.tsx` wiring (`navigateTo`, mount Cockpit). *(14.B.1, FR7)*
- [x] `events.json` ×10 locales -- all new strings (EN+DE first-class).
- [ ] tests + update `EventPage.test.tsx`.

**Acceptance Criteria:**
- Given the Cockpit tab, when it renders, then it shows exactly three stacked regions (spine → attention → tiles) and **no event-identity block**. *(FR7)*
- Given each of the 8 states, when the spine renders, then it is an 8-step stepper with completed=check, current="Step N of 8". *(FR8)*
- Given open tasks + derived cards, when the attention list renders, then cards are sorted overdue→due-soon→upcoming with colour strip, due chip, assignee avatar, and a deep-link button that switches tab/sub-view with no route change. *(FR9)*
- Given any card whose completion detector fires, when the Cockpit re-renders, then the card is absent. Every prototype card resolves to a typed detector (registry unit-tested both states). *(FR10, AR3)*
- Given the venue-booking card, then it is task-backed by the seeded `Venue Booking` task (trigger `TOPIC_SELECTION`, −90d) and hidden when its `status==='completed'`; annual framing deferred. *(AR4)*
- Given `workflowState >= AGENDA_PUBLISHED`, then two urgent event-day cards pin to the top; before it they are absent; they never carry a done-state. *(FR12)*
- Given the 4 tiles, when clicked, then each deep-links to its destination using the stable keys (`speakers`+`pool`/`agenda`/`slots`) that survive Phase C's relabel. *(FR13)*
- Cross-cutting per the epic DoD: NFR1 (deep-links navigate only, no side-effects), NFR5 (×10 locales), NFR7 (keyboard+SR for spine/cards/tiles), NFR8 (conventions), NFR9 (diff is `web-frontend/` only).

## Spec Change Log

### 2026-06-13 — Review pass 1 (3 reviewers; no loopback)
Acceptance auditor: all FR7–FR13 / AR3 / AR4 / NFR1,5,7,8,9 **MET**. Blind + edge-case
hunters surfaced 5 implementation patches (no spec deviation, no intent gap):
1. **Agenda metric tile** sourced numerator from `sessions[]` but denominator from
   `totalSessionsCount` (inconsistent) → both now derive from `sessions[]` (slotted ≤ total).
2. **Registrations divergence** — tile used `confirmedCount || currentAttendeeCount` while the
   watch-registrations card used `confirmedCount` → tile now uses `confirmedCount` only.
3. **`outstanding-presentations`** showed spuriously for a session-less event → closes when
   `totalSessionsCount === 0`.
4. **Stale `Date.now()`** frozen in a `useMemo` → attention list computed each render.
5. **Sort** treated undated cards as "due today" → undated cards now sort last within a bucket.
Deferred (not this story): `CustomTaskModal` invalidates the generic `['tasks']` key
(pre-existing; mitigated in the Cockpit by AttentionList) → `deferred-work.md`.

## Design Notes

**The attention list is a blend of 3 card sources** (FR9 says "from taskService", but §12.1 also
defines data-derived + event-day cards):
1. **Backend task cards** — `useEventTasks`/`useMyTasks` rows. `resolveTaskCard(task)` maps
   `triggerState` + `taskName` (pattern, like `tabBadges.ts`'s `COMMS_TASK_PATTERN`) → target tab.
   Completion = `status==='completed'` (task-backed). Due chip from `dueDate` via the `isOverdue`/
   due-soon classifier (extract from `tabBadges.ts`).
2. **Data-derived virtual cards** — computed from `useEvent` metrics/sessions; each carries a named
   `done(ctx)` predicate.
3. **Event-day virtual cards** — gated `>= AGENDA_PUBLISHED`, no done-state, pinned top, urgent.

**Registry table (the §12.1 deliverable — must be exhaustive over the prototype `STATES` cards):**

| Card | Type | Target | Done detector |
|------|------|--------|---------------|
| Add details & pick a topic | workflow-action | details | `topicCode` set |
| Confirm the topic | task-backed/workflow | details | task done / `workflowState` advanced |
| Assign the moderator | task-backed | settings | task `status==='completed'` (one trigger; dedupe across states) |
| Topic/line-up/final newsletter, catering, partner-meeting, slides-online | task-backed | comms | `status==='completed'` |
| Fill the speaker pool | data-derived | speakers·pool | `confirmedSpeakersCount >= minSlots` |
| N sessions need a slot | data-derived | speakers·slots | `sessions.filter(s=>!s.startTime).length === 0` |
| Review speaker submissions | data-derived | speakers·agenda | no session awaiting review (materials submitted, not yet reviewed) |
| Collect outstanding presentations | data-derived | speakers·agenda | `sessionsWithMaterialsCount === totalSessionsCount` |
| Watch registrations / capacity | data-derived | registrations | ongoing — no hide |
| Finalize & publish agenda | workflow-action | publishing | `workflowState` advanced past `SLOT_ASSIGNMENT` |
| Upload photos / curate thank-yous / open Q&A | task-backed | wrapup/settings | `status==='completed'` |
| 🔴 Start presentation / 📡 Live Control | event-day | present / live-control | none (window-only) |
| Book venue (annual, 2-yrs-ahead framing) | — | — | **deferred** (AR4) — not a card this epic |

Confirm `minSlots` and the "awaiting review" session field against `src/types/generated/` during
impl; if absent → HALT (Ask First), do not invent a backend field.

**Deep-link mechanism:** `EventPage` passes `navigateTo(tab, view?)` that calls `setSearchParams`
(`?tab=`, `?view=`) — the same path `handleTabChange` uses today. Sub-view keys `pool|agenda|slots`
map to the current `EventSpeakersTab` views and are preserved by Phase C (per 14.B.2/14.B.5 note).

**Spine:** new component; `WorkflowProgressBar` (a % bar) is NOT the stepper — reuse only the helpers.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage/cockpit src/components/organizer/EventPage/__tests__/EventPage.test.tsx` -- expected: all pass, registry predicates covered both states.
- `cd web-frontend && npm run lint` -- expected: ≤ existing warning budget.
- i18n completeness: every new `eventPage.cockpit.*` key present in all 10 `events.json` files.

**Manual checks:**
- Walk a CREATED→ARCHIVED event (or mocked states): spine step advances; attention cards change per
  state and vanish when their predicate/status flips; event-day cards appear only from
  `AGENDA_PUBLISHED`; all 4 tiles deep-link correctly; diff is `web-frontend/`-only.

## Suggested Review Order

**Completion-signal model (the crux — start here)**

- The §12.1 registry: every virtual card's state-window + `isDone` predicate (AR3/AR4).
  [`cockpitCards.ts:176`](../../web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts#L176)

- Merge + sort: backend tasks (completed filtered) + virtual cards, pinned/severity/due ordering.
  [`cockpitCards.ts:326`](../../web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts#L326)

- Backend tasks carry no target — ordered name→tab rules derive the deep-link (order matters).
  [`cockpitCards.ts:135`](../../web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts#L135)

**Data wiring (real sources, frontend-only)**

- Gathers ctx from useEvent / useEventTasks / useSpeakerPool (awaiting-review) / useEventType (minSlots).
  [`useCockpitCards.ts:48`](../../web-frontend/src/components/organizer/EventPage/cockpit/useCockpitCards.ts#L48)

**Composition & deep-link plumbing**

- The 3-region landing; no identity block (FR7).
  [`CockpitTab.tsx:42`](../../web-frontend/src/components/organizer/EventPage/cockpit/CockpitTab.tsx#L42)

- EventPage mounts Cockpit + the tab/route deep-link handler (navigate-only, NFR1).
  [`EventPage.tsx:204`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L204)

**Presentational**

- 8-step spine via existing helpers (not WorkflowProgressBar).
  [`LifecycleSpine.tsx:60`](../../web-frontend/src/components/organizer/EventPage/cockpit/LifecycleSpine.tsx#L60)

- Attention region: empty/error/retry + ＋Add task (re-invalidates the event-task query on close).
  [`AttentionList.tsx:45`](../../web-frontend/src/components/organizer/EventPage/cockpit/AttentionList.tsx#L45)

- Card: colour strip, due chip, assignee avatar, deep-link button.
  [`AttentionCard.tsx:22`](../../web-frontend/src/components/organizer/EventPage/cockpit/AttentionCard.tsx#L22)

- 4 clickable metric tiles (agenda fraction sourced consistently from sessions[]).
  [`MetricTiles.tsx:61`](../../web-frontend/src/components/organizer/EventPage/cockpit/MetricTiles.tsx#L61)

**Peripherals (tests + i18n)**

- Registry unit tests — every predicate open AND done + §12.1 exhaustiveness.
  [`cockpitCards.test.ts:1`](../../web-frontend/src/components/organizer/EventPage/cockpit/__tests__/cockpitCards.test.ts#L1)

- Component tests + EventPage shell test updated to mount CockpitTab; `eventPage.cockpit.*` added in all 10 locales.
  [`CockpitTab.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/cockpit/__tests__/CockpitTab.test.tsx#L1)
