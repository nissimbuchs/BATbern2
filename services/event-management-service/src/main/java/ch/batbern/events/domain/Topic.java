package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Topic entity for event topic management (Story 5.2).
 *
 * Manages topics with:
 * - Staleness scoring (0-100, where 100 = safe to reuse)
 * - Similarity detection (TF-IDF + cosine similarity)
 * - Usage history tracking
 * - Full-text search vectors (title_vector, description_vector)
 *
 * Database table: topics (created by migration V14)
 */
@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    /**
     * Staleness score (0-100):
     * - 100 = safe to reuse (>12 months since last use)
     * - 0 = too recent (just used)
     * - Formula: min(100, (monthsSinceLastUse / 12) * 100)
     */
    @Column(name = "staleness_score")
    private Integer stalenessScore = 100;

    @Column(name = "calculated_wait_period")
    private Integer calculatedWaitPeriod;

    @Column(name = "partner_influence_score")
    private Double partnerInfluenceScore;

    /**
     * Similarity scores stored as JSONB array:
     * Example: [{"topicId": "uuid", "score": 0.85}, ...]
     * Calculated using TF-IDF and cosine similarity.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "similarity_scores", columnDefinition = "jsonb")
    private List<SimilarityScore> similarityScores = new ArrayList<>();

    @Column(name = "is_active")
    private Boolean active = true;

    // Note: title_vector and description_vector are GENERATED columns in PostgreSQL
    // They are managed by the database automatically, so we don't map them here

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Getters and Setters ====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(LocalDateTime lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public Integer getStalenessScore() {
        return stalenessScore;
    }

    public void setStalenessScore(Integer stalenessScore) {
        this.stalenessScore = stalenessScore;
    }

    public Integer getCalculatedWaitPeriod() {
        return calculatedWaitPeriod;
    }

    public void setCalculatedWaitPeriod(Integer calculatedWaitPeriod) {
        this.calculatedWaitPeriod = calculatedWaitPeriod;
    }

    public Double getPartnerInfluenceScore() {
        return partnerInfluenceScore;
    }

    public void setPartnerInfluenceScore(Double partnerInfluenceScore) {
        this.partnerInfluenceScore = partnerInfluenceScore;
    }

    public List<SimilarityScore> getSimilarityScores() {
        return similarityScores;
    }

    public void setSimilarityScores(List<SimilarityScore> similarityScores) {
        this.similarityScores = similarityScores;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ==================== Nested Classes ====================

    /**
     * Similarity score entry for JSONB storage.
     * Represents similarity to another topic.
     */
    public static class SimilarityScore {
        private UUID topicId;
        private Double score;

        public SimilarityScore() {}

        public SimilarityScore(UUID topicId, Double score) {
            this.topicId = topicId;
            this.score = score;
        }

        public UUID getTopicId() {
            return topicId;
        }

        public void setTopicId(UUID topicId) {
            this.topicId = topicId;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }
    }
}
