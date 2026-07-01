package ch.batbern.events.controller;

import ch.batbern.events.api.generated.topics.TopicSimilarityApi;
import ch.batbern.events.dto.generated.topics.TopicSimilarityRequest;
import ch.batbern.events.dto.generated.topics.TopicSimilarityResponse;
import ch.batbern.events.service.TopicSimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides topic similarity classification for the blob topic selector canvas (Story 10.4 AC: 32).
 *
 * Called by the frontend when a new blue blob is summoned (organizer types a topic name).
 * Returns cluster + similarity score + related past event numbers to drive canvas behaviour:
 * - Green blobs gravitate toward the most similar blue blob (via cluster/score)
 * - Red star blobs ignite when relatedPastEventNumbers matches their event number
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TopicSimilarityController implements TopicSimilarityApi {

    private final TopicSimilarityService topicSimilarityService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicSimilarityResponse> getTopicSimilarity(
            String eventCode,
            TopicSimilarityRequest topicSimilarityRequest) {
        TopicSimilarityResponse response =
                topicSimilarityService.getSimilarity(topicSimilarityRequest.getTopic());
        return ResponseEntity.ok(response);
    }
}
