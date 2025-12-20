package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository stub for TDD RED phase
 * Story 5.4: Speaker Status Management
 *
 * This is a minimal stub to allow tests to compile.
 * Will be fully implemented in Task 4 (GREEN phase) with custom JPQL queries.
 */
@Repository
public interface SpeakerStatusHistoryRepository extends JpaRepository<SpeakerStatusHistory, UUID> {

    /**
     * Find status history for a speaker, ordered by changed_at descending
     * Story 5.4 AC15
     */
    List<SpeakerStatusHistory> findBySpeakerPoolIdOrderByChangedAtDesc(UUID speakerPoolId);

    /**
     * Find status history by event code and new status
     * Story 5.4 AC15
     */
    List<SpeakerStatusHistory> findByEventCodeAndNewStatus(String eventCode, SpeakerWorkflowState newStatus);

    /**
     * Find all status history for a specific event
     * Story 5.4 - Performance fix (PERF-001): Replace findAll() with event-specific query
     */
    List<SpeakerStatusHistory> findByEventCode(String eventCode);
}
