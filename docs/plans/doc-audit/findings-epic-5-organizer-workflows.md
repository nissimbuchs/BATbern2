# Doc Audit Findings — Organizer Workflows & Lifecycle Automation (Epic 5)

**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-5-enhanced-organizer-workflows.md`
**Tests searched:** `services/event-management-service/src/test/java`

## Summary

- VALIDATED: 22
- MISMATCH: 6
- UNTESTED: 5
- UNDOCUMENTED: 7

---

## MISMATCH

### M1 — Doc describes 9-state machine; implementation uses 8 states (AGENDA_FINALIZED removed)

**Doc claims:** (lines 73–79)
```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```
The doc explicitly names this the "9 States" event workflow and lists `AGENDA_FINALIZED` as a distinct state between `AGENDA_PUBLISHED` and `EVENT_LIVE`.

**Test asserts:** `WorkflowTransitionValidatorTest#should_allowTransition_when_validSequentialTransition` — the valid transitions list is:
```
CREATED → TOPIC_SELECTION or SPEAKER_IDENTIFICATION
TOPIC_SELECTION → SPEAKER_IDENTIFICATION
SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
SLOT_ASSIGNMENT → AGENDA_PUBLISHED
AGENDA_PUBLISHED → EVENT_LIVE   // comment: "Direct scheduler transition (AGENDA_FINALIZED removed)"
EVENT_LIVE → EVENT_COMPLETED
EVENT_COMPLETED → ARCHIVED
```
The test comment is explicit: "AGENDA_FINALIZED removed." The `EventWorkflowStateMachineIntegrationTest` workflow sequence array (line 119–133) also omits `AGENDA_FINALIZED` entirely.

**Action:** Update doc state machine diagram and all references to `AGENDA_FINALIZED` (Stories 5.8, 5.11) to reflect the 8-state model. Change section heading "9 States" to "8 States". Remove `AGENDA_FINALIZED → EVENT_LIVE` transition rule and replace with `AGENDA_PUBLISHED → EVENT_LIVE` (auto-triggered by scheduler).

---

### M2 — Doc describes `AGENDA_FINALIZED → EVENT_LIVE` as "Automatic (event day)"; test shows `AGENDA_PUBLISHED → EVENT_LIVE` direct transition

**Doc claims:** (line 88)
> `AGENDA_FINALIZED → EVENT_LIVE`: Automatic (event day)

**Test asserts:** `WorkflowTransitionValidatorTest` — `"AGENDA_PUBLISHED, EVENT_LIVE"` is a valid transition with comment "Direct scheduler transition (AGENDA_FINALIZED removed)". There is no `AGENDA_FINALIZED` state in the validated transition matrix at all.

**Action:** Update line 88 of the doc to read:
> `AGENDA_PUBLISHED → EVENT_LIVE`: Automatic (on event day, via scheduler)

---

### M3 — Doc describes speaker `slot_assigned` as a workflow state; implementation rejects it as invalid

**Doc claims:** (lines 112–118, Speaker Workflow diagram and key states list)
```
slot_assigned: Assigned to time slot
confirmed: Both quality_reviewed AND slot_assigned (order doesn't matter)
```
The doc states `slot_assigned` is a speaker workflow state stored in `speaker_pool.status`, and that `SLOT_ASSIGNED` transitions are driven by `speakerWorkflowService.updateSpeakerWorkflowState(..., SpeakerWorkflowState.SLOT_ASSIGNED, ...)`.

**Test asserts:** `SpeakerWorkflowServiceTest#shouldRejectSlotAssignedStateTransition` — explicitly tests that attempting to transition to `SLOT_ASSIGNED` throws `IllegalStateException: Invalid state transition`. The test comment reads: "it's not a valid state in linear model." The auto-confirmation mechanism instead checks whether `session.startTime` is set (i.e., slot assignment is orthogonal — it sets `session.startTime`, not a speaker pool status).

**Action:** Update the Speaker Workflow diagram and the "Key States" section to remove `slot_assigned` as a state. Replace the confirmation description with: "Auto-confirmed when `QUALITY_REVIEWED` AND session.startTime is set (slot assigned externally — does not change speaker pool status)."

---

### M4 — Doc claims `EVENT_LIVE → EVENT_COMPLETED` is "Manual (after event)"; tests show it is automatic

**Doc claims:** (line 89)
> `EVENT_LIVE → EVENT_COMPLETED`: Manual (after event)

**Test asserts:** `WatchEventCompletionIntegrationTest#should_transitionEventToCompleted_when_allCompleteableSessionsEnded` — `EVENT_COMPLETED` is triggered automatically by `WatchSessionService.endSession()` once all completeable sessions (keynote, presentation, workshop, panel_discussion) are marked done. The transition is automatic, not manually triggered by an organizer.

