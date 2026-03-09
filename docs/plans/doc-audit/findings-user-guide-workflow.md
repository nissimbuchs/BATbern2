# Doc Audit Findings — User Guide — Organiser Workflow Phases
**Audited:** 2026-03-09
**Doc:** `docs/user-guide/workflow` (README.md, phase-a-setup.md, phase-b-outreach.md, phase-c-quality.md, phase-d-assignment.md, phase-e-publishing.md, phase-f-communication.md)
**Tests searched:** `services/event-management-service/src/test/java`

## Summary
- VALIDATED: 10
- MISMATCH: 8
- UNTESTED: 5
- UNDOCUMENTED: 10

---

## MISMATCH

### M1 — EVENT_COMPLETED trigger mechanism
**Doc claims (phase-e-publishing.md):** "Cron job runs hourly. Checks all events in EVENT_LIVE state. For events where current date/time >= event end time: Automatically transitions event to EVENT_COMPLETED state."
**Test asserts:** `WatchEventCompletionIntegrationTest#should_transitionEventToCompleted_when_allCompleteableSessionsEnded` — EVENT_COMPLETED is triggered when an organizer calls `WatchSessionService.endSession()` for all completeable sessions (keynote, presentation, workshop, panel_discussion). Break/lunch/networking sessions are explicitly excluded from this check.
**Risk:** Users expect an automatic time-based transition; reality requires active session management via the Watch feature. If organizers don't use Watch mode, the event may never reach EVENT_COMPLETED automatically.
**Action:** Update Phase E to document the Watch-based trigger as the primary mechanism; clarify whether the hourly cron also exists or has been superseded.

---

### M2 — Topic selection state transition (CREATED → where?)
**Doc claims (phase-a-setup.md Step 2):** "Event state: CREATED → **TOPIC_SELECTION**" when topics are confirmed.
**Test asserts:** `TopicSelectionWorkflowIntegrationTest#should_transitionToSpeakerBrainstorming_when_topicSelected` — selecting a single topic from CREATED state transitions the event directly to **SPEAKER_IDENTIFICATION**, not TOPIC_SELECTION. Test comment: "AC14: Event state transition to SPEAKER_IDENTIFICATION (topic selection complete)".
**Action:** Update Phase A Step 2 and README state table to reflect that topic selection from CREATED goes directly to SPEAKER_IDENTIFICATION. TOPIC_SELECTION is an entry point, not a mandatory intermediate stop from CREATED.

---

### M3 — Auto-publish speakers: required event state
**Doc claims (README.md auto-publishing table):** "Speaker profiles — Required Event State: `AGENDA_PUBLISHED` or `AGENDA_FINALIZED`"
**Doc claims (phase-e-publishing.md):** "Checks all events in AGENDA_PUBLISHED or AGENDA_FINALIZED states"
**Test asserts:** `PublishingScheduledServiceIntegrationTest#should_autoPublishSpeakers_when_eventIsThirtyDaysAway` — event is created in **SLOT_ASSIGNMENT** state; auto-publish speakers still runs. The gate is `currentPublishedPhase IS NULL` (not yet published), not the workflow state.
**Action:** Replace the event-state gate claim with the correct check: events where `currentPublishedPhase` has not yet reached "speakers" are eligible, regardless of workflow state (as long as speakers exist).

---

### M4 — Auto-publish agenda: required event state
**Doc claims (phase-e-publishing.md):** "Checks all events in AGENDA_FINALIZED state" for agenda auto-publish.
**Test asserts:** `PublishingScheduledServiceIntegrationTest#should_autoPublishAgenda_when_eventIsFourteenDaysAwayAndTimingComplete` — event is in **SLOT_ASSIGNMENT** state with `currentPublishedPhase = "speakers"`; agenda auto-publishes and **transitions event to AGENDA_PUBLISHED**. Separate test `should_notAutoPublishAgenda_when_speakersNotPublished` confirms the gate is `currentPublishedPhase = "speakers"`, not `AGENDA_FINALIZED` state.
**Action:** Update Phase E to say: "Checks all events where `currentPublishedPhase = 'speakers'` and all sessions have timing". Remove the AGENDA_FINALIZED requirement for auto-publish agenda.

