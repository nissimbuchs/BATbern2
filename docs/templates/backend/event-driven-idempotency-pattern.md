# Event-Driven Idempotency Pattern

**Category**: Backend
**Used in Stories**: 5.5
**Last Updated**: 2025-12-24

## Overview

Pattern for implementing idempotent event listeners in Spring Boot that handle domain events safely, even when events are replayed or duplicated. Essential for ensuring event-driven workflows create side effects exactly once, preventing duplicate tasks, notifications, or data corruption.

## Prerequisites

- Spring Boot 3.x with Spring Events
- Spring Data JPA
- Understanding of event-driven architecture
- Knowledge of idempotency concepts

## When to Use This Pattern

Use idempotent event listeners when:
- Event handlers create database records (tasks, notifications, audit logs)
- Events may be replayed due to retries or system failures
- Multiple instances of the application run concurrently
- Duplicate processing would corrupt data or annoy users

## Implementation Steps

### Step 1: Create Domain Event

```java
package ch.batbern.events.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EventWorkflowTransitionEvent {
    private final String eventId;
    private final EventWorkflowState oldState;
    private final EventWorkflowState newState;
    private final String triggeredByUsername;
    private final LocalDateTime occurredAt = LocalDateTime.now();

    // Event metadata
    private final Event event; // Full event entity with event_date
}
```

### Step 2: Event Publisher (from Service Layer)

```java
@Service
@Slf4j
public class EventWorkflowStateMachine {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void transitionToState(String eventId, EventWorkflowState newState,
                                 String username) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        EventWorkflowState oldState = event.getWorkflowState();

        // Update state
        event.setWorkflowState(newState);
        eventRepository.save(event);

        // Publish event (will be processed after transaction commits)
        eventPublisher.publishEvent(new EventWorkflowTransitionEvent(
            eventId, oldState, newState, username, event
        ));

        log.info("Event {} transitioned from {} to {}",
                 eventId, oldState, newState);
    }
}
```

### Step 3: Idempotent Event Listener with Database Check

```java
@Service
@Slf4j
public class EventTaskService implements ApplicationListener<EventWorkflowTransitionEvent> {

    private final TaskTemplateRepository templateRepository;
    private final EventTaskRepository eventTaskRepository;

    @Override
    @Transactional
    public void onApplicationEvent(EventWorkflowTransitionEvent event) {
        String triggeredState = event.getNewState().name().toLowerCase();
        String eventId = event.getEventId();

        // IDEMPOTENCY CHECK 1: Have we already processed this state transition?
        boolean alreadyProcessed = eventTaskRepository.existsByEventIdAndTriggerState(
            UUID.fromString(eventId),
            triggeredState
        );

        if (alreadyProcessed) {
            log.info("Tasks already created for event {} state {}. Skipping duplicate processing.",
                     eventId, triggeredState);
            return; // EXIT EARLY - no-op
        }

        // Fetch templates that trigger on this state
        List<TaskTemplate> templates = templateRepository.findByTriggerState(triggeredState);

        if (templates.isEmpty()) {
            log.debug("No task templates configured for state: {}", triggeredState);
            return;
        }

        // Create tasks from templates
        for (TaskTemplate template : templates) {
            createTaskFromTemplate(eventId, template, event.getEvent().getEventDate());
        }

        log.info("Created {} tasks for event {} on state transition to {}",
                 templates.size(), eventId, triggeredState);
    }

    private void createTaskFromTemplate(String eventId, TaskTemplate template,
                                       LocalDateTime eventDate) {
        // IDEMPOTENCY CHECK 2: Task-level check (defensive)
        boolean taskExists = eventTaskRepository.existsByEventIdAndTemplateId(
            UUID.fromString(eventId),
            template.getId()
        );

        if (taskExists) {
            log.debug("Task {} already exists for event {}. Skipping.",
                     template.getName(), eventId);
            return; // EXIT EARLY
        }

        // Calculate due date based on template configuration
        LocalDateTime dueDate = calculateDueDate(template, eventDate);

        // Create task
        EventTask task = EventTask.builder()
            .eventId(UUID.fromString(eventId))
            .templateId(template.getId())
            .taskName(template.getName())
            .triggerState(template.getTriggerState())
            .dueDate(dueDate)
            .assignedOrganizerUsername(template.getDefaultAssignee())
            .status("todo")
            .build();

        // Database constraint prevents duplicates even if check is bypassed
        try {
            eventTaskRepository.save(task);
            log.info("Created task '{}' for event {} with due date {}",
                     template.getName(), eventId, dueDate);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation - task already exists (race condition)
            log.warn("Duplicate task creation prevented by unique constraint: {} for event {}",
                     template.getName(), eventId);
            // Don't rethrow - this is expected and handled
        }
    }

    private LocalDateTime calculateDueDate(TaskTemplate template, LocalDateTime eventDate) {
        return switch (template.getDueDateType()) {
            case "immediate" -> LocalDateTime.now();
            case "relative_to_event" -> eventDate.plusDays(template.getDueDateOffsetDays());
            case "absolute" -> template.getAbsoluteDueDate();
            default -> throw new IllegalArgumentException("Unknown due date type: "
                                                         + template.getDueDateType());
        };
    }
}
```

