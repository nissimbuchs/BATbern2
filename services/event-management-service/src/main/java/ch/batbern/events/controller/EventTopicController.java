package ch.batbern.events.controller;

import ch.batbern.events.api.generated.topics.EventTopicsApi;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.topics.SelectTopicForEventRequest;
import ch.batbern.events.dto.generated.topics.TopicSelectionResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic selection for an event ({@code EventTopicsApi}, Story 5.2 AC14-16), topics module; moved out
 * of {@code EventController}, where it was mapped by hand.
 *
 * <p>POST /api/v1/events/{eventCode}/topics validates that event and topic exist and that the event
 * is in CREATED or TOPIC_SELECTION, assigns the topic, moves the event to TOPIC_SELECTION and
 * publishes the workflow transition. Not-found and invalid-state errors are mapped by
 * {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventTopicController implements EventTopicsApi {

    private final TopicService topicService;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicSelectionResponse> selectTopicForEvent(String eventCode,
                                                                      SelectTopicForEventRequest request) {
        // ADR-003: the topic by its code, not its UUID
        String topicCode = request.getTopicCode();
        String organizerUsername = securityContextHelper.getCurrentUsername();

        Event updatedEvent = topicService.selectTopicForEvent(eventCode, topicCode, organizerUsername);

        return ResponseEntity.ok(new TopicSelectionResponse(
                updatedEvent.getEventCode(),
                topicCode,
                updatedEvent.getWorkflowState().name(),
                "Topic selected successfully"
        ));
    }
}
