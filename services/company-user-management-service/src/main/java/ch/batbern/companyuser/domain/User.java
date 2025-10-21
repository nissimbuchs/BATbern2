package ch.batbern.companyuser.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User aggregate root
 * Story 1.14-2: User Management Service Foundation
 * Story 1.16.2: Dual-identifier pattern - UUID (internal PK) + username (public API ID)
 */
@Entity
@Table(name = "user_profiles", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_company", columnList = "company_id"),
        @Index(name = "idx_users_cognito_user_id", columnList = "cognito_user_id", unique = true),
        @Index(name = "idx_users_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Internal UUID primary key (NOT exposed in API)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Story 1.16.2: Public meaningful identifier (exposed in API as "id")
     * Format: lowercase.letters.optional.numeric.suffix (e.g., "john.doe" or "john.doe.1")
     */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /**
     * AWS Cognito user identifier for authentication
     */
    @Column(name = "cognito_user_id", nullable = false, unique = true, length = 255)
    private String cognitoUserId;

    /**
     * User email address (unique, used for login)
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * User first name
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * User last name
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * User bio/description
     */
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * Story 1.16.2: Company reference uses company name (not UUID)
     * Format: alphanumeric, max 12 chars (e.g., "GoogleZH", "MicrosoftBE")
     */
    @Column(name = "company_id", length = 12)
    private String companyId;

    /**
     * CloudFront CDN URL for profile picture
     */
    @Column(name = "profile_picture_url", length = 2048)
    private String profilePictureUrl;

    /**
     * S3 storage key for profile picture
     */
    @Column(name = "profile_picture_s3_key", length = 500)
    private String profilePictureS3Key;

    /**
     * User roles (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)
     * Stored in separate role_assignments table
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_assignments", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Embedded user preferences (theme, language, notifications)
     */
    @Embedded
    private UserPreferences preferences;

    /**
     * Embedded user settings (privacy, visibility, timezone)
     */
    @Embedded
    private UserSettings settings;

    /**
     * Whether user account is active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Timestamp when user was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when user was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Timestamp of last login
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * JPA lifecycle callback - set timestamps and initialize defaults on creation
     */
    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();

        // Initialize preferences and settings with defaults if null
        if (preferences == null) {
            preferences = UserPreferences.builder().build();
        }
        if (settings == null) {
            settings = UserSettings.builder().build();
        }
    }

    /**
     * JPA lifecycle callback - update timestamp on modification
     */
    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // Business methods

    /**
     * Add a role to the user
     *
     * @param role the role to add
     */
    public void addRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
        this.updatedAt = Instant.now();
    }

    /**
     * Remove a role from the user
     *
     * @param role the role to remove
     */
    public void removeRole(Role role) {
        if (this.roles != null) {
            this.roles.remove(role);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Check if user has a specific role
     *
     * @param role the role to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(Role role) {
        return this.roles != null && this.roles.contains(role);
    }

    /**
     * Record a user login
     * Updates lastLoginAt and updatedAt timestamps
     */
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
