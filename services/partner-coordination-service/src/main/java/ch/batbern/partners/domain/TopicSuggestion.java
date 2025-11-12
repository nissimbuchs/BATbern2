package ch.batbern.partners.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * TopicSuggestion entity representing a topic idea suggested by a partner.
 *
 * Partners can submit topic suggestions for future BAT events.
 * Suggestions go through a review workflow (SUBMITTED → UNDER_REVIEW → ACCEPTED/REJECTED → IMPLEMENTED).
 *
 * Database: topic_suggestions table (via Flyway V2__create_partner_coordination_schema.sql)
 */
@Entity
@Table(name = "topic_suggestions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Partner who submitted the suggestion (FK to partners table).
     */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /**
     * Suggested topic title (max 500 characters).
     */
    @Column(name = "suggested_topic", nullable = false, length = 500)
    private String suggestedTopic;

    /**
     * Detailed description of the suggested topic (max 2000 characters).
     */
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    /**
     * Business justification for the topic (max 1000 characters, optional).
     */
    @Column(name = "business_justification", length = 1000)
    private String businessJustification;

    /**
     * Current status of the suggestion in the review workflow.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SuggestionStatus status;

    /**
     * Timestamp when the suggestion was submitted.
     */
    @Column(name = "suggested_at", nullable = false)
    private Instant suggestedAt;

    /**
     * Timestamp when the suggestion was reviewed (nullable until reviewed).
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * UUID of the organizer who reviewed the suggestion (nullable until reviewed).
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Automatically set timestamps before persisting.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (suggestedAt == null) {
            suggestedAt = Instant.now();
        }
        if (status == null) {
            status = SuggestionStatus.SUBMITTED;
        }
    }

    /**
     * Automatically update timestamp before updating.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Validate that partner ID is provided.
     *
     * @throws IllegalArgumentException if partner ID is null
     */
    public void validatePartnerId() {
        if (partnerId == null) {
            throw new IllegalArgumentException("Partner ID is required");
        }
    }

    /**
     * Validate suggested topic field.
     *
     * @throws IllegalArgumentException if topic is null or exceeds max length
     */
    public void validateSuggestedTopic() {
        if (suggestedTopic == null || suggestedTopic.isBlank()) {
            throw new IllegalArgumentException("Suggested topic is required");
        }
        if (suggestedTopic.length() > 500) {
            throw new IllegalArgumentException("Suggested topic must not exceed 500 characters");
        }
    }

    /**
     * Validate description field.
     *
     * @throws IllegalArgumentException if description is null or exceeds max length
     */
    public void validateDescription() {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (description.length() > 2000) {
            throw new IllegalArgumentException("Description must not exceed 2000 characters");
        }
    }

    /**
     * Validate business justification field (optional, but if provided must not exceed max length).
     *
     * @throws IllegalArgumentException if justification exceeds max length
     */
    public void validateBusinessJustification() {
        if (businessJustification != null && businessJustification.length() > 1000) {
            throw new IllegalArgumentException("Business justification must not exceed 1000 characters");
        }
    }

    /**
     * Validate that status is provided.
     *
     * @throws IllegalArgumentException if status is null
     */
    public void validateStatus() {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
    }

    /**
     * Validate all fields of the topic suggestion.
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public void validate() {
        validatePartnerId();
        validateSuggestedTopic();
        validateDescription();
        validateBusinessJustification();
        validateStatus();
    }
}
