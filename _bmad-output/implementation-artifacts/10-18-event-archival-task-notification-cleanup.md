# Story 10.18: Event Archival Task & Notification Cleanup

Status: ready-for-dev

<!-- No prerequisites — independent bug fix / data cleanup story -->

## Story

As an **organizer**,
I want all pending tasks and in-app notifications for an event to be automatically cleaned up when the event is archived,
so that my task board and notification center are not cluttered with stale items from past events.

## Acceptance Criteria

1. **AC1 — Task bulk cancellation on archival**: When an event transitions to `ARCHIVED` state, all non-completed tasks for that event are bulk-updated to `status = 'cancelled'`. Tasks already in `status = 'completed'` or `status = 'cancelled'` are skipped (idempotency).

2. **AC2 — Scheduler excludes cancelled tasks**: `EventTaskRepository.findTasksDueForReminder()` query explicitly excludes `status IN ('completed', 'cancelled')` so no reminder emails fire for cancelled tasks.

3. **AC3 — Waitlist registrations cancelled**: All registrations with `status = 'waitlist'` (or `'waitlisted'` — verify exact string in DB) for the archived event are bulk-updated to `status = 'cancelled'`. Confirmed/registered registrations are preserved (historical data).

4. **AC4 — Notifications dismissed**: All unread `Notification` records where `eventCode = archivedEventCode` and `status != 'READ'` are bulk-updated to `status = 'READ'`. If no such records exist, step is skipped without error.

5. **AC5 — Single @Transactional method**: All 3 cleanup steps (tasks, registrations, notifications) execute in a single `@Transactional` method in `EventArchivalCleanupService`. Task cancellation (AC1) is most critical; steps 2-4 individually caught and logged but do not roll back step 1.

6. **AC6 — Idempotent**: Running cleanup twice on the same event produces the same final state with no errors or exceptions.

7. **AC7 — Hooked into ARCHIVED transition**: `EventWorkflowStateMachine.transitionToState()` calls `eventArchivalCleanupService.cleanup(eventCode)` when the new state is `ARCHIVED`.

8. **AC8 — TDD compliance**: `EventArchivalCleanupServiceTest` is written FIRST (RED phase) covering all paths. Integration test archives an event with open tasks/waitlisted registrations and verifies DB state. All tests pass; Checkstyle passes.

---

## Tasks / Subtasks

### Phase 0: Pre-check (before writing any code)

