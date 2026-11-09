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
 *
 * Attendance is not stored here: it is the event's registrations (see
 * TopicUsageHistoryRepository). Engagement was never measured and was removed (owner, 2026-11-09).
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

    @Column(name = "feedback_score")
    private Double feedbackScore;

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

    public Double getFeedbackScore() {
        return feedbackScore;
    }

    public void setFeedbackScore(Double feedbackScore) {
        this.feedbackScore = feedbackScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
