package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * OrganizerThanks entity — a single "thank the organizers" submission (Story 7.4).
 *
 * <p>Two shapes share this table:
 * <ul>
 *   <li>Logged-in: {@code thankedByUsername} set; deduped to one row per (event, username)
 *       by the partial unique index {@code ux_organizer_thanks_user}. A repeat updates the
 *       note rather than inserting a second row (AC4).</li>
 *   <li>Anonymous: {@code thankedByUsername} is {@code null} — a clap-style row, not
 *       user-deduped (AC5), guarded by Turnstile + per-(event,IP) rate limit at the service.</li>
 * </ul>
 *
 * <p>The aggregate count is public; {@code note} is organizer-visible only (AC6).
 * {@code eventId} is an in-service UUID FK (ADR-003 allows UUID FKs within one service).
 */
@Entity
@Table(name = "organizer_thanks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerThanks {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Meaningful ID (ADR-003) of the logged-in attendee; {@code null} for anonymous claps. */
    @Column(name = "thanked_by_username", length = 100)
    private String thankedByUsername;

    /** Optional short free-text, organizer-visible only (AC6). */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
