package ch.batbern.events.service.publishing;

import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.ListEvents200Response;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mapper.EventGeneratedMapper;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.EventReadService;
import ch.batbern.events.service.EventResponseContributor.View;
import ch.batbern.events.service.EventSearchService;
import ch.batbern.shared.dto.PaginatedResponse;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The public events read model (website, 2026-10-07), publishing: shaped by publishing state, never
 * by the caller. Builds on the event read of event core ({@link EventReadService}) in the public
 * session view and shapes the result with {@link PublicEventShaper}. Moved from
 * {@code EventController}.
 */
@Service
@RequiredArgsConstructor
public class PublicEventsService {

    private static final String PUBLIC_DETAIL_INCLUDES = "topics,venue,sessions,registrations";
    private static final String PUBLIC_LIST_INCLUDES = "topics,sessions";
    private static final ObjectMapper ARCHIVE_FILTER_MAPPER = new ObjectMapper();

    private final EventReadService eventReadService;
    private final EventSearchService eventSearchService;
    private final EventRepository eventRepository;
    private final EventGeneratedMapper eventGeneratedMapper;
    private final PublicSessionVisibilityPolicy publicSessionVisibilityPolicy;
    private final PublicEventShaper publicEventShaper;

    /** The event the homepage features, if any ({@link EventReadService#findCurrentEvent}). */
    public Optional<EventDetail> current() {
        return Optional.ofNullable(eventReadService.findCurrentEvent()).map(this::publicDetail);
    }

    /** A publicly visible event; 404 for an unknown, unpublished or cancelled (#824) one. */
    public EventDetail detail(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .filter(e -> e.getWorkflowState() != EventWorkflowState.CANCELLED)
                .filter(publicSessionVisibilityPolicy::isPubliclyVisible)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));
        return publicDetail(event);
    }

    /** {@code scope=upcoming}: published upcoming events; {@code scope=archive}: archived, searchable. */
    public ListEvents200Response list(String scope, String search, String topicCodes, String sort,
                                      Integer page, Integer limit) {
        PaginatedResponse<Event> result = switch (scope == null ? "" : scope) {
            case "upcoming" -> eventSearchService.searchUpcomingPublicEvents(page, limit);
            case "archive" -> eventSearchService.searchEvents(
                    archiveFilter(search, topicCodes), archiveSort(sort), page, limit, true);
            default -> throw new ValidationException("scope must be 'upcoming' or 'archive'");
        };

        List<Event> events = result.getData();
        List<EventResponse> responses = eventReadService.batchResponses(events, PUBLIC_LIST_INCLUDES, View.PUBLIC);
        List<ch.batbern.events.core.dto.generated.Event> data = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            data.add(publicEventShaper.shape(eventGeneratedMapper.toEvent(responses.get(i)), events.get(i)));
        }
        return new ListEvents200Response().data(data).pagination(result.getPagination());
    }

    private EventDetail publicDetail(Event event) {
        EventResponse response = eventReadService.detailResponse(event, PUBLIC_DETAIL_INCLUDES, View.PUBLIC);
        return publicEventShaper.shape(eventGeneratedMapper.toEventDetail(response), event);
    }

    /** Server-built archive filter: archived events only, optionally by title/speaker and topics. */
    private String archiveFilter(String search, String topicCodes) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("workflowState", Map.of("$in", List.of("ARCHIVED")));
        if (search != null && !search.isBlank()) {
            filter.put("title", Map.of("$contains", search.trim()));
        }
        if (topicCodes != null && !topicCodes.isBlank()) {
            List<String> codes = Arrays.stream(topicCodes.split(","))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty())
                    .toList();
            if (!codes.isEmpty()) {
                filter.put("topicCode", Map.of("$in", codes));
            }
        }
        try {
            return ARCHIVE_FILTER_MAPPER.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid archive filter");
        }
    }

    private static String archiveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "-date";
        }
        if (!sort.equals("-date") && !sort.equals("date")) {
            throw new ValidationException("sort must be '-date' or 'date'");
        }
        return sort;
    }
}