**Action:** Change line 89 to:
> `EVENT_LIVE → EVENT_COMPLETED`: Automatic — triggered when all completeable sessions (keynote, presentation, workshop, panel_discussion) are ended via the Watch feature. Break/lunch/networking sessions are excluded from this check.

---

### M5 — Doc claims Speaker workflow initial status is `"OPEN"`; tests enforce `IDENTIFIED`

**Doc claims:** (line 113, Story 5.2 AC13)
> `identified`: Added to speaker pool
> Speaker Status: Initial status = "OPEN" (not yet contacted)

The doc uses both terms inconsistently. In the redesigned workflow section the doc says initial state is `identified`, but Story 5.2 AC13 says `"OPEN"`.

**Test asserts:** `SpeakerPoolWorkflowIntegrationTest#should_setStatusToIdentified_when_speakerAddedToPool` — the API response is asserted to contain `"status": "IDENTIFIED"` (not "OPEN"). `SpeakerWorkflowServiceIntegrationTest` also uses `SpeakerWorkflowState.IDENTIFIED` as the starting state throughout.

**Action:** Remove all references to `OPEN` as an initial speaker status. The initial state is `IDENTIFIED`. The status `OPEN` does not exist in the implementation.

---

### M6 — Doc describes auto-publish speakers as "1 month" before event; tests enforce exactly 30 days

**Doc claims:** (lines 863, 870, 889, Story 5.10 AC2, AC6)
> Phase 2 - Speakers: Publish speaker lineup **1 month before event** (auto-triggered)
> Auto-Publish Scheduling: Automatically publish Phase 2 at **1 month before**

**Test asserts:** `PublishingScheduledServiceIntegrationTest#should_autoPublishSpeakers_when_eventIsThirtyDaysAway` — uses `LocalDate.now().plusDays(30)` with no tolerance for calendar-month variation. The test name explicitly states "ThirtyDaysAway". Similarly `PublishingEngineControllerIntegrationTest#should_configureAutoPublish_when_scheduleSet` asserts `"phase2DaysBeforeEvent": 30`.

**Action:** Replace "1 month before" with "30 days before" throughout the doc for the speaker auto-publish trigger.

---

## UNTESTED

### U1 — Speaker initial status `INVITED` present in validator but not in speaker pool workflow

**Doc claims:** (line 113) `identified`: Added to speaker pool. No mention of `INVITED` as a distinct state from `IDENTIFIED` for pool speakers.

**Risk:** Medium. `StatusTransitionValidatorTest` includes `INVITED` in the list of states from which DECLINED is reachable (`names = {"IDENTIFIED", "INVITED", "CONTACTED", ...}`). The `SpeakerInvitationServiceTest` and `SpeakerPortalResponseControllerIntegrationTest` reference `INVITED` state for speaker portal flows. The doc does not explain when `INVITED` is used vs `IDENTIFIED`, nor is there a test for the `IDENTIFIED → INVITED` transition in the organizer workflow path.

---

### U2 — Story 5.7 threshold validation: minimum speaker counts per event type not tested end-to-end

**Doc claims:** (Story 5.7 AC1)
> Minimum Speaker Count: Based on event type (full-day: 6, afternoon: 6, evening: 3)

**Risk:** Medium. `EventWorkflowControllerIntegrationTest#should_return422_when_validationFails_forTransition` only checks that *at least 1 ACCEPTED* speaker is required for `SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT`. No test verifies the per-event-type minimum counts (6/6/3). The threshold check used in the test requires at least 1 speaker, which is weaker than the documented rule.

---

### U3 — `WITHDREW → ACCEPTED` (re-acceptance) not mentioned in doc

**Doc claims:** Speaker workflow defines `withdrew: Speaker drops out after accepting` (line 119). The doc does not describe any path back from `withdrew`.

**Risk:** Low. `SpeakerWorkflowServiceIntegrationTest#should_allowReAcceptanceFromWithdrew` validates that `WITHDREW → ACCEPTED` is a valid transition. This is an undocumented recovery path.

---

### U4 — Story 5.14 catering task due date is "1 month before"; no scheduler test verifying this

**Doc claims:** (Story 5.14 AC4)
> Task due date = event date minus 1 month

**Risk:** Low. `EventTaskServiceIntegrationTest` tests the `relative_to_event` due-date calculation with custom offsets but does not include a test specifically for the catering template's 30-day offset. The default templates are seeded in the DB migration; their due dates are not asserted in tests.

---

### U5 — `AGENDA_PUBLISHED → AGENDA_FINALIZED` manual transition (Story 5.11) has no test

**Doc claims:** (line 87, Story 5.11)
> `AGENDA_PUBLISHED → AGENDA_FINALIZED`: Manual (2 weeks before event)

