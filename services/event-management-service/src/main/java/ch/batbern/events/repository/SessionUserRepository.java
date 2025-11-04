package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SessionUser entity (many-to-many session-speaker relationship).
 *
 * Story 4.1.4: Homepage Event Content Sections - supports querying speakers for sessions
 * ADR-004: References User entities via userId
 */
@Repository
public interface SessionUserRepository extends JpaRepository<SessionUser, UUID> {

    /**
     * Find all speakers for a specific session
     *
     * @param sessionId the session UUID
     * @return list of SessionUser associations for the session
     */
    List<SessionUser> findBySessionId(UUID sessionId);

    /**
     * Find all sessions for a specific user (speaker)
     *
     * @param userId the user UUID
     * @return list of SessionUser associations for the user
     */
    List<SessionUser> findByUserId(UUID userId);

    /**
     * Find all confirmed speakers for a specific session
     *
     * @param sessionId the session UUID
     * @return list of confirmed SessionUser associations
     */
    List<SessionUser> findBySessionIdAndIsConfirmedTrue(UUID sessionId);

    /**
     * Find a specific speaker assignment for a session
     *
     * @param sessionId the session UUID
     * @param userId the user UUID
     * @return optional SessionUser association
     */
    Optional<SessionUser> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    /**
     * Check if a user is assigned to a session
     *
     * @param sessionId the session UUID
     * @param userId the user UUID
     * @return true if user is assigned to session
     */
    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    /**
     * Find all session speakers for an event (for homepage display)
     *
     * @param eventId the event UUID
     * @return list of all SessionUser associations for the event
     */
    @Query("SELECT su FROM SessionUser su " +
           "JOIN su.session s " +
           "WHERE s.eventId = :eventId " +
           "ORDER BY s.startTime ASC, su.speakerRole ASC")
    List<SessionUser> findAllByEventId(@Param("eventId") UUID eventId);

    /**
     * Count speakers assigned to a session
     *
     * @param sessionId the session UUID
     * @return number of speakers assigned
     */
    long countBySessionId(UUID sessionId);

    /**
     * Delete all speaker assignments for a session
     *
     * @param sessionId the session UUID
     */
    void deleteBySessionId(UUID sessionId);

    /**
     * Delete a specific speaker assignment
     *
     * @param sessionId the session UUID
     * @param userId the user UUID
     */
    void deleteBySessionIdAndUserId(UUID sessionId, UUID userId);
}
