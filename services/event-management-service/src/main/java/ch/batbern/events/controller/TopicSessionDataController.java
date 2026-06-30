package ch.batbern.events.controller;

import ch.batbern.events.api.generated.topics.TopicSessionDataApi;
import ch.batbern.events.dto.generated.topics.TopicSessionDataResponse;
import ch.batbern.events.service.TopicSessionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides the session bootstrap data for the blob topic selector canvas (Story 10.4 AC: 29).
 *
 * Returns all canvas data in one call to minimise round trips when the organizer opens the page.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TopicSessionDataController implements TopicSessionDataApi {

    private final TopicSessionDataService topicSessionDataService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicSessionDataResponse> getTopicSessionData(String eventCode) {
        return ResponseEntity.ok(topicSessionDataService.getSessionData(eventCode));
    }
}
