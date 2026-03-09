# Doc Audit Retrospective Report
**Generated:** 2026-03-09

## Status

| Target | Label | Audit | Fix | Findings |
|--------|-------|-------|-----|----------|
| `state-machines` | Event state machine & lifecycle | ✅ | — | [view](findings-state-machines.md) |
| `backend-architecture` | Backend architecture overview | ✅ | — | [view](findings-backend-architecture.md) |
| `epic-4-public-website` | Public website & registration (Epic 4) | ✅ | — | [view](findings-epic-4-public-website.md) |
| `epic-5-organizer-workflows` | Organizer workflows & lifecycle automation (Epic 5) | ✅ | — | [view](findings-epic-5-organizer-workflows.md) |
| `epic-6-speaker-portal` | Speaker self-service portal (Epic 6) | ✅ | — | [view](findings-epic-6-speaker-portal.md) |
| `epic-8-partner` | Partner coordination (Epic 8) | ✅ | — | [view](findings-epic-8-partner.md) |
| `epic-9-speaker-auth` | Speaker authentication (Epic 9) | ✅ | — | [view](findings-epic-9-speaker-auth.md) |
| `user-lifecycle-sync` | User lifecycle sync | ✅ | — | [view](findings-user-lifecycle-sync.md) |
| `notification-system` | Notification system | ✅ | — | [view](findings-notification-system.md) |
| `user-guide-workflow` | User guide — organiser workflow phases | ✅ | — | [view](findings-user-guide-workflow.md) |
| `user-guide-speaker-portal` | User guide — speaker portal | ✅ | — | [view](findings-user-guide-speaker-portal.md) |
| `user-guide-partner-portal` | User guide — partner portal | ✅ | — | [view](findings-user-guide-partner-portal.md) |

## Findings

---
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

---
# Doc Audit Findings — Backend Architecture Overview
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06-backend-architecture.md`
**Tests searched:** `services/event-management-service/src/test/java`, `services/company-user-management-service/src/test/java`, `api-gateway/src/test/java`

## Summary
- VALIDATED: 8
- MISMATCH: 4
- UNTESTED: 4
- UNDOCUMENTED: 6

---

## MISMATCH

### M1 — `canDemoteOrganizer` threshold: `> 2` vs `>= 2`
**Doc claims:**
```java
public boolean canDemoteOrganizer(String username, String eventCode) {
    long activeOrganizerCount = userRoleRepository.countActiveOrganizers(eventCode);
    return activeOrganizerCount > 2;
}
```
(adjacent comment: `"Cannot demote: minimum 2 organizers required"`)

**Test asserts:** `RoleServiceTest#should_allowRemovingOrganizer_when_twoOrMoreOrganizersExist`
— mocks exactly **2** organizers and expects **success**. `RoleServiceTest#should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains`
— mocks **1** organizer and expects `MinimumOrganizersException("minimum of 2 organizers")`.

The implemented rule is: demotion is allowed as long as 2 or more organizers currently exist (`count >= 2`). The doc's `> 2` predicate would require 3+ organizers, contradicting both tests.

**Action:** Update doc code to `return activeOrganizerCount >= 2;`

---

### M2 — Security filter chain: event endpoints require roles vs. anonymous access
**Doc claims:**
```java
.requestMatchers("/api/v1/events").hasAnyRole("ORGANIZER", "ATTENDEE", "SPEAKER", "PARTNER")
.requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ORGANIZER")
```

**Test asserts:** `SecurityConfigTest#should_allowAnonymousAccess_when_viewingEventDetails` — `GET /api/v1/events/{code}` returns 404 (not 401/403) with no `Authorization` header, confirming anonymous access is **permitted**.
Similarly `SecurityConfigTest#should_allowAnonymousAccess_when_viewingCurrentEvent` — `GET /api/v1/events/current` passes security without any auth header.
`SecurityConfigTest#should_trustApiGateway_when_noJwtValidationInService` — domain services trust the API Gateway; they do **not** perform JWT validation themselves.

The actual security model for event-management-service is: public GET endpoints are unauthenticated, JWT enforcement lives in the API Gateway, not in each domain service.

**Action:** Replace the security snippet with the actual service-level config showing public event/session/speaker endpoints and the API-Gateway-trust pattern. Remove the role-restricted snippet (`hasAnyRole`) from the service section and clarify it only applies at the gateway layer (if at all).

---

### M3 — Role storage: `role_assignments` table vs. embedded `User.roles`
**Doc claims:** "Roles stored exclusively in PostgreSQL `role_assignments` table" (under User Lifecycle section) and the `RoleManagementService` snippet uses a separate `UserRoleEntity` / `userRoleRepository`.

**Test asserts:** `RoleServiceTest` (the actual role service) injects `UserRepository` only — no `UserRoleRepository`. Roles are stored as `Set<Role>` on the `User` entity (`user.getRoles()`). The test verifies `userRepository.findByRolesContaining(Role.ORGANIZER)` — a query on the User table, not a separate `role_assignments` table.

The doc describes an aspirational `RoleManagementService` with a separate `UserRoleEntity`; the deployed implementation uses `RoleService` with roles embedded on `User`.

**Action:** Update the "User Lifecycle" section to reflect that roles are stored on the `User` entity. Remove or clearly mark the `RoleManagementService` snippet as a design sketch rather than deployed code.

---

### M4 — Circuit breaker: custom `CircuitBreakerService` with count-based threshold vs. Resilience4j rate-based
**Doc claims:**
```java
@Value("${circuit-breaker.failure-threshold:5}")
private int failureThreshold;

@Value("${circuit-breaker.timeout:60000}")
private long timeoutMs;
```
Describes a bespoke `CircuitBreakerService` with a count-based failure threshold of 5 and timeout of 60 000 ms.

**Test asserts (shared-kernel):** `TestResilience4jConfig` configures **Resilience4j** circuit breakers:
- `failureRateThreshold: 60%` (production) — rate-based, not count-based
- `waitDurationInOpenState: 10 seconds` (not 60 000 ms)
- `permittedNumberOfCallsInHalfOpenState: 5`
- Named instance: `eventBridgePublisher`

The production `application-shared.yml` also uses `resilience4j.circuitbreaker` properties. The custom `CircuitBreakerService` class with `ConcurrentHashMap<String, CircuitBreaker>` does not exist as described.

**Action:** Replace the `CircuitBreakerService` snippet with the actual Resilience4j configuration. Update threshold to `failureRateThreshold: 60%` and wait duration to `10s`.

---

## UNTESTED

### U1 — Event date must be at least 30 days in the future
**Doc claims:**
```java
if (request.getEventDate().isBefore(LocalDateTime.now().plusDays(30))) {
    throw new BusinessValidationException("eventDate",
        "Event date must be at least 30 days in the future", ...)
}
```
**Risk:** high — No test was found in the three searched directories validating this constraint. If the code exists, a regression could silently drop the rule. If the code does not exist, the doc is aspirational.

---

### U2 — Only one event per quarter
**Doc claims:**
```java
if (eventRepository.existsByQuarter(getQuarter(request.getEventDate()))) {
    throw new BusinessValidationException("eventDate",
        "Only one event is allowed per quarter", ...)
}
```
**Risk:** high — No test was found validating this constraint. Same risk as U1: either a missing test or a doc-only fiction.

---

### U3 — Retry mechanism with `@Retryable` on `TransientException` / `TemporaryUnavailableException`
**Doc claims:**
```java
@Retryable(
    value = {TransientException.class, TemporaryUnavailableException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
)
```
**Risk:** medium — The Resilience4j retry config (maxAttempts=3, waitDuration=1000ms, multiplier=2.0, maxInterval=10000ms) is consistent in values, but no unit test was found asserting retry behaviour for `TransientException` or `TemporaryUnavailableException`. The Spring-Retry (`@Retryable`) class may or may not exist alongside Resilience4j.

---

### U4 — `X-Correlation-ID` header propagated via `RequestCorrelationFilter`
**Doc claims:** `RequestCorrelationFilter` reads or generates an `X-Correlation-ID` header and stores it in `ThreadLocal`.

**Risk:** low — `GlobalExceptionHandlerTest#should_includeCorrelationId_when_errorResponseGenerated` confirms a correlation ID is included in error responses, but no test verifies that `X-Correlation-ID` is **read from the incoming request header** and propagated through to downstream calls.

---

## UNDOCUMENTED

### N1 — Domain services do not validate JWTs; they trust the API Gateway
**Test:** `SecurityConfigTest#should_trustApiGateway_when_noJwtValidationInService` and `SecurityConfigTest#should_notRejectRequests_when_invalidJwtProvided` — services accept requests with no token or an invalid token on public endpoints without returning 401.

**Action:** Add a note to the "Authentication and Authorization" section: "Domain services delegate JWT validation to the API Gateway. Services are configured to trust requests forwarded by the gateway and do not perform their own JWT signature verification."

---

### N2 — Swagger UI is publicly accessible (no auth required)
**Test:** `SecurityConfigTest#should_allowAccess_when_swaggerUiAccessed` — `GET /swagger-ui/index.html` returns 200 without any token.

**Action:** Add `/swagger-ui/**` and `/v3/api-docs/**` to the documented list of permitted-all endpoints alongside `/actuator/health` and `/actuator/info`.

---

