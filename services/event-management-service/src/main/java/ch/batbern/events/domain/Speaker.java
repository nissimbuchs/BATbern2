package ch.batbern.events.domain;

import ch.batbern.events.converter.SpeakerAvailabilityConverter;
import ch.batbern.events.converter.SpeakerWorkflowStateConverter;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global Speaker Profile entity - Story 6.0.
 *
 * Represents a speaker profile independent of specific events.
 * Follows ADR-003/ADR-004 compliance:
 * - References User via username (meaningful ID, NOT userId UUID)
 * - NO foreign key constraint (cross-service reference)
 * - User data enriched via HTTP call to User Management Service
 *
 * Fields stored here are domain-specific speaker attributes only.
 * User data (email, name, bio, photo, company) comes from User Service.
 *
 * @see ch.batbern.events.domain.SpeakerPool for event-specific speaker tracking
 */
@Entity
@Table(name = "speakers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Speaker {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Username as meaningful identifier (ADR-003).
     * References User in Company User Management Service.
     * NO foreign key constraint (cross-service per ADR-004).
     * User data enriched via HTTP at runtime.
     */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /**
     * Speaker availability status.
     * Stored as lowercase in database (e.g., 'available').
     */
    @Column(name = "availability", nullable = false, length = 50)
    @Convert(converter = SpeakerAvailabilityConverter.class)
    @Builder.Default
    private SpeakerAvailability availability = SpeakerAvailability.AVAILABLE;

    /**
     * Speaker workflow state.
     * Uses shared-kernel SpeakerWorkflowState enum.
     * Stored as lowercase in database (e.g., 'identified').
     */
    @Column(name = "workflow_state", nullable = false, length = 50)
    @Convert(converter = SpeakerWorkflowStateConverter.class)
    @Builder.Default
    private SpeakerWorkflowState workflowState = SpeakerWorkflowState.IDENTIFIED;

    /**
     * Areas of technical expertise (PostgreSQL TEXT[] array).
     * Examples: ["Security", "Cloud Architecture", "DevOps"]
     * Uses Hibernate 6 native array support.
     */
    @Column(name = "expertise_areas", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> expertiseAreas = new ArrayList<>();

    /**
     * Topics the speaker can present on (PostgreSQL TEXT[] array).
     * Examples: ["AWS", "Kubernetes", "Zero Trust"]
     */
    @Column(name = "speaking_topics", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> speakingTopics = new ArrayList<>();

    /**
     * LinkedIn profile URL.
     */
    @Column(name = "linkedin_url", length = 500)
    private String linkedInUrl;

    /**
     * Twitter/X handle.
     */
    @Column(name = "twitter_handle", length = 100)
    private String twitterHandle;

    /**
     * Professional certifications (PostgreSQL TEXT[] array).
     * Examples: ["AWS Solutions Architect", "CISSP"]
     */
    @Column(name = "certifications", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> certifications = new ArrayList<>();

    /**
     * Languages speaker can present in (PostgreSQL VARCHAR(10)[] array).
     * ISO 639-1 codes. Default: German and English.
     */
    @Column(name = "languages", columnDefinition = "varchar(10)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> languages = new ArrayList<>(List.of("de", "en"));

    /**
     * Speaking history as JSONB.
     * Contains past session participation records.
     */
    @Column(name = "speaking_history", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private String speakingHistory = "[]";

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Record last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Soft delete timestamp.
     * When set, the speaker is considered deleted.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if speaker is soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
