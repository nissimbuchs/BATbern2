package ch.batbern.events.client;

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
     * Partner topic group — one entry per company with their suggested topic titles.
     *
     * @param companyName the partner company name
     * @param logoUrl     company logo URL (may be null if unavailable)
     * @param topics      list of topic titles suggested by this company
     */
    record PartnerTopicGroup(String companyName, String logoUrl, List<String> topics) {}
}
