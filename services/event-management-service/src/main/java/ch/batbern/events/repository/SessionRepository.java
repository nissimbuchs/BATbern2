package ch.batbern.events.repository;

import ch.batbern.events.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Find all sessions for a specific event and session type
     */
    List<Session> findByEventIdAndSessionType(UUID eventId, String sessionType);

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
     * Find all sessions by event code (joins with events table)
     * Story BAT-11 (5.7): Slot Assignment - for conflict detection
     */
    @Query("SELECT s FROM Session s JOIN ch.batbern.events.domain.Event e ON s.eventId = e.id "
           + "WHERE e.eventCode = :eventCode")
    List<Session> findByEventCode(@Param("eventCode") String eventCode);

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
}