### N3 — Event details, sessions, and speaker lists are anonymous/public
**Tests:**
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingEventDetails`
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingSessions`
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingSpeakers`

**Action:** Add a "Public Endpoints" subsection to the security section documenting that `GET /api/v1/events/**`, `GET /api/v1/events/{code}/sessions/**`, and speaker read endpoints are open to unauthenticated consumers.

---

### N4 — Event registration creation and confirmation are public (no auth required)
**Tests:**
- `SecurityConfigTest#should_allowAnonymousAccess_when_creatingRegistration` — `POST /api/v1/events/{code}/registrations` passes security without auth.
- `SecurityConfigTest#should_allowAnonymousAccess_when_confirmingRegistration` — `POST /api/v1/events/{code}/registrations/confirm` passes security without auth.

**Action:** Document these public POST endpoints in the security section.

---

### N5 — Conflict detection severities: ROOM_OVERLAP / SPEAKER_DOUBLE_BOOKED = ERROR, PREFERENCE_MISMATCH = WARNING
**Tests:**
- `ConflictDetectionServiceTest#should_detectRoomOverlap_when_sessionsOverlapInSameRoom` — `severity=ERROR`
- `ConflictDetectionServiceTest#should_detectSpeakerDoubleBooking_when_speakerInOverlappingSessions` — `severity=ERROR`
- `ConflictDetectionServiceTest#should_detectPreferenceConflict_when_sessionOutsidePreferredTime` — `severity=WARNING`

The doc mentions conflict detection only generically ("Speaker cannot be invited to multiple sessions in same time slot"). It does not document severity levels or distinguish between hard errors and soft warnings.

**Action:** Add to the `validateSpeakerInvitation` / Slot Assignment section: conflict types, their severities (ERROR vs WARNING), and that preference conflicts are non-blocking.

---

### N6 — Role operations are idempotent; no domain event published on no-ops
**Tests:**
- `RoleServiceTest#should_notPublishEvent_when_roleAlreadyExists` — `addRole` when role already present: saves but does **not** publish a domain event.
- `RoleServiceTest#should_notPublishEvent_when_roleNotPresent` — `removeRole` when role absent: saves but does **not** publish a domain event.

**Action:** Add to the Role Management section: "Role add and remove operations are idempotent. A `UserRoleChangedEvent` is only published when the role set actually changes."

---

## VALIDATED
- "custom:role JWT claim extraction" → `CognitoJWTValidatorTest#should_extractUserContext_when_validTokenProvided`, `UserContextExtractorTest#should_extractUserContext_when_tokenContainsAllRequiredClaims`
- "comma-separated roles `ATTENDEE,SPEAKER` → `[ROLE_ATTENDEE, ROLE_SPEAKER]`" → `UserContextExtractorTest#should_extractAllRoles_when_multipleRolesProvided`
- "Algorithm.none() token attack rejected" → `CognitoJWTValidatorTest#should_rejectAlgorithmNone_when_unsignedTokenProvided`
- "9-state event workflow state machine" → `EventWorkflowStateMachineTest#should_transitionSuccessfully_when_validStateTransition_attempted` (CREATED→TOPIC_SELECTION), `should_throwWorkflowException_when_invalidStateTransition_attempted`
- "Retry maxAttempts=3, delay=1000ms, multiplier=2, maxDelay=10000ms" (Resilience4j values match) → `TestResilience4jConfig` / `application-shared.yml`
- "SpeakerAlreadyInvitedException idempotent" → `SpeakerInvitationServiceTest#should_returnExistingSpeakerPool_when_alreadyInvitedToEvent`
- "Error response includes correlationId and path" → `GlobalExceptionHandlerTest#should_includeCorrelationId_when_errorResponseGenerated`, `should_returnStandardErrorResponse_when_BATbernExceptionThrown`
- "Actuator health endpoint is public (permitAll)" → `SecurityConfigTest#should_allowAccess_when_healthEndpointAccessed`

---
# Doc Audit Findings — Public Website & Content Discovery (Epic 4)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-4-public-website-content-discovery.md`
**Tests searched:** `services/event-management-service/src/test/java`, `web-frontend/src`

## Summary
- VALIDATED: 12
- MISMATCH: 3
- UNTESTED: 7
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Cancellation endpoint URL includes event code in path (frontend) but not in doc
**Doc claims:** "Public Cancellation Endpoint: POST `/api/v1/registrations/cancel?token={jwt}` (no authentication required)" (Story 4.1.5d)
**Test asserts:** `eventApiClient.mocked.test.ts#cancelRegistration` — calls `cancelRegistration('BATbern142', 'jwt-token')`, and `eventApiClient.ts` line 351 uses path `/events/${eventCode}/registrations/cancel?token={token}` (event code embedded in URL path)
**Action:** Update doc to say the endpoint is `POST /api/v1/events/{eventCode}/registrations/cancel?token={jwt}` or confirm the backend route

### M2 — Cancellation produces a status change, not permanent deletion
**Doc claims:** "Permanent Deletion: Registration permanently deleted from database (not just status change)" (Story 4.1.5d)
**Test asserts:** `DeregistrationControllerIntegrationTest#deregisterByToken_validToken_returns200AndCancelsRegistration` — `assertThat(updated.getStatus()).isEqualTo("cancelled")`. The record remains in the DB with status `cancelled`; the test even retrieves it post-deregistration via `findByDeregistrationToken`.
**Action:** Update doc: "Cancellation sets registration status to `cancelled` (record is retained). Permanent deletion language is inaccurate."

### M3 — Cancellation token is UUID-based in the implemented deregistration flow, not JWT
**Doc claims:** "Separate JWT cancellation token (48-hour validity, type: 'registration-cancellation')" and "Token Type Validation: Cancellation tokens explicitly marked with `type: 'registration-cancellation'`" (Story 4.1.5d)
**Test asserts:** `DeregistrationControllerIntegrationTest` uses `UUID deregistrationToken` (stored as a plain UUID column, not a JWT). `ConfirmationTokenServiceTest` has no test for `generateCancellationToken` or `validateCancellationToken` with type `"registration-cancellation"`. The story 10.12 deregistration endpoints entirely replace the JWT mechanism.
**Action:** Update Story 4.1.5d to reflect that the implemented token mechanism is a UUID `deregistrationToken` (not a JWT). The 48-hour expiry and type-validation claims no longer apply to the current implementation.

---

## UNTESTED

### U1 — Archive-style layout: specific fields hidden after event
**Doc claims:** "timetable and speaker list are visible, but registration, logistics, and venue blocks are hidden" (Post-Event 14-Day Display Rule)
**Risk:** medium — tests verify that EVENT_COMPLETED events within 14 days are returned by `/api/v1/events/current`, but no test validates which specific response fields or UI sections are suppressed in archive-style mode.

### U2 — 1-minute cache TTL for event data
**Doc claims:** "Caffeine in-memory cache for event data (1-minute TTL)" (Story 4.1, Architecture Integration)
**Risk:** low — `CaffeineCacheConfigTest` only mentions and tests the 15-minute TTL (for archive browsing). There is no assertion on a 1-minute TTL for the current-event or single-event endpoints.

### U3 — 5-minute cache TTL for search results
**Doc claims:** "Caffeine in-memory cache for search results (5-minute TTL)" (Story 4.3, Architecture Integration)
**Risk:** low — no test in either directory verifies a 5-minute Caffeine TTL for any search cache.

### U4 — Infinite scroll triggers within 400px of page bottom
**Doc claims:** "Automatic loading when scrolling within 400px of bottom (20 events per page)" (Story 4.2 AC3)
**Risk:** low — `useInfiniteEvents.test.tsx` validates the page size (limit: 20) and pagination, but does not assert the 400px IntersectionObserver `rootMargin` threshold.

### U5 — Search debounce 300ms
**Doc claims:** "Search Bar: Full-text search across event titles, topics, speakers (debounced 300ms)" (Story 4.2 AC9, Story 4.3 AC2)
**Risk:** low — no test in either directory asserts the 300ms debounce timing on the archive search or content discovery search inputs.

### U6 — Email confirmations sent within 1 minute
**Doc claims:** "Email confirmations sent within 1 minute" (Story 4.1 Deliverables)
**Risk:** low — `RegistrationEmailServiceTest` validates that emails are sent and contain correct content, but no test measures or bounds the delivery latency.

### U7 — Story 4.3 (content search) entirely untested at service layer
**Doc claims:** Full-text search returns results in <500ms, PostgreSQL GIN indexes on searchable fields, Caffeine 5-minute cache for search results, autocomplete suggestions debounced 300ms, faceted filtering (Story 4.3 ACs 1–22)
**Risk:** high — no test in `services/event-management-service/src/test/java` or `web-frontend/src` directly exercises the content-search endpoint, GIN index usage, or search cache behaviour described for Story 4.3.

---

## UNDOCUMENTED

### N1 — Deregistration via UUID token (Story 10.12 — new endpoints not in Epic 4 doc)
**Test:** `DeregistrationControllerIntegrationTest` — asserts:
- `GET /api/v1/registrations/deregister/verify?token={uuid}` → 200 with registration info, 404 when token unknown/already-cancelled
- `POST /api/v1/registrations/deregister` with UUID token → 200 and status `cancelled`; 409 on second call; 404 for unknown token
- `POST /api/v1/registrations/deregister/by-email` → always 200 (anti-enumeration)
**Action:** Add a section to Epic 4 doc (or link to Story 10.12) describing the UUID-based deregistration endpoints that supersede the JWT cancellation mechanism from Story 4.1.5d.

### N2 — `/api/v1/events/current` Phase 1 requires `currentPublishedPhase != null`
**Test:** `EventControllerIntegrationTest#should_showRecentlyCompletedEvent_when_upcomingEventIsUnpublished` — an upcoming event in state `SPEAKER_IDENTIFICATION` (no published phase) does NOT qualify for Phase 1 and must not block the Phase 2 (recently-completed) fallback. Comment: "Rule: only events with currentPublishedPhase != null qualify for Phase 1 (upcoming)."
**Action:** Add to the Post-Event 14-Day Display Rule section: Phase 1 only considers events that have `currentPublishedPhase` set; unpublished future events are excluded.

### N3 — Unconfirmed registration cleanup after 48 hours
**Test:** `RegistrationCleanupServiceTest#shouldDeleteUnconfirmedRegistrations_whenOlderThan48Hours` and `RegistrationCleanupServiceIntegrationTest#shouldDeleteOldUnconfirmedRegistrations` — unconfirmed registrations older than 48 hours are permanently deleted by a scheduled cleanup job.
**Action:** Add to Story 4.1 Technical Requirements or 4.1.5c: "Unconfirmed registrations (status = `registered` / unconfirmed) are permanently deleted after 48 hours by a cleanup job."

### N4 — Anti-enumeration: deregister/by-email always returns 200
**Test:** `DeregistrationControllerIntegrationTest#deregisterByEmail_anyInput_alwaysReturns200` — the `POST /api/v1/registrations/deregister/by-email` endpoint returns HTTP 200 for any email, whether registered or not, to prevent email enumeration.
**Action:** Add to Story 4.1.5d (or linked Story 10.12): "The by-email deregistration endpoint returns HTTP 200 unconditionally to prevent user-enumeration attacks."

### N5 — Waitlist promotion triggered on deregistration
**Test:** `DeregistrationControllerIntegrationTest#deregisterByToken_whenWaitlistExists_waitlistRegistrationPromoted` — when a registered attendee deregisters and a waitlisted attendee exists, the first waitlisted registration is automatically promoted to `registered` status and their `waitlistPosition` cleared.
**Action:** Add to Story 4.1 or 4.1.5d Acceptance Criteria: "On successful deregistration, the first waitlisted registration (if any) is automatically promoted to confirmed."

---

## VALIDATED
- "After an event finishes, the homepage continues to show it for 14 days" → `EventWorkflowScheduledServiceIntegrationTest#should_archiveCompletedEvent_when_olderThan14Days` + `should_notArchiveCompletedEvent_when_within14Days`
- Exclusive boundary: exactly-14-days stays in EVENT_COMPLETED → `EventWorkflowScheduledServiceIntegrationTest#should_notArchiveCompletedEvent_when_exactlyAt14DayBoundary`
- `/api/v1/events/current` returns recently completed event (within 14 days) → `EventControllerIntegrationTest#should_returnCompletedEvent_when_completedEventDateWasYesterday`
- `/api/v1/events/current` returns 404 when no active or recent event → `EventControllerIntegrationTest#should_return404_when_noCurrentOrRecentEvent`
- `/api/v1/events/current` prefers upcoming over recently completed → `EventControllerIntegrationTest#should_preferUpcomingEvent_when_bothUpcomingAndRecentlyCompletedExist`
- "48-hour validity" of JWT confirmation token → `ConfirmationTokenServiceTest#should_have48HourExpiry_when_tokenGenerated`
- Confirmation token type is "email-confirmation" → `ConfirmationTokenServiceTest#should_returnClaims_when_tokenIsValid`
- `registrationCode` not returned in registration API response (Story 4.1.5c security) → `AnonymousRegistrationE2ETest#should_completeAnonymousRegistrationFlow_when_userRegistersWithoutAuth`
- Registration code format `{eventCode}-reg-{6alphanumeric}` → `AnonymousRegistrationE2ETest`
- Archive browsing page size is 20 events → `useInfiniteEvents.test.tsx` (limit: 20 in pagination mock)
- Archive events cache (`archiveEvents`) exists in CacheManager → `CaffeineCacheConfigTest#should_haveArchiveEventsCache_when_cacheManagerConfigured`
- Archive browsing sort by date asc/desc → `ArchiveBrowsingIntegrationTest#should_sortByDateDescending_when_sortMinusDateProvided` + `should_sortByDateAscending_when_sortPlusDateProvided`

---
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

---
# Doc Audit Findings — Speaker Self-Service Portal (Epic 6)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-6-speaker-portal-support.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 2
- MISMATCH: 2
- UNTESTED: 13
- UNDOCUMENTED: 1

The `speaker-coordination-service` test suite contains only **3 files** (base class, security config, health endpoint test). None of the Epic 6 business logic — invitations, portal responses, material submission, dashboard, or reminders — has any test coverage in this service.

---

## MISMATCH

### M1 — Claimed implementation service contradicts itself
**Doc claims (line 11):** "Speaker portal functionality implemented in `event-management-service` with magic link authentication, invitation workflow, response handling, content submission, dashboard, and automated reminders."
**Story 6.0 states (line 89):** "Service: `speaker-coordination-service/` (Java 21 + Spring Boot 3.2)"
**Observed:** The `speaker-coordination-service` main source contains only 4 files: `SpeakerCoordinationApplication.java`, `CacheConfig.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`. None of the controllers listed in Stories 6.1–6.3 (`SpeakerInvitationController`, `SpeakerPortalTokenController`, `SpeakerPortalResponseController`, `SpeakerPortalContentController`, `SpeakerPortalProfileController`) are present.
**Action:** Reconcile which service actually hosts portal functionality. If truly in `event-management-service`, correct the Epic Overview and Story 6.0. If in `speaker-coordination-service`, the claimed controllers are absent and the "deployed" status is inaccurate.

### M2 — AbstractIntegrationTest references wrong story
**Doc claims:** Epic 6 is the speaker self-service portal.
**Test asserts:** `AbstractIntegrationTest` Javadoc says "Story 5.4: Speaker Status Management" — copied from a different service, not Epic 6.
**Action:** Update the Javadoc in `AbstractIntegrationTest` to reference Epic 6 / Story 6.0.

---

## UNTESTED

### U1 — Automated Speaker Invitation System (Story 6.1)
**Doc claims:** "Generate unique response link per speaker (no authentication required)" / "Bulk invitation system handles 50+ speakers"
**Risk:** high — no test validates invitation generation, uniqueness, or bulk send capacity.

### U2 — Magic link validation (Story 6.2)
**Doc claims:** "`SpeakerPortalTokenController.java` - magic link validation" — "Response form works without authentication via unique link"
**Risk:** high — no test verifies token validation, expiry, or unauthenticated access enforcement.

### U3 — Accept/Decline response processing (Story 6.2)
**Doc claims:** "Response automatically updates speaker status in Epic 5 workflow" / "Status tracking syncs between self-service and manual"
**Risk:** high — no test verifies status transitions triggered by portal responses.

### U4 — Tentative response backward compatibility (Story 6.2)
**Doc claims:** "Tentative removed from UI 2026-02-11; API still supports for backward compat"
**Risk:** medium — no test validates that the tentative response type is still accepted at the API level after UI removal.

### U5 — Manual override / conflict resolution (Story 6.2)
**Doc claims:** "If speaker self-responds after organizer manual update, show warning"
**Risk:** medium — no test covers the conflict detection path.

### U6 — Abstract length validation (Story 6.3)
**Doc claims:** "Validation: Enforce abstract length (1000 char max)"
**Risk:** high — no test asserts the 1000-character constraint is enforced.

### U7 — Material submission S3 presigned URL (Story 6.3)
**Doc claims:** "Materials upload to S3 using presigned URLs"
**Risk:** medium — no test verifies presigned URL generation or S3 integration flow.

### U8 — Speaker dashboard 30-day session expiration (Story 6.4)
**Doc claims:** "Session Management: 30-day session expiration"
**Risk:** high — no test validates session TTL or expiry enforcement.

### U9 — Speaker CRUD and resource expansion (Story 6.0)
**Doc claims:** "`GET /api/v1/speakers?include=events,sessions,companies`" / "detail+includes <300ms P95"
**Risk:** high — no test covers CRUD endpoints, `?include` expansion, or performance constraints.

### U10 — Domain events published (Story 6.0)
**Doc claims:** "Domain events publishing to EventBridge (SpeakerCreatedEvent, SpeakerUpdatedEvent, SpeakerInvitedEvent)"
**Risk:** medium — no test verifies domain event publication.

### U11 — Automated deadline reminders deduplication (Story 6.5)
**Doc claims:** "Deduplication: Don't send reminder if materials already submitted"
**Risk:** high — no test verifies the deduplication guard against re-sending reminders.

### U12 — Reminder escalation tiers (Story 6.5)
**Doc claims:** "Escalation Tiers: Tier 1 (friendly reminder), Tier 2 (urgent), Tier 3 (escalate to organizer)"
**Risk:** medium — no test validates tier selection logic or escalation path.

### U13 — Organizer notification on speaker response (Story 6.2)
**Doc claims:** "Organizer Notification: Organizers notified in real-time of speaker responses"
**Risk:** medium — no test verifies notification dispatch on response events.

---

## UNDOCUMENTED

### N1 — Health and info endpoint availability
**Test:** `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled` — asserts `GET /actuator/health` returns HTTP 200 with `$.status = "UP"`
**Test:** `HealthControllerIntegrationTest#should_returnServiceInfo_when_infoEndpointCalled` — asserts `GET /actuator/info` returns HTTP 200
**Action:** These operational requirements are not mentioned in Epic 6. Add to doc section "Implementation Considerations" or a separate operational requirements section if desired.

---

## VALIDATED
- "Integration tests extend `AbstractIntegrationTest` from testFixtures" → `AbstractIntegrationTest` (present, uses real PostgreSQL 16 via Testcontainers)
- "Integration tests use PostgreSQL via Testcontainers (not H2/in-memory)" → `AbstractIntegrationTest` — singleton `PostgreSQLContainer<>("postgres:16-alpine")` started once per JVM

---
# Doc Audit Findings — Partner Coordination (Epic 8)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-8-partner-coordination.md`
**Tests searched:** `services/partner-coordination-service/src/test/java`

## Summary
- VALIDATED: 22
- MISMATCH: 1
- UNTESTED: 7
- UNDOCUMENTED: 7

---

## MISMATCH

### M1 — Topic title has a minimum length constraint not mentioned in the doc
**Doc claims:** "Topic suggestion: title (required) + short description (optional) — no mandatory justification"
**Test asserts:** `TopicControllerIntegrationTest#should_return400_when_titleTooShort` — 400 returned for title `"Hi"` (2 chars), comment says `"< 5 chars"`
**Action:** Update doc to say: "Topic suggestion: title (required, minimum 5 characters) + short description (optional) — no mandatory justification"

---

## UNTESTED

### U1 — Caffeine cache TTLs not validated
**Doc claims:** "Data queried on demand; Caffeine-cached 15 minutes — no nightly job, no materialized views" (Story 8.1) and "EventManagementClient.getEventSummary(eventCode) (Caffeine cache 1h)" (Story 8.3)
**Risk:** medium — if cache is misconfigured (e.g. wrong TTL or disabled) tests would still pass; stale data bugs could surface only in production

### U2 — Performance targets for Epic 8 endpoints not covered
**Doc claims:** "Dashboard P95 < 5s | Cached response < 50ms | Cold response < 500ms" (8.1); "Topic list P95 < 3s | Vote toggle < 500ms" (8.2); "Meeting list P95 < 3s | Invite 202 response < 200ms" (8.3)
**Risk:** medium — `PartnerPerformanceTest` only measures the base partner CRUD list/detail endpoints; no performance test covers analytics, topics, or meetings

### U3 — ICS file content (two VEVENTs) not validated
**Doc claims:** "Calendar invite: standard .ics file (RFC 5545) with two VEVENTs (partner lunch + BATbern event itself), sent via AWS SES to all partner contacts"
**Risk:** high — `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled` only asserts HTTP 202 and the JSON response body; the ICS byte content (structure, two VEVENTs, RFC 5545 headers) is never inspected

### U4 — Attendance table percentage column not validated
**Doc claims:** "Attendance table: event name, date, company attendees, total attendees, percentage — sorted by date descending"
**Risk:** low — `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_partnerRequestsOwnCompany` only asserts `eventCode`, `totalAttendees`, and `companyAttendees`; the `percentage` field (and its formula) is never asserted

### U5 — Attendance table date ordering not validated
**Doc claims:** "Attendance table: … sorted by date descending" (Story 8.1)
**Risk:** low — no test verifies the order of rows in `$.attendanceSummary`; test data has two events but order is not asserted

### U6 — Email recipients for calendar invite not validated
**Doc claims:** "sent via AWS SES to all partner contacts on record"
**Risk:** medium — `PartnerMeetingControllerIntegrationTest#should_setInviteSentAt_when_sendInviteCalled` mocks `userServiceClient.getUsersByRole("PARTNER")` but does not assert that the SES call was made with those recipients; actual email dispatch is `@Async` and not inspected

### U7 — Agenda included in ICS invite description not validated
**Doc claims:** "Agenda as free text — included in the calendar invite description" (Story 8.3)
**Risk:** low — tests confirm agenda is persisted via PATCH; no test checks that the ICS `DESCRIPTION` field contains the agenda text

---

## UNDOCUMENTED

### N1 — Topic title minimum length (≥ 5 characters) enforced
**Test:** `TopicControllerIntegrationTest#should_return400_when_titleTooShort` — 400 for title `"Hi"` (< 5 chars)
**Action:** Add to Story 8.2 scope: "Title must be at least 5 characters (validation enforced at API level)"

### N2 — Partner note title maximum length (500 characters) enforced
**Test:** `PartnerNoteControllerIntegrationTest#should_return400_when_titleExceeds500CharsOnUpdate` — 400 for title ≥ 501 chars
**Action:** Add to Story 8.4 scope: "Title has a 500-character maximum length"

### N3 — Partner note content is required (not optional)
**Test:** `PartnerNoteControllerIntegrationTest#should_return400_when_contentMissing` — 400 when only `title` is supplied
**Action:** Update Story 8.4 scope: "title (required) + content (required)" — the doc says "title + free-text content" but does not state content is mandatory

### N4 — Cross-partner note ownership scoping enforced
**Test:** `PartnerNoteControllerIntegrationTest#should_return404_when_updatingNoteFromDifferentPartner` and `#should_return404_when_deletingNoteFromDifferentPartner` — 404 when an organizer tries to update/delete a note belonging to a different partner via the wrong company URL
**Action:** Add to Story 8.4: "Note PATCH/DELETE validate that the noteId belongs to the companyName in the URL path; cross-partner access returns 404"

### N5 — Partner cannot spoof companyName in topic suggestion request body
**Test:** `TopicControllerIntegrationTest#should_ignoreCompanyName_in_body_when_partnerSubmits` — when a PARTNER submits a topic with `companyName: "SomeOtherCompany"` the body field is silently ignored; `suggestedByCompany` is resolved from the JWT instead
**Action:** Add security note to Story 8.2: "For PARTNER users, `companyName` in the request body is ignored; the company is always resolved from the authenticated user's JWT. Only ORGANIZER users may specify `companyName` explicitly."

### N6 — Organizer cannot vote on topics (403)
**Test:** `TopicControllerIntegrationTest#should_return403_when_organizerTriesToVote` — ORGANIZER receives 403 on `POST /api/v1/partners/topics/{topicId}/vote`
**Action:** Add to Story 8.2 role-based access section: "Organizers may NOT cast votes (403 Forbidden). Only PARTNER users may vote."

### N7 — Organizer can suggest topics on behalf of a partner company
**Test:** `TopicControllerIntegrationTest#should_createTopic_when_organizerSubmits_onBehalfOfPartnerCompany` — ORGANIZER `POST /api/v1/partners/topics` with `companyName` body field returns 201; `#should_return400_when_organizerSuggestsTopic_withoutCompanyName` — omitting `companyName` as organizer returns 400
**Action:** Add to Story 8.2 scope: "Organizers may suggest topics on behalf of a partner company by supplying `companyName` in the request body. Omitting `companyName` as an organizer returns 400."

---

## VALIDATED
- "Partners see only their own company's data (enforced at API level)" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerRequestsOtherCompany`
- "Organizers can view any company's analytics" → `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_organizerRequestsAnyCompany`
- "Single KPI: cost-per-attendee = partnership cost ÷ total company attendees" → `PartnerAnalyticsControllerIntegrationTest#should_computeCostPerAttendee_when_attendeesExist` (10000/18 ≈ 555.56)
- "costPerAttendee is null when no attendees" → `PartnerAnalyticsControllerIntegrationTest#should_returnNullCostPerAttendee_when_noAttendees`
- "Excel XLSX export" → `PartnerAnalyticsControllerIntegrationTest#should_returnXlsx_when_exportRequested`
- "fromYear parameter passed through to downstream client" → `PartnerAnalyticsControllerIntegrationTest#should_passFromYearToClient_when_fromYearParamProvided`
- "Unauthenticated analytics access returns 403" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_notAuthenticated`
- "Partner cannot export another company's data (403)" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerExportsOtherCompany`
- "Topic list sorted by votes descending" → `TopicControllerIntegrationTest#should_listTopicsSortedByVoteCountDesc`
- "Topic fields: id, title, description, suggestedByCompany, voteCount, status, currentPartnerHasVoted" → `TopicControllerIntegrationTest#should_showTopicFields_whenListingTopics`
- "Toggle vote: cast increments, remove decrements" → `TopicControllerIntegrationTest#should_castVote_and_increment_voteCount`, `#should_removeVote_and_decrement_voteCount`
- "Voting is idempotent (double-vote stays at 1, delete non-existent vote is no-op)" → `TopicControllerIntegrationTest#should_beIdempotent_when_votingTwice`, `#should_beIdempotent_when_removingVoteThatDoesNotExist`
- "New topic status defaults to PROPOSED" → `TopicControllerIntegrationTest#should_createTopic_when_validTitleProvided`
- "Organizer can set status to SELECTED with optional plannedEvent" → `TopicControllerIntegrationTest#should_allowOrganizer_to_updateTopicStatus_to_SELECTED`
- "Organizer can set status to DECLINED" → `TopicControllerIntegrationTest#should_allowOrganizer_to_updateTopicStatus_to_DECLINED`
- "Partners cannot change topic status (403)" → `TopicControllerIntegrationTest#should_return403_when_partnerTriesToUpdateStatus`
- "Partners see plannedEvent when topic is SELECTED" → `TopicControllerIntegrationTest#should_showPlannedEvent_when_topicIsSelected`
- "Meeting date auto-filled from linked BATbern event" → `PartnerMeetingControllerIntegrationTest#should_createMeeting_when_validRequestProvided`
- "202 Accepted for send-invite, inviteSentAt persisted" → `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled`, `#should_setInviteSentAt_when_sendInviteCalled`
- "Agenda and notes independently patchable" → `PartnerMeetingControllerIntegrationTest#should_updateAgendaAndNotes_independently`
- "Partners cannot list/create meetings or send invites (403)" → `PartnerMeetingControllerIntegrationTest#should_return403_when_partnerTriesToListMeetings`, `#should_return403_when_partnerTriesToCreateMeeting`, `#should_return403_when_partnerTriesToSendInvite`
- "Notes list sorted by createdAt descending" → `PartnerNoteControllerIntegrationTest#should_returnNotesSortedByCreatedAtDesc_when_multipleNotesExist`
- "Author username captured from JWT" → `PartnerNoteControllerIntegrationTest#should_createNote_when_validRequest` (asserts `authorUsername` notNull)
- "PARTNER role receives 403 on all note endpoints" → `PartnerNoteControllerIntegrationTest#should_return403_when_partnerTriesToListNotes`, `#should_return403_when_partnerTriesToCreateNote`
- "404 on unknown company for notes" → `PartnerNoteControllerIntegrationTest#should_return404_when_companyNameUnknown`

---
# Doc Audit Findings — Speaker Authentication (Epic 9)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-9-speaker-authentication.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 0
- MISMATCH: 1
- UNTESTED: 9
- UNDOCUMENTED: 2

**Key finding:** The entire Epic 9 test suite is absent from the searched directory. The
speaker-coordination-service test folder contains only 3 files — `AbstractIntegrationTest`,
`TestSecurityConfig` (both tagged Story 5.4), and `HealthControllerIntegrationTest` — none of
which exercise any Epic 9 business rule. According to the doc itself, Story 9.1 is implemented
inside `event-management-service`, not `speaker-coordination-service`, which explains the gap.
However the doc's testing strategy ("Integration tests", "E2E tests") never clarifies *which*
service owns the tests, creating an audit blind spot.

---

## MISMATCH

### M1 — Story 9.1 implementation service contradicts audit scope
**Doc claims:** Story 9.1 is "Implemented in:
`services/event-management-service/.../MagicLinkService.java`" and
`services/event-management-service/.../SpeakerMagicLoginController.java`
**Test asserts:** `services/speaker-coordination-service/src/test/java` contains zero tests
referencing JWT, magic link, or `SpeakerMagicLoginController`. The only controller test present
is `HealthControllerIntegrationTest`.
**Action:** The doc's "Testing" section for Story 9.1 should explicitly state that integration
tests live in `services/event-management-service/src/test/java`, not in the speaker-coordination
service. Add: "Integration tests location: `event-management-service`" to each story's Testing
block.

---

## UNTESTED

### U1 — JWT token 30-day expiry (Story 9.1 AC1 & AC4)
**Doc claims:** "Magic link emails contain JWT tokens (30-day expiry, reusable)" and "JWT tokens
support same 30-day reusability as Epic 6 tokens"
**Risk:** High — no test in the searched scope verifies expiry duration or reusability across
multiple uses within 30 days.

### U2 — JWT claims content (Story 9.1 AC6)
**Doc claims:** "JWT tokens include user_id, email, roles (SPEAKER), expiration timestamp"
**Risk:** High — no test validates the exact claim set embedded in generated tokens.

### U3 — HTTP-only cookie storage (Story 9.1 AC2)
**Doc claims:** "Clicking magic link extracts JWT from URL, stores in HTTP-only cookie"
**Risk:** High (security) — no test in scope verifies that the response sets a cookie with
`HttpOnly` and `Secure` attributes.

### U4 — Invalid/expired JWT error messaging (Story 9.1 AC5)
**Doc claims:** "Invalid/expired JWT tokens show clear error message with contact info"
**Risk:** Medium — no test asserts the shape or content of the error response for bad tokens.

### U5 — Email-match role extension vs. new account creation (Story 9.2 AC1)
**Doc claims:** "Email doesn't exist in Cognito → create new user with SPEAKER role + temp
password" and "Email exists (attendee account) → add SPEAKER role to existing account (no
duplicate)"
**Risk:** High — the branching logic (create vs. extend) is the core invariant of Epic 9 and
is completely untested in this service.

### U6 — Zero duplicate accounts (Story 9.2 AC5 & Risk 2 mitigation)
**Doc claims:** "Zero duplicate accounts created (email uniqueness enforced)" and "Integration
tests validate duplicate prevention"
**Risk:** High — no deduplication test exists in the searched directory.

### U7 — Dual auth paths produce equivalent JWTs (Story 9.3 AC3)
**Doc claims:** "Both methods result in same JWT token (same claims, same session)"
**Risk:** Medium — equivalence between magic-link JWT and password JWT is untested.

### U8 — 7-day grace period for deprecated tokens (Story 9.4 AC3)
**Doc claims:** "Old token-based magic links marked as deprecated (still work for 7-day grace
period)"
**Risk:** Medium — no test verifies that legacy `speaker_tokens` still authenticate within the
grace window and are rejected after it.

### U9 — RS256 signing algorithm (Security Considerations)
**Doc claims:** "Tokens signed with RS256 (asymmetric encryption)" and "JWT signature
validation" listed in Security Tests
**Risk:** High — algorithm enforcement (rejecting HS256 or unsigned tokens) has no test
coverage in scope.

---

## UNDOCUMENTED

### N1 — Health endpoint returns UP
**Test:** `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled`
— asserts `GET /actuator/health` → HTTP 200, `$.status == "UP"`
**Action:** Not a business-rule gap, but Epic 9 doc has no mention of actuator/health SLA.
Low priority; add to a general "operational readiness" section if desired.

### N2 — Info endpoint returns 200
**Test:** `HealthControllerIntegrationTest#should_returnServiceInfo_when_infoEndpointCalled`
— asserts `GET /actuator/info` → HTTP 200
**Action:** Same as N1; operational test unrelated to Epic 9 business rules.

---

## VALIDATED
*(none — no Epic 9 business-rule tests exist in the searched scope)*

---
# Doc Audit Findings — User Lifecycle Sync
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06b-user-lifecycle-sync.md`
**Tests searched:** `services/company-user-management-service/src/test/java`

## Summary
- VALIDATED: 9
- MISMATCH: 6
- UNTESTED: 7
- UNDOCUMENTED: 9

---

## MISMATCH

### M1 — JIT Provisioning is explicitly declared absent, but exists as a core component

**Doc claims:** "❌ No JIT (Just-In-Time) Provisioning — Reason: PostConfirmation Lambda creates database users automatically. No users exist in Cognito without database records. Alternative: PostConfirmation handles all user creation."

**Test asserts:** `JITUserProvisioningInterceptorTest` — 14 tests covering `JITUserProvisioningInterceptor.preHandle()` which provisions users on first authenticated API request (creates DB record from JWT claims if `findByCognitoUserId()` returns empty). Tests include role assignment from JWT authorities, username collision handling, non-blocking error behaviour, and event publishing with `source = "JIT_PROVISIONING"`.

**Action:** Replace the "❌ No JIT Provisioning" section with a description of Pattern 1b: JIT Provisioning Interceptor, which runs on every authenticated API request and creates a DB user if none exists. PostConfirmation Lambda is the primary path; JIT is the safety net.

---

### M2 — Reconciliation Jobs are declared absent, but a full reconciliation service exists

**Doc claims:** "❌ No Reconciliation Jobs — Reason: Unidirectional sync eliminates drift. Database never needs to sync back to Cognito. Alternative: None needed."

**Test asserts:** `UserReconciliationServiceTest` — 15 tests for `UserReconciliationService.reconcileUsers()` and `checkSyncStatus()`. The service iterates all active DB users, checks each against Cognito via `adminGetUser`, deactivates orphans (sets `isActive = false`, `deactivationReason = "Cognito user deleted"`), and creates missing users found in Cognito but absent from DB. Reports `orphanedUsers`, `missingUsers`, duration, and errors. Publishes metrics via `UserSyncMetricsService`.

**Action:** Remove "❌ No Reconciliation Jobs". Add a section describing the reconciliation job, its bidirectional checks, and the deactivation/creation logic.

---

### M3 — PreTokenGeneration Lambda documented as only adding `custom:role`; tests show `custom:username` is also expected

**Doc claims:** PreTokenGeneration code only adds `'custom:role': roles.join(',')` to the JWT. The JWT example contains: `sub`, `cognito:username`, `email`, `custom:role`, `custom:language`, `iss`, `exp`, `iat`.

**Test asserts:** `SecurityContextHelperTest#should_extractUsername_when_customUsernameClaimPresent` — reads `custom:username` claim from JWT and returns it as the current user's username. Comment: `"ADR-001: PreTokenGeneration Lambda sets custom:username claim from database"`. Also `SecurityContextHelperTest#should_extractCompanyId_when_presentInToken` reads `custom:companyId`.

**Action:** Update PreTokenGeneration Lambda code snippet and JWT example to include `custom:username` (DB username) and `custom:companyId` claims. Update the "JWT Token Example" block accordingly.

---

### M4 — Schema says `cognito_user_id NOT NULL`; tests prove null is valid for pre-invited users

**Doc claims:** "cognito_user_id is NOT NULL for self-registered users (always populated by PostConfirmation)" and the schema shows `cognito_user_id VARCHAR(255) NOT NULL UNIQUE`.

**Test asserts:**
- `JITUserProvisioningInterceptorTest#should_linkCognitoIdToExistingUser_when_emailAlreadyExistsInDatabase` — creates `preExistingUser = createUser(null, "partner.user", email, ...)` with `cognitoUserId = null` stored in DB. The interceptor then links the Cognito ID to this existing record on first login.
- `UserReconciliationServiceTest#should_skipUsers_when_noCognitoUserId` — creates a user with `cognitoUserId(null)` in the DB and asserts it is skipped during the orphan check (`verify(cognitoClient, never()).adminGetUser(...)`).

**Action:** Change the schema comment to reflect that `cognito_user_id` is nullable (pre-invited users), and add a note that users without a `cognito_user_id` are skipped in reconciliation and linked on first login via the JIT interceptor.

---

### M5 — SecurityConfig declares `anyRequest().authenticated()`; company search is publicly accessible without auth

**Doc claims:** SecurityConfig code snippet (Pattern 3) shows:
```java
.anyRequest().authenticated()
```
implying all unlisted routes require authentication.

**Test asserts:** `AuthenticationIntegrationTest#should_allowSearch_when_notAuthenticated` (Test 10.9) — performs `GET /api/v1/companies/search` without any authentication and asserts `status().isOk()`. Comment: "Story 4.1.5: Company search is now public for registration autocomplete".

**Action:** Update the SecurityConfig code snippet in the doc to add an explicit `permitAll()` rule for the company search endpoint (e.g., `"/api/v1/companies/search"`), before the `anyRequest().authenticated()` catch-all.

---

### M6 — Doc omits that DB-Cognito sync is not strictly unidirectional; reconciliation deactivates DB users based on Cognito state

**Doc claims:** "Unidirectional Sync: Cognito → Database only (via Lambda triggers)" and "No Bidirectional Sync (Database → Cognito)".

**Test asserts:** `UserReconciliationServiceTest#should_deactivateOrphanedUsers_when_usersInDbNotInCognito` — the reconciliation service reads Cognito user state and writes it back to the DB (setting `isActive = false`). This is still a Cognito→DB direction, but it involves Cognito acting as the authoritative source to drive DB mutations (deactivations). The doc's framing of "no reconciliation jobs" is incorrect (see M2), and the "unidirectional" claim needs qualification: there is a reconciliation job that reads Cognito and mutates the DB.

**Action:** Qualify "Unidirectional Sync" to clarify it means no reverse sync (DB→Cognito), but a reconciliation job does read Cognito state to deactivate orphaned DB users.

---

## UNTESTED

### U1 — PostConfirmation Lambda performance target: < 1s p95

**Doc claims:** "Performance: Completes within 1 second (p95 latency requirement)"

**Risk:** Low — this is an operational SLA, not a business rule. Unlikely to be wrong, but no test validates it.

---

### U2 — PreTokenGeneration Lambda performance target: < 500ms p95

**Doc claims:** "Performance: Completes within 500ms (p95 latency requirement)"

**Risk:** Low — same as U1.

---

### U3 — Lambda timeout values (10s / 5s)

**Doc claims:** "Lambda timeout: 10s for PostConfirmation, 5s for PreTokenGeneration"

**Risk:** Low — infrastructure config, not validated by unit/integration tests.

---

### U4 — CloudWatch alarm thresholds

**Doc claims:** "`HighUserCreationFailureRate` — Triggers when failures exceed 5 per 5-minute window" and "`HighLambdaLatency` — Triggers when latency exceeds 2 seconds (average over 5 minutes)"

**Risk:** Low — CDK config, no unit test coverage. Alarm thresholds may have drifted from doc.

---

### U5 — Database connection pool: max 2, 30s idle timeout, 5s connection timeout

**Doc claims:** Lambda DB pool `max: 2`, `idleTimeoutMillis: 30000`, `connectionTimeoutMillis: 5000`.

**Risk:** Low — Lambda-level config, not covered by Java tests.

---

### U6 — `is_active` flag checked by application logic to block inactive users

**Doc claims:** "No PreAuthentication Trigger — Alternative: Application logic checks `is_active` flag in database."

**Risk:** Medium — the reconciliation service deactivates users, but no test validates that deactivated users are actually blocked from API access. The enforcement path is untested.

---

### U7 — Provisioned concurrency for PostConfirmation and PreTokenGeneration Lambdas

**Doc claims:** "Provisioned concurrency for PostConfirmation and PreTokenGeneration Lambdas"

**Risk:** Low — infrastructure config, no unit test coverage.

---

## UNDOCUMENTED

### N1 — `custom:username` JWT claim set by PreTokenGeneration Lambda

**Test:** `SecurityContextHelperTest#should_extractUsername_when_customUsernameClaimPresent` — asserts that `SecurityContextHelper.getCurrentUsername()` reads the `custom:username` claim (e.g., `"john.doe"`) from the JWT. Comment attributes this to ADR-001: "PreTokenGeneration Lambda sets custom:username claim from database".

**Action:** Add `custom:username` to the JWT Token Example block and to the PreTokenGeneration Lambda implementation snippet in the doc. Describe fallback to `sub` when the claim is absent (validated by `should_fallbackToSubject_when_customUsernameClaimMissing`).

---

### N2 — `custom:companyId` JWT claim

**Test:** `SecurityContextHelperTest#should_extractCompanyId_when_presentInToken` — reads `custom:companyId` from JWT. `should_returnNull_when_companyIdNotInToken` confirms it's optional.

**Action:** Add `custom:companyId` to the JWT Token Example and PreTokenGeneration description.

---

### N3 — Minimum 2 organizers business rule enforced at role removal

**Test:** `RoleServiceTest#should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains` — throws `MinimumOrganizersException` with message "minimum of 2 organizers" when removing the last organizer. Also enforced by `setRoles` (`should_throwMinimumOrganizersException_when_setRolesWouldRemoveLastOrganizer`).

**Action:** Add a "Business Rules" section to the doc (or expand the role_assignments section) describing the minimum 2 organizers constraint.

---

### N4 — `deactivationReason` field on user entity

**Test:** `UserReconciliationServiceTest#should_deactivateUser_when_orphanDetected` — asserts `user.getDeactivationReason() == "Cognito user deleted"`.

**Action:** Add `deactivation_reason` column to the `user_profiles` schema block.

---

### N5 — Users with null `cognito_user_id` are skipped in reconciliation

**Test:** `UserReconciliationServiceTest#should_skipUsers_when_noCognitoUserId` — user with `cognitoUserId = null` is skipped; `adminGetUser` is never called for it.

**Action:** Add a note to the Reconciliation section: users without a Cognito ID (pre-invited, not yet linked) are excluded from the orphan check.

---

### N6 — `activity_history` table exists in the schema

**Test:** `UserManagementMigrationsTest#should_createActivityHistoryTable_when_migrationsRun` — asserts the `activity_history` table exists after migrations.

**Action:** Add `activity_history` table to the Database Schema section.

---

### N7 — Additional notification preference columns not in doc schema

**Test:** `UserManagementMigrationsTest#should_haveEmbeddedPreferencesColumns_when_userProfilesTableExists` — asserts columns `pref_in_app_notifications`, `pref_push_notifications`, `pref_notification_frequency` exist, in addition to `pref_theme`, `pref_language`, `pref_email_notifications`.

**Action:** Add the three missing `pref_*` columns to the `user_profiles` schema block in the doc.

---

### N8 — Additional settings columns not in doc schema

**Test:** `UserManagementMigrationsTest#should_haveEmbeddedSettingsColumns_when_userProfilesTableExists` — asserts columns `settings_show_email` and `settings_show_company` exist, which are not in the doc's `user_profiles` CREATE TABLE block.

**Action:** Add `settings_show_email` and `settings_show_company` columns to the `user_profiles` schema block.

---

### N9 — JIT provisioning assigns roles from JWT authorities, not always ATTENDEE

**Test:** `JITUserProvisioningInterceptorTest#should_assignRoleFromJWT_when_jitProvisioningUser` — when a new user is JIT-provisioned and the JWT carries `ROLE_ORGANIZER`, the created user gets `Role.ORGANIZER`. `should_assignMultipleRoles_when_userHasMultipleAuthorities` confirms multi-role assignment. `should_defaultToAttendeeRole_when_noRolesInJWT` confirms ATTENDEE fallback when JWT has no roles.

**Action:** Once M1 (JIT section) is added to the doc, document that JIT-provisioned users receive roles extracted from the JWT's `GrantedAuthority` list, defaulting to ATTENDEE when no roles are present.

---

## VALIDATED
- "Roles stored in `role_assignments` table" → `RoleServiceTest` operates exclusively on the `roles` set via `UserRepository`; no Cognito group calls made
- "JWT claim `custom:role` with comma-separated values" → `SecurityContextHelperTest#should_extractUserRoles_when_authenticated` parses `"ORGANIZER,SPEAKER"` into 2 entries; whitespace trimmed (`should_trimRoles_when_roleClaimHasWhitespace`)
- "Empty roles returned on DB error in PreTokenGeneration" → `SecurityContextHelperTest#should_returnEmptyList_when_roleClaimMissing` and `should_returnEmptyList_when_roleClaimEmpty`
- "Role enum: ORGANIZER, SPEAKER, PARTNER, ATTENDEE (exactly 4)" → `RoleTest#should_haveAllRoles_when_enumDefined`
- "Default ATTENDEE role assigned on JIT user creation (no JWT roles)" → `JITUserProvisioningInterceptorTest#should_defaultToAttendeeRole_when_noRolesInJWT`
- "Username format `firstname.lastname`, numeric suffix for duplicates (`john.doe.2`)" → `JITUserProvisioningInterceptorTest#should_addNumericSuffix_when_usernameAlreadyExists`; `UserManagementMigrationsTest#should_enforceUsernameFormat_when_invalidUsernameInserted` (rejects `john.doe.abc`)
- "Non-blocking JIT provisioning error handling" → `JITUserProvisioningInterceptorTest#should_continueRequest_when_jitProvisioningFails` and `should_continueRequest_when_userSaveFails`
- "`role_assignments` cascade deletes when user deleted" → `UserManagementMigrationsTest#should_cascadeDeleteRoles_when_userDeleted`
- "ORGANIZER-only endpoints for admin operations; ATTENDEE can read" → `AuthenticationIntegrationTest` (Tests 10.1–10.8): ORGANIZER creates/updates, SPEAKER forbidden to update, ATTENDEE allowed to list/search

---
# Doc Audit Findings — Notification System
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06d-notification-system.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`, `services/event-management-service/src/test/java`

## Summary
- VALIDATED: 7
- MISMATCH: 6
- UNTESTED: 5
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Migration file number (V25 vs V33)
**Doc claims:** "Location: `services/event-management-service/src/main/resources/db/migration/V25__Create_notifications_table.sql`"
**Test asserts:** `NotificationControllerIntegrationTest` comment states "Migration V33 applied (notifications table)" — the controller integration test explicitly cites V33, not V25.
**Action:** Update doc migration path to `V33__Create_notifications_table.sql`.

---

### M2 — HTTP client class name: `UserServiceClient` vs `UserApiClient`
**Doc claims:** `NotificationService` depends on `UserServiceClient` (see code snippets throughout the doc, e.g. `private final UserServiceClient userServiceClient`)
**Test asserts:** `NotificationServiceTest#setUp` constructs `NotificationService(notificationRepository, emailService, userApiClient, ...)` where `userApiClient` is typed `UserApiClient` — a different class name with a different import (`ch.batbern.events.client.UserApiClient`).
**Action:** Replace all occurrences of `UserServiceClient` with `UserApiClient` in the doc.

---

### M3 — EmailService method signature: `send(Notification)` vs `sendHtmlEmail(String, String, String)`
**Doc claims:** `emailService.send(notification)` (full `Notification` object passed; AWS SES built inside `EmailService`)
**Test asserts:** `NotificationServiceTest#should_createNotificationRecord_when_sendingEmail` verifies `verify(emailService).sendHtmlEmail(eq("john.doe@example.com"), eq("Test Subject"), anyString())` — three scalar arguments: recipient address, subject, HTML body.
**Action:** Update the EmailService integration snippet to show `emailService.sendHtmlEmail(recipientEmail, notification.getSubject(), htmlContent)`.

---

### M4 — In-app query method: `findPublishedAfter` vs `findByPublishedAtAfter`
**Doc claims:** `List<Event> newEvents = eventRepository.findPublishedAfter(lastLogin);`
**Test asserts:** `NotificationServiceTest#should_queryDynamically_when_gettingInAppNotifications` stubs `when(eventRepository.findByPublishedAtAfter(lastLogin)).thenReturn(...)` — method name follows Spring Data naming convention `findByPublishedAtAfter`.
**Action:** Update doc method call to `eventRepository.findByPublishedAtAfter(lastLogin)`.

---

### M5 — TaskDeadlineReminderJob: class name, lookup window, and delegation target
**Doc claims:** Class `TaskDeadlineReminderJob` runs `@Scheduled(cron = "0 0 9 * * *")`, calls `taskRepository.findByDueDateBeforeAndStatusNot(threeDaysFromNow, "completed")` (3-day lookahead), and calls `notificationService.createAndSendEmailNotification(...)` directly.
**Test asserts:** `TaskDeadlineReminderSchedulerTest` tests class `TaskDeadlineReminderScheduler`, which calls `eventTaskRepository.findTasksDueForReminder(from, to)` with a **tomorrow-only window** (start-of-tomorrow → start-of-day-after-tomorrow in `Europe/Zurich`), and delegates to `TaskReminderEmailService#sendTaskDeadlineReminder(task, Locale.GERMAN)` — not `NotificationService`.
Three specific assertions differ: (1) class name, (2) 1-day vs 3-day lookahead, (3) delegation target (`TaskReminderEmailService` vs `NotificationService`).
**Action:** Replace the `TaskDeadlineReminderJob` section with `TaskDeadlineReminderScheduler`, update the lookup window to tomorrow-only in Swiss timezone, and update the delegation target.

---

### M6 — Notification status `UNREAD` not in documented status set
**Doc claims:** Entity comment lists valid statuses as `PENDING, SENT, FAILED, READ`.
**Test asserts:** `NotificationControllerIntegrationTest#setUp` saves three notifications with `status("UNREAD")`, and `should_returnUnreadOnly_when_statusUnread` / `should_returnUnreadCount_when_statusUnread` filter by `status=UNREAD`. `UNREAD` is a live status in use.
**Action:** Add `UNREAD` to the documented status enum: `PENDING, UNREAD, SENT, FAILED, READ`.

---

## UNTESTED

### U1 — EscalationRuleEngine thresholds
**Doc claims:** Three specific thresholds in `EscalationRuleEngine`:
- `DEADLINE_WARNING` escalates when `timeUntilDeadline.toDays() <= 3`
- `QUALITY_REVIEW_PENDING` escalates when review age `>= 7 days`
- `VOTING_REQUIRED` escalates when `< 50%` voted after `>= 3 days`
No test class for `EscalationRuleEngine` was found in either test directory.
**Risk:** high — these thresholds could drift silently; the 50%-voting and 7-day-review rules are completely unverified.

---

### U2 — Quiet hours logic
**Doc claims:** `isInQuietHours(UserPreferences prefs)` uses `LocalTime` comparison to suppress notifications during configured quiet hours (default 22:00–07:00). Also contains a suspected bug: the `if (start.isBefore(end))` branch comment says "Normal range (e.g., 22:00-07:00 next day)" but 22:00→07:00 crosses midnight so `start.isBefore(end)` is false — the described range falls into the `else` branch.
No test exercises quiet hours suppression or the midnight-crossing edge case.
**Risk:** high — the example in the doc may reflect a logic inversion bug that would cause quiet-hours notifications to fire, and morning-hours notifications to be suppressed.

---

### U3 — `onEventPublished` event listener
**Doc claims:** `@EventListener @Async public void onEventPublished(EventPublishedEvent event)` sends EMAIL notifications to all registered attendees when an event is published, using `registrationRepository.findUsernamesByEventId(event.getEventId())`.
No test mocks or verifies this listener firing.
**Risk:** medium — the listener is async and the `findUsernamesByEventId` method name is never exercised in tests (deadline tests use `findUsernamesByEventCode`).

---

### U4 — `escalateToOrganizers` sending URGENT notifications
**Doc claims:** When escalation is triggered, `escalateToOrganizers()` calls `userServiceClient.getOrganizerUsernames()` and sends URGENT-priority notifications to all organizers.
No test verifies escalation email dispatch or the organizer-lookup HTTP call.
**Risk:** medium — escalation is silent failure territory.

---

### U5 — `DEADLINE_REMINDER` notification type
**Doc claims:** The `NotificationType` enum defines: `SPEAKER_INVITED, SPEAKER_ACCEPTED, SPEAKER_DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEW_PENDING, QUALITY_REVIEW_APPROVED, QUALITY_REVIEW_REQUIRES_CHANGES, SLOT_ASSIGNED, DEADLINE_WARNING, OVERFLOW_DETECTED, VOTING_REQUIRED, EVENT_PUBLISHED`.
**Test uses:** `NotificationControllerIntegrationTest#setUp` creates a notification with `notificationType("DEADLINE_REMINDER")` — a string value absent from the documented enum.
**Risk:** low — `notificationType` is a VARCHAR column, so the value works at runtime, but the enum documentation is incomplete.

---

## UNDOCUMENTED

### N1 — SimpMessagingTemplate injected into NotificationService
**Test:** `NotificationServiceTest#setUp` constructs `NotificationService(notificationRepository, emailService, userApiClient, eventRepository, registrationRepository, messagingTemplate)` where `messagingTemplate` is typed `org.springframework.messaging.simp.SimpMessagingTemplate`. The doc explicitly states "Real-Time Notifications (Future Work) — Status: Not implemented … deferred to future story."
**Action:** Remove the "not implemented" statement or add a note that `SimpMessagingTemplate` is already wired into the service constructor (likely for the `watch/` subsystem integration).

---

### N2 — TaskDeadlineReminderScheduler uses Europe/Zurich timezone
**Test:** `TaskDeadlineReminderSchedulerTest#should_queryTomorrowWindow_in_swissTimezone` asserts the scheduler computes start-of-tomorrow and start-of-day-after-tomorrow using `ZoneId.of("Europe/Zurich")`.
**Action:** Add to the scheduler section: "Tomorrow window is computed in `Europe/Zurich` timezone."

---

### N3 — TaskDeadlineReminderScheduler forces German locale
**Test:** `TaskDeadlineReminderSchedulerTest#should_useGermanLocale_for_allReminders` verifies `sendTaskDeadlineReminder(task, Locale.GERMAN)` — all task reminder emails are German-only regardless of user preference.
**Action:** Add to the scheduler section: "Emails rendered in `Locale.GERMAN`; user language preference is not consulted for task reminders."

---

### N4 — TaskDeadlineReminderScheduler swallows repository exceptions
**Test:** `TaskDeadlineReminderSchedulerTest#should_notThrow_when_repositoryFails` verifies the scheduler completes without re-throwing when the repository throws `RuntimeException`.
**Action:** Add error-handling note: "Scheduler catches and logs all `RuntimeException` from the repository; the job does not fail the Spring scheduler thread."

---

### N5 — `TASK_DEADLINE_WARNING` notification type never exercised
**Test:** `DeadlineReminderJobTest#should_includeDeadlineInfo_when_sendingNotification` verifies type `"DEADLINE_WARNING"` is used for registration deadline reminders. The doc's `TaskDeadlineReminderJob` snippet uses `"TASK_DEADLINE_WARNING"`, but the actual `TaskDeadlineReminderScheduler` delegates to `TaskReminderEmailService` and no `NotificationService` call is verified — `"TASK_DEADLINE_WARNING"` is never asserted in any test.
**Action:** Clarify whether `TASK_DEADLINE_WARNING` is still a valid type or whether the scheduler bypasses the notifications table entirely by delegating directly to `TaskReminderEmailService`.

---

## VALIDATED
- "Create notification record before sending email (audit trail)" → `NotificationServiceTest#should_createNotificationRecord_when_sendingEmail` (2× `repository.save` verified)
- "Skip notification when user opted out of email" → `NotificationServiceTest#should_skipNotification_when_userOptedOut`
- "Status → SENT + sentAt set on successful delivery" → `NotificationServiceTest#should_updateStatusToSent_when_emailDeliverySucceeds`
- "Status → FAILED + failedAt + failureReason set on delivery exception" → `NotificationServiceTest#should_updateStatusToFailed_when_emailDeliveryFails`
- "In-app notifications queried dynamically (zero DB writes)" → `NotificationServiceTest#should_queryDynamically_when_gettingInAppNotifications`
- "Deadline reminder finds events with deadlines in next 3 days via `findByRegistrationDeadlineBetween`" → `DeadlineReminderJobTest#should_findUpcomingDeadlines_when_jobExecutes`
- "Repository query methods: `findByRecipientUsername`, `findByRecipientUsernameAndStatus`, `countByRecipientUsernameAndStatus`, `findByRecipientUsernameAndChannelOrderByCreatedAtDesc`, `findByStatusAndCreatedAtBefore`" → `NotificationRepositoryTest` (5 tests)

---
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

---
# Doc Audit Findings — User guide — speaker portal
**Audited:** 2026-03-09
**Doc:** `docs/user-guide/speaker-portal` (README.md, invitation-response.md, content-submission.md, dashboard.md)
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 1
- MISMATCH: 0
- UNTESTED: 22
- UNDOCUMENTED: 0

**Overall:** The speaker-coordination-service test suite contains exactly **3 test files** with **2 meaningful test methods**, both of which only exercise the Spring Actuator health/info endpoints. There is **zero test coverage** for any speaker portal business rule. Every functional claim in these four documentation files is untested.

---

## MISMATCH

_None found._

---

## UNTESTED

### U1 — RESPOND token is single-use
**Doc claims (README.md):** "RESPOND — Valid For: 30 days | Reusable? No (single-use) | Used For: Accept / Decline invitation"
**Also (invitation-response.md):** "they become invalid after the first click"
**Risk:** high — if single-use enforcement is missing, a token could be replayed by a third party to accept/decline on behalf of a speaker

### U2 — VIEW token is reusable
**Doc claims (README.md):** "VIEW — Valid For: 30 days | Reusable? Yes | Used For: Dashboard, profile updates, content submission"
**Risk:** medium — if VIEW tokens are accidentally marked single-use, speakers lose dashboard access after first click

### U3 — Token validity period is 30 days
**Doc claims (README.md):** "Valid For: 30 days" for both RESPOND and VIEW tokens
**Also (invitation-response.md):** "RESPOND tokens are valid for 30 days from the invitation send date"
**Risk:** high — no test verifies token expiry logic; an off-by-one or missing expiry check would silently accept expired tokens forever or reject tokens prematurely

### U4 — Token is 32-byte SecureRandom; only SHA-256 hash stored
**Doc claims (README.md):** "Tokens are cryptographically generated (32-byte SecureRandom) and only their SHA-256 hash is stored in the database — the token itself is never persisted."
**Risk:** high — if the raw token is persisted instead of the hash, PII/security breach risk on DB compromise

### U5 — Rate limiting: 5 requests/minute per IP
**Doc claims (README.md):** "Rate limiting: 5 requests/minute per IP."
**Risk:** medium — no test validates the rate-limit threshold or that excess requests are rejected with 429

### U6 — Accept transitions INVITED → ACCEPTED
**Doc claims (invitation-response.md):** "Speaker status transitions: INVITED → ACCEPTED"
**Risk:** high — core state machine transition; if absent or wrong, downstream quality-review and publication flows break

### U7 — Decline transitions INVITED → DECLINED
**Doc claims (invitation-response.md):** "Speaker status transitions: INVITED → DECLINED"
**Risk:** high — organiser kanban and reporting depend on DECLINED state being set correctly

### U8 — Decline reason is required
**Doc claims (invitation-response.md):** "Reason for declining *:" (shown as required field)
**Risk:** medium — if backend accepts an empty decline reason, organiser contact history is incomplete

### U9 — `change_reason = 'SPEAKER_PORTAL_RESPONSE'` recorded in status history
**Doc claims (invitation-response.md):** "Status history records the transition with `change_reason = 'SPEAKER_PORTAL_RESPONSE'`"
**Risk:** medium — audit trail correctness; no test verifies the exact value stored

### U10 — Confirmation email sent on acceptance
**Doc claims (invitation-response.md):** "A confirmation email is sent automatically to the speaker with: Event and session details, Content submission deadline, Link to update their profile, Link to submit presentation content"
**Risk:** medium — email delivery untested; a missing domain event or email handler would silently skip confirmations

### U11 — Organiser notified in-app on accept and decline
**Doc claims (invitation-response.md):** "The organiser is notified in-app (async)" on acceptance; "The organiser is notified in-app" on decline
**Risk:** medium — async notification untested; broken event handler would silently suppress organiser alerts

### U12 — Content submission: title max 200 chars (required), abstract max 1000 chars (required)
**Doc claims (content-submission.md):**
> "Title — Required: Yes | Limit: 200 characters"
> "Abstract — Required: Yes | Limit: 1000 characters"
**Risk:** high — if backend does not enforce these limits, oversized content enters the DB and corrupts display

### U13 — Abstract warning shown when under 200 characters
**Doc claims (content-submission.md):** "Warning shown if under 200 chars"
**Risk:** low — UX guidance only; backend enforcement not implied, but doc states this as system behaviour

### U14 — File types restricted to PPTX, PDF, KEY; max 50 MB
**Doc claims (content-submission.md):**
> "Accepted formats: PPTX, PDF, KEY"
> "Maximum size: 50 MB"
**Risk:** high — if backend presigned-URL generation does not restrict content-type or size, speakers can upload arbitrary files to S3

### U15 — Files uploaded directly to S3 via presigned URL (never proxied through backend)
**Doc claims (content-submission.md):** "Files are uploaded directly to S3 via presigned URL — they are never sent through the backend."
**Risk:** medium — architecture correctness; a regression to proxied upload would cause large-file timeouts in production

### U16 — Draft auto-saved every 30 seconds; restored on re-visit
**Doc claims (content-submission.md):** "The portal automatically saves a draft every 30 seconds. If the speaker closes the browser and returns via their link, the draft is restored from the server."
**Risk:** medium — no test verifies server-side draft persistence or restoration; data loss on browser close if broken

### U17 — Content submission transitions ACCEPTED → CONTENT_SUBMITTED
**Doc claims (content-submission.md):** "Speaker status transitions: ACCEPTED → CONTENT_SUBMITTED"
**Risk:** high — organiser Phase C workflow depends on this state; if broken, submissions are invisible to organisers

### U18 — Each resubmission increments version number; all versions stored
**Doc claims (content-submission.md):** "Each resubmission increments the version number (v1, v2, v3…), and all versions are stored for the organiser."
**Risk:** medium — organiser review history and rollback capability depend on versioning; unverified

### U19 — Reminder tiers: Tier 1 = 14 days, Tier 2 = 7 days, Tier 3 = 3 days before deadline
**Doc claims (README.md):**
> "Tier 1 — Friendly: 14 days before deadline"
> "Tier 2 — Urgent: 7 days before deadline"
> "Tier 3 — Final: 3 days before deadline"
**Risk:** high — timing logic untested; wrong thresholds would send reminders at wrong times or not at all

### U20 — Reminders skipped if speaker already responded or submitted
**Doc claims (README.md):** "Reminders are skipped if the speaker has already responded or submitted content."
**Risk:** high — without this check, accepted/submitted speakers receive spam reminders

### U21 — After Tier 3, in-app notification created for organiser
**Doc claims (README.md):** "After Tier 3, an in-app notification is created for the organizer."
**Risk:** medium — organiser escalation path untested; silent failure if domain event is missing

### U22 — Dashboard upcoming events filtered to active states; past events to CONFIRMED or ACCEPTED with past date
**Doc claims (dashboard.md):**
> "Shows all events where the speaker is in an active state (invited, accepted, content submitted, quality reviewed, confirmed)."
> "Shows all events where: The event date is in the past, and The speaker was CONFIRMED or ACCEPTED"
**Risk:** medium — incorrect filter logic would show cancelled/declined speakers in upcoming view or exclude valid past events

---

## UNDOCUMENTED

_No undocumented test assertions found. The only substantive tests (`HealthControllerIntegrationTest`) verify Spring Actuator `/actuator/health` (status UP) and `/actuator/info` (HTTP 200). These are infrastructure-level checks not expected to appear in the speaker portal user guide._

---

## VALIDATED
- "Spring Boot service is operational / health UP" → `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled`

---

## Recommended Actions

The speaker-coordination-service is the most under-tested service in the BATbern backend relative to its documentation surface area. The following test gaps carry the highest production risk and should be addressed first:

| Priority | Finding | Why |
|----------|---------|-----|
| 🔴 Critical | U1 — RESPOND token single-use | Security: token replay attack |
| 🔴 Critical | U3 — 30-day expiry enforced | Security: expired tokens accepted indefinitely |
| 🔴 Critical | U4 — SHA-256 hash stored, not raw token | Security: DB breach exposes all tokens |
| 🔴 Critical | U6, U7 — State machine transitions | Core workflow correctness |
| 🔴 Critical | U17 — ACCEPTED → CONTENT_SUBMITTED | Organiser Phase C depends on this |
| 🟡 High | U12 — Title/abstract field limits | Data integrity |
| 🟡 High | U14 — File type & size enforcement | S3 security / abuse prevention |
| 🟡 High | U19 — Reminder tier timing | Speaker experience / spam prevention |
| 🟡 High | U20 — Reminders skip if already responded | Spam prevention |

---
# Doc Audit Findings — User Guide — Partner Portal
**Audited:** 2026-03-09
**Doc:** `docs/user-guide/partner-portal` (README.md, analytics.md, meetings.md, topic-voting.md)
**Tests searched:** `services/partner-coordination-service/src/test/java`

## Summary
- VALIDATED: 27
- MISMATCH: 2
- UNTESTED: 8
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Partner meeting list visibility
**Doc claims:** `README.md` data visibility table: "Meetings | All upcoming meetings | All meetings + management tools" — implying partners can view a list of upcoming meetings.
**Test asserts:** `PartnerMeetingControllerIntegrationTest#should_return403_when_partnerTriesToListMeetings` — `GET /api/v1/partner-meetings` with PARTNER role returns `403 Forbidden`.
**Action:** Update README.md data visibility table to: "Meetings | Receive .ics calendar invite via email only | All meetings + management tools" — partners have no read access to the meeting list; they only receive the .ics attachment.

### M2 — Location field required on meeting creation
**Doc claims:** `meetings.md` creation form table: "Location | Yes | Venue name and/or address"
**Test asserts:** `PartnerMeetingControllerIntegrationTest` helper `createMeeting(eventCode)` omits the `location` field entirely and expects `status().isCreated()` (201). No test validates that omitting location returns 400.
**Action:** Update meetings.md to mark Location as optional: "Location | No | Venue name and/or address (shown in .ics invite if provided)"

---

## UNTESTED

### U1 — 15-minute analytics cache
**Doc claims:** `analytics.md`: "Data is loaded on demand with a 15-minute cache, so the first load per session may take up to 5 seconds; subsequent loads are near-instant."
**Risk:** medium — cache layer may be misconfigured, absent, or have wrong TTL without any test catching it.

### U2 — XLSX export footer row
**Doc claims:** `analytics.md`: "A footer row with column totals and the cost-per-attendee metric" in the exported Excel file.
**Risk:** medium — `should_returnXlsx_when_exportRequested` only asserts `Content-Type` and `Content-Disposition` headers; actual Excel content (columns, footer) is never inspected.

### U3 — One vote per company, multi-user scenario
**Doc claims:** `topic-voting.md`: "If multiple users from the same company vote, only one vote is counted (last action wins)."
**Risk:** high — all vote tests use users from *different* companies. Two users from the same company voting simultaneously is the edge case that makes this claim testable but untested.

### U4 — Topic description max 500 characters
**Doc claims:** `topic-voting.md` suggestion form table: "Description | No | 500 characters"
**Risk:** low — constraint may not be enforced server-side.

### U5 — Topic title max 255 characters
**Doc claims:** `topic-voting.md` suggestion form table: "Title | Yes | 255 characters (minimum 5)"
**Risk:** low — minimum-5 is tested; maximum-255 is not.

### U6 — .ics file contains exactly two VEVENT blocks
**Doc claims:** `meetings.md`: "The calendar file contains exactly two VEVENT blocks — the partner lunch and the BATbern event."
**Risk:** high — no test inspects the .ics payload at all. A one-VEVENT implementation would pass all existing tests.

### U7 — .ics follows RFC 5545 with METHOD:REQUEST
**Doc claims:** `meetings.md`: "The .ics file follows RFC 5545 (iCalendar specification) and uses `METHOD:REQUEST` so the invite appears as an actionable calendar event in supported email clients."
**Risk:** medium — AWS SES attachment format and METHOD value are never verified.

### U8 — Analytics dashboard sorted by event date (most recent first)
**Doc claims:** `analytics.md`: "The main view is a table sorted by event date (most recent first)."
**Risk:** low — the dashboard test `should_returnDashboard_when_partnerRequestsOwnCompany` asserts row count and field values but not that rows are ordered by date descending.

---

## UNDOCUMENTED

### N1 — Organizer must supply `companyName` when suggesting a topic
**Test:** `TopicControllerIntegrationTest#should_return400_when_organizerSuggestsTopic_withoutCompanyName` — organizer `POST /api/v1/partners/topics` without a `companyName` field in the body returns 400.
**Action:** Add to `topic-voting.md` → "For Organisers" section: "When suggesting a topic on behalf of a partner, the `companyName` field is required in the request body. Omitting it returns 400."

### N2 — Cross-partner note ownership validation
**Test:** `PartnerNoteControllerIntegrationTest#should_return404_when_updatingNoteFromDifferentPartner` and `#should_return404_when_deletingNoteFromDifferentPartner` — patching or deleting a note ID that belongs to a *different* partner company via the wrong company URL returns 404, not 200.
**Action:** Add to `meetings.md` (or a future notes guide): "Notes are scoped to their partner. Attempting to update or delete a note via another partner company's URL returns 404."

### N3 — Partner company name max 12 characters
**Test:** `PartnerTest#should_enforceMaxLength_when_companyNameTooLong` — `companyName` exceeding 12 characters throws `IllegalArgumentException("...12 characters")`.
**Action:** Add to `README.md` or a partner management guide: "Partner company names are limited to 12 characters per ADR-003 (meaningful short identifiers)."

### N4 — Analytics `fromYear` query parameter
**Test:** `PartnerAnalyticsControllerIntegrationTest#should_passFromYearToClient_when_fromYearParamProvided` — the dashboard endpoint accepts an optional `fromYear` integer query param that is forwarded to the event management service for filtering.
**Action:** Add to `analytics.md` → "Time Period Toggle" section: "The toggle maps to a `fromYear` query parameter on `GET /api/v1/partners/{companyName}/analytics/dashboard`. The Last 5 Years option defaults to `currentYear - 5`; Full History omits the parameter."

### N5 — Organisers cannot vote on topics
**Test:** `TopicControllerIntegrationTest#should_return403_when_organizerTriesToVote` — `POST /api/v1/partners/topics/{id}/vote` with ORGANIZER role returns 403.
**Action:** Add to `topic-voting.md` → "For Organisers" section: "Organisers cannot cast or remove votes — the vote endpoints are restricted to the PARTNER role."

---

## VALIDATED
- "Each partner sees only their own company's data" (analytics) → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerRequestsOtherCompany`
- "Organiser can view any company's analytics" → `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_organizerRequestsAnyCompany`
- "Cost per attendee = Partnership cost ÷ Total company attendees" → `should_computeCostPerAttendee_when_attendeesExist` (10 000 / 18 ≈ 555.56)
- "If no employees attended … shows as N/A" (null) → `should_returnNullCostPerAttendee_when_noAttendees`
- "Export XLSX" returns correct content-type + Content-Disposition → `should_returnXlsx_when_exportRequested`
- "Partner cannot export another company's data" (403) → `should_return403_when_partnerExportsOtherCompany`
- "Unauthenticated access returns 403" (analytics) → `should_return403_when_notAuthenticated`
- "Topics sorted by vote count descending" → `TopicControllerIntegrationTest#should_listTopicsSortedByVoteCountDesc`
- "Vote toggle: cast adds, remove decrements" → `should_castVote_and_increment_voteCount`, `should_removeVote_and_decrement_voteCount`
- "Voting twice is idempotent (still 1 vote)" → `should_beIdempotent_when_votingTwice`
- "Remove vote idempotent (no error if not voted)" → `should_beIdempotent_when_removingVoteThatDoesNotExist`
- "Multiple companies each contribute one vote" → `should_countVotesAccurately_when_multiplePartnersVote`
- "New topic starts with status PROPOSED" → `should_createTopic_when_validTitleProvided`
- "Title minimum 5 characters" → `should_return400_when_titleTooShort`
- "Title is required" → `should_return400_when_titleMissing`
- "Partner cannot spoof company name in body" → `should_ignoreCompanyName_in_body_when_partnerSubmits`
- "Organisers can suggest topics on behalf of a partner" → `should_createTopic_when_organizerSubmits_onBehalfOfPartnerCompany`
- "Partner cannot update topic status (403)" → `should_return403_when_partnerTriesToUpdateStatus`
- "SELECTED status with plannedEvent note visible to partners" → `should_showPlannedEvent_when_topicIsSelected`
- "DECLINED status set by organiser" → `should_allowOrganizer_to_updateTopicStatus_to_DECLINED`
- "`currentPartnerHasVoted` flag correctly reflects vote state" → `should_markCurrentPartnerHasVoted_when_partnerHasVoted`
- "Send invite API responds 202 Accepted immediately" → `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled`
- "Invite Sent timestamp appears after send" → `should_setInviteSentAt_when_sendInviteCalled`
- "Meeting date auto-filled from linked event" → `should_createMeeting_when_validRequestProvided` (meetingDate = 2026-05-14 from mocked event)
- "Partner cannot create meetings (403)" → `should_return403_when_partnerTriesToCreateMeeting`
- "Partner cannot send invite (403)" → `should_return403_when_partnerTriesToSendInvite`
- "Partner notes hidden from partners (403)" → `PartnerNoteControllerIntegrationTest#should_return403_when_partnerTriesToListNotes`, `#should_return403_when_partnerTriesToCreateNote`