- [ ] **T0 — Verify EventTask schema for cancellation fields** (AC: #1)
  - [ ] T0.1 — Read `EventTask.java` fully: does it have `cancelledReason` and `cancelledAt` fields?
    ```
    services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java
    ```
  - [ ] T0.2 — If fields are **absent**: a Flyway migration (V76) is needed to add them. If fields are **present**: skip T0.3.
  - [ ] T0.3 — Identify next available Flyway version: latest migration under
    `services/event-management-service/src/main/resources/db/migration/` — stories 10.12-10.16 use V73-V75; if those are not yet applied, V76 is next available.
  - [ ] T0.4 — Verify exact registration `status` string used for waitlist. Run:
    ```bash
    grep -r "waitlist" services/event-management-service/src/main/java --include="*.java" | grep -i "status\|setStatus\|\"waitlist" | head -20
    ```
    Confirm whether the string is `"waitlist"` or `"waitlisted"` (RegistrationService maps 'waitlisted' → WAITLIST enum — use exact stored string).

### Phase 1: Flyway Migration (only if T0.2 confirms fields missing)

- [ ] **T1 — Create Flyway migration V76** (AC: #1 — conditional on T0.2)
  - [ ] T1.1 — Create `services/event-management-service/src/main/resources/db/migration/V76__add_task_cancellation_fields.sql`:
    ```sql
    -- Story 10.18: Event Archival Task & Notification Cleanup
    ALTER TABLE event_tasks
        ADD COLUMN IF NOT EXISTS cancelled_reason VARCHAR(255),
        ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMPTZ;
    ```
  - [ ] T1.2 — Verify migration applies: `./gradlew :services:event-management-service:flywayMigrate 2>&1 | tee /tmp/flyway-10-18.log && grep -i "error\|fail\|success" /tmp/flyway-10-18.log | head -20`

### Phase 2: EventTask entity update (only if migration created in Phase 1)

- [ ] **T2 — Add cancellation fields to EventTask entity** (AC: #1)
  - [ ] T2.1 — In `EventTask.java`, add:
    ```java
    @Column(name = "cancelled_reason")
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    ```
  - [ ] T2.2 — Add getters/setters (Lombok `@Data` should cover this; check if entity uses `@Data` or manual accessors and match the existing pattern).

### Phase 3: Repository queries (TDD first)

- [ ] **T3 — Write FAILING repository tests (RED phase)** (AC: #8)
  - [ ] T3.1 — Locate or create `EventTaskRepositoryTest.java` (integration test extending `AbstractIntegrationTest`).
  - [ ] T3.2 — Add test: `findTasksDueForReminder() excludes tasks with status='cancelled'`
    - Create event + task with `status = 'cancelled'` and `dueDate = tomorrow`
    - Call `findTasksDueForReminder(startOfTomorrow, endOfTomorrow)`
    - Assert: result list does NOT contain the cancelled task
  - [ ] T3.3 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*EventTaskRepository*" 2>&1 | tee /tmp/test-10-18-repo-red.log`

- [ ] **T4 — Update EventTaskRepository.findTasksDueForReminder()** (AC: #2)
  - [ ] T4.1 — Read current query in `EventTaskRepository.java`:
    ```
    services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java
    ```
  - [ ] T4.2 — Current query: `AND t.status != 'completed'`
    Update to: `AND t.status NOT IN ('completed', 'cancelled')`
    ```java
    @Query("SELECT t FROM EventTask t WHERE t.dueDate >= :from AND t.dueDate < :to "
            + "AND t.status NOT IN ('completed', 'cancelled') AND t.assignedOrganizerUsername IS NOT NULL")
    List<EventTask> findTasksDueForReminder(@Param("from") Instant from, @Param("to") Instant to);
    ```
  - [ ] T4.3 — Add bulk cancel query for archival:
    ```java
    @Modifying
    @Query("UPDATE EventTask t SET t.status = 'cancelled' WHERE t.eventId = :eventId "
            + "AND t.status NOT IN ('completed', 'cancelled')")
    int cancelOpenTasksForEvent(@Param("eventId") UUID eventId);
    ```
    If `cancelledReason`/`cancelledAt` fields were added in T2:
    ```java
    @Modifying
    @Query("UPDATE EventTask t SET t.status = 'cancelled', t.cancelledReason = 'Event archived', "
            + "t.cancelledAt = CURRENT_TIMESTAMP WHERE t.eventId = :eventId "
            + "AND t.status NOT IN ('completed', 'cancelled')")
    int cancelOpenTasksForEvent(@Param("eventId") UUID eventId);
    ```
  - [ ] T4.4 — Run tests GREEN: `./gradlew :services:event-management-service:test --tests "*EventTaskRepository*" 2>&1 | tee /tmp/test-10-18-repo-green.log`

- [ ] **T5 — Add RegistrationRepository bulk cancel query** (AC: #3)
  - [ ] T5.1 — Read `RegistrationRepository.java` to verify existing query patterns and the exact waitlist status string.
  - [ ] T5.2 — Add query (use the verified waitlist status string from T0.4):
    ```java
    @Modifying
    @Query("UPDATE Registration r SET r.status = 'cancelled' WHERE r.eventId = :eventId "
            + "AND r.status = :waitlistStatus")
    int cancelWaitlistRegistrationsForEvent(@Param("eventId") UUID eventId,
                                             @Param("waitlistStatus") String waitlistStatus);
    ```

- [ ] **T6 — Add NotificationRepository bulk dismiss query** (AC: #4)
  - [ ] T6.1 — Read `NotificationRepository.java` to understand existing query patterns.
  - [ ] T6.2 — Check if `Notification` has an `eventCode` field (from the explore agent, it does).
  - [ ] T6.3 — Add query:
    ```java
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ' WHERE n.eventCode = :eventCode "
            + "AND n.status != 'READ'")
    int dismissNotificationsForEvent(@Param("eventCode") String eventCode);
    ```
    **Note**: If `Notification` entity uses `NotificationStatus` enum instead of a String status, use `n.status != ch.batbern.events.notification.NotificationStatus.READ`. Check entity definition first.

### Phase 4: EventArchivalCleanupService (TDD)

- [ ] **T7 — Write EventArchivalCleanupServiceTest FIRST (RED phase)** (AC: #8)
  - [ ] T7.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupServiceTest.java`
  - [ ] T7.2 — Use `@ExtendWith(MockitoExtension.class)` (unit test with mocks — no Spring context needed):
    ```java
    @ExtendWith(MockitoExtension.class)
    class EventArchivalCleanupServiceTest {
        @Mock EventRepository eventRepository;
        @Mock EventTaskRepository eventTaskRepository;
        @Mock RegistrationRepository registrationRepository;
        @Mock NotificationRepository notificationRepository;
        @InjectMocks EventArchivalCleanupService service;
    ```
  - [ ] T7.3 — Test: `cleanup() with open tasks → bulk cancel called with correct eventId`
    - Setup: `eventRepository.findByEventCode("BATbern42")` returns event with UUID
    - Assert: `eventTaskRepository.cancelOpenTasksForEvent(eventId)` called once
  - [ ] T7.4 — Test: `cleanup() with waitlisted registrations → bulk cancel registrations called`
    - Assert: `registrationRepository.cancelWaitlistRegistrationsForEvent(eventId, <waitlistStatus>)` called
  - [ ] T7.5 — Test: `cleanup() → notifications dismissed`
    - Assert: `notificationRepository.dismissNotificationsForEvent("BATbern42")` called
  - [ ] T7.6 — Test: `cleanup() is idempotent → calling twice produces no exceptions`
    - Setup: all repos return 0 (nothing to cancel on second run)
    - Assert: no exception thrown
  - [ ] T7.7 — Test: `cleanup() notification step throws → task cancellation is NOT rolled back`
    - Setup: `notificationRepository.dismissNotificationsForEvent()` throws RuntimeException
    - Assert: no exception propagated; `eventTaskRepository.cancelOpenTasksForEvent()` was still called
    - (This validates the "catch and log" behavior for non-critical steps)
  - [ ] T7.8 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*EventArchivalCleanupServiceTest" 2>&1 | tee /tmp/test-10-18-service-red.log`

- [ ] **T8 — Implement EventArchivalCleanupService** (AC: #1, #3, #4, #5, #6)
  - [ ] T8.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/EventArchivalCleanupService.java`:
    ```java
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class EventArchivalCleanupService {

        // Waitlist status string — set to match exact DB value (verify from RegistrationService)
        private static final String WAITLIST_STATUS = "waitlist"; // or "waitlisted" — verify!

        private final EventRepository eventRepository;
        private final EventTaskRepository eventTaskRepository;
        private final RegistrationRepository registrationRepository;
        private final NotificationRepository notificationRepository;

        /**
         * Clean up all open tasks, waitlisted registrations, and unread notifications
         * for an event being archived. Idempotent — safe to call multiple times.
         * Task cancellation is transactional and is the primary step.
         * Steps 2 (waitlist cleanup) and 3 (notification dismissal) are best-effort:
         * failures are logged but do not roll back step 1.
         *
         * @param eventCode the event code being archived (e.g. "BATbern42")
         */
        @Transactional
        public void cleanup(String eventCode) {
            log.info("Starting archival cleanup for event: {}", eventCode);

            Event event = eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));
            UUID eventId = event.getId();

            // Step 1: Cancel open tasks (primary — inside @Transactional)
            int cancelledTasks = eventTaskRepository.cancelOpenTasksForEvent(eventId);
            log.info("Archival cleanup: cancelled {} open tasks for event: {}", cancelledTasks, eventCode);

            // Step 2: Cancel waitlisted registrations (best-effort)
            try {
                int cancelledWaitlist = registrationRepository.cancelWaitlistRegistrationsForEvent(
                        eventId, WAITLIST_STATUS);
                log.info("Archival cleanup: cancelled {} waitlist registrations for event: {}",
                        cancelledWaitlist, eventCode);
            } catch (Exception e) {
                log.warn("Archival cleanup: failed to cancel waitlist registrations for event: {} — {}",
                        eventCode, e.getMessage());
            }

            // Step 3: Dismiss unread notifications (best-effort)
            try {
                int dismissed = notificationRepository.dismissNotificationsForEvent(eventCode);
                log.info("Archival cleanup: dismissed {} notifications for event: {}", dismissed, eventCode);
            } catch (Exception e) {
                log.warn("Archival cleanup: failed to dismiss notifications for event: {} — {}",
                        eventCode, e.getMessage());
            }

            log.info("Archival cleanup complete for event: {}", eventCode);
        }
    }
    ```
  - [ ] T8.2 — Run unit tests GREEN: `./gradlew :services:event-management-service:test --tests "*EventArchivalCleanupServiceTest" 2>&1 | tee /tmp/test-10-18-service-green.log`

### Phase 5: Hook into EventWorkflowStateMachine

- [ ] **T9 — Read EventWorkflowStateMachine before modifying** (AC: #7)
  - [ ] T9.1 — Read `EventWorkflowStateMachine.java` fully:
    ```
    services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java
    ```
  - [ ] T9.2 — Identify `transitionToState()` method and find the right injection point for `ARCHIVED` state entry.

- [ ] **T10 — Inject EventArchivalCleanupService into EventWorkflowStateMachine** (AC: #7)
  - [ ] T10.1 — Add `EventArchivalCleanupService` as a constructor-injected dependency (Lombok `@RequiredArgsConstructor` — just add the field).
  - [ ] T10.2 — In `transitionToState()`, after state transition is saved and before domain event is published, add:
    ```java
    if (newState == EventWorkflowState.ARCHIVED) {
        eventArchivalCleanupService.cleanup(event.getEventCode());
    }
    ```
  - [ ] T10.3 — Verify no circular dependencies: `EventArchivalCleanupService` → repositories only; `EventWorkflowStateMachine` → `EventArchivalCleanupService` → no back-reference to `EventWorkflowStateMachine`. Should be safe.

### Phase 6: Integration Test

- [ ] **T11 — Write integration test (RED phase)** (AC: #8)
  - [ ] T11.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupIntegrationTest.java`
  - [ ] T11.2 — Pattern: extends `AbstractIntegrationTest`, `@SpringBootTest`, `@Transactional`, inject repositories and services.
  - [ ] T11.3 — Test: `archiving event cancels open tasks`:
    - Create event in `EVENT_COMPLETED` state
    - Create 3 tasks: 2 `todo`, 1 `completed`
    - Call `eventArchivalCleanupService.cleanup(eventCode)`
    - Assert: 2 tasks have `status = 'cancelled'`; 1 task still has `status = 'completed'`
  - [ ] T11.4 — Test: `archiving event cancels waitlist registrations`:
    - Create 1 `confirmed` and 1 `waitlist` registration for the event
    - Call cleanup
    - Assert: waitlist registration `status = 'cancelled'`; confirmed registration unchanged
  - [ ] T11.5 — Test: `cleanup is idempotent`:
    - Call cleanup twice
    - Assert: no exception; task count in 'cancelled' unchanged on second call
  - [ ] T11.6 — Test: `scheduler does not send reminder for cancelled tasks`:
    - Create task for an event with `status = 'cancelled'` and `dueDate = tomorrow`
    - Call `eventTaskRepository.findTasksDueForReminder(startOfTomorrow, endOfTomorrow)`
    - Assert: result does NOT include the cancelled task
  - [ ] T11.7 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*EventArchivalCleanupIntegration*" 2>&1 | tee /tmp/test-10-18-integration-red.log`

- [ ] **T12 — Run integration tests GREEN** (AC: #8)
  - [ ] T12.1 — `./gradlew :services:event-management-service:test --tests "*EventArchivalCleanupIntegration*" 2>&1 | tee /tmp/test-10-18-integration-green.log`

### Phase 7: Full suite validation

- [ ] **T13 — Run full test suite + checkstyle** (AC: #8)
  - [ ] T13.1 — Full EMS tests: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-18-full.log && grep -E "FAILED|tests|errors|BUILD" /tmp/test-10-18-full.log | tail -10`
  - [ ] T13.2 — Checkstyle: `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/checkstyle-10-18.log && grep -i "violation\|error" /tmp/checkstyle-10-18.log | head -20`
  - [ ] T13.3 — Build: `./gradlew :services:event-management-service:build 2>&1 | tee /tmp/build-10-18.log && grep -E "BUILD|FAILED" /tmp/build-10-18.log`

---

## Dev Notes

### Architecture Overview

This is a **backend-only bug fix** with no frontend changes. The cleanup is triggered synchronously inside the existing event state machine when `ARCHIVED` state is entered.

**Data flow:**
```
Organizer clicks "Archive Event" in UI
    → PATCH /events/{eventCode}/workflow/state { state: "ARCHIVED" }
    → EventWorkflowStateMachine.transitionToState(event, ARCHIVED)
    → event.setWorkflowState(ARCHIVED) + save
    → eventArchivalCleanupService.cleanup(eventCode)   ← NEW
        → EventTaskRepository.cancelOpenTasksForEvent(eventId)    bulk UPDATE tasks
        → RegistrationRepository.cancelWaitlistRegistrationsForEvent(eventId, "waitlist")
        → NotificationRepository.dismissNotificationsForEvent(eventCode)
    → domain event published (EventWorkflowTransitionEvent)
```

### Critical Pre-checks Before Coding

**1. EventTask cancellation fields**: Verify whether `EventTask.java` already has `cancelledReason` and `cancelledAt` columns. If absent, a Flyway migration V76 is required. Do NOT assume — read the entity file first (T0.1).

**2. Waitlist status string**: Two possible strings: `"waitlist"` or `"waitlisted"`. The `RegistrationService.getMyRegistration()` method maps `'waitlisted'` → API WAITLIST enum (from prior analysis). But the entity might store `"waitlist"`. **Verify with T0.4 grep** and use the exact string. Wrong string = zero registrations cancelled with no error (silent bug).

**3. `EventWorkflowStateMachine` injection pattern**: It uses `@RequiredArgsConstructor` (Lombok). Adding `EventArchivalCleanupService` as a `private final` field is sufficient — Lombok generates the constructor automatically.

**4. NotificationStatus type**: `Notification.status` may be an enum (`NotificationStatus.READ`) rather than a plain String. The JPQL `UPDATE Notification n SET n.status = 'READ'` would fail if it's an enum. Verify by reading `Notification.java` first, then use the correct type in the JPQL.

### Key Files

**Read before modifying:**
```
services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java
services/event-management-service/src/main/java/ch/batbern/events/domain/Notification.java
services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java
services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java
services/event-management-service/src/main/java/ch/batbern/events/notification/NotificationRepository.java
services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java
```

**New files to create:**
```
services/event-management-service/src/main/java/ch/batbern/events/service/EventArchivalCleanupService.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupIntegrationTest.java
```

**Modified files:**
```
services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java
     — add EventArchivalCleanupService injection + cleanup() call on ARCHIVED entry
services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java
     — update findTasksDueForReminder() + add cancelOpenTasksForEvent()
services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java
     — add cancelWaitlistRegistrationsForEvent()
services/event-management-service/src/main/java/ch/batbern/events/notification/NotificationRepository.java
     — add dismissNotificationsForEvent()
```

**Conditional (only if EventTask lacks cancellation fields):**
```
services/event-management-service/src/main/resources/db/migration/V76__add_task_cancellation_fields.sql
services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java
     — add cancelledReason, cancelledAt fields
```

### Architecture Compliance

**Follow these patterns:**
- `@Transactional` on the cleanup service method (matches `EventTaskService` + `RegistrationService`)
- `@Modifying` + `@Query` JPQL for bulk updates (matches `EventTaskRepository` existing patterns)
- `@RequiredArgsConstructor` + `@Slf4j` on service class (project-standard for all services)
- `AbstractIntegrationTest` for integration tests (PostgreSQL via Testcontainers — never H2)
- `@ExtendWith(MockitoExtension.class)` for unit tests with no Spring context

**DO NOT:**
- Use `findAll()` + iterate + `saveAll()` for bulk updates — JPQL `@Modifying @Query` is required for performance
- Skip the pre-checks (T0) — wrong status string or missing DB columns will cause silent failures
- Add REST endpoints — this is fully internal, triggered by the state machine
- Use H2 for integration tests (always Testcontainers via `AbstractIntegrationTest`)

### EventTask Status Lifecycle (for reference)
```
pending     → Deferred (not yet activated)
todo        → Active (visible on task board)
in_progress → Being worked on
completed   → Done
cancelled   → NEW: Cancelled (archived event cleanup or manual)
```

### Flyway Version Reference
- V73: story 10.12 (deregistration_token on registrations)
- V74: story 10.15 (preferredLanguage to OpenAPI+DTO)
- V75: story 10.16 (AI features / V75 migration)
- V76: story 10.18 — ONLY needed if EventTask.java lacks cancelledReason/cancelledAt

### Test Command Reference
```bash
# Run all new tests
./gradlew :services:event-management-service:test --tests "*EventArchivalCleanup*" 2>&1 | tee /tmp/test-10-18.log

# Check results
grep -E "PASSED|FAILED|tests|errors" /tmp/test-10-18.log | tail -10

# Run scheduler test specifically
./gradlew :services:event-management-service:test --tests "*EventTaskRepository*" 2>&1 | tee /tmp/test-10-18-repo.log

# Full suite
./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-18-full.log && grep -E "FAILED|BUILD" /tmp/test-10-18-full.log
```

### References
- EventWorkflowState.ARCHIVED definition: [Source: shared-kernel/.../types/EventWorkflowState.java]
- EventWorkflowStateMachine transition hook location: [Source: services/event-management-service/.../service/EventWorkflowStateMachine.java]
- WorkflowTransitionValidator — ARCHIVED is terminal (no exits): [Source: services/event-management-service/.../service/WorkflowTransitionValidator.java]
- EventTaskRepository.findTasksDueForReminder() current query: [Source: services/event-management-service/.../repository/EventTaskRepository.java]
- Notification entity + NotificationRepository: [Source: services/event-management-service/.../notification/]
- AbstractIntegrationTest (Testcontainers PostgreSQL 16): [Source: shared-kernel/src/testFixtures/.../AbstractIntegrationTest.java]
- RegistrationService waitlist status mapping: [Source: services/event-management-service/.../service/RegistrationService.java]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
