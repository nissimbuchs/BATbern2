# Organizer cockpit: the right cards at the right time

**Status:** review complete; nothing implemented, no issues filed. Items 1–11 are decided (2026-10-24); #12 is decided as won't do. Implementation of A–C started 2026-10-24; A ships before BATbern60 by Nissim's decision.
Nothing has been implemented and no issues have been filed.
**Started:** 2026-10-24, two weeks before BATbern60 (2026-11-06), from the organizer's point of view.
**Scope:** the event page "Needs your attention" list (Overview tab), the task templates and task
activation behind it, and the "Kritische Aufgaben" widget on `/organizer/events`.

---

## 1. What the organizer saw (BATbern60, 2026-10-24)

Event state `AGENDA_PUBLISHED`, 142 / 220 confirmed. The Overview tab showed 9 cards:

| # | Card | Source | Shown as |
|---|---|---|---|
| 1 | Start presentation | virtual, pinned | Event day · 6 Nov |
| 2 | Open Live Control | virtual, pinned | Event day · 6 Nov |
| 3 | Catering Coordination | task, `pending` | overdue 17 d |
| 4 | Newsletter: Speaker Lineup | task, `pending` | overdue 17 d |
| 5 | Newsletter: Final Agenda | task, `pending` | overdue 1 d |
| 6 | Collect outstanding presentations | virtual (session data) | due soon, no date |
| 7 | Partner Meeting Coordination | task, `todo` | upcoming (2 Nov) |
| 8 | Newsletter: Slides Are Online | task, `pending` | upcoming (7 Nov) |
| 9 | Registrations 142/220 | virtual, never done | upcoming |

Card logic: `web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts`
(`buildAttentionList`, `VIRTUAL_CARDS`).

**What the list missed:** the 16:35 session is titled "Tbd" on the published agenda, with one
speaker still `ACCEPTED` and no content. There were also no tasks for the jobs created by hand in
earlier events: the speaker briefing mail (BATbern59), name tags and wine, and the community and
motivation slides (BATbern58).

## 2. Root cause of the stuck `pending` tasks (verified)

- The agenda was auto-published on 2026-10-23 at 01:00Z. The `event-management` CloudWatch log shows
  `Auto-publishing agenda for event: BATbern60` followed by `Successfully auto-published`, with no
  task activation in between.
- `PublishingService.publishPhase("agenda")` (`PublishingService.java:82`) calls
  `setWorkflowState(AGENDA_PUBLISHED)` directly and bypasses `EventWorkflowStateMachine`. No
  `EventWorkflowTransitionEvent` is published, so `EventWorkflowTransitionListener` never moves
  the waiting tasks from `pending` to `todo`.
- The same bypass exists in the event PUT and PATCH (`EventController.java:1136`, `:1637`) and in
  `WatchSessionService.java:105` (moves to `EVENT_COMPLETED`).

## 3. Template timeline vs. reality (production templates, 2026-10-24)

| Template | Trigger | Due | Problem |
|---|---|---|---|
| Catering Coordination | agenda_published (−14) | −30 | 16 days late by design; real deadline is **−5** |
| Newsletter: Speaker Lineup | agenda_published (−14) | −30 | 16 days late by design |
| Newsletter: Final Agenda | agenda_published (−14) | −14 | due on the day it appears (accepted) |
| Eigener (non-default) | `speaker_outreach` | −30 | not a valid state, so it can never activate |
| Venue / Moderator / Partner / Topic / Slides | — | — | fine |

