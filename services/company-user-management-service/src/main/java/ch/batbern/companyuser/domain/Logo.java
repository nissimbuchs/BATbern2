package ch.batbern.companyuser.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Logo aggregate root for generic file upload service
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Implements state machine pattern:
 * PENDING (upload initiated) → CONFIRMED (file in S3) → ASSOCIATED (linked to entity)
 *
 * Key Features:
 * - Entity-agnostic: Works for companies, users, events, partners, etc.
 * - Lifecycle management: Automatic expiration for orphaned uploads
 * - S3 integration: Stores both temp and final locations
 * - Integrity verification: Checksum validation
 */
@Entity
@Table(name = "logos", indexes = {
        @Index(name = "idx_logos_upload_id", columnList = "upload_id"),
        @Index(name = "idx_logos_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_logos_entity", columnList = "associated_entity_type, associated_entity_id")
})
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

    /**
     * Public identifier used in API requests and responses
     * Example: "abc123-def456"
     */
    @Column(name = "upload_id", nullable = false, unique = true, length = 100)
    private String uploadId;

    /**
     * Current S3 object key
     * Temp: logos/temp/{uploadId}/logo-{fileId}.{ext}
     * Final: logos/{year}/{entity-type}/{entity-name}/logo-{fileId}.{ext}
     */
    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    /**
     * CloudFront CDN URL for file access
     * Example: https://cdn.batbern.ch/logos/2025/companies/Swisscom-AG/logo-f3e8d1a4.png
     */
    @Column(name = "cloudfront_url", length = 1000)
    private String cloudFrontUrl;

    /**
     * File extension (png, jpg, jpeg, svg)
     */
    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    /**
     * File size in bytes (max 5MB = 5,242,880 bytes)
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME type (image/png, image/jpeg, image/svg+xml)
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /**
     * SHA-256 checksum for integrity verification
     * Populated during confirmation phase
     */
    @Column(name = "checksum", length = 100)
    private String checksum;

    /**
     * Upload lifecycle state (PENDING, CONFIRMED, ASSOCIATED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LogoStatus status;

    /**
     * Type of entity this logo is associated with
     * Examples: COMPANY, USER, EVENT, PARTNER, SPEAKER
     * NULL until status = ASSOCIATED
     */
    @Column(name = "associated_entity_type", length = 50)
    private String associatedEntityType;

    /**
     * Identifier of the associated entity
     * Examples: company name, username, event ID
     * NULL until status = ASSOCIATED
     */
    @Column(name = "associated_entity_id", length = 255)
    private String associatedEntityId;

    /**
     * Timestamp when logo was created (upload initiated)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when logo was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Expiration timestamp for automatic cleanup
     * - PENDING: 24 hours after creation
     * - CONFIRMED: 7 days after confirmation
     * - ASSOCIATED: NULL (kept indefinitely)
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * JPA lifecycle callback - sets timestamps on entity creation
     */
    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * JPA lifecycle callback - updates timestamp on entity modification
     */
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Business method: Associate logo with an entity
     * Transitions from CONFIRMED to ASSOCIATED
     *
     * @param entityType Type of entity (COMPANY, USER, EVENT, etc.)
     * @param entityId   Identifier of the entity
     * @param finalS3Key Final S3 location
     * @param cdnUrl     CloudFront URL for access
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
        this.expiresAt = null; // No expiration for associated logos
        this.updatedAt = Instant.now();
    }

    /**
     * Business method: Mark upload as confirmed
     * Transitions from PENDING to CONFIRMED
     *
     * @param checksum SHA-256 checksum for integrity
     * @param expiresAt Expiration time (typically 7 days from now)
     */
    public void markAsConfirmed(String checksum, Instant expiresAt) {
        if (this.status != LogoStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING logos can be confirmed. Current status: " + this.status);
        }

        this.status = LogoStatus.CONFIRMED;
        this.checksum = checksum;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    /**
     * Business method: Check if logo is expired and should be cleaned up
     *
     * @return true if logo is expired (PENDING > 24h or CONFIRMED > 7 days)
     */
    public boolean isExpired() {
        if (this.status == LogoStatus.ASSOCIATED) {
            return false; // Associated logos never expire
        }

        if (this.expiresAt == null) {
            return false; // No expiration set
        }

        return Instant.now().isAfter(this.expiresAt);
    }
}
