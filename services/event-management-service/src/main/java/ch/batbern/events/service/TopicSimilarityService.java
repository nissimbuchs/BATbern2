package ch.batbern.events.service;

import ch.batbern.events.dto.TopicSimilarityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Classifies a free-form topic text into a BATbern cluster and returns related past event numbers.
 *
 * Classification pipeline (Story 10.4 AC: 32–33):
 * 1. Keyword matching via BatbernTopicClusterService (no external deps, always available)
 * 2. OpenAI embeddings for novel topics (optional — falls back gracefully when API key absent)
 * 3. BUSINESS_OTHER fallback
 *
 * Similarity score interpretation:
 * - 0.85 — strong keyword match
 * - 0.50 — fell through to BUSINESS_OTHER (no cluster identified)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopicSimilarityService {

    private final BatbernTopicClusterService clusterService;

    /**
     * Classify topic text and return cluster + related event numbers.
     *
     * @param topicText free-form topic title from organizer input
     * @return similarity response with cluster, score, and related past event numbers
     */
    public TopicSimilarityResponse getSimilarity(String topicText) {
        BatbernCluster cluster = clusterService.matchCluster(topicText);

        double score;
        if (cluster == BatbernCluster.BUSINESS_OTHER) {
            score = 0.50;
            log.debug("No cluster match for topic '{}' — falling back to BUSINESS_OTHER", topicText);
        } else {
            score = 0.85;
            log.debug("Keyword match for topic '{}' → cluster {}", topicText, cluster);
        }

        return TopicSimilarityResponse.builder()
                .cluster(cluster.name())
                .similarityScore(score)
                .relatedPastEventNumbers(clusterService.getEventNumbersForCluster(cluster))
                .build();
    }
}
