package ch.batbern.companyuser.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity - READ-ONLY view for event-management-service
 * ADR-004: Services share database and reference user_profiles table directly
 *
 * This is a READ-ONLY entity used for enriching speaker data.
 * All write operations go through company-user-management-service.
 *
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Internal UUID primary key
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Public meaningful identifier (exposed in API as "id")
     * Format: lowercase.letters.optional.numeric.suffix (e.g., "john.doe" or "john.doe.1")
     */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /**
     * User's email address
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * First name
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * Last name
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Company identifier (e.g., "GoogleZH", "MicrosoftBE")
     */
    @Column(name = "company_id", length = 100)
    private String companyId;

    /**
     * Profile picture URL (S3 location)
     */
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    /**
     * User bio/description
     */
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * Account active status
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * AWS Cognito user identifier
     */
    @Column(name = "cognito_user_id", unique = true, length = 255)
    private String cognitoUserId;

    /**
     * Timestamp when record was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when record was last updated
     */
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
