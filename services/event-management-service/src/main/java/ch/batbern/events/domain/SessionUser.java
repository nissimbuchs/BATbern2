package ch.batbern.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * SessionUser entity representing the many-to-many relationship between sessions and users (speakers).
 *
 * Architecture: ADR-004 pattern - references User entity via userId FK
 * Story 4.1.4: Homepage Event Content Sections - supports multiple speakers per session
 *
 * Design Notes:
 * - References User (not Speaker) to reduce cross-service dependencies
 * - User entity contains all profile data (name, company, photo) per ADR-004
 * - Speaker-specific workflow data can be added later in speaker-coordination-service
 * - Supports multiple speaker roles: primary_speaker, co_speaker, moderator, panelist
 */
@Entity
@Table(name = "session_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"session"})  // Prevent circular reference in bidirectional relationship
public class SessionUser {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @JsonIgnore
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private Session session;

    /**
     * Foreign key to user_profiles.id in company-user-management-service
     * ADR-004: Domain entities reference User directly via userId
     * NOT a @ManyToOne because User is in a different database/service
     *
     * NOTE: This field is kept for backward compatibility during migration.
     * New code should use username field for API-based user data access.
     */
    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    /**
     * Username - public identifier for API-based user data access
     * Used to fetch user profile data from User Management Service API.
     * Replaces direct database dependency on user_profiles table.
     */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * Speaker role in the session:
     * - primary_speaker: Main presenter
     * - co_speaker: Co-presenter
     * - moderator: Panel moderator
     * - panelist: Panel participant
     */
    @Column(name = "speaker_role", nullable = false, length = 50)
    @Convert(converter = SpeakerRoleConverter.class)
    private SpeakerRole speakerRole;

    /**
     * Optional speaker-specific presentation title
     * Used when speaker's title differs from the session title
     */
    @Column(name = "presentation_title", length = 255)
    private String presentationTitle;

    /**
     * Confirmation status for speaker workflow
     */
    @Column(name = "is_confirmed")
    @Builder.Default
    private boolean isConfirmed = false;

    /**
     * When the speaker was invited
     */
    @Column(name = "invited_at")
    private Instant invitedAt;

    /**
     * When the speaker confirmed participation
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /**
     * When the speaker declined participation
     */
    @Column(name = "declined_at")
    private Instant declinedAt;

    /**
     * Reason for declining (if applicable)
     */
    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (invitedAt == null) {
            invitedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Helper method to confirm speaker participation
     */
    public void confirm() {
        this.isConfirmed = true;
        this.confirmedAt = Instant.now();
        this.declinedAt = null;
        this.declineReason = null;
    }

    /**
     * Helper method to decline speaker participation
     */
    public void decline(String reason) {
        this.isConfirmed = false;
        this.declinedAt = Instant.now();
        this.declineReason = reason;
        this.confirmedAt = null;
    }

    /**
     * Speaker role enumeration
     */
    public enum SpeakerRole {
        PRIMARY_SPEAKER,  // Main presenter
        CO_SPEAKER,       // Co-presenter
        MODERATOR,        // Panel moderator
        PANELIST         // Panel participant
    }

    /**
     * JPA Converter to transform enum values to lowercase snake_case for database storage
     * Database constraint expects: 'primary_speaker', 'co_speaker', 'moderator', 'panelist'
     */
    @Converter
    public static class SpeakerRoleConverter implements AttributeConverter<SpeakerRole, String> {
        @Override
        public String convertToDatabaseColumn(SpeakerRole attribute) {
            if (attribute == null) {
                return null;
            }
            return attribute.name().toLowerCase();
        }

        @Override
        public SpeakerRole convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return SpeakerRole.valueOf(dbData.toUpperCase());
        }
    }
}