### Step 4: Database Unique Constraint for Idempotency

```sql
-- Unique index prevents duplicate tasks at database level
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id)
  WHERE template_id IS NOT NULL;

COMMENT ON INDEX idx_event_tasks_unique_template IS
  'Prevent duplicate tasks from same template for same event (AC36 - Idempotency)';
```

**Why both application check AND database constraint?**
- **Application check**: Fast exit without hitting database (performance)
- **Database constraint**: Safety net for race conditions or bugs (correctness)

### Step 5: Custom Repository Methods for Idempotency Checks

```java
@Repository
public interface EventTaskRepository extends JpaRepository<EventTask, UUID> {

    /**
     * Check if any tasks exist for this event and trigger state.
     * Used for batch idempotency check before processing templates.
     */
    boolean existsByEventIdAndTriggerState(UUID eventId, String triggerState);

    /**
     * Check if specific task already exists for event and template.
     * Used for per-task idempotency check.
     */
    boolean existsByEventIdAndTemplateId(UUID eventId, UUID templateId);

    /**
     * Find all tasks for an event (for display/reporting).
     */
    List<EventTask> findByEventId(UUID eventId);

    /**
     * Find tasks by assigned organizer (for task dashboard).
     */
    List<EventTask> findByAssignedOrganizerUsername(String username);
}
```

### Step 6: TransactionalEventListener for Commit-Time Processing

```java
@Component
@Slf4j
public class NotificationEventListener {

    private final EmailService emailService;
    private final NotificationRepository notificationRepository;

    /**
     * Process event AFTER transaction commits (safer for external calls).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpeakerStateChange(SpeakerWorkflowStateChangeEvent event) {
        // Idempotency: Check if notification already sent
        boolean notificationExists = notificationRepository.existsByEventTypeAndEntityId(
            "speaker_state_change",
            event.getSpeakerId()
        );

        if (notificationExists) {
            log.debug("Notification already sent for speaker {} state change. Skipping.",
                     event.getSpeakerId());
            return;
        }

        // Send notification
        emailService.sendStateChangeNotification(event);

        // Record notification sent (for idempotency)
        notificationRepository.save(Notification.builder()
            .eventType("speaker_state_change")
            .entityId(event.getSpeakerId())
            .sentAt(LocalDateTime.now())
            .build());

        log.info("Sent speaker state change notification for speaker {}",
                 event.getSpeakerId());
    }

    /**
     * Handle rollback scenario (cleanup, logging).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onTransactionRollback(SpeakerWorkflowStateChangeEvent event) {
        log.warn("Transaction rolled back for speaker {} state change. No notification sent.",
                 event.getSpeakerId());
    }
}
```

## Idempotency Strategies Comparison

| Strategy | Pros | Cons | When to Use |
|----------|------|------|-------------|
| **Database Unique Constraint** | Bulletproof, handles race conditions | Database error on duplicate | Always (as safety net) |
| **Application-Level Check** | Fast, avoids DB roundtrip | Not safe for concurrent requests | First line of defense |
| **Event Deduplication Table** | Explicit audit trail | Extra table, maintenance overhead | Complex event flows |
| **Idempotency Key (API)** | Client controls deduplication | Requires client cooperation | External APIs only |
| **Version Number Check** | Optimistic locking pattern | Only works for updates | Entity updates only |

