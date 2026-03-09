# Fix Summary — Event state machine & lifecycle
**Fixed:** 2026-03-09
**Doc:** `docs/architecture/06a-workflow-state-machines.md`

## Changes made

### MISMATCH fixes

- **M1:** Removed `AGENDA_FINALIZED` from the state diagram, state table, and switch-case block. Updated heading from "9 States" to "8 States" and overview bullet. Added a historical-note blockquote explaining it was removed during implementation. Moved the `AGENDA_PUBLISHED → EVENT_LIVE` transition to be direct (no intermediate finalization step).

- **M2:** Updated `EventWorkflowStateMachine.transitionToState` signature: first parameter renamed `eventId` → `eventCode`; lookup changed from `findById(eventId)` to `findByEventCode(eventCode)`; log statements updated to use `eventCode`.

- **M3:** Renamed parameter `organizerId` → `organizerUsername` throughout the `EventWorkflowStateMachine` code snippet. Changed `event.setLastUpdatedBy(organizerId)` to `event.setUpdatedBy(organizerUsername)`. Updated `EventWorkflowTransitionEvent` constructor args accordingly.

- **M4:** Updated `validateAllSlotsHaveSpeakers` exception message from `"Not all slots have confirmed speakers"` to `"Minimum threshold not met"` to match test assertion.

- **M5:** Changed initial task status in `createTaskFromTemplate` code snippet from `"todo"` to `"pending"`. Updated the `event_tasks` data model entry to include `pending` in the status value set. Added "Two-phase task lifecycle" explanation: tasks are created as `pending` at event creation; activated to `todo` when `EventWorkflowTransitionEvent` fires for the matching trigger state.

- **M6:** Corrected all four mismatched default template names to match the V22 Flyway migration seed values:
  - `"Newsletter: Topic"` → `"Newsletter: Topic Announcement"`
  - `"Newsletter: Speakers"` → `"Newsletter: Speaker Lineup"`
  - `"Newsletter: Final"` → `"Newsletter: Final Agenda"`
  - `"Partner Meeting"` → `"Partner Meeting Coordination"`
  Trigger state for templates 6 and 7 updated from `AGENDA_FINALIZED` → `AGENDA_PUBLISHED` (M10).

- **M7:** Fixed `submitContentForReview` snippet: speaker workflow state on submission is now `CONTENT_SUBMITTED` (was incorrectly `QUALITY_REVIEWED`). Added clarifying comment noting the moderator's `approveContent()` is what sets `QUALITY_REVIEWED`.

- **M8:** Fixed `updateReviewStatus` APPROVED branch: replaced `SpeakerWorkflowState.FINAL_AGENDA` with `SpeakerWorkflowState.QUALITY_REVIEWED`. Added comment explaining the subsequent auto-confirmation path. Removed all references to the non-existent `FINAL_AGENDA` state.

- **M9:** Changed `eventPublisher.publishEvent(...)` to `eventPublisher.publish(...)` in both the `EventWorkflowStateMachine` and `SpeakerWorkflowService` code snippets.

- **M10:** Updated default template trigger states for "Newsletter: Final Agenda" and "Catering" from `AGENDA_FINALIZED` → `AGENDA_PUBLISHED` (consistent with M1 removal of `AGENDA_FINALIZED`).

- **M11:** Clarified the 14-day archive boundary in the `ARCHIVED` state table row: "Auto-archived when event date is **more than** 14 days in the past (exclusive boundary: exactly 14 days does not qualify)."

### UNDOCUMENTED additions

- **N1:** Added blockquote to state diagram section: `CREATED` may transition directly to `SPEAKER_IDENTIFICATION` (skipping `TOPIC_SELECTION`) if a speaker is added before topic selection. Explicitly allowed by the transition validator.

- **N2:** Added `pending` to the `event_tasks.status` value set in the Data Model section. Added "Two-phase task lifecycle" paragraph (combined with M5 fix).

- **N3:** Added note below the Speaker State Diagram: the `SLOT_ASSIGNED` enum value exists but is rejected by the workflow service as an early design artefact — transitions to it throw `IllegalStateException`.

- **N4:** Marked `confirmed` as **Terminal state** in the Speaker State Definitions table with explanation that further transitions throw `IllegalStateException`.

- **N5:** Added `contentStatus` field description to the `speaker_pool` data model entry. Values: `null`, `"SUBMITTED"`, `"REVISION_NEEDED"`, `"APPROVED"`.

- **N6:** Added "Quality Review — Constraints" subsection: rejection requires non-empty feedback; `null`/blank throws `IllegalArgumentException` (HTTP 400).

- **N7:** Added `content_submissions` table description to the Quality Review Constraints subsection: `reviewer_feedback`, `reviewed_by`, `reviewed_at`, `submission_version`.

- **N8:** Added `reassignTask(taskId, newOrganizerUsername)` description to the Task Dashboard section.

- **N9:** Added `getCriticalTasksForOrganizer()` description: returns tasks overdue or due within 3 days.

- **N10:** Added "Archival Cleanup" section: on transition to ARCHIVED, `EventArchivalCleanupService.cleanup()` runs; task cancellation is mandatory; waitlist cancellation and notification dismissal are best-effort.

- **N11:** Added idempotency note to the Archival Cleanup section.

- **N12:** Added task creation idempotency note to the Task Dashboard section.

- **N13:** Added default template immutability note to the Task Types section (update/delete throws `IllegalStateException`/HTTP 400).

## Skipped — needs manual decision (UNTESTED)

- **U1:** "`getCurrentEvent()` 14-day fallback returns EVENT_COMPLETED event" — no integration test covers this specific public-endpoint fallback. Risk: high. Recommend writing a test: create an EVENT_COMPLETED event 7 days ago, assert `GET /api/v1/events/current` returns 200; repeat 15 days ago and assert 404.

- **U2:** "Auto-archive scheduler cron time `02:00 Bern time`" — no test validates the `@Scheduled` cron expression corresponds to 02:00 Europe/Zurich. Risk: low (logic is tested; only schedule time is unverified). Accept or add an annotation-assertion test.

- **U3:** "ShedLock prevents duplicate execution across ECS tasks" — integration test bypasses ShedLock with a no-op provider. Risk: medium. Recommend adding a test with a real `JdbcLockProvider` verifying a concurrent second call is skipped.

- **U4:** "Speaker WITHDREW triggers overflow promotion" — no test covers the withdrawal → overflow-promotion path. Risk: medium.

- **U5:** "Speaker ACCEPTED overflow detection (`SpeakerOverflowDetectedEvent`)" — no test verifies the overflow event is published when accepted > maxSlots. Risk: medium.

- **U6:** "Homepage archive-style UI (`workflowState === 'EVENT_COMPLETED'`)" — frontend E2E concern, no Playwright test. Risk: low.

- **U7:** "CONTACTED state side effect: `notificationService.recordOutreach()`" — no unit test verifying the call. Risk: low.

- **U8:** "`content_submitted` notifies moderators" — no unit test for `notifyModeratorsOfPendingReview` on `content_submitted` transition. Risk: low.