## 4. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Task activation bug | **Agreed.** Every workflow-state change goes through `EventWorkflowStateMachine`. That covers auto-publish, PUT/PATCH and the Watch path, not just the scheduler. Correct the BATbern60 task data. |
| 2 | Cards show `pending` | **Agreed.** Hide `pending` **and `cancelled`** tasks. Safety net: a `pending` task past its due date is still shown, labelled "Trigger noch nicht erreicht". |
| 3 | Triggers vs. timeline | **Agreed.** A task activates at whichever comes first, its trigger state or *N days before due* (new template field, default 14). Speaker Lineup gets a new trigger, the speakers phase being published (`SpeakersPhasePublishedEvent`). Fix or delete "Eigener", and validate trigger states in the template editor. |
| 3a | Domain deadlines (Nissim) | Catering **−5 days** (template change −30 → −5). Final Agenda stays at **−14**. |
| 4 | Event-day cards | **Agreed.** Pinned only on the event day. Before that, a small "Probelauf" link next to the "At a glance" tiles. Gone after `EVENT_COMPLETED`. |
| 5 | Registrations card | **Agreed.** Remove the always-on card; the "At a glance" tile shows the number. The card comes back only when there is something to do: at **≥ 90 % of capacity** or **as soon as anyone is on the waitlist**. |
| 6 | Outstanding presentations | **Agreed, split into two cards.** (a) **"Präsentationen einsammeln (n von m)"**: slides are not collected in advance; the deadline is the **event day** (USB stick at the latest). (b) **Speakers not yet quality-reviewed**: based on the **speaker-pool status**, not on title text. Every speaker counted towards the agenda who is not `QUALITY_REVIEWED` (`ACCEPTED`, `CONTENT_SUBMITTED`), with "x waiting for content, y waiting for review". Applies **from the moment the speaker names are public** (`currentPublishedPhase` = `SPEAKERS` or `AGENDA`), not only from `AGENDA_PUBLISHED`; it is overdue at once, because the names are already out. (BATbern60 on 2026-10-24: 1 `ACCEPTED`, the "Tbd" session.) |
| 7 | Missing standard tasks | **Agreed: only two new templates.** (a) **Namensschilder** (name tags), with a deep link to the name-tag generator (Participants tab → "Namensschilder exportieren", DOCX Avery L4784, #688). (b) **Wein** (wine). Not added: speaker briefing, community/motivation slides, moderator briefing. Offsets proposed from BATbern58: name tags **−2 days** (print before the day), wine **event day**, both triggered at `agenda_published`; to be confirmed by Nissim. |
| 8 | Superseded newsletters | **Decided by 3a.** A newsletter task is cancelled automatically once a later announcement has gone out: Topic Announcement when the speakers **or** the agenda are published, Speaker Lineup when the agenda is published. |
| 9 | "Kritische Aufgaben" widget | **Keep it** on `/organizer/events`, but show **all my open tasks** across events, not only those due within 3 days. Same visibility rule as #2: hide `completed`, `cancelled` and not-yet-due `pending`. Sorted by due date, overdue first. Rename the heading to "Meine offenen Aufgaben" ("My open tasks"). |
| 10 | PRD drift | **Agreed.** Update `prd-enhanced.md` FR2 and `06a-workflow-state-machines.md` to match these decisions, in the same PR as the code. |
| 12 | Sending does not complete the task | **Decided: won't do (Nissim, 2026-10-24).** Tasks stay closed by hand. Background: Nissim mailed the caterer through the platform, but the Catering task stayed open. Verified in code: no send path (venue/caterer composer, newsletter send) touches `EventTaskService`; only `EventWorkflowTransitionListener` does. Proposal: on send, the composer offers "Aufgabe X als erledigt markieren" ("mark task X as done"), pre-ticked when exactly one open task matches. |
| 11 | Clean-up | **Agreed.** Delete the dead `eventApiClient.getCriticalTasks`. Remove the `critical` query parameter from `my-tasks` and `all-tasks` (OpenAPI first, then backend, then regenerated types); after T8f nothing uses it. |

## 5. Before BATbern60, by hand (not code)

- [x] Catering: the mail had been sent; the task was closed by hand on 2026-10-24 (see #12).
- [x] Speaker Lineup newsletter: closed by hand 2026-10-24.
- [ ] Final Agenda newsletter: due 23 Oct, still open.
- [ ] 16:35 session "Tbd": get the title and abstract from the speaker.
- [ ] Name tags (print via the Participants tab export) and wine: not yet tracked as tasks.
- [ ] Speaker pool: close out the one `IDENTIFIED` and one `CONTACTED` entry.

## 6. Implementation tasks

All items are decided. Grouped into three proposed issues plus the organizer admin actions.
Issue drafts: not filed yet, pending sign-off.

### Issue A: task activation and timing (backend, event-management)
- [x] **T1** (#1) Route `PublishingService`, event PUT/PATCH and `WatchSessionService` state changes through `EventWorkflowStateMachine`. Integration test: auto-publishing the agenda moves `agenda_published` tasks to `todo`. Done: all three use `transitionToState(..., override=true, reason)` (they never validated before; auto-publish must not start failing). Agenda publish no longer moves a live/completed event backwards. Tasks created after their trigger already passed now start as `todo`. Tests: `TaskActivationIntegrationTest`, Watch test in `WatchSessionServiceIntegrationTest`.
- [x] **T4** (#3) Template field "activate N days before due" (Flyway migration + OpenAPI + scheduler): a task activates at whichever comes first, its trigger state or that date. Trigger-state validation in the template editor; fix the "Eigener" template. Done: `activation_lead_days` on templates + tasks (V125; default 14, **none for post-event triggers** so Slides Are Online never shows before the event); `TaskActivationScheduler` daily 05:30 Zurich runs `reconcileOpenTasks` (date rule + level-triggered state rule + supersession). Trigger validation already existed in `TaskTemplateService`; "Eigener" (`speaker_outreach`) predates it — **not modified in prod**, the value is now just impossible to save again. No UI field for lead days yet (API only).
- [x] **T5** (#3) Speakers-phase-published trigger for Speaker Lineup (`SpeakersPhasePublishedEvent`). Done: trigger value `speakers_published`; default template switched by V125 (defaults are seeded by migration and read-only via the API); pending Speaker Lineup tasks retargeted too.
- [x] **T7** (#8) Supersession: cancel Topic Announcement / Speaker Lineup tasks automatically on the later publish. Done: identified by new `task_templates.system_key` (`newsletter_topic`, `newsletter_speakers`, set by V125) rather than name, so a rename cannot silently disable it; runs on speakers publish, every transition, task creation and the daily reconcile. Completed tasks untouched; `cancelled_reason` records why.
- [x] **T2** (#1) Done by hand in production by Nissim (2026-10-24 09:32–09:33Z: Catering, Speaker Lineup and Partner Meeting completed; verified via API). Only Final Agenda is still `pending`; the T4 date rule activates it after deploy.

### Issue B: attention cards and my-tasks widget (frontend)
- [x] **T3** (#2) `buildAttentionList`: hide `pending`/`cancelled`, overdue-pending safety net ("Trigger noch nicht erreicht"), i18n in all 10 locales. Done: `isTaskVisible` / `isTriggerPendingOverdue` in `cockpitCards.ts`, note rendered by `AttentionCard`.
- [x] **T8** (#4) Event-day cards only on the event day; "Probelauf" link otherwise. Done: `inEventDayWindow` (Zurich day or EVENT_LIVE); rehearsal links in `CockpitTab`.
- [x] **T8b** (#5) `watch-registrations` card only at ≥ 90 % capacity or waitlist > 0. Done: `REGISTRATION_ALERT_RATIO = 0.9`.
- [x] **T8c** (#6a) `outstanding-presentations`: label "n von m", due date = event date. Done: due date = event date via `classifyDue`.
- [x] **T8d** (#6b) New card for pool entries not yet `QUALITY_REVIEWED` from the moment speakers are public (`currentPublishedPhase` ∈ {SPEAKERS, AGENDA}, added to `CockpitCardCtx`, reusing `useSpeakerPool`), split into waiting-for-content and waiting-for-review, deep link to the speaker pool. Done: `speakers-not-reviewed`; `review-submissions` stands down once speakers are public, so no double count.
- [x] **T8e** (#7) `TASK_TARGET_RULES` entry (`/namensschild|name.?tag|badge/i`) so the name-tag card deep-links to the Participants tab badge export. Done (code part): rule placed before the communications catch-all.
- [x] **T8f** (#9) `TaskWidget`: all my open tasks (`getMyTasks(false)`), same visibility rule as T3 (shared helper), sorted by due date, heading "Meine offenen Aufgaben" in all 10 locales. Done: shared helpers `isTaskVisible` and `sortTasksByDue`; new keys `tasks.myOpenTasks` and `noOpenTasks`.

### Issue C: docs and clean-up
- [x] **T9** (#10) `prd-enhanced.md` FR2 and `06a-workflow-state-machines.md`; land with Issue A. Done, plus changelog entry (doc-drift mapping for event-management).
- [x] **T10** (#11) Delete `getCriticalTasks`; remove the `critical` parameter (OpenAPI → backend → `npm run generate:api-types`); land with T8f. Done: spec, controller, `getCriticalTasksForOrganizer`, `eventApiClient.getCriticalTasks`, `taskService`/`useMyTasks`/`TaskBoardPage` params. Left: the `useCriticalTasks` stub hook in `useEvents.ts` (returns an empty list, only mocked in tests) and its `CriticalTask` type — removing it touches a components test.

### Organizer admin (turned out to need a migration, see notes)
- [x] **T6** (#3a) Catering template −30 → **−5**. Not an admin step after all: default templates are read-only through the API. Done in migration V125. Existing tasks keep their computed due date.
- [x] **T8e-admin** (#7) New templates "Namensschilder" (−2, `agenda_published`) and "Wein" (event day, `agenda_published`); offsets confirmed by Nissim 2026-10-24. Seeded as **defaults** by V125 (custom templates are not offered as standard). For BATbern60 they still have to be added from the event's task templates after deploy; they then start as `todo` at once.

**Deployment note:** a PR against `develop` deploys to production when it is opened. Issue A changes
workflow behaviour on the live event. **Decision (Nissim, 2026-10-24): ship A–C now, before BATbern60.**
