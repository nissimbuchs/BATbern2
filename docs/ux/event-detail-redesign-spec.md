# Event Detail Page — UX Redesign Specification

**Author:** Sally (UX) with Nissim
**Date:** 2026-06-13
**Status:** Draft for review — design agreed via interactive prototyping
**Prototypes:**
- Desktop — [`event-detail-redesign-prototype.html`](./event-detail-redesign-prototype.html)
- Mobile — [`event-detail-redesign-mobile.html`](./event-detail-redesign-mobile.html)

> These prototypes are the visual source of truth for this spec. Every behavior below is
> demonstrated there. This document is the written hand-off to architecture & development.

---

## 1. Problem & goals

### The problem
The organizer event page (`/organizer/events/:eventCode`, `EventPage.tsx`) is **10 flat tabs**,
all visual peers, with the Speakers tab silently carrying four tools (kanban, sessions, a
*separate* slot-assignment route, a detail drawer). Two compounding issues:

1. **The navigation ignores time, but the work is entirely about time.** The PRD models a
   9-state event workflow and a task table where every task is pinned to a moment ("Venue
   booking 90 days before", "Speaker newsletter 30 days before", "Catering 30 days before",
   "Final newsletter 14 days"). The organizer's real question is never "which of 10 tabs?" —
   it's *"what's due now, and what's next?"* Flat tabs can't answer that.
2. **Only ~3 events per year.** Organizers use the page intensely for ~3 months, then don't
   see it for ~4 months. No muscle memory forms. The page must **re-orient them every time**
   and hide what isn't relevant yet.

### Goals
- Replace flat-tab navigation with a **lifecycle-aware** structure: a task-driven cockpit
  + tabs that dim/badge by workflow state.
- **Consolidate** overlapping tabs (10 → 8, with the last 2 a "set-and-forget" cluster).
- **De-tangle** the Speakers tab and bring slot assignment back in-tab.
- Make the page **self-explanatory on return** and **mobile-usable**.

### Guardrails / non-negotiables
- **No silent consequential actions.** Provisioning a Cognito account and sending invitation
  emails have real side-effects; *staging is production*. Any drag/shortcut that would trigger
  one must open the same confirm/modal as the explicit button.
- All data the cockpit shows already exists (see §11) — **no new backend required** for the
  core redesign.

---

## 2. Information architecture

**Tab rail (8 tabs):**

| # | Tab | Purpose | Cluster |
|---|-----|---------|---------|
| 1 | **🧭 Cockpit** | "What now?" — lifecycle + due tasks + at-a-glance metrics | work |
| 2 | **🎤 Speakers & Agenda** | Pool (kanban) · Agenda (sessions) · Slots | work |
| 3 | **🎟️ Registrations** | Registrants, capacity, waitlist, badges, enrol | work |
| 4 | **✉️ Communications** | All outbound email, 4 audiences | work |
| 5 | **📢 Publishing** | Progressive publish validation & phases | work |
| 6 | **🎁 Wrap-up** | Post-event: photos + thank-you notes | work (late) |
| 7 | **📝 Details** | Event identity + topic — set once, then frozen | config |
| 8 | **⚙️ Settings** | Moderator, capacity, Q&A, teaser images, danger zone | config |

**Rationale for the split:** tabs 1–6 are where an organizer *works* across a cycle; tabs 7–8
are the "set-and-forget" config cluster, visually grouped at the end. The event **title stays
in the page header on every tab**, so identity is never lost despite living in Details.

