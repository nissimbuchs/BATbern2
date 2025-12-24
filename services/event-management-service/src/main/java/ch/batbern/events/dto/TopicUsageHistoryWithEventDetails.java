package ch.batbern.events.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO projection for topic usage history with event details.
 * Used for efficient single-query fetching of usage history with event information.
 * GitHub Issue #379: Optimized data fetching for heatmap visualization.
 * Returns eventNumber instead of UUID per architectural requirement.
 */
public class TopicUsageHistoryWithEventDetails {

    private UUID topicId;
    private Integer eventNumber; // Event number (e.g., 56 for BATbern56) - no UUIDs in API
    private String eventCode;
    private Instant eventDate;
    private LocalDateTime usedDate;
    private Integer attendeeCount;
    private Double engagementScore;

    public TopicUsageHistoryWithEventDetails() {
    }

    public TopicUsageHistoryWithEventDetails(
            UUID topicId,
            Integer eventNumber,
            String eventCode,
            Instant eventDate,
            LocalDateTime usedDate,
            Integer attendeeCount,
            Double engagementScore) {
        this.topicId = topicId;
        this.eventNumber = eventNumber;
        this.eventCode = eventCode;
        this.eventDate = eventDate;
        this.usedDate = usedDate;
        this.attendeeCount = attendeeCount;
        this.engagementScore = engagementScore;
    }

    // ==================== Getters and Setters ====================

    public UUID getTopicId() {
        return topicId;
    }

    public void setTopicId(UUID topicId) {
        this.topicId = topicId;
    }

    public Integer getEventNumber() {
        return eventNumber;
    }

    public void setEventNumber(Integer eventNumber) {
        this.eventNumber = eventNumber;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public void setEventDate(Instant eventDate) {
        this.eventDate = eventDate;
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

    public Double getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(Double engagementScore) {
        this.engagementScore = engagementScore;
    }

}
