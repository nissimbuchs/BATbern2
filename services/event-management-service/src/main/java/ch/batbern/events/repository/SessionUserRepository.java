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
 * ADR-003: Uses username as primary identifier for user references
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
     * Find all confirmed speakers for a specific session
     *
     * @param sessionId the session UUID
     * @return list of confirmed SessionUser associations
     */
    List<SessionUser> findBySessionIdAndIsConfirmedTrue(UUID sessionId);

    /**
     * Find the (at most one) session_user with a given role for a session.
     *
     * <p>Phase A of the post-Epic-11 speaker_pool.username/email cleanup: the speaker
     * pool list endpoint loads the PRIMARY_SPEAKER for each pool row that has a session,
     * and uses it as the canonical identity source. There is never more than one
     * PRIMARY_SPEAKER per session in BATbern's data model, so {@code Optional} fits
     * better than {@code List}.
     *
     * @param sessionId the session UUID
     * @param speakerRole the speaker role to filter by (typically PRIMARY_SPEAKER)
     * @return optional SessionUser association
     */
    Optional<SessionUser> findBySessionIdAndSpeakerRole(
            UUID sessionId, SessionUser.SpeakerRole speakerRole);

    /**
     * Batch variant of {@link #findBySessionIdAndSpeakerRole(UUID, SessionUser.SpeakerRole)}
     * for loading primary speakers across many sessions in one query.
     *
     * @param sessionIds the session UUIDs
     * @param speakerRole the speaker role to filter by
     * @return list of SessionUser associations matching the filter
     */
    List<SessionUser> findBySessionIdInAndSpeakerRole(
            java.util.Collection<UUID> sessionIds, SessionUser.SpeakerRole speakerRole);

    /**
     * Find all session speakers for an event (for homepage display)
     *
     * @param eventId the event UUID
     * @return list of all SessionUser associations for the event
     */
    @Query("SELECT su FROM SessionUser su "
        + "JOIN su.session s "
        + "WHERE s.eventId = :eventId "
        + "ORDER BY s.startTime ASC, su.speakerRole ASC")
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

    // ========================================================================
    // Username-based methods (ADR-003: Meaningful Identifiers)
    // ========================================================================

    /**
     * Find a specific speaker assignment for a session by username
     * ADR-003: Use username instead of userId for API operations
     *
     * @param sessionId the session UUID
     * @param username the user's username (meaningful identifier)
     * @return optional SessionUser association
     */
    Optional<SessionUser> findBySessionIdAndUsername(UUID sessionId, String username);

    /**
     * Check if a user is assigned to a session by username
     * ADR-003: Use username instead of userId for API operations
     *
     * @param sessionId the session UUID
     * @param username the user's username (meaningful identifier)
     * @return true if user is assigned to session
     */
    boolean existsBySessionIdAndUsername(UUID sessionId, String username);

    /**
     * Delete a specific speaker assignment by username
     * ADR-003: Use username instead of userId for API operations
     *
     * @param sessionId the session UUID
     * @param username the user's username (meaningful identifier)
     */
    void deleteBySessionIdAndUsername(UUID sessionId, String username);

    /**
     * Find all sessions for a specific user by username
     * ADR-003: Use username instead of userId for API operations
     *
     * @param username the user's username (meaningful identifier)
     * @return list of SessionUser associations for the user
     */
    List<SessionUser> findByUsername(String username);

    /**
     * Batch-load portrait URLs and company names for a set of speaker usernames.
     *
     * INTENTIONAL ARCHITECTURE BREAK: reads from user_profiles (owned by
     * company-user-management-service). Both services share the same PostgreSQL
     * database in this monorepo, making this one DB query far more efficient than
     * issuing one HTTP call per speaker to the user management service.
     *
     * @param usernames set of speaker usernames from session_users
     * @return username → { profilePictureUrl, companyId } projection
     */
    @Query(value = "SELECT up.username AS username, "
            + "up.profile_picture_url AS profilePictureUrl, "
            + "up.company_id AS companyId, "
            + "COALESCE(c.display_name, c.name, up.company_id) AS companyDisplayName, "
            + "(SELECT l.cloudfront_url FROM logos l "
            + " WHERE l.associated_entity_id = c.id::text "
            + "   AND l.associated_entity_type = 'COMPANY' "
            + " LIMIT 1) AS companyLogoUrl "
            + "FROM user_profiles up "
            + "LEFT JOIN companies c ON c.name = up.company_id "
            + "WHERE up.username IN :usernames",
           nativeQuery = true)
    List<UserPortraitProjection> findUserPortraitsByUsernames(
            @Param("usernames") java.util.Collection<String> usernames);
}
