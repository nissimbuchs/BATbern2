package ch.batbern.events.service;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.types.EventWorkflowState;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventTaskRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventWorkflowScheduledService — auto-archive job.
 *
 * Tests the processEventsToArchive() scheduled job which transitions
 * EVENT_COMPLETED → ARCHIVED after the 14-day post-event window expires.
 *
 * Uses a no-op LockProvider so ShedLock never blocks test execution
 * (lockAtLeastFor = "30s" would otherwise prevent sequential test calls).
 */
@SpringBootTest
public class EventWorkflowScheduledServiceIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class NoOpShedLockConfig {
        @Bean
        @Primary
        LockProvider lockProvider() {
            // Always grant the lock — lets each test call processEventsToArchive() independently
            return lockConfiguration -> Optional.of(() -> { /* no-op unlock */ });
        }
    }

    @Autowired
    private EventWorkflowScheduledService eventWorkflowScheduledService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    private int eventNumberSequence = (int) (System.currentTimeMillis() % 40000) + 60000;

    @BeforeEach
    void setUp() {
        cleanAll();
        eventNumberSequence += 1;
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        notificationRepository.deleteAll();
        registrationRepository.deleteAll();
        eventTaskRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void should_archiveCompletedEvent_when_olderThan14Days() {
        // Given: EVENT_COMPLETED event 15 days ago (outside 14-day window)
        Instant fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS);
        Event event = createCompletedEvent("BAT-ARCH-15", fifteenDaysAgo);

        // When: auto-archive job runs
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: event transitioned to ARCHIVED
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.ARCHIVED);
    }

    @Test
    void should_notArchiveCompletedEvent_when_within14Days() {
        // Given: EVENT_COMPLETED event 7 days ago (still within 14-day window)
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Event event = createCompletedEvent("BAT-ARCH-7", sevenDaysAgo);

        // When: auto-archive job runs
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: event stays in EVENT_COMPLETED
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    void should_notArchiveCompletedEvent_when_exactlyAt14DayBoundary() {
        // Given: EVENT_COMPLETED event exactly 14 days ago (still within window — boundary is exclusive)
        Instant fourteenDaysAgo = Instant.now().minus(14, ChronoUnit.DAYS);
        Event event = createCompletedEvent("BAT-ARCH-14", fourteenDaysAgo);

        // When: auto-archive job runs
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: event stays in EVENT_COMPLETED (window: date < today - 14 days)
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    void should_archiveMultipleCompletedEvents_when_allOlderThan14Days() {
        // Given: two old EVENT_COMPLETED events
        Instant twentyDaysAgo = Instant.now().minus(20, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Event event1 = createCompletedEvent("BAT-ARCH-20", twentyDaysAgo);
        Event event2 = createCompletedEvent("BAT-ARCH-30", thirtyDaysAgo);

        // When
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: both archived
        assertThat(eventRepository.findById(event1.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.ARCHIVED);
        assertThat(eventRepository.findById(event2.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.ARCHIVED);
    }

    @Test
    void should_onlyArchiveOldEvents_when_mixOfOldAndRecentExist() {
        // Given: one old (15 days) and one recent (7 days) EVENT_COMPLETED event
        Instant fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Event oldEvent = createCompletedEvent("BAT-ARCH-OLD", fifteenDaysAgo);
        Event recentEvent = createCompletedEvent("BAT-ARCH-NEW", sevenDaysAgo);

        // When
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: only old event archived, recent one stays
        assertThat(eventRepository.findById(oldEvent.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.ARCHIVED);
        assertThat(eventRepository.findById(recentEvent.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    void should_doNothing_when_noEventsQualifyForArchive() {
        // Given: only recent completed events (within 14 days), no old ones
        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);
        Event event = createCompletedEvent("BAT-ARCH-NONE", threeDaysAgo);

        // When: no exception thrown
        eventWorkflowScheduledService.processEventsToArchive();

        // Then: event unchanged
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Event createCompletedEvent(String eventCode, Instant eventDate) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .title("Test Event " + eventCode)
                .eventNumber(eventNumberSequence++)
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build();
        return eventRepository.save(event);
    }
}
