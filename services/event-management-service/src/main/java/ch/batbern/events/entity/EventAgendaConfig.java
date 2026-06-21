package ch.batbern.events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Per-event copy-on-edit override of the {@link EventTypeConfiguration} template (Story 15.2).
 *
 * <p>Maps to the {@code event_agenda_config} table (V121). Exactly one row per event, created
 * only when an organizer first edits the event type in slot assignment. Mirrors every knob of
 * the shared template plus the apéro knobs. Events with no row resolve to the shared template
 * via {@link ch.batbern.events.service.AgendaConfigResolver}.</p>
 */
@Entity
@Table(name = "event_agenda_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAgendaConfig implements AgendaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Within-service UUID FK to events(id); one row per event (unique). */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "min_slots", nullable = false)
    @Min(1)
    private Integer minSlots;

    @Column(name = "max_slots", nullable = false)
    @Min(1)
    private Integer maxSlots;

    @Column(name = "slot_duration", nullable = false)
    @Min(15)
    private Integer slotDuration;

    @Column(name = "theoretical_slots_am", nullable = false)
    @Builder.Default
    private Boolean theoreticalSlotsAM = true;

    @Column(name = "break_slots", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer breakSlots = 0;

    @Column(name = "lunch_slots", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer lunchSlots = 0;

    @Column(name = "default_capacity", nullable = false)
    @Min(1)
    private Integer defaultCapacity;

    @Column(name = "moderation_start_duration", nullable = false)
    @Min(1)
    @Builder.Default
    private Integer moderationStartDuration = 5;

    @Column(name = "moderation_end_duration", nullable = false)
    @Min(1)
    @Builder.Default
    private Integer moderationEndDuration = 5;

    @Column(name = "break_duration", nullable = false)
    @Min(1)
    @Builder.Default
    private Integer breakDuration = 20;

    @Column(name = "lunch_duration", nullable = false)
    @Min(1)
    @Builder.Default
    private Integer lunchDuration = 60;

    @Column(name = "aperitif_slots", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer aperitifSlots = 0;

    @Column(name = "aperitif_duration", nullable = false)
    @Min(1)
    @Builder.Default
    private Integer aperitifDuration = 90;

    @Column(name = "aperitif_position", nullable = false, length = 10)
    @Builder.Default
    private String aperitifPosition = "end";

    @Column(name = "typical_start_time")
    private LocalTime typicalStartTime;

    @Column(name = "typical_end_time")
    private LocalTime typicalEndTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Validates that maxSlots >= minSlots (business rule, mirrors EventTypeConfiguration).
     */
    @PrePersist
    @PreUpdate
    private void validateSlotConfiguration() {
        if (maxSlots != null && minSlots != null && maxSlots < minSlots) {
            throw new IllegalStateException(
                    String.format("maxSlots (%d) must be >= minSlots (%d) for event %s",
                            maxSlots, minSlots, eventId));
        }
    }
}
