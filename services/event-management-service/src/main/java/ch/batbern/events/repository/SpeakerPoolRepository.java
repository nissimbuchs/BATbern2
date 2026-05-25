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
    // Story 11.E.9: findByEventIdAndEmail, existsByEventIdAndEmail, and findByUsername
    // were removed when the speaker_pool.username + speaker_pool.email columns were
    // dropped (V103). The remaining lookup-by-username flow traverses
    // session_users → session → speaker_pool.

    /**
     * Find the speaker pool entry whose primary speaker has the given username.
     *
     * <p>Story 11.E.9: the post-Phase-A canonical "this username's pool row" lives on
     * the {@link ch.batbern.events.domain.SessionUser} row joined to the session via
     * {@code speaker_pool_id}. Pre-READY pool rows (IDENTIFIED/CONTACTED) have no
     * session_users row yet and therefore do not match — callers handle that case (see
     * {@code SpeakerInvitationService.sendInvitation}'s ID-fallback).
     *
     * @param eventId the event ID
     * @param username the speaker username (matches {@code session_users.username})
     * @return optional speaker pool entry
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT sp FROM SpeakerPool sp, Session s, SessionUser su
            WHERE sp.eventId = :eventId
              AND s.id = sp.sessionId
              AND su.session = s
              AND su.speakerRole = ch.batbern.events.domain.SessionUser$SpeakerRole.PRIMARY_SPEAKER
              AND su.username = :username
            """)
    java.util.Optional<SpeakerPool> findByEventIdAndUsername(
            @org.springframework.data.repository.query.Param("eventId") UUID eventId,
            @org.springframework.data.repository.query.Param("username") String username);

    // E2E Test Support Methods (Story 6.3)

    /**
     * Find speaker by event code and username.
     * Used for E2E test token generation.
     *
     * @param eventCode the event code
     * @param username the speaker username
     * @return optional speaker pool entry
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT sp FROM SpeakerPool sp, Event e, Session s, SessionUser su
            WHERE e.eventCode = :eventCode
              AND sp.eventId = e.id
              AND s.id = sp.sessionId
              AND su.session = s
              AND su.speakerRole = ch.batbern.events.domain.SessionUser$SpeakerRole.PRIMARY_SPEAKER
              AND su.username = :username
            """)
    java.util.Optional<SpeakerPool> findByEventCodeAndUsername(
            @org.springframework.data.repository.query.Param("eventCode") String eventCode,
            @org.springframework.data.repository.query.Param("username") String username);

    // Story 11.F.1 (2026-05-25): 5 orphan finder methods that previously powered
    // E2ETestTokenController were removed alongside the controller and its dedicated
    // SpeakerPoolRepositoryE2EMethodsTest. The methods were:
    //   - findByEventCodeAndStatusOrderByCreatedAtDesc
    //   - findByEventCodeAndStatusAndSessionIdIsNotNullOrderByCreatedAtDesc
    //   - findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc
    //   - findByEventCodeOrderByCreatedAtDesc
    //   - findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc
}
