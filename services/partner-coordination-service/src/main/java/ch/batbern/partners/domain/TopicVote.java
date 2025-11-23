package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * TopicVote entity representing a partner's vote on a topic.
 *
 * Vote weight is calculated based on partnership tier:
 * - BRONZE = 1
 * - SILVER = 2
 * - GOLD = 3
 * - PLATINUM = 4
 * - STRATEGIC = 5
 *
 * Vote value must be between 1-5 (1=low interest, 5=high interest)
 *
 * Database: topic_votes table (via Flyway V2__create_partner_coordination_schema.sql)
 */
@Entity
@Table(name = "topic_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "partner_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicVote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Topic ID (references topic in Event Management Service).
     */
    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    /**
     * Partner ID (references partners table).
     */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /**
     * Vote weight based on partnership level (1-5).
     * Calculated from PartnershipLevel when casting vote.
     */
    @Column(name = "vote_weight", nullable = false)
    private Integer voteWeight;

    /**
     * Vote value indicating interest level (1-5).
     * 1 = Low interest, 5 = High interest
     */
    @Column(name = "vote_value", nullable = false)
    private Integer voteValue;

    /**
     * Timestamp when vote was cast.
     */
    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    /**
     * Validate that topic ID is provided.
     *
     * @throws IllegalArgumentException if topic ID is null
     */
    public void validateTopicId() {
        if (topicId == null) {
            throw new IllegalArgumentException("Topic ID is required");
        }
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
     * Validate vote value is within 1-5 range.
     *
     * @throws IllegalArgumentException if vote value is out of range
     */
    public void validateVoteValue() {
        if (voteValue == null || voteValue < 1 || voteValue > 5) {
            throw new IllegalArgumentException("Vote value must be between 1 and 5");
        }
    }

    /**
     * Validate vote weight is within 1-5 range.
     *
     * @throws IllegalArgumentException if vote weight is out of range
     */
    public void validateVoteWeight() {
        if (voteWeight == null) {
            throw new IllegalArgumentException("Vote weight is required");
        }
        if (voteWeight < 1 || voteWeight > 5) {
            throw new IllegalArgumentException("Vote weight must be between 1 and 5");
        }
    }

    /**
     * Pre-persist lifecycle hook.
     * Sets timestamps and validates business rules.
     */
    @PrePersist
    protected void onCreate() {
        if (votedAt == null) {
            votedAt = Instant.now();
        }
        validateEntity();
    }

    /**
     * Pre-update lifecycle hook.
     * Validates business rules.
     */
    @PreUpdate
    protected void onUpdate() {
        validateEntity();
    }

    /**
     * Validate all business rules.
     */
    private void validateEntity() {
        validateTopicId();
        validatePartnerId();
        validateVoteValue();
        validateVoteWeight();
    }
}
