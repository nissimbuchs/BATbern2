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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Session Timing History entity for audit trail of timing changes
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Tracks all changes to session timing during drag-and-drop slot assignment.
 * This is different from speaker assignment history (tracked in speaker_status_history).
 */
@Entity
@Table(name = "session_timing_history")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTimingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    // Previous timing values
    @Column(name = "previous_start_time")
    private Instant previousStartTime;

    @Column(name = "previous_end_time")
    private Instant previousEndTime;

    @Column(name = "previous_room", length = 100)
    private String previousRoom;

    // New timing values
    @Column(name = "new_start_time")
    private Instant newStartTime;

    @Column(name = "new_end_time")
    private Instant newEndTime;

    @Column(name = "new_room", length = 100)
    private String newRoom;

    // Change metadata
    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "change_reason", length = 50)
    // initial_assignment, drag_drop_reassignment, conflict_resolution,
    // preference_matching, manual_adjustment
    private String changeReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