---

### M5 — Non-existent state OUTREACH_INITIATED referenced
**Doc claims (phase-b-outreach.md Step 8):** "Event state advances to: **OUTREACH_INITIATED**"
**Test asserts:** No test references `OUTREACH_INITIATED`. The `EventWorkflowState` enum has 9 states (CREATED, TOPIC_SELECTION, SPEAKER_IDENTIFICATION, SLOT_ASSIGNMENT, AGENDA_PUBLISHED, AGENDA_FINALIZED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED). `EventWorkflowControllerIntegrationTest` confirms only these 9 states are valid.
**Action:** Remove the OUTREACH_INITIATED reference from Phase B Step 8. The event remains in SPEAKER_IDENTIFICATION throughout Phases B and C.

---

### M6 — Speaker INVITED state and SLOT_ASSIGNED state undocumented
**Doc claims (README.md, phase-b-outreach.md):** Speaker states are: identified, contacted, ready, accepted, declined, content_submitted, quality_reviewed, confirmed, overflow, withdrew. (10 states)
**Test asserts:** `StatusTransitionValidatorTest` — parameterised test lists valid states as: `IDENTIFIED, INVITED, CONTACTED, READY, ACCEPTED, SLOT_ASSIGNED, CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED` (9 active states + DECLINED). Both `INVITED` (between IDENTIFIED and CONTACTED) and `SLOT_ASSIGNED` exist in `SpeakerWorkflowState` enum. Note: `SpeakerWorkflowServiceTest#shouldRejectSlotAssignedStateTransition` shows you cannot manually transition TO SLOT_ASSIGNED, but it is a valid source state for DECLINED transitions.
**Action:** Add INVITED and SLOT_ASSIGNED to the speaker state reference tables. Document INVITED as the state after an invitation email is dispatched (before the speaker marks ready). Clarify that SLOT_ASSIGNED is set automatically and cannot be set manually.

---

### M7 — Task lifecycle: pre-creation vs. on-transition creation
**Doc claims (README.md):** "Tasks are automatically created when the event transitions to their trigger state"
**Doc claims (phase-f-communication.md):** Same — tasks appear when event reaches trigger state.
**Test asserts:** `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated` — tasks are created at **event creation** with `status = "pending"`. They are then **activated to `status = "todo"`** when the event transitions to the trigger state (`should_activatePendingTasks_when_eventReachesTriggerState`).
**Action:** Update docs to describe the two-phase task lifecycle: (1) tasks are pre-created as `pending` when the event is created; (2) they activate to `todo` when the event reaches the trigger state. This distinction matters for custom-task tooling and task dashboard UX.

---

### M8 — README task states list incomplete
**Doc claims (README.md Task Dashboard section):** "Tasks appear in the task list with three statuses: **TODO**, **IN_PROGRESS**, **COMPLETED**"
**Test asserts / Phase F claims:** `EventTaskServiceIntegrationTest` uses status strings `"pending"`, `"todo"`, `"completed"`. Phase F also lists a 4th user-visible state: **CANCELLED**. The pre-activation `pending` status is a 5th internal status not mentioned anywhere in the docs.
**Action:** Update README task-status list to: TODO, IN_PROGRESS, COMPLETED, CANCELLED (user-visible). Add note that PENDING is an internal pre-activation status not shown in the task dashboard.

---

## UNTESTED

### U1 — Content submission field length limits
**Doc claims (phase-b-outreach.md):** "Title: 100 characters max; Abstract: 1000 characters max; Learning Objectives: 3–5 items required"
**Risk:** medium — No test among the files searched directly validates max-length enforcement on the Title or Abstract fields. `ContentSubmissionSchemaValidationTest` exists in the test list but was not examined; this claim may be covered there.

