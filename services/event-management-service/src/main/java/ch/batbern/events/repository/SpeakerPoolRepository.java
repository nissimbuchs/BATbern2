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
     * Count speaker pool entries for a specific event whose status is in the given set.
     *
     * Story 11.B.3: Used by EventWorkflowStateMachine.validateAllSpeakersConfirmed to
     * compute the "accepted or beyond" cohort (ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED)
     * for the AGENDA_PUBLISHED gate. Backed by JPQL (not derived-name) to avoid Spring
     * Data's awkward IN-collection method names.
     *
     * @param eventId the event ID
     * @param statuses the list of speaker workflow statuses to include
     * @return count of speakers whose status is in the given set
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(sp)
            FROM SpeakerPool sp
            WHERE sp.eventId = :eventId
              AND sp.status IN :statuses
            """)
    long countByEventIdAndStatusIn(
            @org.springframework.data.repository.query.Param("eventId") UUID eventId,
            @org.springframework.data.repository.query.Param("statuses") List<SpeakerWorkflowState> statuses);

    /**
     * Count speakers who are "publishable" per ADR-009 §0.1:
     * {@code speaker_pool.status == 'quality_reviewed'} AND the assigned session has a
     * non-null {@code start_time}.
     *
     * Story 11.B.3: Used by {@code EventWorkflowStateMachine.validateAllSpeakersConfirmed}
     * to gate the AGENDA_PUBLISHED transition. The derived {@code is_publishable} predicate
     * lives in the read layer (not stored on speaker_pool) — this query implements the
     * predicate at the database level so it can be aggregated cheaply.
     *
     * @param eventId the event ID
     * @return count of publishable speakers for the event
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(sp)
            FROM SpeakerPool sp
            JOIN Session s ON sp.sessionId = s.id
            WHERE sp.eventId = :eventId
              AND sp.status = ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED
              AND s.startTime IS NOT NULL
            """)
    long countPublishableByEventId(@org.springframework.data.repository.query.Param("eventId") UUID eventId);

    /**
     * Find speakers assigned to a specific session.
     *
     * Story 5.7 (BAT-11): Speaker auto-confirmation when session timing assigned
     * Note: Sessions can have multiple speakers (e.g., panel discussions, co-presenters)
     *
     * @param sessionId the session ID
     * @return list of speaker pool entries assigned to this session
     */
    List<SpeakerPool> findBySessionId(UUID sessionId);

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

    // Story 6.4: Speaker Dashboard - find all events for a speaker by username

    /**
     * Find all speaker pool entries for a given username across all events.
     * Used by the speaker dashboard to show upcoming and past events.
     *
     * @param username the speaker's username
     * @return list of speaker pool entries for this speaker
     */
    List<SpeakerPool> findByUsername(String username);

    // E2E Test Support Methods (Story 6.3)

    /**
     * Find speaker by event code and username.
     * Used for E2E test token generation.
     *
     * @param eventCode the event code
     * @param username the speaker username
     * @return optional speaker pool entry
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode AND s.username = :username")
    java.util.Optional<SpeakerPool> findByEventCodeAndUsername(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            @org.springframework.data.repository.query.Param("username") String username);

    /**
     * Find speakers by event code and status.
     * Used for E2E test token generation with Pageable for limit.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode AND CAST(s.status AS string) = :status "
                    + "ORDER BY s.createdAt DESC")
    List<SpeakerPool> findByEventCodeAndStatusOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find speakers by event code and status with session assigned.
     * Used for E2E test token generation with Pageable for limit.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode AND CAST(s.status AS string) = :status "
                    + "AND s.sessionId IS NOT NULL ORDER BY s.createdAt DESC")
    List<SpeakerPool> findByEventCodeAndStatusAndSessionIdIsNotNullOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find speakers by event code and status without session assigned.
     * Used for E2E test token generation with Pageable for limit.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode AND CAST(s.status AS string) = :status "
                    + "AND s.sessionId IS NULL ORDER BY s.createdAt DESC")
    List<SpeakerPool> findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find speakers by event code.
     * Used for E2E test token generation fallback with Pageable for limit.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode ORDER BY s.createdAt DESC")
    List<SpeakerPool> findByEventCodeOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find speakers by event code with session assigned (any status).
     * Used for E2E test token generation - finds speakers who can submit content.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SpeakerPool s JOIN Event e ON s.eventId = e.id "
                    + "WHERE e.eventCode = :eventCode AND s.sessionId IS NOT NULL "
                    + "ORDER BY s.createdAt DESC")
    List<SpeakerPool> findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            org.springframework.data.domain.Pageable pageable);
}
