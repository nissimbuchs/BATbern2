package ch.batbern.events.controller;

import ch.batbern.events.api.generated.topics.TopicClassificationsApi;
import ch.batbern.events.dto.generated.topics.TopicClassificationRequest;
import ch.batbern.events.dto.generated.topics.TopicClassificationResponse;
import ch.batbern.events.service.TopicSimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch cluster classification for the blob topic selector canvas (Epic 17, entry 8).
 *
 * The canvas loads the partner topics from partner-coordination itself and sends their titles
 * here, so EMS no longer calls partner-coordination (ADR-016). Nothing is stored.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TopicClassificationController implements TopicClassificationsApi {

    private final TopicSimilarityService topicSimilarityService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicClassificationResponse> classifyTopics(
            TopicClassificationRequest topicClassificationRequest) {
        return ResponseEntity.ok(TopicClassificationResponse.builder()
                .classifications(topicSimilarityService.classifyAll(topicClassificationRequest.getTopics()))
                .build());
    }
}
