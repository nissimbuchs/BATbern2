package ch.batbern.speakers.repository;

import ch.batbern.speakers.AbstractIntegrationTest;
import ch.batbern.speakers.domain.SpeakerStatusHistory;
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

    private static final String TEST_EVENT_CODE = "BATbern998";
    private static final UUID TEST_SPEAKER_POOL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final String ORGANIZER_USERNAME = "jane.organizer";

    @BeforeEach
    void setUp() {
        // Data will be created in test methods
        // For RED phase, repository doesn't exist yet so tests will fail
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
            TEST_SPEAKER_POOL_ID,
            SpeakerWorkflowState.IDENTIFIED,
            SpeakerWorkflowState.CONTACTED,
            Instant.now().minusSeconds(3600),
            "Initial contact"
        );
        repository.save(history1);

        SpeakerStatusHistory history2 = createStatusHistory(
            TEST_SPEAKER_POOL_ID,
            SpeakerWorkflowState.CONTACTED,
            SpeakerWorkflowState.READY,
            Instant.now().minusSeconds(1800),
            "Speaker confirmed"
        );
        repository.save(history2);

        SpeakerStatusHistory history3 = createStatusHistory(
            TEST_SPEAKER_POOL_ID,
            SpeakerWorkflowState.READY,
            SpeakerWorkflowState.ACCEPTED,
            Instant.now(),
            "Officially accepted"
        );
        repository.save(history3);

        // When: Find history by speaker pool ID
        List<SpeakerStatusHistory> result = repository.findBySpeakerPoolIdOrderByChangedAtDesc(TEST_SPEAKER_POOL_ID);

        // Then: Should return all history records in descending order
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        assertThat(result.get(1).getNewStatus()).isEqualTo(SpeakerWorkflowState.READY);
        assertThat(result.get(2).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    /**
     * AC15: should_findHistoryByEventCodeAndStatus_when_queryExecuted
     * Story 5.4 AC15: Query history by event and status
     */
    @Test
    @DisplayName("Should find history by event code and new status")
    void should_findHistoryByEventCodeAndStatus_when_queryExecuted() {
        // Given: Multiple speakers with different statuses
        UUID speaker1 = UUID.randomUUID();
        UUID speaker2 = UUID.randomUUID();
        UUID speaker3 = UUID.randomUUID();

        repository.save(createStatusHistory(speaker1, SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.ACCEPTED, Instant.now(), "Accepted 1"));
        repository.save(createStatusHistory(speaker2, SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.ACCEPTED, Instant.now(), "Accepted 2"));
        repository.save(createStatusHistory(speaker3, SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.DECLINED, Instant.now(), "Declined"));

        // When: Find history by event code and ACCEPTED status
        List<SpeakerStatusHistory> result = repository.findByEventCodeAndNewStatus(TEST_EVENT_CODE, SpeakerWorkflowState.ACCEPTED);

        // Then: Should return only ACCEPTED status changes
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(h -> h.getNewStatus() == SpeakerWorkflowState.ACCEPTED);
        assertThat(result).allMatch(h -> h.getEventCode().equals(TEST_EVENT_CODE));
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
            TEST_SPEAKER_POOL_ID,
            SpeakerWorkflowState.IDENTIFIED,
            SpeakerWorkflowState.CONTACTED,
            Instant.now(),
            "Initial contact via email"
        );

        // When: Save to repository
        SpeakerStatusHistory saved = repository.save(history);

        // Then: Should persist with ID and all fields
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSpeakerPoolId()).isEqualTo(TEST_SPEAKER_POOL_ID);
        assertThat(saved.getSessionId()).isEqualTo(TEST_SESSION_ID);
        assertThat(saved.getEventCode()).isEqualTo(TEST_EVENT_CODE);
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
            TEST_SPEAKER_POOL_ID,
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
     * Helper: Create test status history record
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
        history.setSessionId(TEST_SESSION_ID);
        history.setEventCode(TEST_EVENT_CODE);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedByUsername(ORGANIZER_USERNAME);
        history.setChangeReason(reason);
        history.setChangedAt(changedAt);
        return history;
    }
}
