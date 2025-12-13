package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TopicUsageHistory entity for tracking topic usage in events (Story 5.2).
 *
 * Stores historical usage data including:
 * - Event where topic was used
 * - Date of usage
 * - Attendee count
 * - Engagement score
 *
 * Used for:
 * - Heat map visualization (AC2)
 * - Staleness calculation (AC1)
 * - Usage pattern analysis
 *
 * Database table: topic_usage_history (created by migration V14)
 */
@Entity
@Table(name = "topic_usage_history")
public class TopicUsageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "used_date", nullable = false)
    private LocalDateTime usedDate;

    @Column(name = "attendee_count")
    private Integer attendeeCount;

    @Column(name = "feedback_score")
    private Double feedbackScore;

    @Column(name = "engagement_score")
    private Double engagementScore;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
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

    public UUID getTopicId() {
        return topicId;
    }

    public void setTopicId(UUID topicId) {
        this.topicId = topicId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getUsedDate() {
        return usedDate;
    }

    public void setUsedDate(LocalDateTime usedDate) {
        this.usedDate = usedDate;
    }

    public Integer getAttendeeCount() {
        return attendeeCount;
    }

    public void setAttendeeCount(Integer attendeeCount) {
        this.attendeeCount = attendeeCount;
    }

    public Double getFeedbackScore() {
        return feedbackScore;
    }

    public void setFeedbackScore(Double feedbackScore) {
        this.feedbackScore = feedbackScore;
    }

    public Double getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(Double engagementScore) {
        this.engagementScore = engagementScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
