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

    /**
     * Slug-format identifier for external API (ADR-003).
     * Generated from title. Example: "cloud-native-security-2024"
     */
    @Column(name = "topic_code", nullable = false, unique = true, length = 255)
    private String topicCode;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

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
        // Auto-generate topicCode from title if not set (ADR-003)
        if (topicCode == null && title != null) {
            topicCode = generateTopicCode(title);
        }
    }

    /**
     * Generate slug-format topicCode from title (ADR-003).
     * Example: "Cloud Native Security" → "cloud-native-security"
     */
    public static String generateTopicCode(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")  // Remove special chars
            .replaceAll("\\s+", "-")          // Replace spaces with hyphens
            .replaceAll("-+", "-")            // Collapse multiple hyphens
            .replaceAll("^-|-$", "");         // Trim leading/trailing hyphens
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

    public String getTopicCode() {
        return topicCode;
    }

    public void setTopicCode(String topicCode) {
        this.topicCode = topicCode;
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
