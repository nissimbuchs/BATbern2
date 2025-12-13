package ch.batbern.events.dto;

import ch.batbern.events.domain.Topic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for topic data (Story 5.2).
 */
public class TopicResponse {

    private UUID id;
    private String title;
    private String description;
    private String category;
    private LocalDateTime createdDate;
    private LocalDateTime lastUsedDate;
    private Integer usageCount;
    private Integer stalenessScore;
    private String colorZone;
    private String status; // "available", "caution", "unavailable"
    private List<SimilarityScoreDto> similarityScores;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TopicResponse() {
    }

    /**
     * Create TopicResponse from Topic entity.
     */
    public static TopicResponse from(Topic topic) {
        TopicResponse response = new TopicResponse();
        response.setId(topic.getId());
        response.setTitle(topic.getTitle());
        response.setDescription(topic.getDescription());
        response.setCategory(topic.getCategory());
        response.setCreatedDate(topic.getCreatedDate());
        response.setLastUsedDate(topic.getLastUsedDate());
        response.setUsageCount(topic.getUsageCount());
        response.setStalenessScore(topic.getStalenessScore());
        response.setColorZone(getColorZoneForStaleness(topic.getStalenessScore()));
        response.setStatus(getStatusForStaleness(topic.getStalenessScore()));
        response.setSimilarityScores(
            topic.getSimilarityScores().stream()
                .map(SimilarityScoreDto::from)
                .collect(Collectors.toList())
        );
        response.setActive(topic.getActive());
        response.setCreatedAt(topic.getCreatedAt());
        response.setUpdatedAt(topic.getUpdatedAt());
        return response;
    }

    private static String getColorZoneForStaleness(Integer staleness) {
        if (staleness == null) {
            return "gray";
        }
        if (staleness < 50) {
            return "red";
        } else if (staleness <= 83) {
            return "yellow";
        } else {
            return "green";
        }
    }

    private static String getStatusForStaleness(Integer staleness) {
        if (staleness == null || staleness >= 83) {
            return "available"; // Green zone - safe to use
        } else if (staleness >= 50) {
            return "caution"; // Yellow zone - use with caution
        } else {
            return "unavailable"; // Red zone - too recent
        }
    }

    // Getters and Setters

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

    public String getColorZone() {
        return colorZone;
    }

    public void setColorZone(String colorZone) {
        this.colorZone = colorZone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SimilarityScoreDto> getSimilarityScores() {
        return similarityScores;
    }

    public void setSimilarityScores(List<SimilarityScoreDto> similarityScores) {
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Nested DTO for similarity scores.
     */
    public static class SimilarityScoreDto {
        private UUID topicId;
        private Double score;

        public SimilarityScoreDto() {
        }

        public SimilarityScoreDto(UUID topicId, Double score) {
            this.topicId = topicId;
            this.score = score;
        }

        public static SimilarityScoreDto from(Topic.SimilarityScore similarityScore) {
            return new SimilarityScoreDto(
                similarityScore.getTopicId(),
                similarityScore.getScore()
            );
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
