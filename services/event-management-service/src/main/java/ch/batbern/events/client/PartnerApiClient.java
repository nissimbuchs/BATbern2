package ch.batbern.events.client;

import java.time.Instant;
import java.util.List;

/**
 * Client interface for calling partner-coordination-service to retrieve partner topic suggestions.
 * Used by TopicSessionDataService for the blob topic selector canvas (Story 10.4).
 */
public interface PartnerApiClient {

    /**
     * Retrieve all partner topic suggestions grouped by company.
     *
     * @return list of PartnerTopicGroup, one entry per partner company
     */
    List<PartnerTopicGroup> getPartnerTopics();

    /**
     * Partner topic group — one entry per company with their suggested topics (rich objects).
     *
     * @param companyName the partner company name
     * @param logoUrl     company logo URL (may be null if unavailable)
     * @param topics      list of topic items (title, voteCount, createdAt) suggested by this company
     */
    record PartnerTopicGroup(String companyName, String logoUrl, List<PartnerTopicItem> topics) {}

    /**
     * A single partner topic suggestion with vote and recency data for attraction-strength calculation.
     *
     * @param title     topic title (free-form text, may be German or English)
     * @param voteCount number of partner votes cast on this topic
     * @param createdAt when the topic was first submitted (used as recency proxy)
     */
    record PartnerTopicItem(String title, int voteCount, Instant createdAt) {}
}
