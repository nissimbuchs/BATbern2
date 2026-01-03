package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishing Configuration Domain Entity
 * Per-event configuration for auto-publish scheduling.
 *
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3b: GREEN Phase - Publishing Engine Implementation
 *
 * Supports:
 * - Auto-publish scheduling (1 month for speakers, 2 weeks for agenda)
 * - Approval workflow configuration
 * - Preview URL management
 */
@Entity
@Table(name = "publishing_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @NotNull(message = "Event ID is required")
    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "auto_publish_speakers")
    @Builder.Default
    private Boolean autoPublishSpeakers = true;

    @Column(name = "auto_publish_speakers_days_before")
    @Builder.Default
    private Integer autoPublishSpeakersDaysBefore = 30;

    @Column(name = "auto_publish_agenda")
    @Builder.Default
    private Boolean autoPublishAgenda = true;

    @Column(name = "auto_publish_agenda_days_before")
    @Builder.Default
    private Integer autoPublishAgendaDaysBefore = 14;

    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = false;

    @Column(name = "preview_url", length = 1024)
    private String previewUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
