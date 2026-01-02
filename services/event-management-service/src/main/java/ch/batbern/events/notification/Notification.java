package ch.batbern.events.notification;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification entity
 * Story BAT-7: Notifications API Consolidation
 *
 * Follows Task System pattern (Story 5.5):
 * - Single table in Event Management Service
 * - ADR-003 compliant (meaningful IDs: recipient_username, event_code)
 * - Hybrid storage (email creates rows, in-app queries dynamic)
 */
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    // ADR-003: Meaningful IDs (NOT foreign keys)
    @Column(name = "recipient_username", nullable = false, length = 100)
    private String recipientUsername;

    @Column(name = "event_code", length = 50)
    private String eventCode;  // Nullable for non-event notifications

    // Notification details
    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;  // EMAIL, SMS

    @Column(name = "priority", length = 20)
    private String priority;  // LOW, NORMAL, HIGH, URGENT

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    // Delivery tracking
    @Column(name = "status", nullable = false, length = 20)
    private String status;  // PENDING, SENT, FAILED, READ

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // Metadata (flexible JSONB storage)
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
