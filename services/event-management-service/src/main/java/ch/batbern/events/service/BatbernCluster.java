package ch.batbern.events.service;

/**
 * Cluster taxonomy for BATbern past event topics (Story 10.4).
 * Used by BatbernTopicClusterService to group events by thematic domain,
 * driving the red-star repulsion logic in the blob topic selector.
 */
public enum BatbernCluster {
    AI_ML,
    SECURITY,
    ARCHITECTURE,
    DATA,
    CLOUD_INFRA,
    MOBILE,
    BUSINESS_OTHER
}
