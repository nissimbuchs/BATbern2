package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for POST /api/v1/events/{eventCode}/topic-similarity (Story 10.4 AC: 32).
 *
 * Used by the frontend blob canvas to:
 * - Place green blobs near the most similar blue blob (via cluster)
 * - Ignite red star blobs for past events in the same cluster (via relatedPastEventNumbers)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSimilarityResponse {

    /** BATbern cluster name (e.g. "AI_ML", "SECURITY", "BUSINESS_OTHER") */
    private String cluster;

    /** Confidence 0.0–1.0 that the topic belongs to this cluster */
    private double similarityScore;

    /** Sorted list of BATbern event numbers in the same cluster (drives red-star ignition) */
    private List<Integer> relatedPastEventNumbers;
}
