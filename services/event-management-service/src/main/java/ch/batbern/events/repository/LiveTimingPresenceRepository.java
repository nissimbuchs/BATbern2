package ch.batbern.events.repository;

import ch.batbern.events.domain.LiveTimingPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Story 15.1: persistent organizer presence for live-timing polling.
 *
 * Replaces the per-task in-memory presence map. Upsert is a native
 * {@code INSERT ... ON CONFLICT} so concurrent polls on different Fargate tasks
 * converge on a single row per (event, organizer) without read-modify-write races.
 */
@Repository
public interface LiveTimingPresenceRepository
        extends JpaRepository<LiveTimingPresence, LiveTimingPresence.PresenceId> {

    /**
     * Upsert the organizer's last-seen timestamp for an event (heartbeat from the poll).
     *
     * @param eventCode  the event being polled
     * @param username   the authenticated organizer
     * @param lastSeenAt the poll timestamp
     */
    @Modifying
    @Query(value = "INSERT INTO live_timing_presence (event_code, username, last_seen_at) "
            + "VALUES (:eventCode, :username, :lastSeenAt) "
            + "ON CONFLICT (event_code, username) "
            + "DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at",
            nativeQuery = true)
    void upsertPresence(@Param("eventCode") String eventCode,
                        @Param("username") String username,
                        @Param("lastSeenAt") Instant lastSeenAt);

    /**
     * True when at least one organizer has been seen for the event at or after the cutoff
     * (i.e. within the presence TTL).
     *
     * @param eventCode the event
     * @param cutoff    now minus the TTL
     * @return whether any organizer is currently present
     */
    @Query("SELECT COUNT(p) > 0 FROM LiveTimingPresence p "
            + "WHERE p.eventCode = :eventCode AND p.lastSeenAt >= :cutoff")
    boolean existsActivePresence(@Param("eventCode") String eventCode,
                                 @Param("cutoff") Instant cutoff);
}
