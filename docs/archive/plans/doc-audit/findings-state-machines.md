# Doc Audit Findings — Event state machine & lifecycle
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06a-workflow-state-machines.md`
**Tests searched:** `services/event-management-service/src/test/java`

## Summary
- VALIDATED: 13
- MISMATCH: 11
- UNTESTED: 8
- UNDOCUMENTED: 13

---

## MISMATCH

### M1 — `AGENDA_FINALIZED` state removed from transition rules but still documented
**Doc claims:** 9-state machine: `CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED`, with a distinct row in the state table and a guard `validateEventDateInFuture(event, 14)`.
**Test asserts:** `WorkflowTransitionValidatorTest#should_allowTransition_when_validStateTransition` — CsvSource entry `"AGENDA_PUBLISHED, EVENT_LIVE"` with inline comment `// Direct scheduler transition (AGENDA_FINALIZED removed)`. AGENDA_FINALIZED appears nowhere in the parameterised valid or invalid transition lists.
**Action:** Remove the AGENDA_FINALIZED row from the state table and diagram; update the flow to 8 states. Move the "14 days before event" guard note to a historical footnote or delete it.

### M2 — State machine signature uses `eventCode`, not `eventId`
**Doc claims:** `public Event transitionToState(String eventId, ...)` with `eventRepository.findById(eventId)` and `event.setLastUpdatedBy(organizerId)`.
**Test asserts:** `EventWorkflowStateMachineTest#should_transitionSuccessfully_when_validStateTransition_attempted` and `EventWorkflowStateMachineIntegrationTest#should_persistWorkflowState_when_transitionCompletes` both call `stateMachine.transitionToState(eventCode, ...)` and assert `result.getUpdatedBy()`.
**Action:** Update the code snippet: first param is `String eventCode`, lookup uses `findByEventCode(eventCode)`, and field setter is `event.setUpdatedBy()`.

### M3 — Organizer parameter is `organizerUsername`, not `organizerId`
**Doc claims:** parameter name `organizerId` throughout `EventWorkflowStateMachine` and `EventTaskService`.
**Test asserts:** `EventWorkflowStateMachineTest` and all integration tests pass a variable named `organizerUsername`; assertion is `assertThat(result.getUpdatedBy()).isEqualTo(organizerUsername)`.
**Action:** Rename `organizerId` → `organizerUsername` in all doc code snippets.

### M4 — SLOT_ASSIGNMENT validation error message mismatch
**Doc claims:** `validateAllSlotsHaveSpeakers` throws `"Not all slots have confirmed speakers"`.
**Test asserts:** `EventWorkflowStateMachineTest#should_throwValidationException_when_thresholdNotMet_forSlotAssignment` — `.hasMessageContaining("Minimum threshold not met")`.
**Action:** Update doc error message to `"Minimum threshold not met"`.

### M5 — Task creation status is `"pending"`, not `"todo"`
**Doc claims:** `EventTaskService.onApplicationEvent()` creates tasks directly with `status("todo")`.
**Test asserts:** `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated` — `assertThat(tasks).allMatch(task -> "pending".equals(task.getStatus()))`. `#should_activatePendingTasks_when_eventReachesTriggerState` — tasks activate from `"pending"` to `"todo"` only when the event reaches the trigger state.
**Action:** Update doc to describe a two-phase lifecycle: tasks are created with `status="pending"` at event creation; they are activated to `status="todo"` when the `EventWorkflowTransitionEvent` fires for the matching trigger state.

### M6 — Default task template names in doc differ from tested names
**Doc claims:** Names: `"Newsletter: Topic"`, `"Newsletter: Speakers"`, `"Newsletter: Final"`, `"Partner Meeting"`.
**Test asserts:** `TaskTemplateServiceIntegrationTest#should_listDefaultTemplates_when_requested` — exact names expected: `"Newsletter: Topic Announcement"`, `"Newsletter: Speaker Lineup"`, `"Newsletter: Final Agenda"`, `"Partner Meeting Coordination"`.
**Action:** Update doc template name list to match the exact values seeded by the V22 Flyway migration.