**Risk:** High — this transition was removed from the implementation (see M1/M2), but if it were ever re-introduced, there is no test guarding it. The WorkflowTransitionValidatorTest confirms the path does not exist, making this effectively a documentation-only risk.

---

## UNDOCUMENTED

### N1 — `AGENDA_PUBLISHED → EVENT_LIVE` auto-transition via scheduler on event day

**Test:** `WorkflowTransitionValidatorTest` — comment "Direct scheduler transition (AGENDA_FINALIZED removed)" at the `AGENDA_PUBLISHED, EVENT_LIVE` valid-transition entry. The `EventControllerIntegrationTest` (line 1791–1802) tests that an `EVENT_LIVE` event with today's date is returned as the current event ("on the actual event day the scheduler transitions the event to EVENT_LIVE").

**Action:** Add to the doc under Event Workflow State Transitions: "On the event date, a scheduled job automatically transitions `AGENDA_PUBLISHED → EVENT_LIVE`."

---

### N2 — `EVENT_COMPLETED` is triggered automatically when all completeable sessions end

**Test:** `WatchEventCompletionIntegrationTest` — five test cases covering keynote+presentation completion, partial completion, break/lunch exclusion, networking exclusion, workshop+panel inclusion, and idempotent completion. Session types excluded from the check: `lunch`, `networking`. Types included: `keynote`, `presentation`, `workshop`, `panel_discussion`.

**Action:** Add to the doc under Story BAT-16 (Lifecycle automation): "EVENT_COMPLETED transition is automatic — triggered by `WatchSessionService.endSession()` once all completeable sessions (keynote, presentation, workshop, panel_discussion) are ended. Break, lunch, and networking sessions are excluded from this check. Idempotent: calling endSession on an already-completed event is safe."

---

### N3 — `getCurrentEvent` API has two-phase logic with 14-day post-event window

**Test:** `EventControllerIntegrationTest` — tests at lines 2251–2341 define the full algorithm:
- Phase 1: Return nearest upcoming event where `currentPublishedPhase IS NOT NULL` and date is in the future (including today).
- Phase 2 fallback: If no Phase 1 result, return most recent `EVENT_COMPLETED` event within 14 days of today.
- 404 if EVENT_COMPLETED event is older than 14 days.
- Upcoming event with `currentPublishedPhase = null` does NOT qualify for Phase 1 and must not block the Phase 2 fallback.

**Action:** Add a `GET /api/v1/events/current` section to the Epic 5 (or Epic 4 public website) doc describing this two-phase selection algorithm including the 14-day window and the `currentPublishedPhase != null` requirement.

---

### N4 — Task system has two-stage lifecycle: `pending` → `todo` on trigger state

**Test:** `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated` and `#should_activatePendingTasks_when_eventReachesTriggerState` — tasks are created at event creation with `status="pending"`, then transition to `status="todo"` when the event reaches the task's `triggerState`.

**Action:** Add to Story 5.5 Task System section: "Tasks are created in `pending` status at event creation. They activate to `todo` status when the event transitions to the task's configured `triggerState`. Tasks are idempotent — calling `createTasksForEvent` multiple times for the same event/template combination does not create duplicates."

---

### N5 — Task completion tracks `completedByUsername`, `completedDate`, and notes

**Test:** `EventTaskServiceIntegrationTest#should_recordCompletionDetails_when_taskIsCompleted` — verifies `completedByUsername`, `completedDate` (within 1 minute of now), and `notes` are persisted. `EventTaskControllerIntegrationTest#should_completeTask_when_requestedByOrganizer` verifies the same fields in the API response.

**Action:** Add to Story 5.5 task completion acceptance criteria: "Completion records `completedByUsername` (from security context), `completedDate`, and optional notes."

---

### N6 — Speaker overflow detection counts ACCEPTED + CONFIRMED speakers together

**Test:** `SpeakerWorkflowServiceIntegrationTest#should_countConfirmedInOverflow` — `checkForOverflow()` counts both `ACCEPTED` and `CONFIRMED` speakers against `max_slots`. A mix of 5 ACCEPTED + 5 CONFIRMED = 10 triggers overflow when max_slots = 8.

**Action:** Add to Story 5.8 Overflow Detection: "The overflow check (`checkForOverflow`) counts both `ACCEPTED` and `CONFIRMED` speakers together against the event's maximum slot count."

---

### N7 — `DECLINED` is reachable from ALL non-terminal speaker states, including `CONFIRMED`

**Test:** `StatusTransitionValidatorTest#should_allowTransitionToDeclined_from_everyActiveState` — parameterized test covering `IDENTIFIED, INVITED, CONTACTED, READY, ACCEPTED, SLOT_ASSIGNED, CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED` — all can transition to `DECLINED`. `DECLINED → any` is blocked.

