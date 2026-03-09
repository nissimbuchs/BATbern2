# Fix Summary — User guide — organiser workflow phases
**Fixed:** 2026-03-09

## Changes made

### MISMATCH fixes

- **M1** (EVENT_COMPLETED trigger): Replaced the "hourly cron checks event end time" claim in `README.md` (Automatic State Transitions table) and `phase-e-publishing.md` with the correct Watch-mode mechanism: the organiser calls `endSession()` for every completeable session (keynote/presentation/workshop/panel_discussion), which triggers the transition. Added a new "Live Event Management (Watch Mode)" section to `phase-e-publishing.md` (N1/N2 content included here). The AGENDA_FINALIZED → EVENT_LIVE hourly cron remains documented as-is.

- **M2** (Topic selection state transition CREATED → SPEAKER_IDENTIFICATION): Updated `phase-a-setup.md` Step 5 "Confirm Selection" from `CREATED → TOPIC_SELECTION` to `CREATED → SPEAKER_IDENTIFICATION`, with an explanatory note that TOPIC_SELECTION is not a mandatory intermediate stop. Updated `README.md` state diagram note and CREATED row exit condition to show both paths (→ TOPIC_SELECTION or → SPEAKER_IDENTIFICATION directly).

- **M3** (Auto-publish speakers: required event state): Changed the "Required Event State" column in `README.md` auto-publishing table (renamed column to "Gate Condition") and the speaker-publish section in `phase-e-publishing.md` to correctly state the gate is `currentPublishedPhase` not yet "speakers", regardless of workflow state.

- **M4** (Auto-publish agenda: required event state): Updated `phase-e-publishing.md` agenda auto-publish section to say the gate is `currentPublishedPhase = "speakers"` and all sessions have timing (removing the AGENDA_FINALIZED requirement). Added that auto-publish transitions the event to AGENDA_PUBLISHED. Updated `README.md` table accordingly.

- **M5** (Non-existent state OUTREACH_INITIATED): Removed `OUTREACH_INITIATED` from `phase-b-outreach.md` Step 8. Replaced with "Event state remains: SPEAKER_IDENTIFICATION".

- **M6** (Speaker INVITED and SLOT_ASSIGNED states undocumented): Added both states to the speaker state reference table in `phase-b-outreach.md` and the "Complete Speaker State List" in `README.md`. Documented INVITED as the state after invitation email dispatch (contacted → invited → ready) and SLOT_ASSIGNED as a system-set state that cannot be manually assigned.

- **M7** (Task lifecycle: pre-creation vs. on-transition creation): Updated `README.md` "Task Auto-Creation" section and `phase-f-communication.md` "Task Lifecycle" section to describe the two-phase model: (1) tasks pre-created as `pending` at event creation; (2) activated to `todo` when the event reaches the trigger state.

- **M8** (README task states list incomplete): Updated `README.md` Task Dashboard from "three statuses" to four user-visible statuses (TODO, IN_PROGRESS, COMPLETED, CANCELLED) plus a note about the internal PENDING status. Added the critical-task threshold note (tasks due within 3 days appear highlighted as critical).

### UNDOCUMENTED additions

- **N1** (WatchSessionService: organizer-driven EVENT_COMPLETED): Added "Live Event Management (Watch Mode)" section to `phase-e-publishing.md` describing the Watch-mode trigger for EVENT_COMPLETED.

- **N2** (Break/lunch/networking excluded from completion check): Documented which session types count toward the "all sessions complete" trigger inside the new Watch Mode section in `phase-e-publishing.md`.

- **N3** (CONFIRMED can still transition to DECLINED): Added a "Late Withdrawals by Confirmed Speakers" note to `phase-d-assignment.md` Phase D Completion section.

- **N4** (Tasks have a `pending` pre-activation state): Documented PENDING as an internal pre-activation status in `phase-f-communication.md` Task Lifecycle section and in `README.md` Task Dashboard section.

- **N5** (INVITED speaker workflow state): Added INVITED to the speaker state flow diagram and Status Definitions table in `phase-b-outreach.md`; documented the contacted → invited → ready lifecycle. Added to `README.md` Complete Speaker State List.

- **N6** (SLOT_ASSIGNED speaker workflow state — system-set): Added SLOT_ASSIGNED to `phase-d-assignment.md` Step 4 (slot assignment step), noting it is system-managed and cannot be set manually. Added to `README.md` Complete Speaker State List and `phase-b-outreach.md` Status Definitions table.

- **N7** (CREATED → SPEAKER_IDENTIFICATION valid direct transition): Documented in `README.md` as a note after the state progression diagram and in the CREATED row exit condition (covered alongside M2).

- **N8** ("Critical task" threshold: due ≤ 3 days): Added to `README.md` Task Dashboard statuses and `phase-f-communication.md` Task States section.

- **N9** (Idempotent speaker workflow transitions): Added idempotent-transition note to `phase-b-outreach.md` Status Management Tips and `phase-c-quality.md` Review Best Practices.

- **N10** (Rejection feedback stored in ContentSubmission record): Updated `phase-c-quality.md` "Request Revision / Reject" to note that feedback is versioned per ContentSubmission (`reviewerFeedback`, `reviewedBy`, `reviewedAt`) in addition to appearing in speaker contact history.

## Skipped — needs manual decision

- **U1**: "Title: 100 characters max; Abstract: 1000 characters max; Learning Objectives: 3–5 items required" (`phase-b-outreach.md`) — no test directly validates max-length enforcement on these fields among the searched files. `ContentSubmissionSchemaValidationTest` may cover this but was not examined. **Recommend**: confirm test coverage before accepting doc claims as validated.

- **U2**: "Magic link to speaker portal (30-day validity)" (`phase-c-quality.md`) — `MagicLinkServiceTest` exists but was not examined. **Recommend**: verify the 30-day expiry is explicitly asserted in that test.

- **U3**: "Auto-creates 'Newsletter: Speakers' task (if not exists)" during speaker auto-publish (`phase-e-publishing.md`) — `PublishingScheduledServiceIntegrationTest` does not assert task creation side-effects. Doc claim retained; no test to validate or contradict it.

- **U4**: "Auto-creates 'Newsletter: Final' task (if not exists)" during agenda auto-publish (`phase-e-publishing.md`) — same gap as U3.

- **U5**: "Cron job runs hourly. Checks all events in AGENDA_FINALIZED state. Transitions to EVENT_LIVE." (`README.md`, `phase-e-publishing.md`) — no scheduled-service test was found for this AGENDA_FINALIZED → EVENT_LIVE transition. Doc claim retained as-is. **Recommend**: add an integration test for this cron or confirm it exists in production.
