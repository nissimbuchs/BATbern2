package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Story 15.1: persistent organizer presence for live-timing polling.
 *
 * Replaces the per-task in-memory {@code WatchPresenceService} map so the
 * "organizer present" flag survives Fargate task restarts/scale and is identical
 * across tasks (AC3). One row per (event, organizer); the authenticated organizer's
 * {@code GET /live-timing} poll upserts {@code lastSeenAt}. Presence is "active" when
 * {@code lastSeenAt} is within the TTL (~30 s), evaluated in the repository query.
 */
@Entity
@Table(name = "live_timing_presence")
@IdClass(LiveTimingPresence.PresenceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LiveTimingPresence {

    @Id
    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Id
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Composite primary key for (event_code, username). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresenceId implements Serializable {
        private String eventCode;
        private String username;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PresenceId that = (PresenceId) o;
            return Objects.equals(eventCode, that.eventCode)
                    && Objects.equals(username, that.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventCode, username);
        }
    }
}
