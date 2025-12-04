package ch.batbern.events.entity;

import ch.batbern.events.dto.generated.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Event Type Configuration entity (Story 5.1).
 *
 * Maps to event_types table created in V10__Create_event_types_table.sql.
 * Stores slot requirements and scheduling parameters for each event type template.
 *
 * Source of Truth: docs/architecture/03-data-architecture.md, Section "EventType"
 */
@Entity
@Table(name = "event_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventTypeConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Event type enum: FULL_DAY, AFTERNOON, EVENING.
     * Stored as VARCHAR(20) in database with CHECK constraint.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private EventType type;

    /**
     * Minimum number of session slots for this event type.
     */
    @Column(name = "min_slots", nullable = false)
    @Min(1)
    private Integer minSlots;

    /**
     * Maximum number of session slots for this event type.
     * Must be >= minSlots (enforced in service layer).
     */
    @Column(name = "max_slots", nullable = false)
    @Min(1)
    private Integer maxSlots;

    /**
     * Duration of each slot in minutes.
     * Minimum 15 minutes.
     */
    @Column(name = "slot_duration", nullable = false)
    @Min(15)
    private Integer slotDuration;

    /**
     * Whether theoretical presentations are scheduled in morning slots.
     */
    @Column(name = "theoretical_slots_am", nullable = false)
    private Boolean theoreticalSlotsAM = true;

    /**
     * Number of break slots included in event.
     */
    @Column(name = "break_slots", nullable = false)
    @Min(0)
    private Integer breakSlots = 0;

    /**
     * Number of lunch slots included in event.
     */
    @Column(name = "lunch_slots", nullable = false)
    @Min(0)
    private Integer lunchSlots = 0;

    /**
     * Default attendee capacity for this event type.
     */
    @Column(name = "default_capacity", nullable = false)
    @Min(1)
    private Integer defaultCapacity;

    /**
     * Typical start time for this event type (e.g., 09:00 for FULL_DAY).
     */
    @Column(name = "typical_start_time")
    private LocalTime typicalStartTime;

    /**
     * Typical end time for this event type (e.g., 17:00 for FULL_DAY).
     */
    @Column(name = "typical_end_time")
    private LocalTime typicalEndTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Validates that maxSlots >= minSlots (business rule).
     * Called before persist/update.
     */
    @PrePersist
    @PreUpdate
    private void validateSlotConfiguration() {
        if (maxSlots != null && minSlots != null && maxSlots < minSlots) {
            throw new IllegalStateException(
                    String.format("maxSlots (%d) must be >= minSlots (%d) for event type %s",
                            maxSlots, minSlots, type));
        }
    }
}
