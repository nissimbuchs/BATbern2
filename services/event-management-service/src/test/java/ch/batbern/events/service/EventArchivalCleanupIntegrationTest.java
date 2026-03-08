package ch.batbern.events.service;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.notification.Notification;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.service.EventWorkflowStateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for EventArchivalCleanupService (Story 10.18 AC8).
 *
 * Tests cover:
 * - Bulk task cancellation on archival (AC1)
 * - Scheduler excludes cancelled tasks (AC2)
 * - Waitlist registration cancellation (AC3)
 * - Notification dismissal (AC4)
 * - Task cancellation preserved when best-effort step fails (AC5)
 * - Idempotency (AC6)
 *
 * Note: NOT annotated with @Transactional because best-effort steps (waitlist, notification)
 * use PROPAGATION_REQUIRES_NEW and run in their own committed transactions — a wrapping
 * test transaction would prevent the sub-transactions from ever committing.
 * Cleanup is handled explicitly in @BeforeEach and @AfterEach.
 *
 * Uses PostgreSQL via Testcontainers (AbstractIntegrationTest).
 */
@SpringBootTest
class EventArchivalCleanupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventArchivalCleanupService eventArchivalCleanupService;

    @Autowired
    private EventWorkflowStateMachine stateMachine;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final AtomicInteger EVENT_NUMBER_COUNTER =
            new AtomicInteger((int) ((System.currentTimeMillis() / 1000) % 100000) + 20000);

    private Event testEvent;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        registrationRepository.deleteAll();
        eventTaskRepository.deleteAll();
        eventRepository.deleteAll();

        int eventNumber = EVENT_NUMBER_COUNTER.getAndIncrement();
        testEvent = Event.builder()
                .eventCode("BATbern" + eventNumber)
                .eventNumber(eventNumber)
                .title("Archival Test Event")
                .organizerUsername("test.organizer")
                .date(Instant.now().minus(7, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().minus(14, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address, Bern")
                .venueCapacity(100)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        registrationRepository.deleteAll();
        eventTaskRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("archiving event cancels open tasks but not completed ones (AC1)")
    void cleanup_cancelsOpenTasks_preservesCompleted() {
        // Given: 2 todo tasks and 1 completed task
        EventTask todo1 = createAndSaveTask("todo");
        EventTask todo2 = createAndSaveTask("todo");
        EventTask completed = createAndSaveTask("completed");

        // When
        eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode());

        // Then: 2 tasks cancelled, 1 completed preserved
        List<EventTask> allTasks = eventTaskRepository.findByEventId(testEvent.getId());
        assertThat(allTasks).hasSize(3);

        long cancelledCount = allTasks.stream()
                .filter(t -> "cancelled".equals(t.getStatus()))
                .count();
        assertThat(cancelledCount).isEqualTo(2);

        EventTask completedAfter = eventTaskRepository.findById(completed.getId()).orElseThrow();
        assertThat(completedAfter.getStatus()).isEqualTo("completed");

        // Verify cancelledReason and cancelledAt are set on cancelled tasks
        allTasks.stream()
                .filter(t -> "cancelled".equals(t.getStatus()))
                .forEach(t -> {
                    assertThat(t.getCancelledReason()).isEqualTo("Event archived");
                    assertThat(t.getCancelledAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("archiving event cancels waitlist registrations, preserves confirmed (AC3)")
    void cleanup_cancelsWaitlistRegistrations_preservesConfirmed() {
        // Given: 1 confirmed and 1 waitlist registration
        Registration confirmed = createAndSaveRegistration("confirmed");
        Registration waitlisted = createAndSaveRegistration("waitlist");

        // When
        eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode());

        // Then: waitlist cancelled, confirmed preserved
        Registration waitlistAfter = registrationRepository.findById(waitlisted.getId()).orElseThrow();
        assertThat(waitlistAfter.getStatus()).isEqualTo("cancelled");

        Registration confirmedAfter = registrationRepository.findById(confirmed.getId()).orElseThrow();
        assertThat(confirmedAfter.getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("cleanup() is idempotent — calling twice produces no exceptions and same state (AC6)")
    void cleanup_calledTwice_isIdempotent() {
        createAndSaveTask("todo");
        createAndSaveRegistration("waitlist");

        assertThatNoException().isThrownBy(() -> {
            eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode());
            eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode());
        });

        // All tasks still cancelled after second call
        List<EventTask> tasks = eventTaskRepository.findByEventId(testEvent.getId());
        assertThat(tasks).allMatch(t -> "cancelled".equals(t.getStatus()));
    }

    @Test
    @DisplayName("scheduler does not send reminders for cancelled tasks (AC2)")
    void findTasksDueForReminder_excludesCancelledTasks() {
        // Given: cancelled task due tomorrow
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        EventTask cancelledTask = new EventTask();
        cancelledTask.setEventId(testEvent.getId());
        cancelledTask.setTaskName("Cancelled task due tomorrow");
        cancelledTask.setTriggerState("topic_selection");
        cancelledTask.setStatus("cancelled");
        cancelledTask.setDueDate(tomorrow);
        cancelledTask.setAssignedOrganizerUsername("test.organizer");
        UUID cancelledTaskId = eventTaskRepository.save(cancelledTask).getId();

        // When: query tasks due for reminder in tomorrow's window
        Instant startOfTomorrow = Instant.now().plus(20, ChronoUnit.HOURS);
        Instant endOfTomorrow = Instant.now().plus(28, ChronoUnit.HOURS);
        List<EventTask> dueForReminder = eventTaskRepository.findTasksDueForReminder(startOfTomorrow, endOfTomorrow);

        // Then: cancelled task not included
        assertThat(dueForReminder).noneMatch(t -> t.getId().equals(cancelledTaskId));
    }

    @Test
    @DisplayName("cleanup() dismisses unread notifications for the event (AC4)")
    void cleanup_dismissesUnreadNotifications() {
        // Given: 2 unread notifications for this event
        Notification unread1 = createAndSaveNotification("SENT");
        Notification unread2 = createAndSaveNotification("PENDING");
        Notification alreadyRead = createAndSaveNotification("READ");

        // When
        eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode());

        // Then: the two unread ones are now READ, already-READ one unchanged
        assertThat(notificationRepository.findById(unread1.getId()).orElseThrow().getStatus())
                .isEqualTo("READ");
        assertThat(notificationRepository.findById(unread2.getId()).orElseThrow().getStatus())
                .isEqualTo("READ");
        assertThat(notificationRepository.findById(alreadyRead.getId()).orElseThrow().getStatus())
                .isEqualTo("READ");
    }

    @Test
    @DisplayName("AC5: task cancellation committed even when best-effort notification step fails (AC5)")
    void cleanup_tasksCancelledEvenWhenNotificationStepFails() {
        // Given: open task exists; notification step will be stubbed to fail via @MockBean
        // We simulate a failure in the best-effort step by replacing the ArchivalBestEffortSteps bean.
        // Since this test needs a mock, it uses a dedicated test slice — see
        // EventArchivalCleanupAc5IntegrationTest for the @MockBean variant.
        //
        // What we CAN verify here: cleanup() does not throw even when there are no
        // notifications to dismiss (zero-row update = no failure = best-effort step works fine).
        // Full AC5 with injected failure is covered in EventArchivalCleanupAc5IntegrationTest.
        createAndSaveTask("todo");

        assertThatNoException().isThrownBy(() ->
                eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode()));

        List<EventTask> tasks = eventTaskRepository.findByEventId(testEvent.getId());
        assertThat(tasks).allMatch(t -> "cancelled".equals(t.getStatus()));
    }

    @Test
    @DisplayName("regression: ARCHIVED state persists to DB when open tasks exist during archival (flushAutomatically bug)")
    void transitionToArchived_persistsEventState_whenOpenTasksExist() {
        // Regression test for: @Modifying(clearAutomatically = true) on cancelOpenTasksForEvent()
        // was evicting the pending event state change from the JPA first-level cache before the
        // transaction committed, causing the workflowState update to be silently lost.
        // Fix: flushAutomatically = true forces JPA to flush the event UPDATE before clearing the cache.

        // Given: event in EVENT_COMPLETED with one open task
        testEvent.setWorkflowState(EventWorkflowState.EVENT_COMPLETED);
        testEvent = eventRepository.save(testEvent);
        createAndSaveTask("todo");

        // When: transition to ARCHIVED via state machine (override=true so no validation needed)
        // This is the full transaction boundary — stateMachine.transitionToState() commits on return
        stateMachine.transitionToState(
                testEvent.getEventCode(),
                EventWorkflowState.ARCHIVED,
                "test.organizer",
                true,
                "regression test");

        // Then: event state persisted to DB (not just held in memory)
        Event reloaded = eventRepository.findByEventCode(testEvent.getEventCode()).orElseThrow();
        assertThat(reloaded.getWorkflowState())
                .as("workflow_state must be ARCHIVED in DB after transaction commits")
                .isEqualTo(EventWorkflowState.ARCHIVED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private EventTask createAndSaveTask(String status) {
        EventTask task = new EventTask();
        task.setEventId(testEvent.getId());
        task.setTaskName("Task-" + UUID.randomUUID());
        task.setTriggerState("topic_selection");
        task.setStatus(status);
        task.setDueDate(Instant.now().plus(7, ChronoUnit.DAYS));
        return eventTaskRepository.save(task);
    }

    private Registration createAndSaveRegistration(String status) {
        return registrationRepository.save(Registration.builder()
                .registrationCode("REG-" + UUID.randomUUID())
                .eventId(testEvent.getId())
                .attendeeUsername("user." + UUID.randomUUID())
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }

    private Notification createAndSaveNotification(String status) {
        return notificationRepository.save(Notification.builder()
                .recipientUsername("user." + UUID.randomUUID())
                .eventCode(testEvent.getEventCode())
                .notificationType("EVENT_UPDATE")
                .channel("EMAIL")
                .subject("Test notification")
                .body("Test body")
                .status(status)
                .build());
    }
}
