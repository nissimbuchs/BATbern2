package ch.batbern.events.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Logo entity - Shared with company-user-management-service
 * References the same logos table in the database
 * Story 2.5.3a: Event Theme Image Upload
 *
 * This is a minimal representation for event-management-service to access
 * logos created by the Generic File Upload Service in company-user-management-service.
 */
@Entity
@Table(name = "logos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Logo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "upload_id", nullable = false, unique = true, length = 100)
    private String uploadId;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "cloudfront_url", length = 1000)
    private String cloudFrontUrl;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "checksum", length = 100)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LogoStatus status;

    @Column(name = "associated_entity_type", length = 50)
    private String associatedEntityType;

    @Column(name = "associated_entity_id", length = 255)
    private String associatedEntityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Business method: Associate logo with an entity
     * Transitions from CONFIRMED to ASSOCIATED
     */
    public void associateWith(String entityType, String entityId, String finalS3Key, String cdnUrl) {
        if (this.status != LogoStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Logo must be CONFIRMED before association. Current status: " + this.status);
        }

        this.status = LogoStatus.ASSOCIATED;
        this.associatedEntityType = entityType;
        this.associatedEntityId = entityId;
        this.s3Key = finalS3Key;
        this.cloudFrontUrl = cdnUrl;
        this.expiresAt = null;
        this.updatedAt = Instant.now();
    }
}