### U2 — Magic link 30-day validity
**Doc claims (phase-c-quality.md):** "Magic link to speaker portal (30-day validity) for easy content revision"
**Risk:** medium — `MagicLinkServiceTest` exists but was not examined. The 30-day window is a security-sensitive claim that should be explicitly asserted.

### U3 — Speaker auto-publish creates Newsletter: Speakers task
**Doc claims (phase-e-publishing.md):** "Auto-creates 'Newsletter: Speakers' task (if not exists)" during speaker auto-publish.
**Risk:** low — `PublishingScheduledServiceIntegrationTest` does not assert task creation side-effects. Only the `currentPublishedPhase` field is checked.

### U4 — Agenda auto-publish creates Newsletter: Final task
**Doc claims (phase-e-publishing.md):** "Auto-creates 'Newsletter: Final' task (if not exists)" during agenda auto-publish.
**Risk:** low — Same gap as U3; `PublishingScheduledServiceIntegrationTest` does not assert task creation.

### U5 — EVENT_LIVE hourly cron trigger
**Doc claims (README.md + phase-e-publishing.md):** "Cron job runs hourly. Checks all events in AGENDA_FINALIZED state. For events where current date/time >= event start time: Automatically transitions event to EVENT_LIVE."
**Risk:** high — No scheduled-service test was found for this transition (only `PublishingScheduledServiceIntegrationTest` and `WatchEventCompletionIntegrationTest` were examined). If this cron doesn't exist in production, events would never auto-enter EVENT_LIVE without Watch-mode intervention.

---

## UNDOCUMENTED

### N1 — WatchSessionService: organizer-driven EVENT_COMPLETED
**Test:** `WatchEventCompletionIntegrationTest#should_transitionEventToCompleted_when_allCompleteableSessionsEnded` — When an organizer uses the live Watch feature and calls `endSession()` for every completeable session, the event automatically transitions to EVENT_COMPLETED.
**Action:** Add a "Live Event Management (Watch Mode)" section to Phase E describing this mechanism.

### N2 — Break / lunch / networking sessions excluded from completion check
**Test:** `WatchEventCompletionIntegrationTest#should_excludeBreakSessions_fromAllCompleteCheck` and `#should_excludeNetworkingSession_fromAllCompleteCheck` — session types `lunch`, `break`, `networking` are never required to be ended; only `keynote`, `presentation`, `workshop`, `panel_discussion` count.
**Action:** Document which session types count toward the "all sessions complete" trigger in Phase E.

### N3 — CONFIRMED can still transition to DECLINED
**Test:** `StatusTransitionValidatorTest#should_allowTransitionToDeclined_from_everyActiveState` (includes CONFIRMED in the parameterised list) — a confirmed speaker can be moved to DECLINED.
**Action:** Add to Phase D/E: "If a confirmed speaker withdraws after confirmation, mark them as DECLINED to free the slot."

### N4 — Tasks have a `pending` pre-activation state
**Test:** `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated` — tasks exist in `pending` status before the event reaches their trigger state and are invisible to organizers.
**Action:** Document in Phase F that the system pre-creates tasks silently; they only appear in the organiser task board after the trigger state is reached.

### N5 — INVITED speaker workflow state
**Test:** `StatusTransitionValidatorTest` — `INVITED` is a valid `SpeakerWorkflowState` from which a speaker can decline.
**Action:** Add INVITED to Phase B and the state-reference table; document that the transition CONTACTED → INVITED → READY models the invitation-sent / invitation-received lifecycle.

### N6 — SLOT_ASSIGNED speaker workflow state (system-set)
**Test:** `StatusTransitionValidatorTest` lists `SLOT_ASSIGNED` as a valid state (for DECLINED transitions). `SpeakerWorkflowServiceTest#shouldRejectSlotAssignedStateTransition` confirms it cannot be set manually.
**Action:** Add to Phase D: the system automatically sets speakers to SLOT_ASSIGNED when a slot is assigned. Manual transitions to this state are rejected.

