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

    /**
     * Count speaker pool entries for a specific event with a specific status.
     *
     * Story 5.7 (BAT-11): Threshold validation for slot assignment
     *
     * @param eventId the event ID
     * @param status the speaker workflow status
     * @return count of speakers with the given status
     */
    long countByEventIdAndStatus(UUID eventId, SpeakerWorkflowState status);

    /**
     * Find speaker assigned to a specific session.
     *
     * Story 5.7 (BAT-11): Speaker auto-confirmation when session timing assigned
     *
     * @param sessionId the session ID
     * @return optional speaker pool entry
     */
    java.util.Optional<SpeakerPool> findBySessionId(UUID sessionId);

    // Story 6.1b: Speaker Invitation System

    /**
     * Find speaker pool entry by event and email.
     * Used for idempotency check when inviting speakers.
     *
     * @param eventId the event ID
     * @param email the speaker email
     * @return optional speaker pool entry
     */
    java.util.Optional<SpeakerPool> findByEventIdAndEmail(UUID eventId, String email);

    /**
     * Find speaker pool entry by event and username.
     * Used for sending invitations to existing speakers.
     *
     * @param eventId the event ID
     * @param username the speaker username
     * @return optional speaker pool entry
     */
    java.util.Optional<SpeakerPool> findByEventIdAndUsername(UUID eventId, String username);

    /**
     * Check if a speaker already exists in the pool for this event with the given email.
     *
     * @param eventId the event ID
     * @param email the speaker email
     * @return true if speaker exists
     */
    boolean existsByEventIdAndEmail(UUID eventId, String email);
}
