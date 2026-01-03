package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishing Version Domain Entity
 * Tracks version history for published content with rollback capability.
 *
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3b: GREEN Phase - Publishing Engine Implementation
 *
 * Supports:
 * - Version tracking for all publishing operations
 * - Content snapshots for rollback
 * - CDN invalidation status tracking
 * - Rollback history audit trail
 */
@Entity
@Table(name = "publishing_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishingVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @NotNull(message = "Event ID is required")
    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @NotNull(message = "Version number is required")
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @NotBlank(message = "Published phase is required")
    @Column(name = "published_phase", nullable = false, length = 50)
    private String publishedPhase; // topic, speakers, agenda

    @NotNull(message = "Published at timestamp is required")
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @NotBlank(message = "Published by username is required")
    @Column(name = "published_by", nullable = false, length = 255)
    private String publishedBy;

    @Column(name = "cdn_invalidation_id", length = 255)
    private String cdnInvalidationId;

    @Column(name = "cdn_invalidation_status", length = 50)
    private String cdnInvalidationStatus; // pending, in_progress, completed, failed

    @NotNull(message = "Content snapshot is required")
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String contentSnapshot;

    @Column(name = "is_current")
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Column(name = "rolled_back_by", length = 255)
    private String rolledBackBy;

    @Column(name = "rollback_reason", columnDefinition = "TEXT")
    private String rollbackReason;

    @PrePersist
    protected void onCreate() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
        if (cdnInvalidationStatus == null) {
            cdnInvalidationStatus = "pending";
        }
    }
}
