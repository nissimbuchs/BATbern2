---
title: 'Epic 14 Phase C — Speakers & Agenda (4-phase kanban · workflow-safe drag · agenda table · slots in-tab)'
type: 'feature'
created: '2026-06-14'
status: 'done'
baseline_commit: '149cc5b9'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/docs/ux/event-detail-redesign-spec.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-14-a-event-detail-shell.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-14-b-cockpit.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Speakers experience is scattered across an 8-lane kanban (`view=kanban`), a sessions table (`view=sessions`), and a *separate full-page route* (`/organizer/events/:eventCode/slot-assignment`). Phase A/B's Cockpit already deep-links to three stable sub-view keys `pool | agenda | slots`, but `EventSpeakersTab` only understands `kanban | sessions` — so `agenda` and `slots` deep-links silently fall back to the kanban today (latent bug). The 8-lane board is noisy and slotting bounces the organizer off-page.

**Approach (Epic 14 Phase C, stories 14.C.1–C.5 — frontend-only recomposition):** Relabel the sub-view toggle to the stable `pool|agenda|slots` keys (Pool=kanban, Agenda=sessions table, Slots=in-tab slot assignment); rebuild the kanban from 8 lanes into **4 phase columns** keeping each card's exact 8-state chip, your-move sort, Confirmed-column slot tie-in, and a collapsible Declined strip; keep the existing **workflow-safe drag grammar** (reuse `getPrimaryAction` + `classifyDrop`, one transition, always opens the existing confirm/modal) adapted to cross-column-only semantics; surface the **"N of M need a slot"** summary + jump on the Agenda table; and bring **slot assignment in-tab** as a 2-column layout with a top action bar, **retiring the `/slot-assignment` route** with a redirect and repointing every in-app navigator.

## Boundaries & Constraints

**Always:**
- **Frontend-only, additive-only (NFR9/AR9):** the diff touches only `web-frontend/`. No backend, no API/contract change. Reuse the existing services/hooks/endpoints exactly (`useSlotAssignment`, `slotAssignmentService`, `speakerStatusService`, `useEvent(['sessions'])`, the `SpeakerWorkflowService` allow-list via `classifyDrop`).
- **Workflow-safe drag (NFR1/AR6):** a forward cross-column drag invokes the **same handler as the card's primary-action button** and opens the relevant confirm/modal — never auto-commits, never a silent email/Cognito provisioning. Exactly **one** transition per drag (dropping IDENTIFIED on *Content* must not fire three steps). Reuse the existing `classifyDrop` 5-intent dispatcher; do not write a new transition path.
- **Stable sub-view keys:** the toggle uses `'pool' | 'agenda' | 'slots'` (the keys `cockpitCards.ts:33` already deep-links to). This is a *relabel*, not a new key set — existing Cockpit deep-links keep working.
- **i18n ×10 (NFR5):** every new/changed string via `useTranslation()` in all 10 `events.json` locales under the `eventPage.*` namespace, EN+DE first-class.
- **Conventions (NFR8):** service layer only, generated types from `src/types/generated/`, `config` objects, `@/` alias.
- **A11y (NFR7):** kanban drag, sub-view toggle, slot tray/timeline, declined strip, "needs a slot" links are keyboard-operable + screen-reader-labelled (WCAG 2.1 AA).
- TDD: red→green→refactor; every AC has ≥1 test.

**Ask First:**
- If carrying `?speakerId=` into the in-tab Slots sub-view requires **new behaviour in `DragDropSlotAssignment`** beyond highlighting/scrolling the matching unassigned session (it ignores the param today) — confirm scope before building speaker-focus logic.
- If the 4-phase kanban rebuild cannot reuse `SpeakerCard`/`getPrimaryAction`/`classifyDrop` and would require touching the transition allow-list — HALT (that would breach "recompose, don't rewrite").

