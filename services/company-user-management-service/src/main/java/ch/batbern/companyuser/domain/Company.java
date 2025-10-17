package ch.batbern.companyuser.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Company aggregate root
 * Represents a company entity with Swiss business validation
 * AC1: Company Entity with Swiss UID validation
 * AC2: Company Profiles with metadata support
 * AC3: Data Validation and business rules
 */
@Entity
@Table(name = "companies", indexes = {
        @Index(name = "idx_company_name", columnList = "name"),
        @Index(name = "idx_company_uid", columnList = "swiss_uid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "swiss_uid", length = 20)
    private String swissUID;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "logo_s3_key", length = 500)
    private String logoS3Key;

    @Column(name = "logo_file_id", length = 100)
    private String logoFileId;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

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
     * Business method: Mark company as verified
     * AC6: Company verification workflow
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.updatedAt = Instant.now();
    }
}