**Recommended Approach**: Combine application-level check + database unique constraint for best performance and safety.

## Testing Idempotency

### Integration Test: Duplicate Event Handling

```java
@SpringBootTest
@Testcontainers
class EventTaskIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_createTasksOnlyOnce_when_eventPublishedMultipleTimes() {
        // Given: Event in CREATED state with task templates configured
        Event event = createEventWithTaskTemplates();
        String eventId = event.getId().toString();

        // When: Event transitions to TOPIC_SELECTION (triggers task creation)
        eventWorkflowStateMachine.transitionToState(
            eventId,
            EventWorkflowState.TOPIC_SELECTION,
            "organizer"
        );

        // Then: Tasks created
        List<EventTask> tasksAfterFirst = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasksAfterFirst).hasSize(4); // 4 templates trigger on topic_selection

        // When: Event replayed (simulating retry or duplicate event)
        eventWorkflowStateMachine.transitionToState(
            eventId,
            EventWorkflowState.TOPIC_SELECTION,
            "organizer"
        );

        // Then: NO duplicate tasks created (idempotency check prevents)
        List<EventTask> tasksAfterSecond = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasksAfterSecond).hasSize(4); // Still 4 tasks, not 8

        // Verify exact same task IDs (not new records)
        List<UUID> firstTaskIds = tasksAfterFirst.stream()
            .map(EventTask::getId)
            .sorted()
            .collect(Collectors.toList());
        List<UUID> secondTaskIds = tasksAfterSecond.stream()
            .map(EventTask::getId)
            .sorted()
            .collect(Collectors.toList());
        assertThat(firstTaskIds).isEqualTo(secondTaskIds);
    }

    @Test
    void should_preventDuplicateTasks_when_concurrentEventProcessing() throws Exception {
        // Given: Event ready for task creation
        Event event = createEvent();
        TaskTemplate template = createTaskTemplate("Venue Booking", "topic_selection");

        // When: Two threads try to create same task simultaneously
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger constraintViolations = new AtomicInteger(0);

        Runnable createTask = () -> {
            try {
                startLatch.await(); // Synchronize start for max concurrency
                taskService.createTaskFromTemplate(event.getId(), template);
                successCount.incrementAndGet();
            } catch (DataIntegrityViolationException e) {
                constraintViolations.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        CompletableFuture.runAsync(createTask);
        CompletableFuture.runAsync(createTask);

        startLatch.countDown(); // Start both threads
        doneLatch.await(5, TimeUnit.SECONDS); // Wait for completion

        // Then: Only ONE task created (one success, one constraint violation)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(constraintViolations.get()).isEqualTo(1);

        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasks).hasSize(1);
    }

    @Test
    void should_skipProcessing_when_idempotencyCheckReturnsTrue() {
        // Given: Event with tasks already created
        Event event = createEventWithExistingTasks();

        // When: Event listener receives duplicate event
        EventWorkflowTransitionEvent duplicateEvent = new EventWorkflowTransitionEvent(
            event.getId().toString(),
            EventWorkflowState.CREATED,
            EventWorkflowState.TOPIC_SELECTION,
            "organizer",
            event
        );

        // Capture logs to verify early exit
        long taskCountBefore = eventTaskRepository.count();

        taskService.onApplicationEvent(duplicateEvent);

        // Then: No database queries executed (check logs)
        // And: Task count unchanged
        long taskCountAfter = eventTaskRepository.count();
        assertThat(taskCountAfter).isEqualTo(taskCountBefore);
    }
}
```

## Common Pitfalls

### Pitfall 1: Relying Only on Application-Level Check
**Problem**: Race conditions create duplicates
```java
❌ BAD (no database constraint):
@Override
public void onApplicationEvent(EventWorkflowTransitionEvent event) {
    if (eventTaskRepository.existsByEventId(event.getEventId())) {
        return; // Idempotency check
    }
    // Two concurrent requests both pass check, both create tasks!
    eventTaskRepository.save(newTask);
}

✅ GOOD (database constraint):
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id) WHERE template_id IS NOT NULL;
```

