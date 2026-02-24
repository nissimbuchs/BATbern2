package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for GET /api/v1/events/{eventCode}/topic-session-data (Story 10.4 AC: 29–31).
 *
 * Contains all data needed to bootstrap the blob topic selector canvas in one request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSessionDataResponse {

    /**
     * Partner topics grouped by company.
     * Used to create green blobs (partner interests) on the canvas.
     */
    private List<PartnerTopicGroup> partnerTopics;

    /**
     * Past BATbern events with cluster classification.
     * Used to create red star blobs (past event history) on the canvas.
     */
    private List<PastEventEntry> pastEvents;

    /**
     * Available topics from the organizer's backlog (existing topics table, status=AVAILABLE).
     * Used to create white ghost blobs on the canvas.
     */
    private List<String> organizerBacklog;

    /**
     * AI-generated trending IT topics.
     * Used to create gold shimmer ghost blobs on the canvas.
     */
    private List<String> trendingTopics;

    // ==================== Nested types ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnerTopicGroup {
        private String companyName;
        /** Company logo URL; may be null when not available. */
        private String logoUrl;
        private List<String> topics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PastEventEntry {
        private int eventNumber;
        private String topicName;
        private String cluster;
    }
}
