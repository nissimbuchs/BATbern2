package ch.batbern.events.domain;

import jakarta.persistence.Column;
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
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Speaker Slot Preference entity
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Stores speaker preferences for session timing:
 * - Preferred time of day (morning/afternoon/evening/any)
 * - Times to avoid
 * - A/V requirements
 * - Room setup notes
 */
@Entity
@Table(name = "speaker_slot_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerSlotPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "speaker_id", nullable = false, columnDefinition = "UUID")
    private UUID speakerId;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "preferred_time_of_day", length = 50)
    private String preferredTimeOfDay; // morning, afternoon, evening, any

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoid_times", columnDefinition = "jsonb")
    @Builder.Default
    private Object avoidTimes = "[]"; // JSON array of time ranges

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "av_requirements", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> avRequirements = Map.of(); // JSON object {projector: true, microphone: true, etc}

    @Column(name = "room_setup_notes", columnDefinition = "TEXT")
    private String roomSetupNotes;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
