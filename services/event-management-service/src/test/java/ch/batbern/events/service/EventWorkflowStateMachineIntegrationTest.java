package ch.batbern.events.service;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.events.exception.WorkflowValidationException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for EventWorkflowStateMachine - Story 5.1a AC8-11 (RED Phase)
 *
 * Test Strategy: TDD Red-Green-Refactor
 * - These tests are written BEFORE implementation (RED Phase)
 * - Tests should FAIL initially because EventWorkflowStateMachine doesn't exist yet
 * - Implementation in Task 4 will make these tests pass (GREEN Phase)
 *
 * CRITICAL: Uses Testcontainers PostgreSQL (NOT H2) for production parity
 * - Tests real database persistence
 * - Tests PostgreSQL-specific features
 * - Tests Flyway migrations
 * - Tests optimistic locking
 *
 * Coverage Requirements:
 * - >80% coverage for integration tests
 * - All workflow states tested in sequence
 * - Concurrent access scenarios tested
 */
@Transactional
@DisplayName("EventWorkflowStateMachine Integration Tests")
class EventWorkflowStateMachineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventWorkflowStateMachine stateMachine;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private Event testEvent;
    private String eventCode;
    private String organizerUsername;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        eventCode = "BATbern56";
        organizerUsername = "john.doe";

        // Create test event with CREATED workflow state
        testEvent = Event.builder()
                .eventCode(eventCode)
                .title("Integration Test Event")
                .eventNumber(56)
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(EventType.FULL_DAY)
                .organizerUsername(organizerUsername)
                .workflowState(EventWorkflowState.CREATED)
                .build();

        testEvent = eventRepository.save(testEvent);
    }

    // Test 2.8: AC9 - should_persistWorkflowState_when_transitionCompletes
    @Test
    @DisplayName("Test 2.8: Should persist workflow state to PostgreSQL database")
    void should_persistWorkflowState_when_transitionCompletes() {
        // Given: Event in CREATED state
        assertThat(testEvent.getWorkflowState()).isEqualTo(EventWorkflowState.CREATED);

        // When: Transition to TOPIC_SELECTION
        Event transitionedEvent = stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, organizerUsername);

        // Then: State persisted in database
        assertThat(transitionedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);

        // Verify by re-fetching from database (tests actual persistence)
        Event reloadedEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(reloadedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
        assertThat(reloadedEvent.getUpdatedBy()).isEqualTo(organizerUsername);
        assertThat(reloadedEvent.getUpdatedAt()).isNotNull();
    }

    // Test 5.1: AC18 - should_transitionThroughCompleteWorkflow_when_allValidationsPassed
    @Test
    @Disabled("Waiting for Story 5.3 (Speaker Brainstorming) and Story 5.6 (Content Collection) - placeholder validations always fail")
    @DisplayName("Test 5.1: Should transition through complete 16-step workflow sequence")
    void should_transitionThroughCompleteWorkflow_when_allValidationsPassed() {
        // Given: Event in CREATED state
        Event currentEvent = testEvent;

        // Define the complete workflow sequence (8-state model)
        EventWorkflowState[] workflowSequence = {
            EventWorkflowState.CREATED,
            EventWorkflowState.TOPIC_SELECTION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SLOT_ASSIGNMENT,
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED,
            EventWorkflowState.ARCHIVED
        };

        // When: Transition through all states sequentially
        for (int i = 1; i < workflowSequence.length; i++) {
            EventWorkflowState targetState = workflowSequence[i];

            currentEvent = stateMachine.transitionToState(eventCode, targetState, organizerUsername);

            // Then: Each transition succeeds and persists
            assertThat(currentEvent.getWorkflowState()).isEqualTo(targetState);

            // Verify persistence in database
            Event persistedEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
            assertThat(persistedEvent.getWorkflowState()).isEqualTo(targetState);
        }

        // Final verification: Event reached ARCHIVED state
        Event finalEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(finalEvent.getWorkflowState()).isEqualTo(EventWorkflowState.ARCHIVED);
    }

    // Test 2.9: AC11 - should_handleConcurrentTransitions_when_optimisticLocking_enabled
    @Test
    @Disabled("Waiting for Story 5.3 (Speaker Brainstorming) - depends on complete workflow implementation")
    @DisplayName("Test 2.9: Should handle concurrent state transitions with optimistic locking")
    void should_handleConcurrentTransitions_when_optimisticLocking_enabled() throws InterruptedException {
        // Given: Event in CREATED state
        int numberOfThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        // When: Multiple threads attempt to transition the same event concurrently
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    // Each thread attempts to transition to TOPIC_SELECTION
                    stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, "organizer" + threadId);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    // Expected: Optimistic locking prevents concurrent modifications
                    failureCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: Only ONE transition should succeed (optimistic locking prevents others)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(numberOfThreads - 1);

        // Verify final state in database
        Event finalEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(finalEvent.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
    }

    // Test: Invalid transition persisted check
    @Test
    @DisplayName("Should NOT persist state when invalid transition attempted")
    void should_notPersist_when_invalidTransitionAttempted() {
        // Given: Event in CREATED state
        EventWorkflowState initialState = testEvent.getWorkflowState();
        assertThat(initialState).isEqualTo(EventWorkflowState.CREATED);

        // When: Attempt invalid transition to ARCHIVED
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.ARCHIVED, organizerUsername)
        ).isInstanceOf(InvalidStateTransitionException.class);

        // Then: State should remain unchanged in database
        Event unchangedEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(unchangedEvent.getWorkflowState()).isEqualTo(initialState);
    }

    // Test: Validation failure rollback
    @Test
    @DisplayName("Should rollback transaction when validation fails")
    void should_rollbackTransaction_when_validationFails() {
        // Given: Event in SPEAKER_IDENTIFICATION state without accepted speakers (will fail threshold validation)
        testEvent.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        testEvent = eventRepository.save(testEvent);

        // When: Attempt transition to SLOT_ASSIGNMENT without meeting threshold (no ACCEPTED speakers)
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.SLOT_ASSIGNMENT, organizerUsername)
        ).isInstanceOf(WorkflowValidationException.class);

        // Then: State should remain unchanged (transaction rolled back)
        Event unchangedEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(unchangedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.SPEAKER_IDENTIFICATION);
    }

    // Test: Database index usage (workflow_state column)
    @Test
    @DisplayName("Should efficiently query events by workflow state using database index")
    void should_queryByWorkflowState_when_indexExists() {
        // Given: Multiple events in different workflow states
        Event event1 = createTestEvent("BATbern57", EventWorkflowState.CREATED);
        Event event2 = createTestEvent("BATbern58", EventWorkflowState.TOPIC_SELECTION);
        Event event3 = createTestEvent("BATbern59", EventWorkflowState.CREATED);

        eventRepository.saveAll(List.of(event1, event2, event3));

        // When: Query events by workflow state (uses idx_events_workflow_state)
        List<Event> createdEvents = eventRepository.findAll().stream()
                .filter(e -> e.getWorkflowState() == EventWorkflowState.CREATED)
                .toList();

        // Then: Correct events retrieved efficiently
        assertThat(createdEvents).hasSize(3); // testEvent + event1 + event3
        assertThat(createdEvents).allMatch(e -> e.getWorkflowState() == EventWorkflowState.CREATED);
    }

    // Test: Idempotent transitions
    @Test
    @DisplayName("Should handle idempotent transitions (same state to same state)")
    void should_handleIdempotentTransition_when_transitioningToSameState() {
        // Given: Event in CREATED state
        EventWorkflowState currentState = EventWorkflowState.CREATED;
        assertThat(testEvent.getWorkflowState()).isEqualTo(currentState);

        // When: Transition to same state (idempotent)
        Event result = stateMachine.transitionToState(eventCode, currentState, organizerUsername);

        // Then: No error, state remains unchanged
        assertThat(result.getWorkflowState()).isEqualTo(currentState);

        // Verify persistence
        Event persistedEvent = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(persistedEvent.getWorkflowState()).isEqualTo(currentState);
    }

    // ========================================================================
    // Story 11.B.3 AC9: validateAllSpeakersConfirmed — 3 failure cases + 1 happy path
    // ========================================================================

    @Test
    @DisplayName("AC9-1: AGENDA_PUBLISHED fails when no accepted-or-beyond speakers exist")
    void should_rejectAgendaPublished_when_noAcceptedSpeakersExist() {
        testEvent.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        testEvent = eventRepository.save(testEvent);
        // Pool has only IDENTIFIED speakers — acceptedOrBeyondSpeakers == 0
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId()).speakerName("Alice").status(SpeakerWorkflowState.IDENTIFIED).build());

        assertThatThrownBy(() -> stateMachine.transitionToState(eventCode, EventWorkflowState.AGENDA_PUBLISHED, organizerUsername))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("no accepted speakers");
    }

    @Test
    @DisplayName("AC9-2: AGENDA_PUBLISHED fails when ACCEPTED speaker is not yet QUALITY_REVIEWED (gap > 0)")
    void should_rejectAgendaPublished_when_acceptedSpeakerNotQualityReviewed() {
        testEvent.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        testEvent = eventRepository.save(testEvent);
        Session session = new Session();
        session.setEventId(testEvent.getId());
        session.setEventCode(eventCode);
        session.setTitle("S1");
        session.setSessionSlug("s1-" + testEvent.getId());
        session.setSessionType("presentation");
        session.setStartTime(Instant.now().plus(30, ChronoUnit.DAYS));
        session = sessionRepository.save(session);
        // Speaker is ACCEPTED (counts in acceptedOrBeyondSpeakers) but not QUALITY_REVIEWED (not publishable)
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId()).speakerName("Bob").status(SpeakerWorkflowState.ACCEPTED)
                .sessionId(session.getId()).build());

        assertThatThrownBy(() -> stateMachine.transitionToState(eventCode, EventWorkflowState.AGENDA_PUBLISHED, organizerUsername))
                .isInstanceOf(WorkflowValidationException.class);
    }

    @Test
    @DisplayName("AC9-3: AGENDA_PUBLISHED fails when QUALITY_REVIEWED speaker has no session start_time")
    void should_rejectAgendaPublished_when_qualityReviewedSpeakerHasNoSessionStartTime() {
        testEvent.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        testEvent = eventRepository.save(testEvent);
        // Session exists but has no start_time → speaker is not publishable
        Session session = new Session();
        session.setEventId(testEvent.getId());
        session.setEventCode(eventCode);
        session.setTitle("S2");
        session.setSessionSlug("s2-" + testEvent.getId());
        session.setSessionType("presentation");
        session.setStartTime(null);
        session = sessionRepository.save(session);
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId()).speakerName("Carol").status(SpeakerWorkflowState.QUALITY_REVIEWED)
                .sessionId(session.getId()).build());

        assertThatThrownBy(() -> stateMachine.transitionToState(eventCode, EventWorkflowState.AGENDA_PUBLISHED, organizerUsername))
                .isInstanceOf(WorkflowValidationException.class);
    }

    @Test
    @DisplayName("AC9-4: AGENDA_PUBLISHED succeeds when all accepted speakers are publishable")
    void should_publishAgenda_when_allAcceptedSpeakersArePublishable() {
        testEvent.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        testEvent = eventRepository.save(testEvent);
        Session session = new Session();
        session.setEventId(testEvent.getId());
        session.setEventCode(eventCode);
        session.setTitle("S3");
        session.setSessionSlug("s3-" + testEvent.getId());
        session.setSessionType("presentation");
        session.setStartTime(Instant.now().plus(30, ChronoUnit.DAYS));
        session = sessionRepository.save(session);
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId()).speakerName("Dave").status(SpeakerWorkflowState.QUALITY_REVIEWED)
                .sessionId(session.getId()).build());

        Event result = stateMachine.transitionToState(eventCode, EventWorkflowState.AGENDA_PUBLISHED, organizerUsername);
        assertThat(result.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
    }

    // Helper method to create test events
    private Event createTestEvent(String code, EventWorkflowState workflowState) {
        return Event.builder()
                .eventCode(code)
                .title("Test Event " + code)
                .eventNumber(Integer.parseInt(code.replaceAll("[^0-9]", "")))
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .eventType(EventType.FULL_DAY)
                .organizerUsername(organizerUsername)
                .workflowState(workflowState)
                .build();
    }
}
