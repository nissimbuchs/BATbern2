---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - docs/ux/event-detail-redesign-spec.md
  - docs/ux/event-detail-redesign-prototype.html
  - docs/ux/event-detail-redesign-mobile.html
  - docs/architecture/06a-workflow-state-machines.md
  - docs/architecture/05-frontend-architecture.md
  - docs/prd/epic-11-speaker-workflow-refactor.md
---

# Epic 14: Event Detail Page — Lifecycle-Aware Redesign

## Overview

This document provides the complete epic and story breakdown for the **Organizer Event Detail
Page redesign**, decomposing the requirements from the UX Design Specification
(`docs/ux/event-detail-redesign-spec.md` + its two clickable prototypes — the visual source of
truth) and the relevant Architecture decisions into implementable stories.

This is a **brownfield UI-restructure epic**: the page (`/organizer/events/:eventCode`,
`EventPage.tsx`) moves from **10 flat tabs** to a **lifecycle-aware 8-tab IA** with a
task-driven Cockpit. The underlying product behaviour (the event workflow, the task-template
system, the speaker workflow) is already shipped — this epic re-organizes and re-skins access
to it, adds one small declarative front-end artifact (the workflow-state → relevance map), and
makes a handful of targeted interaction changes (4-phase kanban, in-tab slot assignment,
topic overlay, mobile shape-change).

### Grounding decisions (resolved 2026-06-13 during prerequisite validation)

