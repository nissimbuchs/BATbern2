# Story 10.18: Event Archival Task & Notification Cleanup

Status: done

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

- [x] **T0 — Verify EventTask schema for cancellation fields** (AC: #1)
  - [x] T0.1 — Read `EventTask.java` fully: does it have `cancelledReason` and `cancelledAt` fields?
    ```
    services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java
    ```
  - [x] T0.2 — If fields are **absent**: a Flyway migration (V76) is needed to add them. If fields are **present**: skip T0.3.
  - [x] T0.3 — Identify next available Flyway version: latest migration under
    `services/event-management-service/src/main/resources/db/migration/` — stories 10.12-10.16 use V73-V75; if those are not yet applied, V76 is next available.
  - [x] T0.4 — Verify exact registration `status` string used for waitlist. Run:
    ```bash
    grep -r "waitlist" services/event-management-service/src/main/java --include="*.java" | grep -i "status\|setStatus\|\"waitlist" | head -20
    ```
    Confirm whether the string is `"waitlist"` or `"waitlisted"` (RegistrationService maps 'waitlisted' → WAITLIST enum — use exact stored string).

### Phase 1: Flyway Migration (only if T0.2 confirms fields missing)

- [x] **T1 — Create Flyway migration V78** (AC: #1 — conditional on T0.2; actual version V78, not V76)
  - [x] T1.1 — Created `services/event-management-service/src/main/resources/db/migration/V78__add_task_cancellation_fields.sql`
  - [x] T1.2 — Migration applies (verified via integration test run with Testcontainers)

### Phase 2: EventTask entity update (only if migration created in Phase 1)

- [x] **T2 — Add cancellation fields to EventTask entity** (AC: #1)
  - [x] T2.1 — Added `cancelledReason` and `cancelledAt` fields to `EventTask.java`
  - [x] T2.2 — Added manual getters/setters (entity uses manual accessors, not Lombok @Data)

### Phase 3: Repository queries (TDD first)

- [x] **T3 — Write FAILING repository tests (RED phase)** (AC: #8)
  - [x] T3.1 — Repository test covered in EventArchivalCleanupIntegrationTest (AC2 test)
  - [x] T3.2 — Test `findTasksDueForReminder() excludes tasks with status='cancelled'` — PASSED
  - [x] T3.3 — RED confirmed before fix, then GREEN

- [x] **T4 — Update EventTaskRepository.findTasksDueForReminder()** (AC: #2)
  - [x] T4.1 — Read current query
  - [x] T4.2 — Updated to `AND t.status NOT IN ('completed', 'cancelled')`
  - [x] T4.3 — Added `cancelOpenTasksForEvent()` with cancelledReason/cancelledAt set
  - [x] T4.4 — Tests GREEN

- [x] **T5 — Add RegistrationRepository bulk cancel query** (AC: #3)
  - [x] T5.1 — Read RegistrationRepository; confirmed waitlist status = `'waitlist'`
  - [x] T5.2 — Added `cancelWaitlistRegistrationsForEvent()`

- [x] **T6 — Add NotificationRepository bulk dismiss query** (AC: #4)
  - [x] T6.1 — Read NotificationRepository
  - [x] T6.2 — Confirmed `eventCode` field exists, `status` is plain String
  - [x] T6.3 — Added `dismissNotificationsForEvent()`

### Phase 4: EventArchivalCleanupService (TDD)

- [x] **T7 — Write EventArchivalCleanupServiceTest FIRST (RED phase)** (AC: #8)
  - [x] T7.1-T7.8 — 7 unit tests written; RED confirmed, then all 7 PASSED GREEN

- [x] **T8 — Implement EventArchivalCleanupService** (AC: #1, #3, #4, #5, #6)
  - [x] T8.1 — Created `EventArchivalCleanupService.java` with WAITLIST_STATUS="waitlist", @Transactional, best-effort steps 2-3
  - [x] T8.2 — 7/7 unit tests GREEN

### Phase 5: Hook into EventWorkflowStateMachine

- [x] **T9 — Read EventWorkflowStateMachine before modifying** (AC: #7)
  - [x] T9.1 — Read fully; uses @RequiredArgsConstructor, @Transactional, @Slf4j
  - [x] T9.2 — Injection point: after `eventRepository.save(event)`, before domain event publish

- [x] **T10 — Inject EventArchivalCleanupService into EventWorkflowStateMachine** (AC: #7)
  - [x] T10.1 — Added `private final EventArchivalCleanupService eventArchivalCleanupService`
  - [x] T10.2 — Added `if (targetState == EventWorkflowState.ARCHIVED)` cleanup call
  - [x] T10.3 — No circular dependency: cleanup → repos only

### Phase 6: Integration Test

- [x] **T11 — Write integration test (RED phase)** (AC: #8)
  - [x] T11.1-T11.7 — 5 integration tests written. Extra: V78 needed to also expand status constraint to include 'cancelled'.

- [x] **T12 — Run integration tests GREEN** (AC: #8)
  - [x] T12.1 — 5/5 integration tests PASSED

### Phase 7: Full suite validation

- [x] **T13 — Run full test suite + checkstyle** (AC: #8)
  - [x] T13.1 — Full EMS test suite: BUILD SUCCESSFUL, zero failures
  - [x] T13.2 — Checkstyle: BUILD SUCCESSFUL, no violations
  - [x] T13.3 — Build: SUCCESSFUL

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

- `/tmp/test-10-18-service.log` — unit tests 7/7 PASSED
- `/tmp/test-10-18-integration.log` — integration tests 5/5 PASSED
- `/tmp/test-10-18-full.log` — full EMS suite BUILD SUCCESSFUL
- `/tmp/checkstyle-10-18.log` — no violations

### Completion Notes List

- **Pre-check**: `EventTask.java` had no cancellation fields → V78 migration needed. V76/V77 already taken by other stories. Waitlist status confirmed as `'waitlist'` via V74 migration. `Notification.status` is plain String (not enum).
- **V78 migration**: Added `cancelled_reason`, `cancelled_at` columns AND expanded `event_tasks_status_check` constraint to include `'cancelled'` (required — existing constraint only covered pending/todo/in_progress/completed).
- **EventTask**: Added `cancelledReason`/`cancelledAt` fields + manual getters/setters (entity uses manual pattern, not Lombok @Data). Status Javadoc updated to include all 5 valid statuses.
- **EventTaskRepository**: Updated `findTasksDueForReminder()` to exclude `'cancelled'`. Added `cancelOpenTasksForEvent()` with `@Modifying(clearAutomatically = true)`.
- **RegistrationRepository**: Added `cancelWaitlistRegistrationsForEvent()` with `@Modifying(clearAutomatically = true)`. Corrected orphaned Javadoc — `findAttendanceSummary()` Javadoc now sits immediately above its method.
- **NotificationRepository**: Added `dismissNotificationsForEvent()` with `@Modifying(clearAutomatically = true)`.
- **ArchivalBestEffortSteps** (NEW): Package-private Spring bean. Steps 2 (waitlist) and 3 (notifications) each annotated `@Transactional(propagation = REQUIRES_NEW)` — true independent transactions implementing AC5's "best-effort" guarantee against JPA-level failures.
- **EventArchivalCleanupService**: Refactored — removed `EventRepository` dependency (redundant re-fetch eliminated). `cleanup()` now accepts `(UUID eventId, String eventCode)`. Injects `ArchivalBestEffortSteps` for steps 2-3.
- **EventWorkflowStateMachine**: Updated cleanup call to `cleanup(savedEvent.getId(), eventCode)`.
- **Tests (Code Review fixes)**: Unit tests updated to use new API and mock `ArchivalBestEffortSteps` (7 tests → still 7 unit tests, improved AC5 coverage). Integration test `@Transactional` removed (REQUIRES_NEW requires no wrapping test transaction); EntityManager removed. Added `EventArchivalCleanupAc5IntegrationTest` with 2 dedicated AC5 DB-verified tests using `@MockBean ArchivalBestEffortSteps`. Total: 7 unit + 6 integration + 2 AC5 integration = 15 tests. All pass. Checkstyle clean.

### File List

services/event-management-service/src/main/resources/db/migration/V78__add_task_cancellation_fields.sql
services/event-management-service/src/main/java/ch/batbern/events/domain/EventTask.java
services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java
services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java
services/event-management-service/src/main/java/ch/batbern/events/notification/NotificationRepository.java
services/event-management-service/src/main/java/ch/batbern/events/service/ArchivalBestEffortSteps.java
services/event-management-service/src/main/java/ch/batbern/events/service/EventArchivalCleanupService.java
services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupIntegrationTest.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventArchivalCleanupAc5IntegrationTest.java
_bmad-output/implementation-artifacts/sprint-status.yaml
_bmad-output/implementation-artifacts/10-18-event-archival-task-notification-cleanup.md
