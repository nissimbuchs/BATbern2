package ch.batbern.events.dto;

import ch.batbern.events.domain.TopicUsageHistory;

import java.time.LocalDateTime;

/**
 * Response DTO for topic usage history data (Story 5.2 - AC2).
 *
 * Used for heat map visualization and usage pattern analysis.
 * GitHub Issue #379: Returns eventNumber and eventCode instead of UUID.
 */
public class TopicUsageHistoryResponse {

    private Integer eventNumber; // Event number (e.g., 56 for BATbern56) - no UUIDs in API
    private String eventCode; // Event code like "BATbern56" (added for Issue #379)
    private java.time.Instant eventDate; // Actual event date (added for Issue #379)
    private LocalDateTime usedDate;
    private Integer attendance;
    private Double engagementScore;

    public TopicUsageHistoryResponse() {
    }

    /**
     * Create TopicUsageHistoryResponse from TopicUsageHistory entity.
     * @deprecated Use fromWithEventDetails instead to include event number
     */
    @Deprecated
    public static TopicUsageHistoryResponse from(TopicUsageHistory history) {
        TopicUsageHistoryResponse response = new TopicUsageHistoryResponse();
        response.setUsedDate(history.getUsedDate());
        response.setAttendance(history.getAttendeeCount());
        response.setEngagementScore(history.getEngagementScore());
        return response;
    }

    /**
     * Create TopicUsageHistoryResponse with event details.
     * @param history The usage history entity
     * @param eventNumber The event number (e.g., 56 for BATbern56)
     * @param eventCode The event code (e.g., "BATbern56")
     * @param eventDate The actual event date
     */
    public static TopicUsageHistoryResponse fromWithEventDetails(
            TopicUsageHistory history,
            Integer eventNumber,
            String eventCode,
            java.time.Instant eventDate) {
        TopicUsageHistoryResponse response = from(history);
        response.setEventNumber(eventNumber);
        response.setEventCode(eventCode);
        response.setEventDate(eventDate);
        return response;
    }

    // ==================== Getters and Setters ====================

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

    public java.time.Instant getEventDate() {
        return eventDate;
    }

    public void setEventDate(java.time.Instant eventDate) {
        this.eventDate = eventDate;
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