**Consolidations from the old 10 tabs:**
- Newsletter + Registrant Notices → **Communications** (audience switch).
- Photos + Appreciation → **Wrap-up**.
- Venue/Catering composer → **Communications** (it's a communication, 4th audience) — *not* Settings.
- Old **Overview** → split: lifecycle/tasks/metrics → **Cockpit**; identity/topic → **Details**.
- **Slot assignment** (was a separate `/slot-assignment` route) → 3rd sub-view of Speakers & Agenda.

---

## 3. Lifecycle-awareness model

The page adapts to `event.workflowState` (9 states: `CREATED → TOPIC_SELECTION →
SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE →
EVENT_COMPLETED → ARCHIVED`).

- **Tab dimming:** tabs not yet relevant for the current state render dimmed + 🔒 and are
  non-interactive (e.g. **Wrap-up** is locked until `EVENT_LIVE`).
- **Attention badges:** tabs needing action show a count badge or red dot (e.g. Speakers shows
  the count of sessions needing a slot; Communications shows a dot for an overdue newsletter).
- **NEW artifact required:** a single **workflow-state → relevance map**. The same declarative
  table drives *two* things per state: (a) what the **Cockpit emphasizes** as "what now", and
  (b) which **tabs are active / dimmed / locked / badged**. `workflowState.ts` has the states +
  progress helpers (`getWorkflowProgress`, `getWorkflowStepNumber`, `isEarlyStage`,
  `isLateStage`) but **no** such mapping today — it must be authored.

### 3.1 The map (draft)

| State | Cockpit emphasis ("what now") | Focus tab(s) | Dimmed | Locked |
|---|---|---|---|---|
| **CREATED** | Define the event — fill details & pick a topic | Details | Speakers, Registrations, Communications, Publishing | Wrap-up |
| **TOPIC_SELECTION** | Confirm the topic; assign a moderator; start the pool | Details, Speakers·Pool | Registrations, Publishing | Wrap-up |
| **SPEAKER_IDENTIFICATION** | Fill the pool; review submissions & start slotting | Speakers·Pool | Registrations | Wrap-up |
| **SLOT_ASSIGNMENT** | Finish slotting & reviews; publish the agenda | Speakers·Slots | Registrations | Wrap-up |
| **AGENDA_PUBLISHED** | Collect outstanding presentations; watch registrations | Registrations, Speakers·Agenda | — | Wrap-up |
| **AGENDA_FINALIZED** | Final newsletter; request the catering offer; print agenda | Communications, Publishing | — | Wrap-up |
| **EVENT_LIVE** | 🔴 Run the event — present / live control | Cockpit (event-day cards), Registrations (check-in) | — | — *(Wrap-up unlocks)* |
| **EVENT_COMPLETED** | "Slides online"; photos; thank-yous; **book the venue ahead** | Wrap-up, Communications | Speakers | — |
| **ARCHIVED** | Archived — read-only | — | most | — |

**Badges (independent of the focus column, driven by counts):** Speakers badge = sessions
needing a slot or a content review; Communications dot = an overdue comms task; Publishing
badge = a phase that passes validation and is ready to publish. Cockpit, Details, and Settings
are always active.

**Non-obvious task placements (confirmed 2026-06-13; the prototype's `STATES` array is the
draft task table):**
- **Review speaker submissions** and **"N sessions need a slot"** span **Speakers +
  Slot Assignment** — *not* Agenda Published. By the time the agenda publishes, all submissions
  must be reviewed and all sessions slotted (Publishing validation blocks otherwise), so there's
  nothing left to surface there.
- **Assign the moderator** appears from **Topic** onward (PRD trigger = `TOPIC_SELECTION`), and
  persists into Speakers / Slot Assignment until done.
- **Catering** is two distinct touchpoints: at **Agenda Finalized** the task is **"request the
  catering offer"** (send mail). (Headcount confirmation, if modelled, also belongs at Finalized —
  never earlier.)
- **Venue booking** is **annual** (booked once a year for the upcoming ~2 years), so it is **not**
  a per-event speaker-phase task — it surfaces at **Event Completed** as forward planning.

---

## 4. Cockpit (tab 1)

The home/landing tab. Three stacked regions, top to bottom:

### 4.1 Lifecycle spine
Horizontal stepper of the 9 states; completed = green check, current = highlighted with step
number ("Step 4 of 9"). Uses existing `WorkflowProgressBar` / `getWorkflowProgress`.

### 4.2 "Needs your attention" (task cards)
- Driven by the **event task list** (`taskService`, `GET /events/{code}/tasks` and/or
  `GET /tasks/my-tasks?critical=true`).