> **⚠️ The spec's "9-state workflow" is reconciled to the shipped 8-state model.** The spec
> (§3, §3.1, Decision #3) assumes a 9-state workflow including `AGENDA_FINALIZED`. The shipped
> system has **8 states** — `AGENDA_FINALIZED` was removed in migration **V82**
> (`EventWorkflowState` enum: *"8-step consolidated workflow … the scheduler transitions
> AGENDA_PUBLISHED directly to EVENT_LIVE"*; frontend `WORKFLOW_STATES` array has 8 entries).
> **Resolution (Nissim, 2026-06-13):**
> 1. **Reconcile to 8 states — frontend-only.** The lifecycle spine is an **8-step** stepper.
>    The work the spec parks under `AGENDA_FINALIZED` (final newsletter, request catering offer,
>    print agenda) is, in the real system, **due-date-driven tasks that already trigger at
>    `AGENDA_PUBLISHED`** (seeded templates: *Newsletter: Final Agenda* @ 14 days before,
>    *Catering* @ 30 days before). The Cockpit surfaces these by **due date within
>    `AGENDA_PUBLISHED`**, not as a separate state. **No backend state-machine change** — honours
>    the spec's "no new backend required for the core redesign" guardrail.
> 2. **Event-day controls gate at `AGENDA_PUBLISHED` onward** (the 8-state equivalent of the
>    spec's "from `AGENDA_FINALIZED` onward"), so organizers retain the pre-doors rehearsal
>    affordance (Decision #3 intent).
>
> Every requirement below that the spec phrased against `AGENDA_FINALIZED` has been rewritten
> against the 8-state model accordingly.

The **8 event workflow states** (canonical):
`CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED →
EVENT_LIVE → EVENT_COMPLETED → ARCHIVED`.

The **8 speaker workflow states** (ADR-009, unchanged — the 4-phase kanban groups these):
`IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED`
(+ `DECLINED`, reachable from any non-terminal state).

### Delivery constraint — beta-first, dual-serve backend (Nissim, 2026-06-13)

> The redesigned page will be **shown off on `beta.batbern.ch` first**. Per the beta-canary rule
> (`CLAUDE.md` §"Beta Frontend Canary"), beta is a **frontend-only** preview served against the
> **same production API / Cognito / DB** as `www`. This imposes two hard design rules that every
> story in this epic must honour:
>
> 1. **The new page must be deployable as a frontend-only artifact.** Wherever a backend change
>    is unavoidable, it MUST be **additive and backward-compatible** so the **same production
>    backend serves BOTH the old (`www`) and the new (`beta`) frontend simultaneously**. No
>    breaking API changes, no removed/renamed fields, no migration that the old frontend can't
>    tolerate. Old frontend keeps working on prod throughout.
> 2. **Recompose, don't rewrite.** Frontend components should be **re-assembled into the new IA
>    while the content (data, behaviour, copy) stays the same** wherever possible — reuse the
>    existing data hooks, services, drawers, panels, and exports; change the *arrangement*, not
>    the *substance*. This keeps the diff frontend-only, lowers risk, and is what makes a clean
>    beta preview + prod promotion possible.
>
> See **NFR9** and **AR9** below. This makes the epic's natural delivery shape: land any
> backward-compatible backend prerequisites first (invisible to the old frontend), then build the
> new frontend behind the existing route/beta artifact, preview on beta, promote to `www`.

---

## Requirements Inventory

### Functional Requirements

**Information architecture & navigation**

- **FR1:** The page presents an **8-tab rail** — `Cockpit · Speakers & Agenda · Registrations ·
  Communications · Publishing · Wrap-up` (the "work" cluster, 1–6) and `Details · Settings` (the
  "config" cluster, 7–8). The two clusters are visually grouped, config last.
- **FR2:** The **event title stays in the page header on every tab**, so event identity is never
  lost despite identity living in the Details tab.
- **FR3:** The old 10 tabs are consolidated: Newsletter + Registrant Notices → **Communications**;
  Photos + Appreciation → **Wrap-up**; Venue/Catering composer → **Communications** (4th audience);
  old **Overview** splits into Cockpit (lifecycle/tasks/metrics) + Details (identity/topic); the
  separate `/slot-assignment` route → 3rd sub-view of Speakers & Agenda.

**Lifecycle awareness (cross-cutting)**

- **FR4:** The page adapts to `event.workflowState` (8 states). A single declarative
  **workflow-state → relevance map** drives both (a) what the Cockpit emphasizes as "what now"
  and (b) which tabs are active / dimmed / locked / badged for that state.
- **FR5:** Tabs not yet relevant for the current state render **dimmed + 🔒 and non-interactive**
  (e.g. **Wrap-up** is locked until `EVENT_LIVE`). Cockpit, Details, and Settings are always active.
- **FR6:** **Attention badges** are driven by counts: Speakers badge = sessions needing a slot or
  a content review; Communications dot = an overdue comms task; Publishing badge = a phase that
  passes validation and is ready to publish.

**Cockpit (tab 1)**

- **FR7:** The Cockpit is the landing tab with three stacked regions: lifecycle spine →
  "Needs your attention" task cards → "At a glance" metric tiles.
- **FR8:** The **lifecycle spine** is a horizontal **8-state** stepper (completed = green check,
  current = highlighted with step number "Step N of 8"), built on the existing
  `WorkflowProgressBar` / `getWorkflowProgress` helpers.
- **FR9:** "**Needs your attention**" surfaces task cards from the event task list
  (`taskService` — `GET /events/{code}/tasks` and/or `GET /tasks/my-tasks?critical=true`),
  sorted **overdue → due-soon → upcoming**, with a colour strip (red/amber/green), task name,
  due chip, assigned-organizer avatar, and a **deep-link button** to the relevant tab.
- **FR10:** **Only OPEN cards show.** A card whose work is done disappears, per the per-card
  completion-signal model (see UX-DR16 / §12.1 of the spec).
- **FR11:** A "**＋ Add task**" affordance creates a custom task (per the task-template system).
- **FR12:** **Event-day controls** ("🔴 Start the presentation", "📡 Open Live Control") appear
  as two **urgent attention cards** at the top of the list — **emitted as virtual tasks from
  `workflowState === AGENDA_PUBLISHED` onward** (8-state reconciliation of the spec's
  `AGENDA_FINALIZED`-onward intent). They are not in the page header.
- **FR13:** Four **clickable metric tiles** deep-link to their destinations: 🎟️ Registrations →
  Registrations tab; 🎤 Speakers → Speakers & Agenda · Pool; 📋 Materials → Speakers & Agenda ·
  Agenda; 🗓️ Agenda → Speakers & Agenda · Slots. Values come from
  `useEvent(['metrics','registrations','workflow'])`.

**Speakers & Agenda (tab 2)**

- **FR14:** The tab has a summary bar (accepted/min progress, acceptance rate, "Add speakers")
  above a **3-way sub-view toggle**: Pool · Agenda · Slots.
- **FR15:** **Pool** is a **4-phase kanban** — `Sourcing` (IDENTIFIED·CONTACTED), `Inviting`
  (READY·INVITED), `Content` (ACCEPTED·CONTENT_SUBMITTED), `Confirmed` (QUALITY_REVIEWED). Each
  card keeps its **exact 8-state chip** (`Identified`, `Ready`, …).
- **FR16:** Within a column, cards sort by **"whose move it is"**: "your move" (organizer action
  needed) cards get an accent left border and sit on top; below a faint divider sit "waiting on
  speaker" cards.
- **FR17:** The **Confirmed** column surfaces the slot tie-in: a `QUALITY_REVIEWED` speaker with
  no slot shows "⚠ Needs a slot →" (links to Slots); slotted ones show ✓ + time.
- **FR18:** **DECLINED** is a **collapsible bottom strip** ("▸ Declined (n)", collapsed by
  default), not a column.
- **FR19:** **Drag forward** invokes the **same handler as the card's primary-action button** —
  it *opens* the relevant confirm/modal (log-outreach, promote, send-invitation); it never
  auto-commits. It advances exactly **one** transition (dropping IDENTIFIED on *Content* must not
  fire three steps). The two within-pair advances happen via the card button (re-sorting the card
  inside its column); only cross-column steps are a drag.
- **FR20:** **Backward** drag is limited to the single legal back-transition
  (`QUALITY_REVIEWED → CONTENT_SUBMITTED`) or the Decline/Override affordance; otherwise the card
  snaps back.
- **FR21:** Clicking a speaker anywhere opens the **Speaker Detail Drawer** (Details · History ·
  Content tabs + primary-action surface) — unchanged from Epic 11.
- **FR22:** **Agenda** sub-view = an editable session table (title, speaker, slot chip, materials
  status, edit) with a "N of M need a slot" summary and a "🧩 Arrange slots →" jump to Slots.
- **FR23:** **Slots** sub-view = slot assignment **brought in-tab**, redesigned to **2 columns +
  a top action bar** (`🧱 Generate structure · ✨ Auto-assign · 🗑️ Clear all timings` + summary):
  left = unassigned-sessions tray (drag source); right = the timeline (drop targets; structural
  slots shown non-droppable; hover shows preference-match %). No separate route, no overlay.

**Registrations (tab 3)**

- **FR24:** The tab label shows a **subtle muted count** (e.g. "Registrations 140"), not a heavy
  badge.
- **FR25:** Header = active-count chip + capacity bar ("128/180 confirmed, 12 on waitlist", red
  when full) + an action row: **Name badges (XLSX)**, **Name badges (DOCX)**, and **👥 Enrol
  organizers & partners** (moved here from the old Overview quick-actions).
- **FR26:** Filters = debounced search (name/email/company) + a status segmented control
  (All · Confirmed · Registered · Attended · Cancelled · Waitlisted).
- **FR27:** Table = Name (avatar) · Email · Company · Status · Registered · Actions; row actions
  (`RegistrationActionsMenu`): Resend confirmation (REGISTERED only), Cancel, Delete.
- **FR28:** **Waitlist is the "Waitlisted" filter**, not a separate table. Selecting it swaps the
  list to **queue-ordered rows (#1, #2, …) with an inline "Promote" action**.

**Communications (tab 4)**

- **FR29:** One compose surface with an **audience switch over 4 audiences**: 📰 Newsletter
  subscribers · 🎟️ Event registrants · 🎤 Speakers · 🏛️ Venue & Caterer. The form adapts per
  audience.
- **FR30:** **Newsletter** = global list, `NEWSLETTER` template, organizer-only test-mode toggle,
  Send Newsletter + Send Reminder, send-history table + retry.
- **FR31:** **Event registrants** = this event's active registrants, `REGISTRANT_NOTICE` template
  (e.g. "slides are online"), sent in **each registrant's own language**, organizer-only preview
  picker, single send (no history).
- **FR32:** **Speakers** = bulk speaker comms (content-deadline reminder, logistics & arrival,
  thank-you) — **surfacing the existing-but-unwired `useSendReminder` hook**. Individual
  invitations & per-speaker reminders stay on the speaker card (1:1, workflow-bound).
- **FR33:** **Venue & Caterer** = venue + caterer contacts, `VENUE_COORDINATION` template
  (booking, headcount, setup), Reply-To = configured coordinator. **Moved here from Settings.**
- **FR34:** Common compose form = template select, preview-language picker, preview iframe, and a
  send **confirm dialog** showing recipient count + template + event title.

**Publishing (tab 5)**

- **FR35:** Publishing = a **validation checklist** (Topic ✓, Speakers ✓, "N sessions not yet
  slotted", "N materials awaiting review"), a **publishing-phases timeline** (Topic → Speakers →
  Agenda → Final; each Live / Blocked / Pending) with a validation-gated "Publish agenda phase"
  action, and a **live preview** of current published public content.

**Wrap-up (tab 6, locked until `EVENT_LIVE`)**

- **FR36:** **Photos** = upload (3-phase presigned PUT), grid, delete-with-confirm.
- **FR37:** **Thank-you notes** = per-note quote + author (name·company / "Anonymous") with a
  ★ Feature toggle for the public marquee — **disabled for anonymous notes**. The "slides are
  online" send is **not** duplicated here (it is a Cockpit task that deep-links to Communications).

**Details (tab 7) — identity + topic**

- **FR38:** Details holds the event's identity (set once, then frozen): **Theme image**
  (replace / ✨ AI-generate), **Title + Description** (+ ✨ AI-generate description), the selected
  **Topic** chip + "Change topic" (opens the Topic overlay), and **When & where** (date & start
  time, event type, registration deadline, venue name, address).
- **FR39:** Removed from this tab: "**Preview public page**" (redundant — Publishing has a live
  preview) and "**Enrol organizers & partners**" (moved to Registrations).

**Topic selection + brainstorming (overlay, from Details)**

- **FR40:** Topic selection is a **focused overlay** (not the standalone `/organizer/topics`
  route) with two states. **State A (pick):** filters on a horizontal top bar (search, category,
  status, sort, list/heat-map toggle, ＋ New topic) + a **single card grid** where each card folds
  in its own detail (title, staleness chip + colour-coded border, category, last-used + usage
  count, inline similarity warning).
- **FR41:** State-A **card actions** = `Select for event` · `Edit` · `🗑️ Delete` (delete disabled
  when `usageCount > 0`); a too-recent (red) topic shows "Select anyway…" + keeps the
  override/justification dialog.
- **FR42:** **State B (brainstorm):** on select, the topic **pins to the top** as a confirmed
  banner and the view becomes **speaker brainstorming** (`SpeakerBrainstormingPanel`): add a
  potential speaker form + speaker-pool list with status chips + "Promote" on CONTACTED speakers.
  Footer: "Skip for now" / "Continue to outreach →" (lands on Speakers & Agenda · Pool).

**Settings (tab 8)**

- **FR43:** Settings holds **Event moderator** (organizer select), **Registration capacity**
  (blank = unlimited; ≤ venue capacity), **Session Q&A** ("The Apéro Continues": enable toggle,
  open-trigger, days-open; Save / Close-now / Open), **Teaser images** (n/10 with per-image "show
  after" placement), and a **Danger zone** (Cancel event → notifies registrants; Delete event →
  disabled when `realAttendeeCount > 0`). **Logistics (venue/caterer) is removed** (now the
  Venue & Caterer audience in Communications).

**Mobile**

- **FR44:** On mobile the IA **changes shape**: a **bottom nav** with the 4 day-to-day
  destinations (Cockpit · Speakers · Registrations · Communications, with badges/dots) and the
  occasional ones (Publishing · Wrap-up · Details · Settings) behind a **⋯ More** bottom sheet
  (Wrap-up shows 🔒 in the sheet until `EVENT_LIVE`).
- **FR45:** The mobile Cockpit collapses the lifecycle to a "Step N/8" bar, stacks tasks
  full-width, and shows metrics **2-up** (tappable deep-links).
- **FR46:** Slots on touch use **tap-to-assign** (tap an unassigned session → empty slots light up
  → tap one to place), not drag-drop.
- **FR47:** Registrations on mobile renders **rows as cards** with a `⋯` overflow per row.

### NonFunctional Requirements

- **NFR1 (No silent consequential actions — guardrail):** Any drag or shortcut that would trigger
  a real side-effect (Cognito account provisioning, invitation/notice/reminder emails) MUST open
  the **same confirm/modal as the explicit button**. *Staging is production* — no test/E2E path
  may trigger real outbound communications or leave test data behind.
- **NFR2 (No new backend for the core):** All Cockpit/metric/task data is read from existing
  services and endpoints (see §12 grounding table). The only genuinely new artifacts are
  **front-end**: the declarative relevance map and the virtual event-day task cards. (The one
  pre-existing-but-unwired backend capability, `useSendReminder`, is surfaced, not built.)
- **NFR3 (Registrations performance):** The participant table is **not virtualized today**;
  rendering ~200 rows in one pass is sluggish. Add **pagination** ("Showing 1–25 of 140") or
  virtualization as part of this work.
- **NFR4 (Mobile usability):** The mobile experience reshapes the IA (bottom nav + More sheet,
  tap-to-assign, tables→cards) rather than shrinking the desktop layout. Touch targets and
  interactions must be usable on a phone.
- **NFR5 (i18n — all 10 locales):** Every new/changed user-facing string ships in all 10 frontend
  locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`), EN + DE first-class. (Backend email
  templates remain DE + EN only — unchanged by this epic.)
- **NFR6 (Self-explanatory on return):** Because organizers use the page intensely for ~3 months
  then not for ~4 (≈3 events/year, no muscle memory), the page must **re-orient on every return**
  and **hide what isn't relevant yet** — this is the success criterion the lifecycle-awareness
  model serves.
- **NFR7 (Accessibility):** New interactive surfaces (kanban drag, metric-tile deep-links, tab
  dim/lock states, the topic overlay, bottom-sheet nav) must remain keyboard-operable and
  screen-reader-labelled, and meet the project's existing WCAG 2.1 AA bar (Epic 6 baseline).
- **NFR8 (Conventions):** Frontend work follows project standards — service layer only (no direct
  `fetch`/`axios` in components), generated types from `src/types/generated/`, `config` objects
  (no `process.env`), `@/` path alias, `useTranslation()` for all strings. **Public-page MUI
  boundary is irrelevant here** (this is an authenticated organizer route, behind the MUI layout
  boundary already).
- **NFR9 (Beta-first / dual-serve backward compatibility):** The redesign ships to
  `beta.batbern.ch` first and must be a **frontend-only artifact** running against the **shared
  production backend**. Any backend change is **additive and backward-compatible** so the same
  prod backend serves BOTH the old `www` frontend and the new `beta` frontend at once — no
  breaking API/field changes, no migration the old frontend can't tolerate. Backend prerequisites
  land first (invisible to the old frontend); the new frontend is then previewed on beta and
  promoted to `www`. See the Delivery-constraint note above.

### Additional Requirements

_(Technical/architecture requirements that shape implementation — grounded in
`06a-workflow-state-machines.md`, `05-frontend-architecture.md`, ADR-009, and the
prerequisite-validation decisions.)_

- **AR1 (8-state reconciliation):** All workflow-state logic targets the shipped **8 states** —
  there is **no `AGENDA_FINALIZED`**. Do NOT re-introduce it. The lifecycle spine is an 8-step
  stepper; the relevance map has 8 rows.
- **AR2 (Relevance map artifact):** Author a **single declarative `workflowState → relevance`
  table** in `web-frontend/src/utils/workflow/workflowState.ts` (which today has the states +
  `getWorkflowProgress`/`getWorkflowStepNumber`/`isEarlyStage`/`isLateStage` but **no** relevance
  map). It drives, per state: Cockpit emphasis, focus tab(s), dimmed tabs, locked tabs. Tab
  badges are count-driven and computed separately.
- **AR3 (Per-card completion-signal table — §12.1 deliverable):** For every card in the relevance
  map, define **how "done" is detected** so completed cards disappear (FR10). Signal types:
  (a) **task-backed** → `status === 'completed'`; (b) **data-derived** → a named computed
  predicate (e.g. hide "needs a slot" when `sessions.filter(s => !s.startTime).length === 0`;
  hide "review submissions" when no session is awaiting review; hide "outstanding presentations"
  when `sessionsWithMaterialsCount === totalSessionsCount`; hide "fill the pool" when
  `acceptedCount >= minSlots`); (c) **workflow-action** → underlying field/state change
  (e.g. `topicCode` set; `workflowState` advanced); (d) **event-day** → no done-state (live
  actions, shown while in the FR12 window). Each "no-signal-yet" card MUST be resolved.
- **AR4 (Venue-booking completion signal — OPEN decision):** "Book the venue for the next ~2
  years" has **no data source today** that says "booked". Resolve to either (a) a tickable task
  the organizer marks complete, or (b) a derived/added venue-booking record. NB the seeded
  *Venue Booking* template triggers at `TOPIC_SELECTION` (90 days before) — the spec also frames
  it as annual forward-planning at `EVENT_COMPLETED`; reconcile these two framings during story
  detailing. (See Open Questions.)
- **AR5 (Slot-assignment rework):** Rework `DragDropSlotAssignment` from a `100vh` 3-column layout
  to a **2-column in-tab layout** with the actions hoisted into a top bar; **drop the
  viewport-lock**, **remove the separate `/slot-assignment` route**, no overlay fallback.
- **AR6 (Drag = workflow-safe):** The kanban drag handler reuses the card's primary-action handler
  and routes through the existing speaker workflow (ADR-009 8-state `SpeakerWorkflowService`
  allow-list) — exactly one transition, always via the existing confirm/modal, never a direct
  status write and never a silent email (NFR1).
- **AR7 (Topic overlay vs route):** In this flow the topic experience is the **focused overlay**
  reusing `TopicBacklogManager` + `SpeakerBrainstormingPanel` content, NOT a navigation to
  `/organizer/topics`.
- **AR8 (Reuse existing data sources):** Cockpit task cards ← `taskService`; lifecycle spine ←
  `workflowState.ts`; metric tiles ← `useEvent(['metrics','registrations','workflow'])`
  (confirmedCount, spotsRemaining, waitlistCount, confirmedSpeakersCount,
  sessionsWithMaterialsCount, totalSessionsCount, maxSpeakerSlots); templates ← `NEWSLETTER` /
  `REGISTRANT_NOTICE` / `VENUE_COORDINATION` categories.
- **AR9 (Additive backend + recompose-don't-rewrite — beta-first enabler):** Any backend change
  this epic needs is **additive only** (new optional endpoint/param/field; never remove, rename,
  or change the meaning of anything the old `www` frontend reads) so the shared prod backend
  serves both frontends (NFR9). Concretely: the **relevance map** and **virtual event-day cards**
  are frontend; **`useSendReminder`** already exists (surface, don't build); **Registrations
  pagination** prefers a **client-side** page/virtualization over the existing list payload, and
  if a server-side paginated param is introduced it must be **optional** (old frontend ignores
  it). Frontend stories **re-assemble existing components** (drawer, brainstorming panel, topic
  manager, exports, registration table, comms forms) into the new IA with **unchanged content**
  wherever possible — the new page is a recomposition, not a content rewrite.

### UX Design Requirements

_(Reusable components, visual standards, interaction patterns, and responsive work items the
redesign introduces. The two prototypes are the visual source of truth.)_

- **UX-DR1 — 8-tab rail with lifecycle visual states:** A tab-rail component rendering the
  active / **dimmed+🔒 locked** / **count-badged** / red-dot states per the relevance map, with
  the work/config cluster grouping and the persistent header title.
- **UX-DR2 — Lifecycle spine:** Horizontal 8-state stepper (completed/current/upcoming), reusing
  `WorkflowProgressBar`; collapses to a "Step N/8" bar on mobile.
- **UX-DR3 — Attention task card:** A card component with a colour strip (red/amber/green), task
  name, due chip ("Overdue · 3 days" / "Due in 4 days" / "Locks in 6 days"), assignee avatar, and
  a deep-link button; plus the urgent **event-day** variant.
- **UX-DR4 — Clickable metric tile:** A 4-up tile component (value, fraction, % filled / counts)
  that acts as a deep-link; 2-up on mobile.
- **UX-DR5 — 4-phase kanban:** Redesign the speaker board from 8 columns to **4 phase columns**
  with per-card 8-state chips, the "your-move / waiting-on-speaker" within-column sort + divider,
  the Confirmed-column slot tie-in, and the collapsible Declined strip.
- **UX-DR6 — Kanban drag grammar:** Implement drag = primary-action (one step, opens confirm),
  within-pair advance via card button, constrained backward drag with snap-back (FR19/FR20/AR6).
- **UX-DR7 — 2-column slot assignment:** The tray + timeline layout with a top action bar;
  non-droppable structural blocks; hover preference-match %; in-tab (no `100vh`).
- **UX-DR8 — Communications compose surface:** One adaptive compose form with a 4-audience switch,
  per-audience field sets, preview-language picker + preview iframe, and the send-confirm dialog.
- **UX-DR9 — Registrations table + waitlist:** The header (count chip, capacity bar, export/enrol
  row), status segmented control + debounced search, the row-actions menu, and the
  Waitlisted-filter queue-ordered + inline-Promote view.
- **UX-DR10 — Topic overlay:** State A (filters-on-top + single card grid with folded-in detail +
  card actions + staleness override) and State B (pinned-topic banner + brainstorming panel +
  footer), as a focused overlay.
- **UX-DR11 — Details tab layout:** Identity card cluster (theme image, title/description with
  AI-generate, topic chip + Change topic, when & where).
- **UX-DR12 — Wrap-up tab:** Photo upload grid (delete-confirm) + thank-you-notes list with the
  feature-toggle (disabled-for-anonymous) interaction.
- **UX-DR13 — Settings tab:** Moderator / capacity / Session-Q&A / teaser-images / danger-zone
  layout (logistics removed).
- **UX-DR14 — Mobile bottom-nav + More sheet:** Bottom navigation (4 primary destinations with
  badges) + a ⋯ More bottom sheet (4 secondary, Wrap-up locked), matching the app's existing
  `BottomNavigation`.
- **UX-DR15 — Mobile touch interactions:** Tap-to-assign for Slots; tables→cards with `⋯`
  overflow for Registrations.
- **UX-DR16 — "Done cards disappear" wiring:** Implement FR10 against the AR3 per-card
  completion-signal table so the Cockpit only ever shows still-open work.

### FR Coverage Map

All 47 FRs are delivered within the single Epic 14, grouped into ordered story phases (A–G):

| FR | Phase | Coverage |
|----|-------|----------|
| FR1 | A | 8-tab rail (work + config clusters) |
| FR2 | A | Persistent event title in page header |
| FR3 | A | 10→8 tab consolidations |
| FR4 | A | Workflow-state → relevance map (single declarative artifact) |
| FR5 | A | Tab dim/lock per state (Wrap-up locked until EVENT_LIVE) |
| FR6 | A | Count-driven attention badges |
| FR7 | B | Cockpit landing layout (spine / tasks / metrics) |
| FR8 | B | 8-step lifecycle spine |
| FR9 | B | "Needs your attention" task cards (sorted, colour-stripped, deep-linked) |
| FR10 | B | Only-open cards (done cards disappear) |
| FR11 | B | ＋ Add task |
| FR12 | B | Event-day virtual cards (@ AGENDA_PUBLISHED onward) |
| FR13 | B | 4 clickable metric tiles (deep-links) |
| FR14 | C | Speakers & Agenda summary bar + 3-way sub-view toggle |
| FR15 | C | 4-phase kanban with per-card 8-state chips |
| FR16 | C | Your-move / waiting-on-speaker within-column sort |
| FR17 | C | Confirmed-column slot tie-in |
| FR18 | C | Collapsible Declined strip |
| FR19 | C | Drag = primary-action, one transition, opens confirm |
| FR20 | C | Constrained backward drag + snap-back |
| FR21 | C | Speaker Detail Drawer (reused from Epic 11) |
| FR22 | C | Agenda sub-view session table |
| FR23 | C | Slots sub-view (2-col in-tab, top action bar) |
| FR24 | D | Muted Registrations tab count |
| FR25 | D | Registrations header (count, capacity bar, exports, enrol) |
| FR26 | D | Search + status segmented control |
| FR27 | D | Participant table + row actions |
| FR28 | D | Waitlist-as-filter (queue-ordered + inline Promote) |
| FR29 | E | Communications 4-audience switch |
| FR30 | E | Newsletter audience |
| FR31 | E | Registrant-notice audience (per-recipient language) |
| FR32 | E | Speaker bulk comms (wire `useSendReminder`) |
| FR33 | E | Venue & Caterer audience (moved from Settings) |
| FR34 | E | Common compose form + send-confirm dialog |
| FR35 | F | Publishing (validation, phases, gated publish, live preview) |
| FR36 | F | Wrap-up photos |
| FR37 | F | Wrap-up thank-you notes (★ feature, disabled-for-anonymous) |
| FR38 | F | Details tab (identity + topic + when/where) |
| FR39 | F | Details removals (Preview public page; Enrol → Registrations) |
| FR40 | F | Topic overlay State A (pick) |
| FR41 | F | Topic State-A card actions + staleness override |
| FR42 | F | Topic overlay State B (pin + brainstorming) |
| FR43 | F | Settings (moderator, capacity, Q&A, teasers, danger zone; logistics removed) |
| FR44 | G | Mobile bottom nav + More sheet |
| FR45 | G | Mobile Cockpit (collapsed spine, stacked tasks, 2-up metrics) |
| FR46 | G | Mobile tap-to-assign for Slots |
| FR47 | G | Mobile Registrations tables→cards |

**NFR / AR / UX-DR distribution:** AR1,AR2,UX-DR1,UX-DR2 → A · AR3,AR4,UX-DR3,UX-DR4,UX-DR16 → B ·
AR5,AR6,UX-DR5,UX-DR6,UX-DR7 → C · NFR3,UX-DR9 → D · UX-DR8 → E · AR7,UX-DR10–13 → F ·
NFR4,UX-DR14,UX-DR15 → G · AR8,AR9 span A–G · **NFR1,NFR2,NFR5,NFR6,NFR7,NFR8,NFR9 are acceptance
constraints on every story** (not standalone work).

## Epic List

This redesign is delivered as a **single epic** (per the file-churn-consolidation principle — one
surface, shared core files, fully-validated design with prototypes as source of truth), decomposed
into **8 ordered story phases (A–G + an out-of-band beta gate)**. Stories are detailed in the next
step.

### Epic 14: Event Detail Page — Lifecycle-Aware Redesign

**Goal:** Replace the organizer event page's 10 flat tabs with a lifecycle-aware 8-tab IA fronted
by a task-driven Cockpit, so that an organizer returning after months away is **re-oriented every
time** ("what's due now, and what's next?") and never sees what isn't relevant yet — while
de-tangling the Speakers tab, bringing slot assignment in-tab, consolidating outbound comms, and
making the whole page mobile-usable. Delivered **frontend-first against the shared prod backend**
so it can be previewed on `beta.batbern.ch` before promotion to `www` (NFR9).

**FRs covered:** FR1–FR47 (all). **Reqs:** NFR1–NFR9, AR1–AR9, UX-DR1–UX-DR16.

**Ordered story phases:**

- **Phase A — Lifecycle foundation & shell:** the `workflowState → relevance` map, the 8-tab rail
  with dim/lock/badge states, the IA shell, the persistent header title. The scaffold every tab
  hangs on; frontend-only. _(FR1–FR6 · AR1,AR2 · UX-DR1,UX-DR2)_
- **Phase B — Cockpit:** 8-step lifecycle spine, "Needs your attention" task cards + the §12.1
  **per-card completion-signal model** (so done cards disappear), event-day virtual cards
  (@ AGENDA_PUBLISHED onward), 4 clickable metric tiles, ＋Add task. _(FR7–FR13 · AR3,AR4 ·
  UX-DR3,UX-DR4,UX-DR16)_
- **Phase C — Speakers & Agenda:** 4-phase kanban + workflow-safe drag grammar + collapsible
  Declined + reused Speaker Detail Drawer; Agenda session table; **2-column in-tab Slots** (rework
  `DragDropSlotAssignment`, drop the `/slot-assignment` route + `100vh` lock). _(FR14–FR23 ·
  AR5,AR6 · UX-DR5,UX-DR6,UX-DR7)_
- **Phase D — Registrations:** header (count, capacity bar, badge exports, Enrol organizers &
  partners), search + status filters, participant table + row actions, **waitlist-as-filter**, and
  pagination/virtualization. _(FR24–FR28 · NFR3 · UX-DR9)_
- **Phase E — Communications:** one compose surface with the **4-audience switch**, **wiring the
  existing `useSendReminder`** hook for speaker bulk comms, and the Venue & Caterer audience moved
  here from Settings. _(FR29–FR34 · UX-DR8)_
- **Phase F — Config cluster + remaining tabs:** Publishing, Wrap-up (photos + thank-you notes),
  Details (identity + topic), the **Topic focused overlay** (pick → pin → brainstorm), and Settings.
  _(FR35–FR43 · AR7 · UX-DR10,UX-DR11,UX-DR12,UX-DR13)_
- **Phase G — Mobile:** bottom nav + ⋯ More sheet, tap-to-assign Slots, tables→cards, mobile
  Cockpit. _(FR44–FR47 · NFR4 · UX-DR14,UX-DR15)_
- **Beta gate (out-of-band ops, not a tracked story):** publish to `beta.batbern.ch`, click-through
  QA against shared prod data, promote to `www`. NFR9's dual-serve backward-compat is enforced as
  an acceptance constraint on every story above, so this gate is a deploy activity rather than a
  build deliverable.

---

## Epic 14: Event Detail Page — Lifecycle-Aware Redesign

**Goal:** Replace the organizer event page's (`web-frontend/src/components/organizer/EventPage/EventPage.tsx`)
10 flat tabs with a lifecycle-aware 8-tab IA fronted by a task-driven Cockpit, so an organizer
returning after months away is re-oriented every time and never sees what isn't relevant yet —
while de-tangling Speakers, bringing slot assignment in-tab, consolidating outbound comms, and
making the page mobile-usable. **Delivered frontend-only against the shared prod backend** for
preview on `beta.batbern.ch` before promotion to `www`.

**Cross-cutting acceptance constraints on EVERY story (do not restate per story unless sharpened):**
- **NFR1 — No silent consequential actions:** any drag/shortcut that would provision a Cognito
  account or send email opens the **same confirm/modal as the explicit button**. *Staging is
  production* — no test/E2E may send real comms or leave data behind.
- **NFR5 — i18n ×10:** every new/changed user-facing string ships in all 10 locales
  (`de,en,fr,it,rm,es,fi,nl,ja,gsw-BE`) via `useTranslation()`, EN+DE first-class.
- **NFR7 — Accessibility:** new interactive surfaces stay keyboard-operable + screen-reader-labelled
  (WCAG 2.1 AA).
- **NFR8 — Conventions:** service layer only (no direct `fetch`), generated types, `config` objects,
  `@/` alias.
- **NFR9 / AR9 — Beta-first / dual-serve:** the change is frontend-only; this epic introduces **no
  backend changes** (all data sources already exist — `taskService`, `useEvent` includes,
  `useSendReminder`, publishing/registration endpoints). If any additive backend need is discovered,
  it must be optional + backward-compatible so the old `www` frontend keeps working. Components are
  **recomposed**, content unchanged.

> **Implementation seam — the 8-tab shell mounts existing components (Phase A), later phases upgrade
> internals.** Phase A establishes the 8 tab slots and re-groups today's components into them
> (consolidation containers for Communications + Wrap-up; Cockpit interim = today's Overview content;
> Details surfaces today's identity edit). This makes the shell independently shippable and
> beta-previewable. Phases B–F then replace each tab's internals with the redesigned experience.
> **Publishing and Settings are essentially unchanged by the redesign** — they are satisfied by the
> Phase A mount of the existing `EventPublishingTab` / `EventSettingsTab`; Phase F only finalizes
> their placement + the small removals/guards (FR35, FR43).

---

### Phase A — Lifecycle foundation & shell

#### Story 14.A.1: Author the workflow-state → relevance map

As a returning organizer,
I want the page to know which parts of the workflow are relevant to my event's current state,
So that what I see is driven by where the event actually is — not a flat list of everything.

**Acceptance Criteria:**

**Given** `web-frontend/src/utils/workflow/workflowState.ts` (8 states + `getWorkflowProgress`/
`getWorkflowStepNumber`/`isEarlyStage`/`isLateStage`, **no** relevance map today)
**When** the map is authored
**Then** a single declarative table maps each of the **8 states** to `{ cockpitEmphasis, focusTabs[],
dimmedTabs[], lockedTabs[] }`, and a `getTabRelevance(state)` helper returns the per-tab relevance
(`active | dimmed | locked`).
**And** the table has exactly 8 rows — `CREATED, TOPIC_SELECTION, SPEAKER_IDENTIFICATION,
SLOT_ASSIGNMENT, AGENDA_PUBLISHED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED` — **no `AGENDA_FINALIZED`**
(AR1).

**Given** the 8-state map
**When** the state is `CREATED` … `AGENDA_PUBLISHED`
**Then** `Wrap-up` is `locked`; **when** state is `EVENT_LIVE` or later, `Wrap-up` is `active`
(FR5).
**And** `Cockpit`, `Details`, and `Settings` are `active` in every state.

**Given** any state
**When** `getTabRelevance` is unit-tested
**Then** every state→tab pairing is asserted, and the "finalized" emphasis (final newsletter /
catering offer / print agenda) is attributed to `AGENDA_PUBLISHED` (the 8-state reconciliation),
not a separate state.

#### Story 14.A.2: 8-tab IA shell recomposing existing components

As an organizer,
I want the event page organized into 8 lifecycle-aware tabs in two clusters,
So that I can tell work tabs from set-and-forget config at a glance and never lose the event's identity.

**Acceptance Criteria:**

**Given** `EventPage.tsx` renders 10 flat tabs today (Overview, Speakers, Venue, Participants,
Publishing, Newsletter, Registrant Notices, Settings, Photos, Appreciation)
**When** the shell is rebuilt
**Then** the tab rail shows **8 tabs** in order — `🧭 Cockpit · 🎤 Speakers & Agenda · 🎟️ Registrations ·
✉️ Communications · 📢 Publishing · 🎁 Wrap-up` (work cluster) then `📝 Details · ⚙️ Settings` (config
cluster), visually grouped with the config cluster last (FR1).
**And** the **event title remains in the page header on every tab** (FR2).

**Given** the new slots
**When** each tab mounts
**Then** existing components are **recomposed** into them with unchanged content (FR3): Cockpit =
today's `EventOverviewTab` content (interim, replaced in Phase B); Speakers & Agenda = existing
`EventSpeakersTab`; Registrations = existing `EventParticipantsTab`; Communications = a consolidation
container holding `EventNewsletterTab` + `EventRegistrantNoticesTab` + `VenueCoordinationComposer`
(replaced in Phase E); Publishing = existing `EventPublishingTab`; Wrap-up = container holding
`EventPhotosTab` + `EventAppreciationTab` (refined in Phase F); Details = the existing identity edit
surface (`EventForm`) shown in-tab (refined in Phase F); Settings = existing `EventSettingsTab`.
**And** the old standalone `Venue`, `Newsletter`, `Registrant Notices`, `Photos`, `Appreciation`,
`Overview` tabs no longer appear as top-level tabs.

**Given** the relevance map (14.A.1)
**When** a tab is `dimmed` or `locked` for the current state
**Then** dimmed tabs render visually de-emphasized and locked tabs render dimmed + 🔒 and are
**non-interactive** (e.g. Wrap-up before `EVENT_LIVE`) (FR5).

#### Story 14.A.3: Count-driven tab attention badges + task data hooks

As an organizer,
I want tabs to badge when they need action,
So that I can see where work is waiting without opening each tab.

**Acceptance Criteria:**

**Given** `taskService` exists but has **no React Query hooks**
**When** this story lands
**Then** `useEventTasks(eventCode)` and `useMyTasks({critical})` hooks wrap the existing
`taskService` calls (`GET /events/{code}/tasks`, `GET /tasks/my-tasks?critical=true`) — **frontend
only, no backend change** (AR8/NFR9).

**Given** event metrics from `useEvent(['metrics','sessions'])` and the task hooks
**When** the tab rail renders
**Then** the **Speakers & Agenda** tab shows a count badge = sessions needing a slot or a content
review; the **Publishing** tab shows a badge when a phase passes validation and is ready to publish;
the **Communications** tab shows a red dot when a comms task is overdue (FR6).

**Given** no outstanding action for a tab
**When** counts are zero
**Then** that tab shows no badge/dot.

---

### Phase B — Cockpit

#### Story 14.B.1: Cockpit landing layout + 8-step lifecycle spine

As a returning organizer,
I want the Cockpit to open with a clear "where is this event" spine,
So that I'm re-oriented the moment I land, every time.

**Acceptance Criteria:**

**Given** the Cockpit tab (interim Overview content from 14.A.2)
**When** Phase B builds the Cockpit
**Then** it renders three stacked regions top-to-bottom: lifecycle spine → "Needs your attention" →
"At a glance" (FR7).

**Given** `getWorkflowProgress`/`getWorkflowStepNumber` and `WorkflowProgressBar`
**When** the spine renders
**Then** it is a horizontal **8-state** stepper: completed states = green check, current =
highlighted with "Step N of 8" (FR8), built by reusing the existing helpers/component.

**Given** event identity lives in Details (§8)
**When** the Cockpit renders
**Then** the Cockpit shows **no** event identity block — orientation is the header title + spine only.

#### Story 14.B.2: "Needs your attention" task cards + Add task

As an organizer,
I want a sorted list of what's due now and next, each with a jump to the right tab,
So that the page answers "what now?" without me hunting through tabs.

**Acceptance Criteria:**

**Given** `useEventTasks`/`useMyTasks` (14.A.3)
**When** the attention region renders
**Then** it shows a card per open task, **sorted overdue → due-soon → upcoming**, each with a colour
strip (red overdue / amber due-soon / green comfortable), task name, a due chip ("Overdue · 3 days" /
"Due in 4 days" / "Locks in 6 days"), the assigned-organizer avatar, and a **deep-link button** to
the relevant tab ("Open Communications →", "Open Publishing →") (FR9).

**Given** the attention region
**When** the organizer clicks "＋ Add task"
**Then** a custom task is created via the existing `taskService` create call and appears in the list
(FR11).

**Given** a deep-link button
**When** clicked
**Then** the page switches to the target tab (and sub-view where applicable) without a route change.

#### Story 14.B.3: Per-card completion-signal model (done cards disappear)

As an organizer,
I want a card to vanish the moment its work is actually done,
So that the Cockpit only ever shows what's genuinely left.

**Acceptance Criteria:**

**Given** AR3 / spec §12.1, the Cockpit must show a card only while it is still open
**When** the completion-signal registry is implemented
**Then** every card type has a defined "done" detector: **task-backed** → hide when
`status === 'completed'`; **data-derived** → a named predicate (hide "N sessions need a slot" when
`sessions.filter(s => !s.startTime).length === 0`; hide "review submissions" when no session is
awaiting review; hide "outstanding presentations" when
`sessionsWithMaterialsCount === totalSessionsCount`; hide "fill the pool" when
`acceptedCount >= minSlots`); **workflow-action** → hide when the underlying field/state changes
(e.g. `topicCode` set; `workflowState` advanced) (FR10).

**Given** a card whose predicate is satisfied
**When** the Cockpit re-renders
**Then** that card no longer appears in the attention list.

**Given** the registry
**When** unit-tested
**Then** each predicate is covered by a passing test for both its open and its done state.

> **Open item carried to story detailing — venue-booking signal (AR4):** "book the venue for the
> next ~2 years" has no data source that says "booked", and its trigger framing is split
> (`TOPIC_SELECTION`@90d seeded template vs `EVENT_COMPLETED` forward-planning). Resolve to a
> tickable task or a derived record before this card is added. (See Open Questions.)

#### Story 14.B.4: Event-day virtual cards (Start Presentation / Live Control)

As an organizer on event day,
I want the live controls surfaced as urgent cards exactly when they matter,
So that they're one tap away on the day but invisible the other 99% of the time.

**Acceptance Criteria:**

**Given** the live controls today live off-page (the `/organizer/events/:eventCode/live-control`
route) and are not in the page header
**When** `workflowState` is `AGENDA_PUBLISHED` or later
**Then** two **virtual** attention cards appear at the **top** of the Cockpit attention list —
"🔴 Start the presentation" and "📡 Open Live Control" — styled urgent, due "Now · event is live"
(FR12, gate = `AGENDA_PUBLISHED` onward per the 8-state reconciliation).

**Given** an event-day card
**When** clicked
**Then** it navigates to the existing live-control / presentation surface (no new backend).

**Given** `workflowState` is before `AGENDA_PUBLISHED`
**When** the Cockpit renders
**Then** the event-day cards are absent.

**Given** event-day cards are live actions, not tasks
**When** the completion model (14.B.3) runs
**Then** they have no "done" state and persist while in the window.

#### Story 14.B.5: At-a-glance metric tiles (clickable deep-links)

As an organizer,
I want four headline metrics I can click straight through to,
So that I can both read the state at a glance and jump to act on it.

**Acceptance Criteria:**

**Given** `useEvent(['metrics','registrations','workflow'])` (confirmedCount, spotsRemaining,
waitlistCount, confirmedSpeakersCount, sessionsWithMaterialsCount, totalSessionsCount, maxSpeakerSlots)
**When** the "At a glance" region renders
**Then** four tiles show: 🎟️ Registrations (`confirmedCount / registrationCapacity`, % filled,
waitlist), 🎤 Speakers (`confirmedSpeakersCount / maxSpeakerSlots`), 📋 Materials
(`sessionsWithMaterialsCount / totalSessionsCount`), 🗓️ Agenda (sessions slotted / total) (FR13).

**Given** a metric tile
**When** clicked
**Then** it deep-links: Registrations → Registrations tab; Speakers → Speakers & Agenda · Pool;
Materials → Speakers & Agenda · Agenda; Agenda → Speakers & Agenda · Slots.

---

### Phase C — Speakers & Agenda

#### Story 14.C.1: Tab summary bar + Pool / Agenda / Slots sub-view toggle

As an organizer,
I want one Speakers & Agenda tab with three clearly-named sub-views,
So that the pool, the agenda, and slotting are one place instead of four scattered tools.

**Acceptance Criteria:**

**Given** `EventSpeakersTab` already has three sub-views (kanban / table / sessions)
**When** the tab is reworked
**Then** a summary bar (accepted/min progress, acceptance rate, "Add speakers") sits above a
**3-way sub-view toggle** labelled **Pool · Agenda · Slots** (FR14).
**And** the sub-view selection is preserved when the organizer arrives via a Cockpit deep-link
(e.g. Materials → Agenda).

#### Story 14.C.2: 4-phase speaker kanban with state chips, your-move sort, declined strip

As an organizer,
I want the speaker board grouped into four phases with each card showing its exact state,
So that I see the progression funnel and whose move it is without 8 columns of noise.

**Acceptance Criteria:**

**Given** `SpeakerStatusLanes` renders 8 lanes (one per state) today
**When** the Pool sub-view is rebuilt
**Then** it shows **4 phase columns** — `Sourcing` (IDENTIFIED·CONTACTED), `Inviting` (READY·INVITED),
`Content` (ACCEPTED·CONTENT_SUBMITTED), `Confirmed` (QUALITY_REVIEWED) — and each card keeps its
**exact 8-state chip** (FR15).

**Given** cards within a column
**When** rendered
**Then** "your move" (organizer-action) cards get an accent left border and sit at the top; below a
faint "waiting on speaker" divider sit the blocked-on-speaker cards (FR16).

**Given** a `QUALITY_REVIEWED` speaker in the Confirmed column
**When** they have no slot
**Then** the card shows "⚠ Needs a slot →" linking to the Slots sub-view; slotted speakers show ✓ +
their time (FR17).

**Given** declined speakers
**When** the board renders
**Then** `DECLINED` is a **collapsible bottom strip** ("▸ Declined (n)", collapsed by default), not a
column (FR18).

**Given** any card
**When** the organizer clicks it
**Then** the existing `SpeakerDetailDrawer` (Details · History · Content + primary-action surface)
opens unchanged (FR21).

#### Story 14.C.3: Workflow-safe kanban drag grammar

As an organizer,
I want dragging a card forward to open the same confirmation as its button, advancing exactly one step,
So that a drag can never silently provision an account or fire an email.

**Acceptance Criteria:**

**Given** the 4-phase board and the ADR-009 8-state `SpeakerWorkflowService` allow-list
**When** a card is dragged forward across a column boundary
**Then** it invokes the **same handler as the card's primary-action button** — opening the relevant
confirm/modal (log-outreach, promote, send-invitation) — and **never auto-commits** (FR19, NFR1, AR6).
**And** it advances **exactly one** transition (dropping IDENTIFIED on *Content* must not fire three
steps).

**Given** the two within-pair advances (Identified→Contacted, Ready→Invited, Accepted→Content-submitted)
**When** the organizer triggers them
**Then** they happen via the **card button** and re-sort the card inside its column; only cross-column
steps are a drag (FR19).

**Given** a backward drag
**When** attempted
**Then** only the single legal back-transition (`QUALITY_REVIEWED → CONTENT_SUBMITTED`) or the
Decline/Override affordance is allowed; any other drag **snaps back** (FR20).

#### Story 14.C.4: Agenda sub-view — session table with slot summary + jump

As an organizer,
I want an editable session list that tells me how many slots are still open,
So that I can manage the agenda and step straight into slotting.

**Acceptance Criteria:**

**Given** the existing `SpeakersSessionsTable`
**When** the Agenda sub-view renders
**Then** it shows the editable session list (title, speaker, slot chip, materials status, edit) and a
"N of M need a slot" summary (FR22).

**Given** the summary
**When** the organizer clicks "🧩 Arrange slots →"
**Then** the tab switches to the Slots sub-view.

#### Story 14.C.5: Slots in-tab — 2-column rework + retire the separate route

As an organizer,
I want slot assignment inside the Speakers & Agenda tab,
So that I'm not bounced to a separate full-page route just to place sessions.

**Acceptance Criteria:**

**Given** `DragDropSlotAssignment` is a `100vh` 3-column layout opened via the dedicated
`/organizer/events/:eventCode/slot-assignment` route (`SlotAssignmentPage`)
**When** it is reworked
**Then** it becomes a **2-column** layout — left = unassigned-sessions tray (drag source), right =
the timeline (drop targets; structural moderation/break slots shown non-droppable; hover shows
preference-match %) — with the old right-hand "Quick actions" hoisted into a **top action bar**
(`🧱 Generate structure · ✨ Auto-assign · 🗑️ Clear all timings` + summary "Total 6 · Assigned 4 ·
Pending 2") (FR23, UX-DR7).

**Given** the 2-column layout no longer needs full viewport height
**When** it mounts
**Then** it renders **in-tab** as the Slots sub-view with the `100vh`/viewport-lock removed, no
overlay, and the separate `/slot-assignment` route + `SlotAssignmentPage` removed (AR5).

**Given** a bookmark/link to the old `/slot-assignment` route
**When** opened
**Then** it redirects to the event's Speakers & Agenda · Slots sub-view (no dead link).

---

### Phase D — Registrations

#### Story 14.D.1: Registrations header — count, capacity, exports, enrol

As an organizer,
I want the registrations header to show capacity at a glance with the exports and enrol action beside it,
So that the actions that act on registrations live next to the registration data.

**Acceptance Criteria:**

**Given** the Registrations tab (existing `EventParticipantsTab`)
**When** it renders
**Then** the tab label shows a **subtle muted count** (e.g. "Registrations 140"), not a heavy badge
(FR24).
**And** the header shows an active-count chip + a capacity bar ("128/180 confirmed, 12 on waitlist",
red when full) + an action row with **Name badges (XLSX)**, **Name badges (DOCX)** (existing
`exportParticipantsXlsx`/`Docx`), and **👥 Enrol organizers & partners** (FR25).

**Given** "Enrol organizers & partners" currently lives in `EventOverviewTab` (`enrollStakeholders()`)
**When** Phase D lands
**Then** it is moved into the Registrations header and removed from the (now-Cockpit) Overview content
(FR25, FR39 cross-ref).

#### Story 14.D.2: Registrations filters + table row actions

As an organizer,
I want to search and filter registrants by status,
So that I can find the right people and act on their registration.

**Acceptance Criteria:**

**Given** `EventParticipantTable` + `RegistrationActionsMenu`
**When** the filters render
**Then** there is a **debounced** search (name/email/company) and a status segmented control
(All · Confirmed · Registered · Attended · Cancelled · Waitlisted) (FR26).

**Given** a table row
**When** the actions menu opens
**Then** it offers Resend confirmation (REGISTERED only), Cancel, Delete — via the existing
`RegistrationActionsMenu` (FR27).

#### Story 14.D.3: Waitlist as a filter (queue-ordered + inline promote)

As an organizer,
I want the waitlist to be the "Waitlisted" filter rather than a separate table,
So that there is one list and one mental model.

**Acceptance Criteria:**

**Given** the waitlist is a separate `WaitlistSection` accordion today
**When** the "Waitlisted" status filter is selected
**Then** the main list swaps to **queue-ordered rows (#1, #2, …)** with an inline **"Promote"**
action (reusing `promoteFromWaitlist`), and the separate accordion is removed (FR28).

**Given** a waitlisted row
**When** "Promote" is clicked and confirmed
**Then** the registrant is promoted and the queue re-numbers.

#### Story 14.D.4: Registrations pagination / virtualization

As an organizer,
I want the large participant list to render quickly,
So that ~200 registrants don't make the tab sluggish.

**Acceptance Criteria:**

**Given** the participant table is not virtualized today and ~200 rows render sluggishly (NFR3)
**When** the list renders
**Then** it is paginated ("Showing 1–25 of 140") or virtualized.

**Given** the beta-first/dual-serve constraint (AR9)
**When** pagination is implemented
**Then** it is **client-side over the existing list payload** by default; if a server-side paginated
param is introduced it is **optional** and the old `www` frontend (which ignores it) keeps working.

---

### Phase E — Communications

#### Story 14.E.1: Communications tab — 4-audience switch with Newsletter + Registrant audiences

As an organizer,
I want one Communications tab where I pick an audience and compose,
So that all outbound email is one surface instead of scattered tabs.

**Acceptance Criteria:**

**Given** Newsletter and Registrant Notices are separate tabs today (`EventNewsletterTab`,
`EventRegistrantNoticesTab`)
**When** the Communications tab is built
**Then** it presents one compose surface with an **audience switch over 4 audiences** (📰 Newsletter
subscribers · 🎟️ Event registrants · 🎤 Speakers · 🏛️ Venue & Caterer), and the form adapts per
audience (FR29).

**Given** the common compose form
**When** any audience is active
**Then** it offers template select, preview-language picker, preview iframe, and a send **confirm
dialog** showing recipient count + template + event title (FR34).

**Given** the Newsletter audience
**Then** it reuses the existing newsletter behaviour (global list, `NEWSLETTER` template, organizer-only
test-mode, Send Newsletter + Send Reminder, send-history table + retry) via the existing hooks (FR30).

**Given** the Event-registrants audience
**Then** it reuses the existing registrant-notice behaviour (`REGISTRANT_NOTICE` template, sent in each
registrant's own language, organizer-only preview picker, single send, no history) (FR31).
**And** the old standalone Newsletter and Registrant Notices tabs are removed.

#### Story 14.E.2: Venue & Caterer audience moved into Communications

As an organizer,
I want venue and caterer coordination to be a Communications audience,
So that it lives with the other outbound email rather than in its own tab.

**Acceptance Criteria:**

**Given** venue/catering is the standalone `EventVenueTab` (`VenueCoordinationComposer`) today
**When** Phase E lands
**Then** the 🏛️ Venue & Caterer audience composes to venue + caterer contacts with the
`VENUE_COORDINATION` template (booking, headcount, setup) and Reply-To = configured coordinator,
reusing `venueCoordinationService` (FR33).
**And** the standalone Venue tab is removed.

#### Story 14.E.3: Speakers audience — wire the existing bulk-reminder hook

As an organizer,
I want to send bulk speaker comms (deadline reminders, logistics, thank-you) from Communications,
So that I stop sending these ad-hoc from my own mail client.

**Acceptance Criteria:**

**Given** `useSendReminder` exists in `useSpeakerPool.ts` but is **wired to no UI**
**When** the 🎤 Speakers audience is built
**Then** it surfaces bulk speaker comms (content-deadline reminder, logistics & arrival, thank-you)
using the existing `useSendReminder` hook — **surfacing, not building, backend** (FR32, NFR9).

**Given** a bulk send
**When** the organizer sends
**Then** the send-confirm dialog (recipient count + template + event title) is shown first (NFR1).

**Given** 1:1 invitations and per-speaker reminders
**Then** they **stay on the speaker card** in Speakers & Agenda (workflow-bound, 1:1) — not moved here.

---

### Phase F — Config cluster + remaining tabs

#### Story 14.F.1: Wrap-up tab — photos + thank-you notes, locked until live

As an organizer,
I want a single post-event Wrap-up tab that's hidden until the event is live,
So that post-event tools don't clutter the page during planning.

**Acceptance Criteria:**

**Given** Photos and Appreciation are separate tabs today (`EventPhotosTab`, `EventAppreciationTab`)
**When** the Wrap-up tab is built
**Then** it contains **Photos** (upload via 3-phase presigned PUT, grid, delete-with-confirm — reusing
`useEventPhotos`/`useUploadEventPhoto`/`useDeleteEventPhoto`) (FR36) and **Thank-you notes** (per-note
quote + author, ★ Feature toggle for the public marquee, **disabled for anonymous notes** — reusing
`useEventThanks`/`useSetThanksFeatured`) (FR37).

**Given** the relevance map (14.A.1)
**When** `workflowState` is before `EVENT_LIVE`
**Then** the Wrap-up tab is dimmed + 🔒 and non-interactive; from `EVENT_LIVE` onward it is active (FR5).

**Given** the "slides are online" communication
**Then** it is **not** duplicated on Wrap-up — it is a Cockpit task that deep-links to Communications.

#### Story 14.F.2: Details tab — identity + topic, with removals

As an organizer,
I want the event's identity and topic on a dedicated Details tab next to Settings,
So that set-once identity doesn't compete with the Cockpit's "what now?".

**Acceptance Criteria:**

**Given** event identity is edited via a modal (`EventForm` from `openEditModal`) today
**When** the Details tab is built
**Then** it shows in-tab: **Theme image** (replace / ✨ AI-generate), **Title + Description** (+
✨ AI-generate description), the selected **Topic** chip + "Change topic" (opens the Topic overlay,
14.F.3), and **When & where** (date & start time, event type, registration deadline, venue name,
address) (FR38).

**Given** the old Overview/Details surface
**When** the Details tab lands
**Then** "**Preview public page**" is removed (Publishing already has a live preview) and "**Enrol
organizers & partners**" is absent here (moved to Registrations in 14.D.1) (FR39).

#### Story 14.F.3: Topic selection as a focused overlay (pick → pin → brainstorm)

As an organizer,
I want choosing a topic and brainstorming speakers to be one focused overlay from Details,
So that setup is a single continuous gesture without a page change.

**Acceptance Criteria:**

**Given** the topic experience is the standalone `/organizer/topics` route
(`TopicManagementPage` → `TopicBacklogManager` + `SpeakerBrainstormingPanel`) today
**When** "Change topic" is clicked in Details
**Then** a **focused overlay** opens (not a route change), reusing `TopicBacklogManager` content in
**State A**: filters on a horizontal top bar (search, category, status, sort, list/heat-map toggle,
＋ New topic) + a **single card grid** where each card folds in its detail (title, staleness chip +
colour-coded border, category, last-used + usage count, inline similarity warning) (FR40, AR7).

**Given** a topic card in State A
**When** rendered
**Then** card actions are `Select for event` · `Edit` · `🗑️ Delete` (delete disabled when
`usageCount > 0`); a too-recent (red) topic shows "Select anyway…" + keeps the override/justification
dialog (FR41).

**Given** a topic is selected
**When** State B renders
**Then** the topic **pins to the top** as a confirmed banner and the view becomes speaker
brainstorming (`SpeakerBrainstormingPanel`: add-a-potential-speaker form + pool list with status chips
+ "Promote" on CONTACTED speakers) with a footer "Skip for now" / "Continue to outreach →" that lands
on Speakers & Agenda · Pool (FR42).

#### Story 14.F.4: Publishing & Settings finalization (placement, removals, guard)

As an organizer,
I want Publishing and Settings to sit correctly in the new IA with their small redesign deltas applied,
So that the config cluster is complete and safe.

**Acceptance Criteria:**

**Given** the existing `EventPublishingTab` mounted by the Phase A shell
**When** Phase F finalizes it
**Then** Publishing keeps its validation checklist, publishing-phases timeline (validation-gated
publish action), and live preview, and its "ready to publish" state feeds the Phase A Publishing
badge (FR35).

**Given** the existing `EventSettingsTab`
**When** Phase F finalizes it
**Then** Settings keeps Event moderator (`OrganizerSelect`), Registration capacity (blank = unlimited;
≤ venue capacity), Session Q&A (enable toggle, open-trigger, days-open; Save/Close-now/Open), Teaser
images (n/10 with per-image "show after"), and a Danger zone — **Cancel event** (notifies registrants)
and **Delete event** (disabled when `realAttendeeCount > 0`) (FR43).
**And** logistics (venue/caterer) is confirmed absent from Settings (it is the Communications Venue &
Caterer audience from 14.E.2).

#### Story 14.F.5: Cockpit replaces interim Overview — remove the old Overview content

As an organizer,
I want the Cockpit to be the sole landing surface,
So that there is no leftover Overview duplicating identity, metrics, or quick-actions.

**Acceptance Criteria:**

**Given** Phase A mounted today's `EventOverviewTab` content as the interim Cockpit, and Phases B/D
moved its pieces (metrics → Cockpit tiles; enrol → Registrations; identity → Details)
**When** Phase F completes
**Then** the interim Overview content is removed and the Cockpit (14.B.*) is the only landing tab — no
duplicated identity block, metrics, topic display, or quick-actions remain (FR3, FR7 cross-ref).

---

### Phase G — Mobile

#### Story 14.G.1: Mobile bottom nav + More sheet

As an organizer on my phone,
I want the four day-to-day destinations in a bottom bar and the rest behind "More",
So that the 8-tab IA fits a phone without cramming.

**Acceptance Criteria:**

**Given** `EventPage.tsx` already uses MUI `BottomNavigation` on mobile
**When** the mobile nav is reshaped
**Then** the bottom bar holds **Cockpit · Speakers · Registrations · Communications** (with
badges/dots) and a **⋯ More** entry opens a bottom sheet with **Publishing · Wrap-up · Details ·
Settings** (FR44).

**Given** the relevance map
**When** `workflowState` is before `EVENT_LIVE`
**Then** Wrap-up shows 🔒 in the More sheet and is non-interactive.

#### Story 14.G.2: Mobile Cockpit

As an organizer checking in on the go,
I want the Cockpit to adapt to a phone,
So that "what now?" is readable and tappable on a small screen.

**Acceptance Criteria:**

**Given** the desktop Cockpit (Phase B)
**When** rendered on mobile
**Then** the lifecycle spine collapses to a "Step N/8" bar, task cards stack full-width, and the four
metric tiles render **2-up** and remain tappable deep-links (FR45).

#### Story 14.G.3: Mobile Slots — tap-to-assign

As an organizer assigning slots on a phone,
I want to tap a session then tap a slot,
So that I'm not fighting drag-drop on a touchscreen.

**Acceptance Criteria:**

**Given** the 2-column Slots sub-view (14.C.5)
**When** used on touch
**Then** tapping an unassigned session lights up the empty slots; tapping a slot places the session
there (FR46) — drag-drop is replaced by tap-to-assign on touch.

#### Story 14.G.4: Mobile Registrations — tables to cards

As an organizer on my phone,
I want registrants as cards with an overflow menu,
So that the table is usable on a narrow screen.

**Acceptance Criteria:**

**Given** the Registrations table (Phase D)
**When** rendered on mobile
**Then** each registrant renders as a card with a `⋯` overflow exposing the row actions (FR47).

---

## Open Questions

**1. How do we know the venue is "booked"? (Story 14.B.3 / AR4)**
The Cockpit only works if a card disappears once its work is done, and for almost every card we
can detect that from existing data or a task status. The one exception is venue booking. There's
no field anywhere today that says "the venue is booked", and the way we talk about it is split:
the seeded task template fires it at Topic Selection (90 days before the event), but the spec also
frames it as an annual "book the next two years ahead" job that surfaces after the event. Before
we add this card we need to decide whether the organizer simply ticks off a task to mark it done,
or whether we record an actual venue-booking somewhere we can check — and which of the two timings
(per-event at 90 days, or annual forward-planning) we actually mean.

**2. Is "✨ AI-generate" for the theme image and description new or existing? (Story 14.F.2)**
The Details tab reproduces the prototype's "AI-generate" buttons for the theme image and the event
description. Today's Overview tab already has an "AI assist drawer", so this may just be a matter of
re-surfacing what exists. We should confirm the AI-generate actions already work against a real
endpoint and are simply being recomposed onto the Details tab — and not quietly a new capability,
which would pull backend work into an otherwise frontend-only, beta-first epic.

**3. Does the §12.1 completion-signal table need to be exhaustively enumerated before Phase B starts?**
Story 14.B.3 defines the per-card "done" detector by card *type* (task-backed, data-derived,
workflow-action, event-day). The spec asks for a full card-by-card table covering every card in the
prototype's `STATES` map. We should agree whether enumerating every individual card is a prerequisite
for starting Phase B, or whether the four typed detectors plus the named predicates already listed
are enough to build against, with the long-tail cards filled in as they're added.