### M7 — `submitContentForReview` sets `QUALITY_REVIEWED` immediately on submission (should be on approval)
**Doc claims:** `QualityReviewService.submitContentForReview()` calls `speakerWorkflowService.updateSpeakerWorkflowState(..., SpeakerWorkflowState.QUALITY_REVIEWED, ...)` — i.e., the speaker is marked `quality_reviewed` the moment content is submitted.
**Test asserts:** `QualityReviewServiceIntegrationTest#should_updateToQualityReviewed_when_contentApproved` — `approveContent()` transitions the speaker to `QUALITY_REVIEWED`; submission itself moves to `CONTENT_SUBMITTED`.
**Action:** Correct the `submitContentForReview` snippet: it should transition the speaker to `CONTENT_SUBMITTED`; the moderator's `approveContent()` call is what sets `QUALITY_REVIEWED`.

### M8 — `updateReviewStatus` moves approved speaker to non-existent `FINAL_AGENDA` state
**Doc claims:** When review status is `APPROVED`, `speakerWorkflowService.updateSpeakerWorkflowState(..., SpeakerWorkflowState.FINAL_AGENDA, ...)`.
**Test asserts:** `FINAL_AGENDA` does not appear in any test or in the speaker state enum. `QualityReviewServiceIntegrationTest#should_updateToQualityReviewed_when_contentApproved` confirms approval → `QUALITY_REVIEWED`.
**Action:** Replace `FINAL_AGENDA` with `QUALITY_REVIEWED` in the `updateReviewStatus` snippet; remove all references to `FINAL_AGENDA` from the doc.

### M9 — `DomainEventPublisher` method is `publish()`, not `publishEvent()`
**Doc claims:** `eventPublisher.publishEvent(transitionEvent)` in both `EventWorkflowStateMachine` and `SpeakerWorkflowService` snippets.
**Test asserts:** `EventWorkflowStateMachineTest#should_publishDomainEvent_when_successfulStateTransition_occurs` — `verify(eventPublisher).publish(eventCaptor.capture())`.
**Action:** Replace `publishEvent(...)` with `publish(...)` in all code snippets in the doc.

