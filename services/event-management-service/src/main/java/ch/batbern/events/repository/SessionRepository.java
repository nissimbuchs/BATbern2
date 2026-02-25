package ch.batbern.events.repository;

import ch.batbern.events.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Session entities
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Story 1.16.2: Uses sessionSlug as public identifier
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

    /**
     * Find a session by its slug (public identifier)
     * Story 1.16.2: Public API uses sessionSlug instead of UUID
     */
    Optional<Session> findBySessionSlug(String sessionSlug);

    /**
     * Check if a session slug already exists
     * Story 1.16.2: For collision detection during slug generation
     */
    boolean existsBySessionSlug(String sessionSlug);

    /**
     * Check if a session slug already exists within a specific event
     * Story 5.5: For collision detection during content submission (allows duplicate slugs across events)
     */
    boolean existsByEventIdAndSessionSlug(UUID eventId, String sessionSlug);

    /**
     * Find all sessions for a specific event
     */
    List<Session> findByEventId(UUID eventId);

    /**
     * Find all sessions for a specific event with session users eagerly loaded
     * Story 5.5: Fix lazy loading issue when expanding sessions in API responses
     */
    @Query("SELECT DISTINCT s FROM Session s LEFT JOIN FETCH s.sessionUsers "
           + "WHERE s.eventId = :eventId ORDER BY s.startTime, s.sessionSlug")
    List<Session> findByEventIdWithSpeakers(UUID eventId);

    /**
     * Batch-load sessions with their session users for multiple events in one query.
     * Used by EventController.buildBatchExpandedResponses() to avoid N+1 session queries.
     */
    @Query("SELECT DISTINCT s FROM Session s LEFT JOIN FETCH s.sessionUsers "
           + "WHERE s.eventId IN :eventIds ORDER BY s.startTime, s.sessionSlug")
    List<Session> findByEventIdInWithSpeakers(@Param("eventIds") java.util.Collection<UUID> eventIds);

    /**
     * Find all sessions for a specific event and session type
     */
    List<Session> findByEventIdAndSessionType(UUID eventId, String sessionType);

    /**
     * Find all sessions for a specific event with any of the given session types.
     * Used for structural session detection (moderation, break, lunch).
     */
    List<Session> findByEventIdAndSessionTypeIn(UUID eventId, List<String> sessionTypes);

    /**
     * Delete all sessions for a specific event with any of the given session types.
     * Used when overwrite=true for structural session generation.
     */
    void deleteByEventIdAndSessionTypeIn(UUID eventId, List<String> sessionTypes);

    /**
     * Delete all sessions for a specific event
     */
    void deleteByEventId(UUID eventId);

    /**
     * Find a session by event ID and title (for duplicate detection during batch import)
     */
    Optional<Session> findByEventIdAndTitle(UUID eventId, String title);

    /**
     * Find unassigned sessions (placeholder sessions without timing)
     * Story BAT-11 (5.7): Slot Assignment - AC5, AC12
     */
    List<Session> findByEventIdAndStartTimeIsNull(UUID eventId);

    /**
     * Find sessions with timing assigned for an event
     * Story BAT-11 (5.7): Slot Assignment - conflict detection
     */
    List<Session> findByEventIdAndStartTimeIsNotNull(UUID eventId);

    /**
     * Find sessions in a specific room during a time range (for conflict detection)
     * Story BAT-11 (5.7): Slot Assignment - AC9
     */
    @Query("SELECT s FROM Session s WHERE s.eventId = :eventId "
           + "AND s.room = :room "
           + "AND s.startTime IS NOT NULL "
           + "AND s.endTime IS NOT NULL "
           + "AND s.startTime < :endTime "
           + "AND s.endTime > :startTime")
    List<Session> findOverlappingSessionsInRoom(
            @Param("eventId") UUID eventId,
            @Param("room") String room,
            @Param("startTime") java.time.Instant startTime,
            @Param("endTime") java.time.Instant endTime
    );

    /**
     * Find unassigned sessions by event code (joins with events table)
     * Story BAT-11 (5.7): Slot Assignment - for unit test compatibility
     */
    @Query("SELECT s FROM Session s JOIN ch.batbern.events.domain.Event e ON s.eventId = e.id "
           + "WHERE e.eventCode = :eventCode AND s.startTime IS NULL")
    List<Session> findByEventCodeAndStartTimeIsNull(@Param("eventCode") String eventCode);

    /**
     * Find all sessions by event code.
     * Story BAT-11 (5.7): Slot Assignment - for conflict detection.
     * Review fix item 5: aligned to use s.eventCode directly (same strategy as findByEventCodeAndSessionSlug)
     * instead of a JOIN through Event, since Session.eventCode is a persistent denormalized column.
     */
    @Query("SELECT s FROM Session s WHERE s.eventCode = :eventCode")
    List<Session> findByEventCode(@Param("eventCode") String eventCode);

    /**
     * Find a session by event code and session slug.
     * W4.2 Task 9.1: Used by WatchSessionService.endSession() to look up the session
     * being advanced by an organizer action without joining via eventId.
     */
    @Query("SELECT s FROM Session s WHERE s.eventCode = :eventCode AND s.sessionSlug = :sessionSlug")
    Optional<Session> findByEventCodeAndSessionSlug(
            @Param("eventCode") String eventCode,
            @Param("sessionSlug") String sessionSlug
    );

    /**
     * Find sessions where at least one speaker belongs to the given company.
     * Joins session_users with user_profiles (shared table) to filter by company_id.
     * Used by GlobalSessionController for company detail page Sessions tab.
     *
     * @param companyName Company name (ADR-003 meaningful ID = company_id in user_profiles)
     * @param pageable Pagination + sort
     * @return Page of sessions
     */
    @Query(value = """
        SELECT s.* FROM sessions s
        WHERE s.id IN (
            SELECT DISTINCT su.session_id FROM session_users su
            JOIN user_profiles up ON up.username = su.username
            WHERE up.company_id = :companyName
        )
        ORDER BY s.start_time DESC NULLS LAST
        """,
        countQuery = """
        SELECT COUNT(*) FROM sessions s
        WHERE s.id IN (
            SELECT DISTINCT su.session_id FROM session_users su
            JOIN user_profiles up ON up.username = su.username
            WHERE up.company_id = :companyName
        )
        """,
        nativeQuery = true)
    Page<Session> findSessionsByCompanyName(
            @Param("companyName") String companyName,
            Pageable pageable);

    /**
     * Count all sessions for a specific event
     * Story BAT-11 (5.7): Workflow validation for agenda publishing
     */
    long countByEventId(UUID eventId);

    /**
     * Count distinct speaker usernames across all sessions for a given event.
     * W2.4: Used by WatchSpeakerArrivalService to compute totalCount for STOMP broadcasts.
     * Single query replaces N+1 pattern of fetching sessions then streaming sessionUsers.
     */
    @Query("SELECT COUNT(DISTINCT su.username) FROM Session s "
           + "JOIN ch.batbern.events.domain.Event e ON s.eventId = e.id "
           + "JOIN s.sessionUsers su "
           + "WHERE e.eventCode = :eventCode AND su.username IS NOT NULL")
    long countDistinctSpeakersByEventCode(@Param("eventCode") String eventCode);

    /**
     * Count sessions with timing assigned for an event
     * Story BAT-11 (5.7): Workflow validation for agenda publishing
     */
    long countByEventIdAndStartTimeNotNull(UUID eventId);

    /**
     * Find sessions starting after a given time for downstream cascade (extend/delay).
     * W4.3 Task 10.1 (AC2): Used by extendSession to shift downstream sessions.
     */
    @Query("SELECT s FROM Session s WHERE s.eventCode = :eventCode "
           + "AND s.startTime > :after ORDER BY s.startTime")
    List<Session> findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(
            @Param("eventCode") String eventCode,
            @Param("after") java.time.Instant after);

    /**
     * Find the previous session (the one scheduled immediately before a given time).
     * W4.3 Task 10.1 (AC4): Used by delayToPreviousSession to find the session to re-activate.
     */
    @Query("SELECT s FROM Session s WHERE s.eventCode = :eventCode "
           + "AND s.startTime < :before ORDER BY s.startTime DESC LIMIT 1")
    Optional<Session> findFirstByEventCodeAndScheduledStartTimeBeforeOrderByScheduledStartTimeDesc(
            @Param("eventCode") String eventCode,
            @Param("before") java.time.Instant before);

    /**
     * Find sessions starting at or after a given time (for shifting current + downstream).
     * W4.3 Task 10.1 (AC4): Used by delayToPreviousSession to cascade time shifts.
     */
    @Query("SELECT s FROM Session s WHERE s.eventCode = :eventCode "
           + "AND s.startTime >= :startTime ORDER BY s.startTime")
    List<Session> findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(
            @Param("eventCode") String eventCode,
            @Param("startTime") java.time.Instant startTime);
}