### Pitfall 2: Not Handling DataIntegrityViolationException
**Problem**: Constraint violation crashes event handler
```java
❌ BAD:
eventTaskRepository.save(task);
// Exception propagates, event listener fails

✅ GOOD:
try {
    eventTaskRepository.save(task);
} catch (DataIntegrityViolationException e) {
    log.warn("Duplicate task prevented by constraint: {}", template.getName());
    // Don't rethrow - this is expected idempotency behavior
}
```

### Pitfall 3: Using @EventListener Instead of @TransactionalEventListener
**Problem**: Event processed before transaction commits
```java
❌ BAD:
@EventListener
public void onSpeakerApproved(SpeakerApprovedEvent event) {
    emailService.sendApprovalEmail(event.getSpeakerId());
    // Email sent even if transaction rolls back!
}

✅ GOOD:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSpeakerApproved(SpeakerApprovedEvent event) {
    emailService.sendApprovalEmail(event.getSpeakerId());
    // Email only sent if transaction commits
}
```

### Pitfall 4: Non-Deterministic Idempotency Keys
**Problem**: Same logical event has different identifiers
```java
❌ BAD (timestamp in key):
boolean isDuplicate = eventTaskRepository.existsByEventIdAndTimestamp(
    eventId, event.getOccurredAt() // Different each time!
);

✅ GOOD (stable business key):
boolean isDuplicate = eventTaskRepository.existsByEventIdAndTriggerState(
    eventId, event.getNewState() // Same for same transition
);
```

### Pitfall 5: Missing @Transactional on Event Listener
**Problem**: Idempotency check and task creation in separate transactions
```java
❌ BAD:
@Override
public void onApplicationEvent(EventWorkflowTransitionEvent event) {
    // Check happens in transaction 1
    if (taskExists(event)) return;
    // Save happens in transaction 2 (race condition!)
    saveTask(event);
}

✅ GOOD:
@Override
@Transactional
public void onApplicationEvent(EventWorkflowTransitionEvent event) {
    // Check and save in SAME transaction
    if (taskExists(event)) return;
    saveTask(event);
}
```

## Performance Optimization

### Batch Idempotency Check

```java
// Instead of checking each task individually
❌ SLOW:
for (TaskTemplate template : templates) {
    if (eventTaskRepository.existsByEventIdAndTemplateId(eventId, template.getId())) {
        continue; // N database queries
    }
    createTask(template);
}

✅ FAST:
// Fetch all existing tasks once
Set<UUID> existingTemplateIds = eventTaskRepository
    .findByEventId(eventId)
    .stream()
    .map(EventTask::getTemplateId)
    .collect(Collectors.toSet());

// Check in memory
for (TaskTemplate template : templates) {
    if (existingTemplateIds.contains(template.getId())) {
        continue; // No DB query
    }
    createTask(template);
}
```

## Story-Specific Adaptations

### Story 5.5: Task Auto-Creation on Workflow Transitions

**Multi-Level Idempotency:**
```java
@Override
@Transactional
public void onApplicationEvent(EventWorkflowTransitionEvent event) {
    String triggeredState = event.getNewState().name().toLowerCase();
    String eventId = event.getEventId();

    // Level 1: Batch check by trigger state (fast exit)
    if (eventTaskRepository.existsByEventIdAndTriggerState(
        UUID.fromString(eventId), triggeredState)) {
        return;
    }

    List<TaskTemplate> templates = templateRepository.findByTriggerState(triggeredState);

    // Level 2: Per-task check (defensive)
    for (TaskTemplate template : templates) {
        if (!eventTaskRepository.existsByEventIdAndTemplateId(
            UUID.fromString(eventId), template.getId())) {
            createTaskFromTemplate(eventId, template, event.getEvent().getEventDate());
        }
    }
}
```

**Database Constraint:**
```sql
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id)
  WHERE template_id IS NOT NULL;
```

## Related Patterns

- See also: `backend/spring-transaction-pattern.md` - Transaction management
- See also: `backend/spring-boot-service-foundation.md` - Service layer structure
- See also: `backend/integration-test-pattern.md` - Testing event-driven code
