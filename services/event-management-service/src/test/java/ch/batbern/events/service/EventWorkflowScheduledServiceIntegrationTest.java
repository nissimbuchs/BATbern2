package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventWorkflowScheduledService
 * Story BAT-16 (AC8): Event Lifecycle Automation
 *
 * Tests:
 * - Automatic EVENT_LIVE transition on event day
 * - Automatic EVENT_COMPLETED transition after event
 * - Multiple events processed in batch
 * - Error handling for failed transitions
 *
 * NOTE: This test class does NOT use @Transactional to allow the scheduled service
 * to see committed data. Instead, we use TransactionTemplate to control transactions
 * and clean up data in @AfterEach.
 */
@SpringBootTest
public class EventWorkflowScheduledServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventWorkflowScheduledService eventWorkflowScheduledService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final ZoneId BERN_ZONE = ZoneId.of("Europe/Zurich");
    // Use timestamp-based sequence to ensure uniqueness across test runs
    private int eventNumberSequence = (int) (System.currentTimeMillis() % 100000);

    @BeforeEach
    void setUp() {
        // Clear any leftover data from previous tests
        transactionTemplate.execute(status -> {
            eventRepository.deleteAll();
            entityManager.flush();
            return null;
        });
        entityManager.clear();

        // Each test uses unique event numbers to avoid constraint violations
        eventNumberSequence += 1;
    }

    @AfterEach
    void tearDown() {
        // Clean up test data after each test
        transactionTemplate.execute(status -> {
            eventRepository.deleteAll();
            entityManager.flush();
            return null;
        });
        // Clear entity manager cache to ensure clean state for next test
        entityManager.clear();
    }

    /**
     * AC8: Transition event to EVENT_LIVE when event date is today
     */
    @Test
    void should_transitionToEventLive_when_eventDateIsToday() {
        // Given: Event happening today in AGENDA_FINALIZED state
        LocalDate today = LocalDate.now(BERN_ZONE);
        Instant eventDate = today.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-GOING-LIVE", eventDate, EventWorkflowState.AGENDA_FINALIZED);

        // When: processEventsGoingLive job runs
        eventWorkflowScheduledService.processEventsGoingLive();

        // Then: Event should transition to EVENT_LIVE
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_LIVE);
    }

    /**
     * AC8: Should not transition events happening tomorrow
     */
    @Test
    void should_notTransitionToEventLive_when_eventDateIsTomorrow() {
        // Given: Event happening tomorrow
        LocalDate tomorrow = LocalDate.now(BERN_ZONE).plusDays(1);
        Instant eventDate = tomorrow.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-TOMORROW", eventDate, EventWorkflowState.AGENDA_FINALIZED);

        // When: processEventsGoingLive job runs
        eventWorkflowScheduledService.processEventsGoingLive();

        // Then: Event should remain in AGENDA_FINALIZED
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_FINALIZED);
    }

    /**
     * AC8: Should not transition events happening yesterday
     */
    @Test
    void should_notTransitionToEventLive_when_eventDateWasYesterday() {
        // Given: Event that happened yesterday
        LocalDate yesterday = LocalDate.now(BERN_ZONE).minusDays(1);
        Instant eventDate = yesterday.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-YESTERDAY", eventDate, EventWorkflowState.AGENDA_FINALIZED);

        // When: processEventsGoingLive job runs
        eventWorkflowScheduledService.processEventsGoingLive();

        // Then: Event should remain in AGENDA_FINALIZED (will be picked up by completion job)
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_FINALIZED);
    }

    /**
     * AC8: Process multiple events going live on same day
     */
    @Test
    void should_transitionMultipleEvents_when_multipleEventsToday() {
        // Given: Three events happening today
        LocalDate today = LocalDate.now(BERN_ZONE);
        Instant morning = today.atTime(9, 0).atZone(BERN_ZONE).toInstant();
        Instant afternoon = today.atTime(14, 0).atZone(BERN_ZONE).toInstant();
        Instant evening = today.atTime(18, 0).atZone(BERN_ZONE).toInstant();

        Event event1 = createEvent("BAT-MORNING", morning, EventWorkflowState.AGENDA_FINALIZED);
        Event event2 = createEvent("BAT-AFTERNOON", afternoon, EventWorkflowState.AGENDA_FINALIZED);
        Event event3 = createEvent("BAT-EVENING", evening, EventWorkflowState.AGENDA_FINALIZED);

        // When: processEventsGoingLive job runs
        eventWorkflowScheduledService.processEventsGoingLive();

        // Then: All three events should transition to EVENT_LIVE
        assertThat(refetchEvent(event1.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_LIVE);
        assertThat(refetchEvent(event2.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_LIVE);
        assertThat(refetchEvent(event3.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_LIVE);
    }

    /**
     * AC8: Transition event to EVENT_COMPLETED when event date has passed
     */
    @Test
    void should_transitionToEventCompleted_when_eventDateHasPassed() {
        // Given: Event that happened yesterday, currently in EVENT_LIVE state
        LocalDate yesterday = LocalDate.now(BERN_ZONE).minusDays(1);
        Instant eventDate = yesterday.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-COMPLETED", eventDate, EventWorkflowState.EVENT_LIVE);

        // When: processCompletedEvents job runs
        eventWorkflowScheduledService.processCompletedEvents();

        // Then: Event should transition to EVENT_COMPLETED
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    /**
     * AC8: Should not complete events happening today
     */
    @Test
    void should_notTransitionToEventCompleted_when_eventDateIsToday() {
        // Given: Event happening today in EVENT_LIVE state
        LocalDate today = LocalDate.now(BERN_ZONE);
        Instant eventDate = today.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-TODAY-LIVE", eventDate, EventWorkflowState.EVENT_LIVE);

        // When: processCompletedEvents job runs
        eventWorkflowScheduledService.processCompletedEvents();

        // Then: Event should remain in EVENT_LIVE (event still ongoing)
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_LIVE);
    }

    /**
     * AC8: Process multiple completed events
     */
    @Test
    void should_transitionMultipleEvents_when_multipleEventsPassed() {
        // Given: Three events that happened in the past
        LocalDate twoDaysAgo = LocalDate.now(BERN_ZONE).minusDays(2);
        LocalDate threeDaysAgo = LocalDate.now(BERN_ZONE).minusDays(3);
        LocalDate lastWeek = LocalDate.now(BERN_ZONE).minusDays(7);

        Instant event1Date = twoDaysAgo.atTime(10, 0).atZone(BERN_ZONE).toInstant();
        Instant event2Date = threeDaysAgo.atTime(14, 0).atZone(BERN_ZONE).toInstant();
        Instant event3Date = lastWeek.atTime(18, 0).atZone(BERN_ZONE).toInstant();

        Event event1 = createEvent("BAT-2-DAYS-AGO", event1Date, EventWorkflowState.EVENT_LIVE);
        Event event2 = createEvent("BAT-3-DAYS-AGO", event2Date, EventWorkflowState.EVENT_LIVE);
        Event event3 = createEvent("BAT-LAST-WEEK", event3Date, EventWorkflowState.EVENT_LIVE);

        // When: processCompletedEvents job runs
        eventWorkflowScheduledService.processCompletedEvents();

        // Then: All three events should transition to EVENT_COMPLETED
        assertThat(refetchEvent(event1.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_COMPLETED);
        assertThat(refetchEvent(event2.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_COMPLETED);
        assertThat(refetchEvent(event3.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    /**
     * AC8: Should only transition events in correct state (AGENDA_FINALIZED -> EVENT_LIVE)
     */
    @Test
    void should_onlyTransitionEventsInAgendaFinalized_when_goingLive() {
        // Given: Two events today - one in AGENDA_FINALIZED, one in SLOT_ASSIGNMENT
        LocalDate today = LocalDate.now(BERN_ZONE);
        Instant eventDate = today.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event finalizedEvent = createEvent("BAT-FINALIZED", eventDate, EventWorkflowState.AGENDA_FINALIZED);
        Event notReadyEvent = createEvent("BAT-NOT-READY", eventDate, EventWorkflowState.SLOT_ASSIGNMENT);

        // When: processEventsGoingLive job runs
        eventWorkflowScheduledService.processEventsGoingLive();

        // Then: Only finalized event should transition
        assertThat(refetchEvent(finalizedEvent.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_LIVE);
        assertThat(refetchEvent(notReadyEvent.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.SLOT_ASSIGNMENT);
    }

    /**
     * AC8: Should only transition events in EVENT_LIVE state to EVENT_COMPLETED
     */
    @Test
    void should_onlyTransitionEventsInEventLive_when_completing() {
        // Given: Two past events - one in EVENT_LIVE, one in AGENDA_FINALIZED
        LocalDate yesterday = LocalDate.now(BERN_ZONE).minusDays(1);
        Instant eventDate = yesterday.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event liveEvent = createEvent("BAT-LIVE-EVENT", eventDate, EventWorkflowState.EVENT_LIVE);
        Event finalizedEvent = createEvent("BAT-FINALIZED-EVENT", eventDate, EventWorkflowState.AGENDA_FINALIZED);

        // When: processCompletedEvents job runs
        eventWorkflowScheduledService.processCompletedEvents();

        // Then: Only EVENT_LIVE event should transition to EVENT_COMPLETED
        assertThat(refetchEvent(liveEvent.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_COMPLETED);
        assertThat(refetchEvent(finalizedEvent.getId()).getWorkflowState())
                .isEqualTo(EventWorkflowState.AGENDA_FINALIZED);
    }

    /**
     * AC8: Test manual trigger methods
     */
    @Test
    void should_transitionEvents_when_manualTriggerCalled() {
        // Given: Event happening today
        LocalDate today = LocalDate.now(BERN_ZONE);
        Instant eventDate = today.atTime(10, 0).atZone(BERN_ZONE).toInstant();

        Event event = createEvent("BAT-MANUAL-TRIGGER", eventDate, EventWorkflowState.AGENDA_FINALIZED);

        // When: Manual trigger is called
        eventWorkflowScheduledService.triggerManualGoingLive();

        // Then: Event should transition
        Event updated = refetchEvent(event.getId());
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_LIVE);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Event createEvent(String eventCode, Instant eventDate, EventWorkflowState workflowState) {
        // Create and save event in a transaction, ensuring it's committed before scheduled service runs
        return transactionTemplate.execute(status -> {
            Event event = Event.builder()
                    .eventCode(eventCode)
                    .title("Test Event " + eventCode)
                    .eventNumber(eventNumberSequence++)
                    .date(eventDate)
                    .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                    .venueName("Kursaal Bern")
                    .venueAddress("Kornhausstrasse 3, 3013 Bern")
                    .venueCapacity(300)
                    .organizerUsername("test.organizer")
                    .eventType(EventType.FULL_DAY)
                    .workflowState(workflowState)
                    .topicCode("cloud-architecture")
                    .build();
            Event saved = eventRepository.save(event);
            entityManager.flush(); // Ensure data is written to database
            return saved;
        });
    }

    private Event refetchEvent(java.util.UUID eventId) {
        // Clear entity manager cache and fetch fresh data from database
        entityManager.clear();
        return transactionTemplate.execute(status ->
                eventRepository.findById(eventId).orElseThrow()
        );
    }
}