**Never:**
- No backend change, no new endpoint, no migration, no removed/renamed field (would break the old `www` frontend on the shared prod backend).
- Never set `speaker_pool.status` via any new path; the only writer is the existing workflow service reached through `classifyDrop`.
- No 8-column→`AGENDA_FINALIZED` regressions; the workflow is 8 states (AR1).
- Do not leave a dead `/slot-assignment` navigation anywhere; do not remove the route without the redirect.
- Out of scope: Phases D–G (deferred), the annual venue forward-planning card, any mobile tap-to-assign (that's Phase G).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Deep-link Pool | Cockpit tile/card `view=pool` | Speakers tab → Pool (kanban) sub-view active | N/A |
| Deep-link Agenda | `view=agenda` | Agenda (sessions table) sub-view — **not** kanban (fixes today's fallback) | N/A |
| Deep-link Slots | `view=slots` | Slots in-tab sub-view active (no route change) | N/A |
| Forward drag cross-column | drag IDENTIFIED card → Inviting column | opens promote confirm/modal; advances exactly one step on confirm | cancel → card snaps back, no transition |
| Forward drag skipping steps | drag IDENTIFIED → Content column | advances exactly ONE legal step (opens the next-step modal), never three | illegal multi-step → snap back + toast |
| Backward drag | QUALITY_REVIEWED → Content | allowed (single legal back-transition) via existing dialog | any other backward drag → snap back |
| Confirmed w/o slot | QUALITY_REVIEWED, `startTime` null | card shows "⚠ Needs a slot →" linking to Slots sub-view (in-tab) | N/A |
| Confirmed w/ slot | QUALITY_REVIEWED, `startTime` set | card shows ✓ + time | N/A |
| Declined speakers | n DECLINED | collapsible bottom strip "▸ Declined (n)", collapsed by default | empty → strip hidden |
| Agenda needs-slot summary | sessions, k with `startTime==null` | "k of M need a slot" + "🧩 Arrange slots →" switches to Slots | k==0 → "all slotted" |
| Old route bookmark | open `/slot-assignment[?speakerId=]` | redirect → `?tab=speakers&view=slots` (carry speaker context if present) | missing eventCode → events list |
| Validation jump | ValidationDashboard "Assign timings" | switches to Speakers · Slots in-tab (no route) | N/A |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` — **14.C.1 core.** `ViewMode='kanban'|'sessions'` (l.56), `currentView` (l.70 default kanban), `handleViewChange` (l.180), summary bar inline (l.342–389), toggle (l.433–455), render switch (l.461–494). `handleAssignSessionSlotForSpeaker` (l.265 → `navigate(.../slot-assignment?speakerId=)`) and `handleManageSlotAssignments` (l.325 → `navigate(.../slot-assignment)`) — repoint to in-tab.
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — `navigateToTab(tab,view)` (l.187) passes `view` verbatim for `tab==='speakers'`; `handleCardNavigate` (l.204). No change needed (keys already plumbed).
- `web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts` — `SpeakersSubView='pool'|'agenda'|'slots'` (l.33) — the canonical key contract (do not change).
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` — **14.C.2/C.3 core.** 8 lanes in two grids (`OUTREACH_LANES` l.142–160, render l.708–763); `<DndContext>` (l.698, @dnd-kit); `handleDragStart` (l.539), `handleDragEnd` (l.561), `StatusLane`/`SpeakerCard` sub-components; DECLINED is a lane today (l.157).
- `web-frontend/src/components/organizer/SpeakerStatus/getPrimaryAction.ts` — per-state primary action (pure fn, l.80–190); the "Assign Session Slot" QUALITY_REVIEWED case (l.155–175). Reuse unchanged.
- `web-frontend/src/components/organizer/SpeakerStatus/speakerTransitions.ts` — `classifyDrop` 5-intent dispatcher (l.94–131). The drag allow-list; reuse for cross-column drags.
- `web-frontend/src/components/organizer/SpeakerStatus/kanbanThresholds.ts` — `classifyChipSeverity`/`makeAttentionPredicate` (severity = your-move proxy; error/warning=your move, normal=waiting).
- `web-frontend/src/components/organizer/EventManagement/SpeakersSessionsTable.tsx` — **14.C.4.** sessions table; `startTime` null = needs slot (sort l.119–133); receives `sessions` prop.
- `web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx` — **14.C.5 core.** 3-column 100vh (`height:{md:'100vh'}` l.371), native HTML5 drag, top "Quick actions" (Generate l.592 / Auto-assign l.607 / Clear l.618). Rework → 2-col + top action bar, drop 100vh. `useSlotAssignment` + `useEvent(['sessions'])` + `useTimetable` unchanged.
- `web-frontend/src/pages/organizer/SlotAssignmentPage.tsx` — page wrapper (reads `eventCode`, ignores `?speakerId=`); back-nav → `?tab=speakers&view=sessions`. To be retired / converted to redirect.
- `web-frontend/src/App.tsx` — route `/organizer/events/:eventCode/slot-assignment` (l.480–490), lazy import (l.123). Replace element with a redirect to `?tab=speakers&view=slots`.
- `web-frontend/src/components/Publishing/ValidationDashboard/ValidationDashboard.tsx` — `handleAssignTimingsClick` (l.90–92 → route). Repoint to in-tab Slots.
- `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDetailDrawer.tsx` — `onAssignSessionSlot` (l.266) threaded from EventSpeakersTab; must invoke in-tab switch, not route nav.
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` — `eventPage.*` namespace; add Phase C keys (sub-view labels, phase-column titles, declined strip, needs-a-slot, slot action-bar).

## Tasks & Acceptance

**Execution:**

_14.C.1 — sub-view toggle + summary bar_
- [x] `EventSpeakersTab.tsx` -- widen `ViewMode` to `'pool'|'agenda'|'slots'`; map `currentView` from `view` param (default `pool`); render Pool=kanban, Agenda=sessions table, Slots=new in-tab slots; relabel the toggle to **Pool · Agenda · Slots**; keep the summary bar (accepted/min, acceptance rate, Add speakers) above the toggle. Preserve sub-view selection on Cockpit deep-link arrival.
- [x] Add `eventPage.*` i18n keys (toggle labels) ×10 locales.

_14.C.2 — 4-phase kanban_
- [x] `SpeakerStatusLanes.tsx` (+ extracted sub-components if cleaner) -- render **4 phase columns** — `Sourcing`(IDENTIFIED·CONTACTED), `Inviting`(READY·INVITED), `Content`(ACCEPTED·CONTENT_SUBMITTED), `Confirmed`(QUALITY_REVIEWED) — each card keeps its exact 8-state chip; within a column, your-move (error/warning severity) cards get an accent left border + sort on top, "waiting on speaker" (normal) below a faint divider; Confirmed column shows "⚠ Needs a slot →" (no `startTime`) linking to Slots, or ✓+time; **DECLINED** becomes a collapsible bottom strip (collapsed by default), not a column; clicking a card opens the unchanged `SpeakerDetailDrawer`.
- [x] Phase-column titles + declined-strip + needs-a-slot i18n keys ×10.

_14.C.3 — workflow-safe drag grammar_
- [x] `SpeakerStatusLanes.tsx` / `speakerTransitions.ts` -- adapt drag to **cross-column-only**: a forward drag across a phase boundary reuses the card's primary-action handler via the existing `classifyDrop` dispatch (opens confirm/modal, one step, never auto-commit); within-pair advances (e.g. Identified→Contacted) happen via the **card button** and re-sort within the column (not a drag); backward drag limited to the single legal `QUALITY_REVIEWED→CONTENT_SUBMITTED` (or Decline/Override) — all else snaps back. No new transition path.

_14.C.4 — Agenda sub-view_
- [x] `EventSpeakersTab.tsx` (Agenda branch) + reuse `SpeakersSessionsTable` -- show the editable session table with a "N of M need a slot" summary (predicate: `sessions.filter(s => !s.startTime).length`) and a "🧩 Arrange slots →" button that switches to the Slots sub-view.
- [x] Summary/jump i18n keys ×10.

_14.C.5 — Slots in-tab + retire route_
- [x] `DragDropSlotAssignment.tsx` -- rework to **2-column** (left tray = unassigned drag source, right = timeline drop targets; structural slots non-droppable; hover preference-match %); hoist Quick actions into a **top action bar** (Generate structure · Auto-assign · Clear all timings + "Total · Assigned · Pending" summary); **remove the `height:{md:'100vh'}` viewport lock** so it renders in-tab. Keep all `useSlotAssignment`/service calls unchanged.
- [x] `EventSpeakersTab.tsx` -- render `DragDropSlotAssignment` as the Slots sub-view; repoint `handleAssignSessionSlotForSpeaker` (l.265) and `handleManageSlotAssignments` (l.325) to **switch `view=slots` in-tab** (no `navigate`), carrying speaker context (best-effort highlight of that speaker's unassigned session) for the QUALITY_REVIEWED jump.
- [x] `App.tsx` -- replace the `/slot-assignment` route element with a redirect to `/organizer/events/:eventCode?tab=speakers&view=slots` (preserve `?speakerId=` → speaker context); remove the now-unused `SlotAssignmentPage` lazy import (or reduce the page to the redirect).
- [x] `ValidationDashboard.tsx` (l.90) + `SpeakerDetailDrawer.tsx` (l.266 slot button) -- invoke the in-tab Slots switch, not route navigation.

_Cross-cutting_
- [x] Unit/integration tests (Vitest) for every AC + the I/O matrix (view-key reconciliation, drag one-step/snap-back, redirect, each repointed call site).

**Acceptance Criteria:**
- The full per-story ACs are defined in `docs/prd/epic-14-event-detail-redesign.md` §"Phase C" (stories 14.C.1–14.C.5, FR14–FR23, AR5/AR6, UX-DR5–UX-DR7) and are authoritative; this spec plans their implementation.
- **DoD (mandatory per epic, every story):** NFR1 (drag opens same confirm, no real comms in tests), NFR5 (i18n ×10), NFR7 (keyboard + SR for new surfaces), NFR8 (conventions), NFR9/AR9 (diff is `web-frontend/`-only; no backend delta).
- Given `view=agenda`/`view=slots` deep-links, when arrived from the Cockpit, then the Agenda/Slots sub-view renders (not the kanban fallback) — closing the Phase B latent mismatch.
- Given the `/slot-assignment` route is retired, when any in-app navigator or external bookmark targets it, then the organizer lands on Speakers · Slots in-tab (speaker context preserved where present) — verified per call site.

## Spec Change Log

- **2026-06-14 — FR20 backward-drag scope clarification (implementation deviation, accepted).** The spec/PRD FR20 lists `QUALITY_REVIEWED → CONTENT_SUBMITTED` as an allowed *backward drag*. The existing `classifyDrop` allow-list (which the spec's "Never change the legal allow-list / recompose-don't-rewrite" constraint forbids touching) has **no** backward transition. Resolution: **all backward drags snap back**; that single legal back-step remains reachable via the **Decline/Override affordance** in the Speaker Detail Drawer (Epic 11) — exactly the FR20 fallback clause ("…or the Decline/Override affordance"). No new transition path was introduced. _Known-bad avoided: adding a bespoke backward-drag transition outside `SpeakerWorkflowService`._
- **2026-06-14 — e2e doc-capture testids updated (not a behaviour change).** The sub-view toggle testids were renamed `kanban-view-toggle`→`pool-view-toggle`, `sessions-view-toggle`→`agenda-view-toggle`. Updated the three Layer-3 doc/screencast references (`SpeakerManagementPage.ts` page object + `complete-event-workflow.spec.ts` + `screencast-event-workflow.spec.ts`). Legacy `?view=kanban`/`?view=sessions` **URLs** still resolve (mapped to pool/agenda) so existing e2e navigations don't dead-end. Deeper reconciliation of Layer-3 kanban assertions to the 4-phase board is a separate e2e task (out of this frontend-unit scope).

## Design Notes

- **View-key reconciliation is the keystone of 14.C.1.** Today `view=sessions` → table, anything else → kanban; the new mapping is `pool→kanban content`, `agenda→sessions table`, `slots→DragDropSlotAssignment`. Keep `pool` as the default so a bare `?tab=speakers` still lands on the board. The deep-link keys in `cockpitCards.ts:33` are frozen — adopt them, don't invent.
- **Two different drag libraries, deliberately untouched at the lib level:** the kanban uses **@dnd-kit** (`SpeakerStatusLanes`), slot assignment uses **native HTML5 DnD** (`DragDropSlotAssignment`). Phase C re-lays-out each but keeps its own drag tech — do not unify them.
- **The workflow-safe drag (14.C.3) mostly already exists** — `classifyDrop` already routes the 5 intents through the right modals and is the sole transition path. The Phase C delta is purely the 4-column geometry: a "forward drag" is now a *cross-phase-boundary* drop, and within-pair advances move to the card button. Reuse `getPrimaryAction` + `classifyDrop` verbatim; the test surface is "boundary crossing fires exactly one classified intent; non-adjacent/backward snaps back."
- **Your-move sort reuses `kanbanThresholds` severity** — error/warning ⇒ organizer's move (accent border, top); normal ⇒ waiting-on-speaker (below divider). No new "whose move" model.
- **`?speakerId=` is a no-op today** (the page ignores it). "Carry speaker context" = best-effort: pass the id into the Slots sub-view and highlight/scroll the matching unassigned session if present. Anything heavier is an Ask-First.

## Verification

**Commands:**
- `cd web-frontend && npx vitest run src/components/organizer/EventPage src/components/organizer/SpeakerStatus src/components/SlotAssignment src/components/organizer/EventManagement/__tests__/SpeakersSessionsTable.test.tsx 2>&1 | tee /tmp/p14c-vitest.log` -- expected: all pass; grep `/tmp/p14c-vitest.log` for failures.
- `cd web-frontend && npm run type-check 2>&1 | tee /tmp/p14c-tsc.log` -- expected: 0 errors.
- `cd web-frontend && npm run lint -- --max-warnings 50 2>&1 | tee /tmp/p14c-lint.log` -- expected: within budget.
- `cd web-frontend && node scripts/i18n/<locale-completeness check> || (compare key sets across the 10 events.json)` -- expected: new `eventPage.*` keys present in all 10 locales.

**Manual checks:**
- Beta-safety: `git diff --name-only` touches only `web-frontend/` (no `services/`, `docs/api/`, `**/db/migration/**`).
- Click-through: from Cockpit, each metric tile lands on the correct sub-view; drag a card across a phase boundary → confirm dialog appears before any change; open a stale `/slot-assignment` bookmark → redirected in-tab.

## Suggested Review Order

**Sub-view reconciliation (14.C.1 — the keystone)**

- Entry point: how `?view=` maps to Pool/Agenda/Slots (legacy kanban→pool, sessions→agenda)
  [`EventSpeakersTab.tsx:63`](../../web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx#L63)
- In-tab sub-view switch (no route change); carries speaker context to Slots
  [`EventSpeakersTab.tsx:192`](../../web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx#L192)
- The 3-way render switch (pool / agenda / slots)
  [`EventSpeakersTab.tsx:472`](../../web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx#L472)

**4-phase kanban + workflow-safe drag (14.C.2 / 14.C.3)**

- The phase model: 4 columns + single forward successor + drop resolution
  [`phaseColumns.ts:26`](../../web-frontend/src/components/organizer/SpeakerStatus/phaseColumns.ts#L26)
- Drag-end routes the resolved one-step drop through the UNCHANGED `classifyDrop` dispatch
  [`SpeakerStatusLanes.tsx:323`](../../web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx#L323)
- Declined collapsible strip (collapsed by default), not a column
  [`SpeakerStatusLanes.tsx:228`](../../web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx#L228)

**Slots in-tab + route retirement (14.C.5)**

- 2-column layout + top action bar; `focusSpeakerId` highlight; no `100vh`
  [`DragDropSlotAssignment.tsx:52`](../../web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx#L52)
- Retired route → redirect to in-tab Slots, preserving `?speakerId=`
  [`SlotAssignmentPage.tsx:23`](../../web-frontend/src/pages/organizer/SlotAssignmentPage.tsx#L23)
- Publishing "Assign timings" repointed to the in-tab Slots view
  [`ValidationDashboard.tsx:90`](../../web-frontend/src/components/Publishing/ValidationDashboard/ValidationDashboard.tsx#L90)

**Agenda summary (14.C.4)**

- "N of M need a slot" / "all slotted" / "no sessions yet" + Arrange-slots jump
  [`EventSpeakersTab.tsx:490`](../../web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx#L490)

**Peripherals**

- Real-render sub-view tests
  [`EventSpeakersTab.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/__tests__/EventSpeakersTab.test.tsx#L1)
