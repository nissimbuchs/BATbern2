package ch.batbern.events.dto;

import ch.batbern.events.domain.TopicUsageHistory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for topic usage history data (Story 5.2 - AC2).
 *
 * Used for heat map visualization and usage pattern analysis.
 */
public class TopicUsageHistoryResponse {

    private UUID eventId;
    private LocalDateTime usedDate;
    private Integer attendance;
    private Double engagementScore;

    public TopicUsageHistoryResponse() {
    }

    /**
     * Create TopicUsageHistoryResponse from TopicUsageHistory entity.
     */
    public static TopicUsageHistoryResponse from(TopicUsageHistory history) {
        TopicUsageHistoryResponse response = new TopicUsageHistoryResponse();
        response.setEventId(history.getEventId());
        response.setUsedDate(history.getUsedDate());
        response.setAttendance(history.getAttendeeCount());
        response.setEngagementScore(history.getEngagementScore());
        return response;
    }

    // ==================== Getters and Setters ====================

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

    public Integer getAttendance() {
        return attendance;
    }

    public void setAttendance(Integer attendance) {
        this.attendance = attendance;
    }

    public Double getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(Double engagementScore) {
        this.engagementScore = engagementScore;
    }
}