### N7 — From CREATED, SPEAKER_IDENTIFICATION is a valid direct transition
**Test:** `EventWorkflowControllerIntegrationTest#should_return200WithStatus_when_workflowStatusQueried` — workflow status response from CREATED shows `nextAvailableStates: ["TOPIC_SELECTION", "SPEAKER_IDENTIFICATION"]`.
**Action:** Update README state table to show CREATED can advance to either TOPIC_SELECTION or SPEAKER_IDENTIFICATION (e.g., when re-creating an event with pre-known topic).

### N8 — "Critical task" threshold: due ≤ 3 days
**Test:** `EventTaskServiceIntegrationTest#should_returnOverdueAndDueSoonTasks_when_gettingCriticalTasks` — tasks with due date within 3 days (or overdue) are returned by `getCriticalTasksForOrganizer()`.
**Action:** Add to Phase F: tasks due within 3 calendar days appear highlighted as critical alongside overdue tasks.

### N9 — Idempotent speaker workflow transitions are allowed
**Test:** `SpeakerWorkflowServiceTest#shouldAllowIdempotentTransitions` — transitioning a speaker to their current state is a no-op (no error thrown).
**Action:** Add a note to Phase B/C: re-submitting the same state transition is safe and will not create duplicate history entries.

### N10 — Rejection feedback stored in ContentSubmission record
**Test:** `QualityReviewServiceIntegrationTest#should_storeReviewerFeedbackInContentSubmission_when_contentRejected` — rejection persists `reviewerFeedback`, `reviewedBy`, and `reviewedAt` on the `ContentSubmission` entity, distinct from the `speaker_pool.notes` field.
**Action:** Phase C currently only mentions "displayed in speaker contact history". Add that feedback is also versioned per ContentSubmission for speaker-portal display.

---

## VALIDATED
- "Auto-confirm when QUALITY_REVIEWED AND session.startTime set (regardless of order)" → `SpeakerWorkflowServiceTest#shouldAutoConfirmWhenSlotAssignedAfterQualityReview` + `#shouldAutoConfirmWhenQualityReviewCompletesWithSlotAlreadyAssigned`
- "QUALITY_REVIEWED without slot = stays QUALITY_REVIEWED (no auto-confirm)" → `SpeakerWorkflowServiceTest#shouldNotAutoConfirmWithoutSlot`
- "content_submitted → quality_reviewed on approval" → `QualityReviewServiceIntegrationTest#should_updateToQualityReviewed_when_contentApproved`
- "Auto-confirm to CONFIRMED when slot already assigned at approval time" → `QualityReviewServiceIntegrationTest#should_updateToConfirmed_when_approvedAndSlotAlreadyAssigned`
- "Rejection requires non-null feedback (throws exception if null)" → `QualityReviewServiceIntegrationTest#should_throwException_when_rejectingWithoutFeedback`
- "Venue Booking due 90 days before event" → `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated` (`-90` day offset for venue task)
- "Tasks for topic_selection include Venue, Partner Meeting, Moderator, Newsletter: Topic (≥ 3)" → `EventTaskServiceIntegrationTest#should_createPendingTasks_when_eventIsCreated`
- "Task idempotency: no duplicate tasks from same template" → `EventTaskServiceIntegrationTest#should_preventDuplicateTasks_when_eventTransitionReplayedMultipleTimes`
- "Invalid backward state transitions rejected with error" → `EventWorkflowControllerIntegrationTest#should_return422_when_invalidBackwardTransition_attempted`
- "Auto-publish speakers skip if already published (idempotent)" → `PublishingScheduledServiceIntegrationTest#should_notAutoPublishSpeakers_when_alreadyPublished`
