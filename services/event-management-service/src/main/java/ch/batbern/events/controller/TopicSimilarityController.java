package ch.batbern.events.controller;

import ch.batbern.events.dto.TopicSimilarityRequest;
import ch.batbern.events.dto.TopicSimilarityResponse;
import ch.batbern.events.service.TopicSimilarityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/api/v1/events/{eventCode}")
@RequiredArgsConstructor
public class TopicSimilarityController {

    private final TopicSimilarityService topicSimilarityService;

    /**
     * Classify a topic text and return cluster affinity data.
     *
     * POST /api/v1/events/{eventCode}/topic-similarity
     *
     * @param eventCode the event for which the topic is being evaluated (path variable)
     * @param request   body containing the topic text
     * @return cluster, similarity score, and related past event numbers
     */
    @PostMapping("/topic-similarity")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicSimilarityResponse> getTopicSimilarity(
            @PathVariable String eventCode,
            @Valid @RequestBody TopicSimilarityRequest request) {
        TopicSimilarityResponse response = topicSimilarityService.getSimilarity(request.getTopic());
        return ResponseEntity.ok(response);
    }
}
