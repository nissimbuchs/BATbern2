package ch.batbern.events.repository;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Tests for SpeakerStatusHistoryRepository
 * Story 5.4: Speaker Status Management (AC15-16)
 *
 * Test Scenarios:
 * - Custom JPQL query: findBySpeakerPoolIdOrderByChangedAtDesc
 * - Custom JPQL query: findByEventCodeAndNewStatus
 * - Basic CRUD operations
 *
 * TDD Workflow: RED Phase - These tests will fail until repository is implemented
 *
 * Uses Testcontainers PostgreSQL for production parity (Migration V19).
 */
@Transactional
public class SpeakerStatusHistoryRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerStatusHistoryRepository repository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    private static final String TEST_EVENT_CODE = "BATbern998";
    private static final String ORGANIZER_USERNAME = "jane.organizer";

    private ch.batbern.events.domain.Event testEvent;
    private ch.batbern.events.domain.Session testSession;
    private ch.batbern.events.domain.SpeakerPool testSpeaker;

    @BeforeEach
    void setUp() {
        // Clean database (reverse FK order)
        repository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Step 1: Create Event (for event_code FK)
        testEvent = ch.batbern.events.domain.Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(998)
                .title("Test Event for Speaker Status")
                .description("Integration test event")
                .date(Instant.now().plusSeconds(86400))
                .registrationDeadline(Instant.now())
                .venueName("Test Venue")
                .venueAddress("123 Test Street, Bern")
                .venueCapacity(100)
                .organizerUsername(ORGANIZER_USERNAME)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Step 2: Create Session (for session_id FK)
        testSession = ch.batbern.events.domain.Session.builder()
                .sessionSlug("test-session-speaker-status")
                .eventId(testEvent.getId())
                .title("Test Session")  // NOT NULL requirement
                .build();
        testSession = sessionRepository.save(testSession);

        // Step 3: Create SpeakerPool (for speaker_pool_id FK)
        testSpeaker = new ch.batbern.events.domain.SpeakerPool();
        testSpeaker.setEventId(testEvent.getId());
        testSpeaker.setSpeakerName("Jane Smith");
        testSpeaker.setCompany("Tech Corp AG");
        testSpeaker.setStatus(SpeakerWorkflowState.IDENTIFIED);
        testSpeaker.setSessionId(testSession.getId());
        testSpeaker = speakerPoolRepository.save(testSpeaker);
    }

    /**
     * AC15: should_findHistoryBySpeakerId_when_orderByChangedAtDesc
     * Story 5.4 AC15: Track status history with JPQL queries
     */
    @Test
    @DisplayName("Should find history by speaker pool ID ordered by changed_at descending")
    void should_findHistoryBySpeakerId_when_orderByChangedAtDesc() {
        // Given: Multiple status changes for a speaker
        SpeakerStatusHistory history1 = createStatusHistory(
            testSpeaker.getId(),
            SpeakerWorkflowState.IDENTIFIED,
            SpeakerWorkflowState.CONTACTED,
            Instant.now().minusSeconds(3600),
            "Initial contact"
        );
        repository.save(history1);

        SpeakerStatusHistory history2 = createStatusHistory(
            testSpeaker.getId(),
            SpeakerWorkflowState.CONTACTED,
            SpeakerWorkflowState.READY,
            Instant.now().minusSeconds(1800),
            "Speaker confirmed"
        );
        repository.save(history2);

        SpeakerStatusHistory history3 = createStatusHistory(
            testSpeaker.getId(),
            SpeakerWorkflowState.READY,
            SpeakerWorkflowState.ACCEPTED,
            Instant.now(),
            "Officially accepted"
        );
        repository.save(history3);

        // When: Find history by speaker pool ID
        List<SpeakerStatusHistory> result = repository.findBySpeakerPoolIdOrderByChangedAtDesc(testSpeaker.getId());

        // Then: Should return all history records in descending order
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        assertThat(result.get(1).getNewStatus()).isEqualTo(SpeakerWorkflowState.READY);
        assertThat(result.get(2).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    /**
     * AC15: should_findHistoryByEventIdAndStatus_when_queryExecuted
     * Story 5.4 AC15: Query history by event and status
     * V29: Updated to use eventId instead of eventCode
     */
    @Test
    @DisplayName("Should find history by event ID and new status")
    void should_findHistoryByEventIdAndStatus_when_queryExecuted() {
        // Given: Multiple speakers with different statuses
        ch.batbern.events.domain.SpeakerPool speaker1 = createTestSpeaker("Speaker 1", "Company 1");
        ch.batbern.events.domain.SpeakerPool speaker2 = createTestSpeaker("Speaker 2", "Company 2");
        ch.batbern.events.domain.SpeakerPool speaker3 = createTestSpeaker("Speaker 3", "Company 3");

        repository.save(createStatusHistory(speaker1.getId(), SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.ACCEPTED, Instant.now(), "Accepted 1"));
        repository.save(createStatusHistory(speaker2.getId(), SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.ACCEPTED, Instant.now(), "Accepted 2"));
        repository.save(createStatusHistory(speaker3.getId(), SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.DECLINED, Instant.now(), "Declined"));

        // When: Find history by event ID and ACCEPTED status
        List<SpeakerStatusHistory> result = repository.findByEventIdAndNewStatus(testEvent.getId(), SpeakerWorkflowState.ACCEPTED);

        // Then: Should return only ACCEPTED status changes
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(h -> h.getNewStatus() == SpeakerWorkflowState.ACCEPTED);
        assertThat(result).allMatch(h -> h.getEventId().equals(testEvent.getId()));
    }

    /**
     * AC3: should_saveStatusHistory_when_validDataProvided
     * Story 5.4 AC3: Track timestamp, organizer, and reason
     */
    @Test
    @DisplayName("Should save status history with all required fields")
    void should_saveStatusHistory_when_validDataProvided() {
        // Given: Status history record with all fields
        SpeakerStatusHistory history = createStatusHistory(
            testSpeaker.getId(),
            SpeakerWorkflowState.IDENTIFIED,
            SpeakerWorkflowState.CONTACTED,
            Instant.now(),
            "Initial contact via email"
        );

        // When: Save to repository
        SpeakerStatusHistory saved = repository.save(history);
        repository.flush(); // Force persistence to trigger @CreationTimestamp

        // Then: Should persist with ID and all fields
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSpeakerPoolId()).isEqualTo(testSpeaker.getId());
        assertThat(saved.getSessionId()).isEqualTo(testSession.getId());
        assertThat(saved.getEventId()).isEqualTo(testEvent.getId()); // V29: Changed from eventCode to eventId
        assertThat(saved.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(saved.getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(saved.getChangedByUsername()).isEqualTo(ORGANIZER_USERNAME);
        assertThat(saved.getChangeReason()).isEqualTo("Initial contact via email");
        assertThat(saved.getChangedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    /**
     * AC4: should_enforceReasonMaxLength_when_saving
     * Story 5.4 AC4: Reason field max 2000 characters
     */
    @Test
    @DisplayName("Should enforce max 2000 characters for reason field")
    void should_enforceReasonMaxLength_when_saving() {
        // Given: Status history with reason exceeding 2000 characters
        String longReason = "a".repeat(2001);
        SpeakerStatusHistory history = createStatusHistory(
            testSpeaker.getId(),
            SpeakerWorkflowState.IDENTIFIED,
            SpeakerWorkflowState.CONTACTED,
            Instant.now(),
            longReason
        );

        // When/Then: Should throw validation exception or truncate
        // (Behavior depends on database constraint - PostgreSQL will throw error)
        try {
            repository.save(history);
            repository.flush(); // Force immediate database write
            assertThat(true).as("Expected validation error for reason > 2000 chars").isFalse();
        } catch (Exception e) {
            // Expected: validation or database constraint violation
            assertThat(e).isInstanceOfAny(
                org.springframework.dao.DataIntegrityViolationException.class,
                jakarta.validation.ConstraintViolationException.class
            );
        }
    }

    /**
     * Helper: Create test speaker in pool
     */
    private ch.batbern.events.domain.SpeakerPool createTestSpeaker(String name, String company) {
        ch.batbern.events.domain.SpeakerPool speaker = new ch.batbern.events.domain.SpeakerPool();
        speaker.setEventId(testEvent.getId());
        speaker.setSessionId(testSession.getId());
        speaker.setSpeakerName(name);
        speaker.setCompany(company);
        speaker.setStatus(SpeakerWorkflowState.IDENTIFIED);
        return speakerPoolRepository.save(speaker);
    }

    /**
     * Helper: Create test status history record
     * V29: Updated to use eventId instead of eventCode
     */
    private SpeakerStatusHistory createStatusHistory(
        UUID speakerPoolId,
        SpeakerWorkflowState previousStatus,
        SpeakerWorkflowState newStatus,
        Instant changedAt,
        String reason
    ) {
        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speakerPoolId);
        history.setSessionId(testSession.getId());
        history.setEventId(testEvent.getId()); // V29: Changed from setEventCode to setEventId
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedByUsername(ORGANIZER_USERNAME);
        history.setChangeReason(reason);
        history.setChangedAt(changedAt);
        return history;
    }
}
