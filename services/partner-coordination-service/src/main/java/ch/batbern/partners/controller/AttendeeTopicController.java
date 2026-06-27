package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.AttendeeTopicsApi;
import ch.batbern.partners.dto.generated.TopicDTO;
import ch.batbern.partners.dto.generated.TopicSuggestionRequest;
import ch.batbern.partners.service.TopicService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendee "Topics From the Floor" — Story 7.1.
 *
 * <p>A logged-in attendee can suggest a future event topic. Suggestions flow into the SAME
 * {@code topic_suggestions} pool that partners feed (Story 8.2), tagged {@code source=COMMUNITY},
 * so organizers triage them in the existing topic-suggestion admin UI.
 *
 * <p>Submit-only by design: there is intentionally NO attendee read/list endpoint.
 *
 * <p>Implements the generated {@link AttendeeTopicsApi} interface. The {@code /api/v1/attendees/topics}
 * path is an intentional attendee-facing alias routed to partner-coordination-service by the gateway
 * (the topic pool physically lives here). The gateway authenticates; the ATTENDEE role is enforced below.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AttendeeTopicController implements AttendeeTopicsApi {

    private final TopicService topicService;

    /**
     * A logged-in attendee suggests a community topic. Company is irrelevant (attendees have none);
     * any {@code companyName} in the body is ignored.
     */
    @Override
    @PreAuthorize("hasRole('ATTENDEE')")
    @Timed("attendee.topics.suggest")
    public ResponseEntity<TopicDTO> suggestCommunityTopic(TopicSuggestionRequest topicSuggestionRequest) {
        log.info("POST /attendees/topics (community) title={}", topicSuggestionRequest.getTitle());
        TopicDTO dto = topicService.suggestCommunityTopic(topicSuggestionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