- Cards sorted **overdue first, then due-soon, then upcoming**. Colour strip: red (overdue),
  amber (due soon), green (comfortable).
- Each card shows: task name, due chip ("Overdue · 3 days", "Due in 4 days", "Locks in 6 days"),
  assigned organizer avatar, and a **deep-link button** to the relevant tab
  ("Open Communications →", "Open Publishing →", "Open Settings →").
- "**＋ Add task**" affordance (tasks are configurable per the PRD task-template system).
- **Only OPEN cards show.** A card whose work is already done must disappear from the list —
  otherwise the cockpit shows stale "to-dos" and loses its whole "what's actually left?" value.
  The completion signal differs per card and is **not** always a task status — see the Scrum
  Master deliverable in §12.1.

**Event-day controls live here.** "Start Presentation" and "Live Control" are **not** in the
page header (they're irrelevant 99% of the time). On the event day they appear as **two
attention cards** at the top of this list ("🔴 Start the presentation", "📡 Open Live Control",
due "Now · event is live"). The prototype has a "▶ Preview event-day" toggle to demonstrate.
Implementation: emit these as (virtual) tasks from `workflowState === AGENDA_FINALIZED` onward
(confirmed 2026-06-13 — allows rehearsal/opening before doors), styled urgent.

### 4.3 "At a glance" (metric tiles — clickable)
Four tiles, each a **deep-link**:

| Tile | Shows | Click → |
|------|-------|---------|
| 🎟️ Registrations | `confirmedCount / registrationCapacity`, % filled, waitlist | **Registrations** tab |
| 🎤 Speakers | `confirmedSpeakersCount / maxSpeakerSlots` | **Speakers & Agenda → Pool** |
| 📋 Materials | `sessionsWithMaterialsCount / totalSessionsCount` | **Speakers & Agenda → Agenda** |
| 🗓️ Agenda | sessions slotted / total | **Speakers & Agenda → Slots** |

All values come from `useEvent(..., ['metrics','registrations','workflow'])` — already available.

> **Event identity is NOT on the Cockpit** (moved to Details, §8). The Cockpit is purely
> "what now?". The header title covers orientation.

---

## 5. Speakers & Agenda (tab 2)

A summary bar (progress: accepted/min, acceptance rate, "Add speakers") sits above a
**sub-view toggle** with three views. Clicking a speaker anywhere opens the **Speaker Detail
Drawer** (Details · History · Content tabs + primary-action surface — unchanged from Epic 11).

### 5.1 Pool — 4-phase kanban (redesigned)
**Was** 8 columns (one per state). **Now** 4 phase columns, because each card already carries
its primary-action button, so the column header no longer needs to encode the exact state:

| Phase column | Workflow states inside |
|---|---|
| **Sourcing** | IDENTIFIED · CONTACTED |
| **Inviting** | READY · INVITED |
| **Content** | ACCEPTED · CONTENT_SUBMITTED |
| **Confirmed** | QUALITY_REVIEWED |

- **Each card keeps its exact state as a chip** (`Identified`, `Ready`, `Invited`, …) — the
  8-state granularity is preserved on the card, hidden only from the headers.
- **Within-column sort = whose move it is:** "your move" cards (organizer action needed) get an
  accent left border and sit at the top; below a faint "waiting on speaker" divider sit the
  cards you're blocked on. This recovers the at-a-glance signal the merge would otherwise lose.
- **Confirmed** surfaces the slot tie-in: a QUALITY_REVIEWED speaker with no slot shows
  "⚠ Needs a slot →" (links to the Slots sub-view); slotted ones show ✓ + time.
- **DECLINED** is a **collapsible strip at the bottom** ("▸ Declined (n)", collapsed by
  default) — not a column.

**Drag-drop rules (critical):**
- A forward drag invokes the **same handler as the card's primary-action button** — it *opens
  the relevant confirm/modal* (log-outreach, promote, send-invitation), it does **not**
  auto-commit.
- It advances **one** transition, never "however many to reach the column" — dropping an
  IDENTIFIED card on *Content* must not fire three emails.
- The two within-pair advances (Identified→Contacted, Ready→Invited, Accepted→Content-submitted)
  happen via the card button and re-sort the card *inside* its column; only cross-column steps
  are a drag. This is an intended change to the board's interaction grammar.
- Backward = only the one legal back-transition (QUALITY_REVIEWED → CONTENT_SUBMITTED) or the
  Decline/Override affordance; otherwise snap back.

> **Rejected alternative:** a "Your move / Their move / Done" board. Great as a *lens* (it
> already lives in the Cockpit's attention list), but as the primary kanban it throws away the
> progression funnel and makes drag-to-advance meaningless. Not adopted.

### 5.2 Agenda — sessions table
Editable session list (title, speaker, slot chip, materials status, edit). 2-of-6-need-a-slot
summary. "🧩 Arrange slots →" jumps to the Slots sub-view.

### 5.3 Slots — slot assignment (brought in-tab, redesigned to 2 columns)
**Was** a separate full-page route (`/slot-assignment`) opened "for space". **Now** the third
sub-view. Redesigned from 3 columns to **2**, by moving the old right-hand "Quick actions"
column into a **top action bar**:
- **Top bar:** `🧱 Generate structure` · `✨ Auto-assign` · `🗑️ Clear all timings` + summary
  ("Total 6 · Assigned 4 · Pending 2").
- **Left column:** unassigned-sessions tray (drag source).
- **Right column:** the timeline (drop targets; structural slots — moderation/break — shown as
  non-droppable blocks; hover shows preference-match %).

> **Implementation note:** the old separate route existed because the 3-column layout wanted
> full height (`100vh`). The 2-column redesign (tray + timeline, actions in a top bar)
> **removes that constraint** — it fits comfortably in the tab body, so slot assignment lives
> **in-tab** with no separate route and no overlay fallback. `DragDropSlotAssignment` is
> reworked from 3 columns to 2; the `100vh`/viewport-lock is dropped.

---

## 6. Registrations (tab 3)

- **Tab shows a count** (e.g. "Registrations 140") as a subtle muted number, *not* a heavy
  badge (a 3-digit pill looks wrong).
- **Header:** active count chip, capacity bar ("128/180 confirmed, 12 on waitlist", red when
  full), and the export/action row: **Name badges (XLSX)**, **Name badges (DOCX)**, and
  **👥 Enrol organizers & partners** (moved here from the old Overview quick-actions — it acts
  on registrations, so it belongs next to the badge exports).
- **Filters:** search (name/email/company, debounced) + status segmented control
  (All · Confirmed · Registered · Attended · Cancelled · Waitlisted).
- **Table:** Name (avatar) · Email · Company · Status · Registered · Actions. Row actions
  (`RegistrationActionsMenu`): Resend confirmation (REGISTERED only), Cancel, Delete.
- **Waitlist = the Waitlisted filter, not a separate table.** Selecting "Waitlisted" swaps the
  list to **queue-ordered rows (#1, #2, …) with an inline "Promote" action** — the only thing
  the old separate accordion added. One list, one mental model.
- **⚠ Implementation note:** the current participant table is **not virtualized**; ~200 rows in
  one render is sluggish. Add **pagination** (shown as "Showing 1–25 of 140") or virtualization
  as part of this work.

---

## 7. Communications (tab 4)

One compose surface, an **audience switch** with **4 audiences**; the form adapts per audience.

| Audience | Recipients | Templates | Notes |
|---|---|---|---|
| 📰 **Newsletter subscribers** | global list (~2,400) | `NEWSLETTER` | Test-mode toggle (organizers only); **Send Newsletter** + **Send Reminder**; send-history table + retry. |
| 🎟️ **Event registrants** | this event's active registrants | `REGISTRANT_NOTICE` (e.g. "slides are online") | Sent in **each registrant's own language**; preview picker is organizer-only; single send, no history. |
| 🎤 **Speakers** | this event's speakers | speaker bulk: content-deadline reminder, logistics & arrival, thank-you | Replaces today's ad-hoc "send from my mail client" workflow. **Individual invitations & per-speaker reminders stay on the speaker card** in Speakers & Agenda (1:1, workflow-bound). NB: a `useSendReminder` hook already exists in code but is wired to no UI — surface it here. |
| 🏛️ **Venue & Caterer** | venue + caterer contacts | `VENUE_COORDINATION` (booking, headcount, setup) | Reply-To = configured coordinator. **Moved here from Settings** — it's a communication. |

Common compose form: template select, preview language, preview iframe, send + confirm dialog
(recipient count + template + event title). The "slides are online" mail is just the
registrant-notice template — it is **not** duplicated on Wrap-up (it shows up as a Cockpit task
at the right time and deep-links here).

---

## 8. Details (tab 7) — event identity + topic

The event's identity — **set once at the start, then frozen**, which is why it sits next to
Settings rather than competing with the Cockpit's "what now?".

Contents:
- **Theme image** (replace / ✨ AI-generate).
- **Event details:** Title, Description (+ ✨ AI-generate description).
- **Topic:** the selected topic chip + "**Change topic**" → opens the Topic overlay (§9).
- **When & where:** date & start time, event type, registration deadline, venue name, address.

Removed from this tab:
- **"Preview public page"** — redundant; Publishing already has a live preview.
- **"Enrol organizers & partners"** — moved to Registrations (§6).

---

## 9. Topic selection + brainstorming (overlay, from Details)

**Was** a 4-column adaptive grid (`TopicBacklogManager`: filter sidebar | list/heat-map |
topic-detail panel | brainstorming). Redesigned into a **focused overlay with two states**:

**State A — pick a topic (1 grid):**
- **Filters on top** (horizontal bar): search, category, status, sort, list/heat-map toggle,
  "＋ New topic". The old left filter *column* is gone.
- **One card grid.** Each topic card shows title, staleness chip + colour-coded left border
  (green/amber/red per `stalenessScore`/`colorZone`), category, last-used + usage count, and an
  inline **similarity warning** when relevant (e.g. "82% similar to …"). The old separate
  right-hand detail *panel* is gone — its content folds onto the card.
- **Actions on the card:** `Select for event` · `Edit` · `🗑️ Delete` (delete disabled when
  `usageCount > 0`). A too-recent (red) topic shows "Select anyway…" + keeps the override/
  justification dialog.

**State B — selected → brainstorm (1–2 columns):**
- On select, the topic **pins to the top** as a confirmed banner ("✓ Topic selected … ·
  staleness · Change topic") and the view **becomes speaker brainstorming**
  (`SpeakerBrainstormingPanel`):
  - **Add a potential speaker** form (Name*, Company, Expertise, Assign to organizer, Notes,
    "＋ Add to pool").
  - **Speaker pool** list with status chips; "Promote" on CONTACTED speakers (provisions the
    account).
- Footer: "Skip for now" / "**Continue to outreach →**" (lands on Speakers & Agenda → Pool).

Net: 4 columns → 1 (picking) then 2 (brainstorming).

> **Confirmed 2026-06-13:** this is a **focused overlay** (not the standalone `/organizer/topics`
> route) — topic-pick → brainstorm is one continuous setup gesture without a page change.

---

## 10. Publishing (tab 5), Wrap-up (tab 6), Settings (tab 8)

### Publishing
- **Validation** checklist (Topic ✓, Speakers ✓, "N sessions not yet slotted", "N materials
  awaiting review").
- **Publishing phases** timeline (Topic → Speakers → Agenda → Final), each Live / Blocked /
  Pending, with a "Publish agenda phase" action gated on validation.
- **Live preview** of current published public content.

### Wrap-up (locked until `EVENT_LIVE`)
- **Photos:** upload (3-phase presigned PUT), grid, delete-with-confirm.
- **Thank-you notes:** per-note quote + author (name·company / "Anonymous"); ★ Feature toggle
  for the public marquee — **disabled for anonymous notes** (no name to attribute).
- The "slides are online" send is **not** duplicated here (Cockpit task → Communications).

### Settings
- **Event moderator** (organizer select).
- **Registration capacity** (number; blank = unlimited; ≤ venue capacity).
- **Session Q&A** ("The Apéro Continues"): enable toggle, open-trigger (after event /
  speakers-published), days-open; Save / Close-now / Open actions.
- **Teaser images** (presentation slides; n/10 with per-image "show after" placement).
- **Danger zone:** Cancel event (notifies registrants) · Delete event (disabled when
  `realAttendeeCount > 0`).
- **Logistics (venue/caterer) removed** → now the Venue & Caterer audience in Communications.

---

## 11. Mobile design

Seven+ destinations don't fit a phone tab bar, so the IA **changes shape**, it doesn't shrink:

1. **Bottom nav + "More".** The 4 day-to-day destinations in the bottom bar — **Cockpit,
   Speakers, Registrations, Communications** (with badges/dots). The occasional ones —
   **Publishing, Wrap-up, Details, Settings** — live behind **⋯ More** (a bottom sheet). Matches
   the real app's existing `BottomNavigation` and platform norms. Wrap-up shows 🔒 in the sheet
   until `EVENT_LIVE`.
2. **Cockpit matters more on mobile** (checked on the go): lifecycle collapses to a `Step 4/9`
   bar, tasks stack full-width, metrics go 2-up (and are tappable deep-links).
3. **Drag → tap-to-assign** for Slots: tap an unassigned session, empty slots light up, tap one
   to place it. Drag-drop is hostile on touch.
4. **Tables → cards** (Registrations) with a `⋯` overflow per row.
5. Event-day controls appear as attention cards on the day (same as desktop).

---

## 12. Grounding — what already exists (no new backend for the core)

| Need | Source | Status |
|---|---|---|
| Cockpit task cards | `taskService` · `GET /events/{code}/tasks` · `GET /tasks/my-tasks?critical=true` | ✅ exists (fields: taskName, triggerState, dueDate, assignedOrganizerUsername, status) |
| Lifecycle spine | `workflowState.ts` (`getWorkflowProgress`, step helpers) | ✅ exists |
| Metric tiles | `useEvent(['metrics','registrations','workflow'])` (confirmedCount, spotsRemaining, waitlistCount, confirmedSpeakersCount, sessionsWithMaterialsCount, totalSessionsCount, maxSpeakerSlots) | ✅ exists |
| Templates per audience | `NEWSLETTER`, `REGISTRANT_NOTICE`, `VENUE_COORDINATION` categories | ✅ exist |
| Speaker reminders | `useSendReminder` hook | ⚠ exists, **unwired** — surface in Communications |

**New work the redesign introduces:**
- **Workflow-state → relevance map** (cockpit emphasis + tab dimming/locking/badging) — small
  declarative table, new (drafted in §3.1).
- **Slot assignment in-tab** — rework `DragDropSlotAssignment` from a `100vh` 3-column layout to
  a 2-column in-tab view (action bar on top); drop the viewport-lock. No separate route, no
  overlay.
- **Registrations pagination/virtualization** — table is not virtualized today.
- **Event-day controls as virtual tasks** (Cockpit) when `EVENT_LIVE`.
- **Mobile tap-to-assign** slot interaction.

### 12.1 Scrum Master action — completion signal per card (so "done" cards never show)

**Requirement:** the Cockpit shows a task card only while it is *still open*; a completed card
must disappear. For **every card in every state** (see the `STATES` map in the prototype), the
team — Scrum Master to drive — must pin down **how we know it's done**. The signal differs by
card type and is *not* always a task status:

| Card type | Examples | "Done" signal |
|---|---|---|
| **Task-backed** | moderator, partner meeting, newsletters, catering offer | a row in the task system with `status` → hide when `status === 'completed'`. Straightforward. |
| **Data-derived** (no task row) | "N sessions need a slot", "Review speaker submissions", "Collect outstanding presentations", "Fill the pool 3/12" | a **computed predicate** — e.g. hide "needs a slot" when `sessions.filter(s => !s.startTime).length === 0`; hide "review submissions" when no session is `CONTENT_SUBMITTED`-awaiting-review; hide "outstanding presentations" when `sessionsWithMaterialsCount === totalSessionsCount`; hide "fill the pool" when `acceptedCount >= minSlots`. Each predicate must be defined. |
| **Workflow-action** | "Confirm the topic", "Finalize & publish the agenda" | done when the underlying field/state changes (e.g. `topicCode` set; `workflowState` advanced). Define the trigger. |
| **Event-day action** | "Start presentation", "Live Control" | not "completable" — live actions; show while `EVENT_LIVE` (+ `AGENDA_FINALIZED` rehearsal), no done-state. |
| **No signal yet — needs a decision** | **venue booking** ("book for the next 2 years") | there is no data source today that says "booked". Decide: (a) make it a real task the organizer ticks off, or (b) add/derive a venue-booking record to check. |

**Deliverable:** a per-card table — *card → completion predicate → data source* — covering every
card in the `STATES` map, with every "no signal yet" card explicitly resolved to (a) a tickable
task or (b) a derived signal. Without it, the Cockpit will surface already-done cards and the
"what's left?" promise breaks.

---

## 13. Decisions confirmed (2026-06-13)

All previously-open questions are resolved:

1. **Topic experience → focused overlay** (not the standalone `/organizer/topics` route).
   Pick → pin → brainstorm in one gesture, lands back on the event with no page change.
2. **Tab count → keep 8** (6 work + Details + Settings). Identity and operational config are
   genuinely different jobs; both stay visible, grouped at the end.
3. **Event-day controls → from `AGENDA_FINALIZED` onward** (not `EVENT_LIVE` only) — so
   organizers can rehearse / open the presentation before doors, and the cards are already
   present on the day.
4. **Slot assignment → in-tab 2-column** (no `100vh`, no separate route, no overlay) — the
   2-column redesign removed the height constraint that justified the old separate route.

---

## 14. Decisions log (what we settled during prototyping)

- ✅ Full reimagining (cockpit + merges + lifecycle dimming + slots in-tab).
- ✅ Cockpit = lifecycle + tasks + metrics only; **identity moved out**.
- ✅ Newsletter+Notices+Speakers+Venue → one **Communications** tab (4 audiences).
- ✅ Photos+Appreciation → **Wrap-up**; "slides online" via Cockpit task, not duplicated.
- ✅ Speaker kanban → **4 phase columns**, state chips on cards, your-move/waiting sort,
  collapsible Declined; **drag = primary action, one step, never a silent email**.
- ✅ "Whose move" board **rejected** as primary (kept as the cockpit lens).
- ✅ Slot assignment → **Slots sub-view**, redesigned to **2 columns + top action bar**;
  the 2-column layout removes the old `100vh` need, so it's **fully in-tab** (no route, no overlay).
- ✅ Authoring the **workflow-state → relevance map** (cockpit emphasis + tab dimming) — drafted
  in §3.1.
- ✅ Registrations: **count on the tab**; **waitlist via the Waitlisted filter** (no separate
  table); **Enrol organizers & partners** moved here.
- ✅ **Details** tab (identity + topic) next to **Settings**; "Preview public page" removed.
- ✅ Topic page → **filters on top + card actions + select→pin→brainstorm** (4 cols → 1–2).
- ✅ Metric tiles are **clickable deep-links**.
- ✅ Venue/caterer comms → **Communications**, removed from Settings.
- ✅ **Start Presentation / Live Control** removed from the header → **attention cards on the
  event day**.
- ✅ Mobile: **bottom nav + More sheet**, tap-to-assign slots, tables→cards, lifecycle dimming.
- ✅ Topic flow = **focused overlay** (not a route); **8 tabs kept** (Details + Settings both
  visible); **event-day controls show from `AGENDA_FINALIZED`**; **slots fully in-tab** (no `100vh`).
