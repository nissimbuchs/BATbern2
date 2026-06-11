package ch.batbern.partners.controller;

import ch.batbern.partners.dto.TopicDTO;
import ch.batbern.partners.dto.TopicSuggestionRequest;
import ch.batbern.partners.service.TopicService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendee "Topics From the Floor" — Story 7.1.
 *
 * <p>A logged-in attendee can suggest a future event topic. Suggestions flow into the SAME
 * {@code topic_suggestions} pool that partners feed (Story 8.2), tagged {@code source=COMMUNITY},
 * so organizers triage them in the existing topic-suggestion admin UI.
 *
 * <p>Submit-only by design (per the resolved decision): there is intentionally NO attendee
 * read/list endpoint — attendees gain a voice, not a browse surface.
 *
 * <p>Routing: {@code /api/v1/attendees/topics} is routed to partner-coordination-service by the
 * gateway {@code DomainRouter} (the topic pool physically lives here). The gateway authenticates
 * the request; the ATTENDEE role is enforced below.
 */
@RestController
@RequestMapping("/api/v1/attendees/topics")
@RequiredArgsConstructor
@Slf4j
public class AttendeeTopicController {

    private final TopicService topicService;

    /**
     * POST /api/v1/attendees/topics
     * A logged-in attendee suggests a community topic. Company is irrelevant (attendees have none);
     * any {@code companyName} in the body is ignored.
     */
    @PostMapping
    @PreAuthorize("hasRole('ATTENDEE')")
    @Timed("attendee.topics.suggest")
    public ResponseEntity<TopicDTO> suggestTopic(@RequestBody TopicSuggestionRequest request) {
        log.info("POST /attendees/topics (community) title={}", request.title());
        TopicDTO dto = topicService.suggestCommunityTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