### M10 — Catering task trigger references removed state `AGENDA_FINALIZED`
**Doc claims:** "Catering: Triggered at AGENDA_FINALIZED, due 30 days before event" (task template #7).
**Test asserts:** `WorkflowTransitionValidatorTest` explicitly comments `AGENDA_FINALIZED removed`; the 8-state model has no `AGENDA_FINALIZED`.
**Action:** Update the Catering task trigger state. Based on the current state machine, identify the correct trigger state (likely `AGENDA_PUBLISHED`) or remove the task from defaults.

### M11 — 14-day archive boundary is exclusive (`> 14 days`), not `>= 14 days`
**Doc claims:** "14 days after event date (auto)" — ambiguous whether boundary day is included.
**Test asserts:** `EventWorkflowScheduledServiceIntegrationTest#should_notArchiveCompletedEvent_when_exactlyAt14DayBoundary` — event exactly 14 days ago stays in `EVENT_COMPLETED` with comment `"window: date < today - 14 days"` (strict less-than, boundary exclusive).
**Action:** Clarify to: "auto-archived when event date is **more than** 14 days in the past (exclusive boundary: exactly 14 days does not qualify)."

---

## UNTESTED

### U1 — `getCurrentEvent()` 14-day fallback returns EVENT_COMPLETED event
**Doc claims:** "Phase 2 falls back to the most recent EVENT_COMPLETED event within the 14-day window. A 404 is NOT returned — the event is still surfaced to the public."
**Test found:** none (no `getCurrentEvent` / public event endpoint test covering this specific fallback)
**Risk:** high — this is a user-visible contract; if the window logic is wrong, the public homepage breaks silently
**Action:** Write an integration test that creates an EVENT_COMPLETED event 7 days ago, calls `GET /api/v1/events/current`, and asserts 200 with that event; add a second test 15 days ago and assert 404.

### U2 — Auto-archive scheduler cron time `02:00 Bern time`
**Doc claims:** "`processEventsToArchive()` runs at **02:00 Bern time** via a daily ShedLock-guarded scheduler."
**Test found:** none — `EventWorkflowScheduledServiceIntegrationTest` uses a no-op `LockProvider` and calls the method directly with no cron validation.
**Risk:** low — the logic is tested; only the schedule time is untested
**Action:** Add a test or annotation assertion that the `@Scheduled` cron expression corresponds to 02:00 Europe/Zurich; or accept as low-risk and note in doc it is not tested.

### U3 — ShedLock prevents duplicate execution across ECS tasks
**Doc claims:** "The scheduler ... is guarded by ShedLock to prevent duplicate execution across ECS tasks."
**Test found:** The integration test explicitly replaces `LockProvider` with a no-op, bypassing ShedLock.
**Risk:** medium — ShedLock misconfiguration could double-archive; no test catches this
**Action:** Add a test that configures a real `JdbcLockProvider` and verifies a second concurrent call is skipped.

### U4 — Speaker WITHDREW triggers overflow promotion
**Doc claims:** `case "withdrew": handleSpeakerWithdrawal(speaker)` — promotes from overflow.
**Test found:** none
**Risk:** medium — withdrawal without promotion leaves a slot unfilled
**Action:** Write a test covering the withdrawal → overflow-promotion path.

### U5 — Speaker ACCEPTED overflow detection (`SpeakerOverflowDetectedEvent`)
**Doc claims:** `checkForOverflow(speaker.getEventId())` publishes `SpeakerOverflowDetectedEvent` when `acceptedSpeakers > maxSlots`.
**Test found:** none
**Risk:** medium — overflow detection is a core business rule that could silently break
**Action:** Write a test that accepts more speakers than slots and asserts the overflow event is published.

### U6 — Homepage archive-style UI signal (`workflowState === 'EVENT_COMPLETED'`)
**Doc claims:** "The archive-style homepage UI is determined in the frontend by checking `event.workflowState === 'EVENT_COMPLETED'`."
**Test found:** none in the backend test directory (frontend E2E concern)
**Risk:** low — frontend concern; backend correctly exposes the field
**Action:** Add a Playwright E2E test that navigates to the homepage for a recently-completed event and asserts the registration form is absent.

### U7 — CONTACTED state side effect: `notificationService.recordOutreach()`
**Doc claims:** `case "contacted": notificationService.recordOutreach(speaker)`.
**Test found:** none
**Risk:** low — operational logging concern
**Action:** Write a unit test for the `contacted` transition verifying `recordOutreach` is called.

### U8 — `content_submitted` notifies moderators: `notifyModeratorsOfPendingReview()`
**Doc claims:** `case "content_submitted": notificationService.notifyModeratorsOfPendingReview(speaker)`.
**Test found:** none in `SpeakerWorkflowServiceTest`
**Risk:** low — notification correctness
**Action:** Add a unit test verifying moderator notification fires on `content_submitted` transition.

---

## UNDOCUMENTED

### N1 — `CREATED → SPEAKER_IDENTIFICATION` skip transition is valid
**Test:** `WorkflowTransitionValidatorTest#should_allowSkippingTopicSelection_when_speakerAddedDirectly` and parameterised entry `"CREATED, SPEAKER_IDENTIFICATION"` — explicitly allowed with comment "Can skip TOPIC_SELECTION if speaker added directly".
**Missing from doc:** The state diagram only shows `CREATED → TOPIC_SELECTION`; the skip path is never mentioned.
**Action:** Add a footnote to the state diagram noting: "CREATED may transition directly to SPEAKER_IDENTIFICATION if a speaker is added to the pool before a topic is selected."

### N2 — Task `"pending"` → `"todo"` activation lifecycle
**Test:** `EventTaskServiceIntegrationTest#should_activatePendingTasks_when_eventReachesTriggerState` — tasks created in `"pending"` state; become `"todo"` on `EventWorkflowTransitionEvent` for the matching trigger state.
**Missing from doc:** The doc's `EventTaskService` snippet shows tasks created directly as `"todo"`; the `"pending"` pre-activation state is absent from both the code snippet and the data model description.
**Action:** Add "pending" to the `event_tasks.status` value set in the Data Model section, and describe the two-phase lifecycle.

### N3 — `SLOT_ASSIGNED` is a rejected pseudo-state (not a valid speaker state)
**Test:** `SpeakerWorkflowServiceTest#shouldRejectSlotAssignedStateTransition` — attempting to transition to `SLOT_ASSIGNED` throws `IllegalStateException("Invalid state transition")`.
**Missing from doc:** The doc states slot assignment is not a speaker state (correct) but doesn't mention that `SLOT_ASSIGNED` exists as an enum value that will be actively rejected if used.
**Action:** Add a note: "The enum value `SLOT_ASSIGNED` exists but is rejected by the workflow service — it was an early design artefact."

### N4 — `CONFIRMED` is a terminal speaker state
**Test:** `SpeakerWorkflowServiceTest#shouldRejectTransitionFromConfirmed` — any further transition from `CONFIRMED` throws `IllegalStateException`.
**Missing from doc:** The state table does not mark `CONFIRMED` as terminal.
**Action:** Add a "Terminal" marker to the `CONFIRMED` row in the Speaker State Definitions table.

### N5 — Content rejection sets `contentStatus = "REVISION_NEEDED"`
**Test:** `QualityReviewServiceIntegrationTest#should_setContentStatusToRevisionNeeded_when_contentRejected`.
**Missing from doc:** No mention of the `contentStatus` field on `speaker_pool` or the `"REVISION_NEEDED"` value anywhere in the doc.
**Action:** Add `contentStatus` to the speaker_pool data model description; note values: `null`, `"SUBMITTED"`, `"REVISION_NEEDED"`, `"APPROVED"`.

### N6 — Content rejection requires non-null feedback
**Test:** `QualityReviewServiceIntegrationTest#should_throwException_when_rejectingWithoutFeedback` — `rejectContent(..., null, ...)` throws `IllegalArgumentException("Feedback is required when rejecting content")`.
**Missing from doc:** Quality Review section mentions `REQUIRES_CHANGES` but doesn't mention validation that feedback is mandatory.
**Action:** Add a line to the Quality Review section: "Rejection requires non-empty feedback; a null/blank value is rejected with a 400."

### N7 — Rejection feedback stored in `ContentSubmission.reviewerFeedback`
**Test:** `QualityReviewServiceIntegrationTest#should_storeReviewerFeedbackInContentSubmission_when_contentRejected` — `contentSubmissionRepository` record gets `reviewerFeedback`, `reviewedBy`, and `reviewedAt` populated.
**Missing from doc:** The `ContentSubmission` table and its review fields are not described in the doc.
**Action:** Add a `content_submissions` table to the Quality Review section with fields: `reviewer_feedback`, `reviewed_by`, `reviewed_at`, `submission_version`.

### N8 — `reassignTask()` allows changing task assignee
**Test:** `EventTaskServiceIntegrationTest#should_allowReassignment_when_taskAssigneeIsUpdated`.
**Missing from doc:** Doc describes task creation and completion but not reassignment.
**Action:** Add `reassignTask(taskId, newOrganizerUsername)` to the Task Management section.

### N9 — Critical tasks API: overdue + due within 3 days
**Test:** `EventTaskServiceIntegrationTest#should_returnOverdueAndDueSoonTasks_when_gettingCriticalTasks` — `getCriticalTasksForOrganizer()` returns only overdue tasks and tasks due within 3 days.
**Missing from doc:** Task Dashboard section describes status-based grouping only; no mention of a "critical" filter or the 3-day threshold.
**Action:** Add to the Task Dashboard section: "A `getCriticalTasksForOrganizer()` query returns tasks that are overdue or due within the next 3 days."

### N10 — Archival triggers cleanup: open task cancellation + waitlist cancellation + notification dismissal
**Test:** `EventArchivalCleanupServiceTest` and `EventArchivalCleanupAc5IntegrationTest` — `EventArchivalCleanupService.cleanup(eventId, eventCode)` (1) bulk-cancels open tasks, (2) cancels waitlist registrations, (3) dismisses notifications — steps 2 and 3 are best-effort (failures do not propagate).
**Missing from doc:** Archival section does not describe any cleanup activity triggered when an event is archived.
**Action:** Add an "Archival Cleanup" subsection: on transition to ARCHIVED, the cleanup service runs; task cancellation is mandatory; waitlist cancellation and notification dismissal are best-effort.

### N11 — Archival cleanup is idempotent
**Test:** `EventArchivalCleanupServiceTest#cleanup_calledTwice_noExceptions`.
**Missing from doc:** Idempotency of the cleanup not mentioned.
**Action:** Note in the Archival Cleanup subsection: "The cleanup is idempotent and safe to call multiple times."

### N12 — Task creation is idempotent (no duplicate tasks per template per event)
**Test:** `EventTaskServiceIntegrationTest#should_preventDuplicateTasks_when_eventTransitionReplayedMultipleTimes`.
**Missing from doc:** The `createTaskFromTemplate` snippet has no guard; idempotency is an undocumented contract.
**Action:** Add a note: "Task creation is idempotent — calling `createTasksForEvent` twice for the same template/event pair does not create duplicate tasks."

### N13 — Default task templates cannot be modified or deleted
**Test:** `TaskTemplateServiceIntegrationTest#should_throwException_when_attemptingToUpdateDefaultTemplate` and `#should_throwException_when_attemptingToDeleteDefaultTemplate` — both throw `IllegalStateException("Cannot modify/delete default template")`.
**Missing from doc:** Custom Tasks section implies all templates are editable; no mention of the immutability guard on default templates.
**Action:** Add: "Default templates (seeded by migration) are immutable — update and delete operations on them throw a 400/IllegalStateException."

---

## VALIDATED (list only — no detail needed)

- Event 9-state sequence broadly correct (`CREATED → … → ARCHIVED`) — caveat see M1 re AGENDA_FINALIZED
- `ARCHIVED` is a terminal state (`WorkflowTransitionValidatorTest#should_rejectTransition_when_fromArchivedState`)
- Backwards transitions rejected (`WorkflowTransitionValidatorTest#should_rejectTransition_when_backwardsTransition`)
- Invalid skip transitions rejected — e.g. `CREATED → ARCHIVED` (`WorkflowTransitionValidatorTest#should_rejectTransition_when_invalidNonSequentialTransition`)
- Idempotent transition (same → same state) allowed (`WorkflowTransitionValidatorTest#should_allowTransition_when_idempotentTransition`)
- Domain event `EventWorkflowTransitionEvent` published on every successful transition, with `eventCode`, `fromState`, `toState`, `organizerUsername`, `transitionedAt`, `context` (`EventWorkflowStateMachineTest#should_publishDomainEvent_when_successfulStateTransition_occurs`)
- Transaction rolled back on validation failure (`EventWorkflowStateMachineIntegrationTest#should_rollbackTransaction_when_validationFails`)
- Speaker auto-confirmation: QUALITY_REVIEWED + slot assigned → CONFIRMED, order-independent (both `SpeakerWorkflowServiceTest` Flow 1 and Flow 2, and `QualityReviewServiceIntegrationTest#should_updateToConfirmed_when_approvedAndSlotAlreadyAssigned`)
- `session_users.is_confirmed` updated to `true` on auto-confirmation (`QualityReviewServiceIntegrationTest#should_updateToConfirmed_when_slotAssignedAfterQualityReview`)
- QUALITY_REVIEWED without slot stays at QUALITY_REVIEWED, not CONFIRMED (`SpeakerWorkflowServiceTest#shouldNotAutoConfirmWithoutSlot`)
- Missing session handled gracefully — no auto-confirm, no crash (`SpeakerWorkflowServiceTest#shouldHandleMissingSessionGracefully`)
- Archive scheduler correctly ignores events < 14 days old (`EventWorkflowScheduledServiceIntegrationTest#should_notArchiveCompletedEvent_when_within14Days`)
- Archive scheduler archives events > 14 days old (`EventWorkflowScheduledServiceIntegrationTest#should_archiveCompletedEvent_when_olderThan14Days`)
- 7 default task templates (exact count) (`TaskTemplateServiceIntegrationTest#should_listDefaultTemplates_when_requested`)
