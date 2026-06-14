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
     * Find all presenting speakers — PRIMARY_SPEAKER <strong>and</strong> CO_SPEAKER — on
     * <strong>scheduled</strong> sessions of an event (i.e. sessions where
     * {@code start_time IS NOT NULL}). MODERATOR and PANELIST roles are intentionally excluded.
     *
     * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
     * (F2) — backs the {@code batbern{N}-speaker@} distribution-list resolution: only speakers
     * whose sessions have been timetabled (a real talk slot, not just a placeholder pool row)
     * should appear on the alias.
     *
     * <p>Co-speakers were previously dropped (PRIMARY_SPEAKER-only), so a mail to the speaker
     * alias never reached them — see event-59 incident (a co-speaker reported a missed mail).
     *
     * @param eventId event UUID
     * @return list of SessionUser rows; empty when no scheduled speakers exist yet
     */
    @Query("SELECT su FROM SessionUser su "
        + "JOIN su.session s "
        + "WHERE s.eventId = :eventId "
        + "AND s.startTime IS NOT NULL "
        + "AND su.speakerRole IN ("
        + "  ch.batbern.events.domain.SessionUser.SpeakerRole.PRIMARY_SPEAKER, "
        + "  ch.batbern.events.domain.SessionUser.SpeakerRole.CO_SPEAKER) "
        + "ORDER BY s.startTime ASC, su.speakerRole ASC")
    List<SessionUser> findScheduledSpeakersByEventId(@Param("eventId") UUID eventId);

    /**
     * Find PRIMARY_SPEAKER + CO_SPEAKER session_users for the given event.
     *
     * <p>Spec F3: backs the XLSX name-badge export's "event speakers" source set. Includes
     * sessions whether or not they have been scheduled — every committed speaker (primary or
     * co) shows up on the badge list.
     *
     * @param eventId event UUID
     * @return list of SessionUser rows for both PRIMARY_SPEAKER and CO_SPEAKER roles
     */
    @Query("SELECT su FROM SessionUser su "
        + "JOIN su.session s "
        + "WHERE s.eventId = :eventId "
        + "AND su.speakerRole IN ("
        + "  ch.batbern.events.domain.SessionUser.SpeakerRole.PRIMARY_SPEAKER, "
        + "  ch.batbern.events.domain.SessionUser.SpeakerRole.CO_SPEAKER) "
        + "ORDER BY su.speakerRole ASC")
    List<SessionUser> findEventSpeakersByEventId(@Param("eventId") UUID eventId);

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
            + "c.logo_url AS companyLogoUrl "
            + "FROM user_profiles up "
            + "LEFT JOIN companies c ON c.name = up.company_id "
            + "WHERE up.username IN :usernames",
           nativeQuery = true)
    List<UserPortraitProjection> findUserPortraitsByUsernames(
            @Param("usernames") java.util.Collection<String> usernames);

    /**
     * Batch-load Q&A author display data (first/last name + company logo) for a set of usernames.
     * Same intentional, read-only cross-service join as {@link #findUserPortraitsByUsernames} —
     * used to enrich the PUBLIC Q&A thread without a JWT (see {@link QnaAuthorProjection}).
     *
     * @param usernames distinct poster usernames from session_qna_post
     * @return username → { firstName, lastName, showCompany, companyDisplayName, companyLogoUrl }
     */
    @Query(value = "SELECT up.username AS username, "
            + "up.first_name AS firstName, "
            + "up.last_name AS lastName, "
            + "up.settings_show_company AS showCompany, "
            + "COALESCE(c.display_name, c.name, up.company_id) AS companyDisplayName, "
            + "c.logo_url AS companyLogoUrl "
            + "FROM user_profiles up "
            + "LEFT JOIN companies c ON c.name = up.company_id "
            + "WHERE up.username IN :usernames",
           nativeQuery = true)
    List<QnaAuthorProjection> findQnaAuthorPortraitsByUsernames(
            @Param("usernames") java.util.Collection<String> usernames);
}
