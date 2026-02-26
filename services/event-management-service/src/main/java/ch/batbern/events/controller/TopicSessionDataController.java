package ch.batbern.events.controller;

import ch.batbern.events.dto.TopicSessionDataResponse;
import ch.batbern.events.service.TopicSessionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides the session bootstrap data for the blob topic selector canvas (Story 10.4 AC: 29).
 *
 * Returns all canvas data in one call to minimise round trips when the organizer opens the page.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}")
@RequiredArgsConstructor
public class TopicSessionDataController {

    private final TopicSessionDataService topicSessionDataService;

    /**
     * GET /api/v1/events/{eventCode}/topic-session-data
     *
     * @param eventCode the event for which to load session data
     * @return aggregated session data: partnerTopics, pastEvents, organizerBacklog, trendingTopics
     */
    @GetMapping("/topic-session-data")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicSessionDataResponse> getTopicSessionData(
            @PathVariable String eventCode) {
        return ResponseEntity.ok(topicSessionDataService.getSessionData(eventCode));
    }
}
