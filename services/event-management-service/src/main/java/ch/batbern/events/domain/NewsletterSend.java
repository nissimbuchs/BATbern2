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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record for a newsletter send operation (Story 10.7).
 *
 * <p>One row per send operation (per event × per locale × per send/reminder action).
 * Individual recipient delivery records are stored in {@code newsletter_recipients}.
 *
 * <p>Database table: newsletter_sends (created by migration V67)
 */
@Entity
@Table(name = "newsletter_sends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSend {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    @Column(name = "is_reminder", nullable = false)
    private boolean reminder;

    @Column(name = "locale", nullable = false, length = 5)
    @Builder.Default
    private String locale = "de";

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "sent_by_username", nullable = false, length = 100)
    private String sentByUsername;

    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        if (sentAt == null) {
            sentAt = now;
        }
    }
}