**Action:** Update the Speaker Workflow section to add: "A speaker can transition to `DECLINED` from any non-terminal state (including `CONFIRMED`). Once `DECLINED`, no further transitions are possible."

---

## VALIDATED

- "CREATED → TOPIC_SELECTION valid transition" → `EventWorkflowControllerIntegrationTest#should_return200_when_validWorkflowTransition_requested`
- "CREATED → ARCHIVED is invalid" → `EventWorkflowControllerIntegrationTest#should_return400_when_invalidStateTransition_attempted` (returns 422)
- "backward transitions rejected" → `EventWorkflowControllerIntegrationTest#should_return422_when_invalidBackwardTransition_attempted`
- "SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT requires threshold" → `EventWorkflowControllerIntegrationTest#should_return422_when_validationFails_forTransition`
- "workflow status endpoint returns nextAvailableStates" → `EventWorkflowControllerIntegrationTest#should_return200WithStatus_when_workflowStatusQueried`
- "CREATED can go to TOPIC_SELECTION or SPEAKER_IDENTIFICATION" → `EventWorkflowControllerIntegrationTest#should_return200WithStatus_when_workflowStatusQueried` (hasSize(2), containsInAnyOrder("TOPIC_SELECTION", "SPEAKER_IDENTIFICATION"))
- "state machine idempotent transitions allowed" → `EventWorkflowStateMachineIntegrationTest#should_handleIdempotentTransition_when_transitioningToSameState`
- "invalid transition does not persist state" → `EventWorkflowStateMachineIntegrationTest#should_notPersist_when_invalidTransitionAttempted`
- "IDENTIFIED → CONTACTED valid speaker transition" → `SpeakerWorkflowServiceIntegrationTest#should_transitionIdentifiedToContacted`
- "CONTACTED → READY valid" → `SpeakerWorkflowServiceIntegrationTest#should_transitionContactedToReady`
- "READY → ACCEPTED valid" → `SpeakerWorkflowServiceIntegrationTest#should_transitionReadyToAccepted`
- "ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED linear progression" → `SpeakerWorkflowServiceTest#shouldAllowLinearProgression`
- "confirmed when QUALITY_REVIEWED + slot assigned" → `SpeakerWorkflowServiceIntegrationTest#should_autoConfirmWhenQualityReviewedAndSlotAssigned` (uses session.startTime as slot proxy)
- "DECLINED is terminal from it — no outgoing transitions" → `SpeakerWorkflowServiceIntegrationTest#should_rejectTransitionFromDeclined`
- "CONFIRMED is terminal" → `SpeakerWorkflowServiceIntegrationTest#should_rejectTransitionFromConfirmed` and `SpeakerWorkflowServiceTest#shouldRejectTransitionFromConfirmed`
- "speaker added to pool starts in IDENTIFIED state" → `SpeakerPoolWorkflowIntegrationTest#should_setStatusToIdentified_when_speakerAddedToPool`
- "speaker notes field persisted" → `SpeakerPoolWorkflowIntegrationTest#should_saveNotes_when_speakerNotesProvided`
- "auto-publish speakers at 30 days before event" → `PublishingScheduledServiceIntegrationTest#should_autoPublishSpeakers_when_eventIsThirtyDaysAway`
- "auto-publish skipped when already published" → `PublishingScheduledServiceIntegrationTest#should_notAutoPublishSpeakers_when_alreadyPublished`
- "auto-publish disabled by config flag" → `PublishingScheduledServiceIntegrationTest#should_notAutoPublishSpeakers_when_autoPublishDisabled`
- "auto-publish agenda at 14 days before event requires speakers published + sessions timed" → `PublishingScheduledServiceIntegrationTest#should_autoPublishAgenda_when_eventIsFourteenDaysAwayAndTimingComplete`
- "agenda publish blocked if sessions lack timing" → `PublishingScheduledServiceIntegrationTest#should_notAutoPublishAgenda_when_sessionsLackTiming`
- "task auto-created with pending status; activated to todo on trigger state transition" → `EventTaskServiceIntegrationTest#should_activatePendingTasks_when_eventReachesTriggerState`
- "task idempotency — no duplicates from same template" → `EventTaskServiceIntegrationTest#should_preventDuplicateTasks_when_eventTransitionReplayedMultipleTimes`
- "task completion records notes, completedBy, completedDate" → `EventTaskServiceIntegrationTest#should_recordCompletionDetails_when_taskIsCompleted`
- "ACCEPTED → WITHDREW valid; WITHDREW → ACCEPTED re-acceptance valid" → `SpeakerWorkflowServiceIntegrationTest#should_allowWithdrewFromAccepted` + `#should_allowReAcceptanceFromWithdrew`
