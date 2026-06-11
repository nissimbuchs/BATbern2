package ch.batbern.events.domain;

import ch.batbern.events.converter.QnaWindowStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-session, time-boxed Q&A window (Story 7.5).
 *
 * <p>Opened (one per session) when an event reaches {@code EVENT_COMPLETED}, closes
 * {@code closesAt} (default +14d, organizer-extendable), then freezes to read-only. The
 * {@code sessionId} is an in-service UUID FK (ADR-003); {@code eventCode} is the denormalized
 * meaningful event id for archive routing.
 */
@Entity
@Table(name = "session_qna_window")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionQnaWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Convert(converter = QnaWindowStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private QnaWindowStatus status;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (opensAt == null) {
            opensAt = now;
        }
        if (status == null) {
            status = QnaWindowStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
