package ch.batbern.events.service;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Integration test for EventArchivalCleanupService AC5:
 * "Task cancellation is preserved even when best-effort steps fail."
 *
 * Uses a @MockBean for {@link ArchivalBestEffortSteps} to inject controlled failures.
 * This requires a separate Spring context from EventArchivalCleanupIntegrationTest
 * (the @MockBean changes the application context configuration).
 *
 * NOT annotated with @Transactional — each operation commits its own transaction.
 */
@SpringBootTest
class EventArchivalCleanupAc5IntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private ArchivalBestEffortSteps bestEffortSteps;

    @Autowired
    private EventArchivalCleanupService eventArchivalCleanupService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final AtomicInteger EVENT_NUMBER_COUNTER =
            new AtomicInteger((int) ((System.currentTimeMillis() / 1000) % 100000) + 50000);

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
                .title("AC5 Test Event")
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
    @DisplayName("AC5: task cancellation committed to DB even when notification step fails")
    void cleanup_tasksCancelledInDb_evenWhenNotificationStepFails() {
        // Given: open tasks exist; notification step fails
        EventTask todo = new EventTask();
        todo.setEventId(testEvent.getId());
        todo.setTaskName("Task-" + UUID.randomUUID());
        todo.setTriggerState("topic_selection");
        todo.setStatus("todo");
        todo.setDueDate(Instant.now().plus(7, ChronoUnit.DAYS));
        UUID taskId = eventTaskRepository.save(todo).getId();

        when(bestEffortSteps.cancelWaitlistRegistrations(any(), anyString())).thenReturn(0);
        doThrow(new RuntimeException("simulated notification store outage"))
                .when(bestEffortSteps).dismissNotifications(anyString());

        // When: cleanup is called — notification step throws but is caught
        assertThatNoException().isThrownBy(() ->
                eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode()));

        // Then: task IS cancelled in DB (its own @Transactional committed independently)
        EventTask taskAfter = eventTaskRepository.findById(taskId).orElseThrow();
        assertThat(taskAfter.getStatus())
                .as("Task must be 'cancelled' in DB even though the notification step failed")
                .isEqualTo("cancelled");
        assertThat(taskAfter.getCancelledReason()).isEqualTo("Event archived");
        assertThat(taskAfter.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("AC5: task cancellation committed to DB even when waitlist step fails")
    void cleanup_tasksCancelledInDb_evenWhenWaitlistStepFails() {
        // Given: open task exists; waitlist step fails
        EventTask todo = new EventTask();
        todo.setEventId(testEvent.getId());
        todo.setTaskName("Task-" + UUID.randomUUID());
        todo.setTriggerState("topic_selection");
        todo.setStatus("todo");
        todo.setDueDate(Instant.now().plus(7, ChronoUnit.DAYS));
        UUID taskId = eventTaskRepository.save(todo).getId();

        doThrow(new RuntimeException("simulated registration store outage"))
                .when(bestEffortSteps).cancelWaitlistRegistrations(any(), anyString());
        when(bestEffortSteps.dismissNotifications(anyString())).thenReturn(0);

        // When: cleanup is called — waitlist step throws but is caught
        assertThatNoException().isThrownBy(() ->
                eventArchivalCleanupService.cleanup(testEvent.getId(), testEvent.getEventCode()));

        // Then: task IS cancelled in DB — independent from the failing waitlist step
        EventTask taskAfter = eventTaskRepository.findById(taskId).orElseThrow();
        assertThat(taskAfter.getStatus())
                .as("Task must be 'cancelled' in DB even though the waitlist step failed")
                .isEqualTo("cancelled");
    }
}
