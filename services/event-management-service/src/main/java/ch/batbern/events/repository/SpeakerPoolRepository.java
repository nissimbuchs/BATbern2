package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SpeakerPool entity (Story 5.2 AC9-13).
 *
 * Provides data access for speaker pool management during event brainstorming phase.
 */
@Repository
public interface SpeakerPoolRepository extends JpaRepository<SpeakerPool, UUID> {

    /**
     * Find all speaker pool entries for a specific event.
     *
     * @param eventId the event ID
     * @return list of speaker pool entries
     */
    List<SpeakerPool> findByEventId(UUID eventId);

    /**
     * Find speaker pool entries assigned to a specific organizer.
     *
     * @param assignedOrganizerId the organizer username
     * @return list of speaker pool entries
     */
    List<SpeakerPool> findByAssignedOrganizerId(String assignedOrganizerId);

    /**
     * Find all speaker pool entries for a specific event with a specific status,
     * sorted by creation date (oldest first).
     *
     * Story 5.5 AC11: Quality review queue retrieval
     *
     * @param eventId the event ID
     * @param status the speaker workflow status
     * @return list of speaker pool entries ordered by created_at ascending
     */
    List<SpeakerPool> findByEventIdAndStatusOrderByCreatedAtAsc(UUID eventId, SpeakerWorkflowState status);
}
