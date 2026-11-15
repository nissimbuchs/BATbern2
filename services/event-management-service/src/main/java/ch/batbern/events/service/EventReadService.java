package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.ListEvents200Response;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mapper.EventGeneratedMapper;
import ch.batbern.events.mapper.EventMapper;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.CallerRoles;
import ch.batbern.events.service.EventResponseContributor.View;
import ch.batbern.shared.dto.PaginatedResponse;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event reads (event core): the list, the detail with its cache, the current event, and the response
 * composition the public read model reuses. Moved from {@code EventController}, which keeps only HTTP.
 *
 * <p>The {@code include=} expansion composes the modules above event core through
 * {@link EventResponseContributor}s, which they implement in their own module: topics, registrations,
 * sessions and speakers, metrics, teaser images. The venue is event core's own (its fields live on
 * the event). An include no contributor owns is logged and ignored.
 *
 * <p>No transaction of its own, as before the move: the reads run in the request's open session.
 */
@Service
@Slf4j
public class EventReadService {

    /** The include value event core expands itself. */
    private static final String VENUE = "venue";

    private final EventSearchService eventSearchService;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventGeneratedMapper eventGeneratedMapper;
    private final EventTimeResolver eventTimeResolver;
    private final CacheManager cacheManager;
    private final List<EventResponseContributor> contributors;
    private final Map<String, EventResponseContributor> contributorByInclude;

    /** The event detail and whether it came from the cache ({@code HIT} or {@code MISS}). */
    public record Detail(EventDetail body, String cacheStatus) {
    }

    public EventReadService(EventSearchService eventSearchService, EventRepository eventRepository,
                            EventMapper eventMapper, EventGeneratedMapper eventGeneratedMapper,
                            EventTimeResolver eventTimeResolver, CacheManager cacheManager,
                            List<EventResponseContributor> contributors) {
        this.eventSearchService = eventSearchService;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.eventGeneratedMapper = eventGeneratedMapper;
        this.eventTimeResolver = eventTimeResolver;
        this.cacheManager = cacheManager;
        this.contributors = List.copyOf(contributors);
        this.contributorByInclude = byInclude(this.contributors);
    }

    /** Each include value belongs to one contributor; two claiming the same one fail the start. */
    static Map<String, EventResponseContributor> byInclude(List<EventResponseContributor> contributors) {
        Map<String, EventResponseContributor> byInclude = new HashMap<>();
        for (EventResponseContributor contributor : contributors) {
            for (String include : contributor.includes()) {
                EventResponseContributor previous = VENUE.equals(include) ? contributor
                        : byInclude.putIfAbsent(include, contributor);
                if (previous != null) {
                    throw new IllegalStateException("include=" + include + " is claimed by "
                            + (VENUE.equals(include) ? "event core" : previous.getClass().getName()) + " and "
                            + contributor.getClass().getName());
                }
            }
        }
        return Map.copyOf(byInclude);
    }

    /**
     * GET /events: search, then the page's expansions in batch queries (no N+1) when includes are
     * requested; without includes the plain mapping, no counts.
     */
    public ListEvents200Response list(String filter, String sort, Integer page, Integer limit,
                                      Boolean includeArchived, String include) {
        log.debug("GET /api/v1/events - filter: {}, sort: {}, page: {}, limit: {}, includeArchived: {}, include: {}",
                filter, sort, page, limit, includeArchived, include);

        PaginatedResponse<Event> result = eventSearchService.searchEvents(filter, sort, page, limit, includeArchived);

        List<EventResponse> eventResponses;
        if (include != null && !include.trim().isEmpty()) {
            eventResponses = batchResponses(result.getData(), include, organizerEndpointView());
        } else {
            eventResponses = result.getData().stream()
                    .map(eventMapper::toDto)
                    .collect(Collectors.toList());
        }

        // Map to the generated wire type (lean Session list items — no embedded materials)
        return new ListEvents200Response()
                .data(eventResponses.stream()
                        .map(eventGeneratedMapper::toEvent)
                        .map(this::forAudience)
                        .collect(Collectors.toList()))
                .pagination(result.getPagination());
    }

    /**
     * GET /events/{eventCode}: cached for 15 minutes (Caffeine) by event code, include and audience.
     * Organizer and public responses differ in which sessions they carry, so they never share an entry.
     */
    public Detail detail(String eventCode, String include) {
        log.debug("GET /api/v1/events/{} - include: {}", eventCode, include);

        String cacheKey = eventCode + "_" + (include != null ? include : "none") + CallerRoles.audienceCacheSuffix();

        Cache cache = cacheManager.getCache(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
        String cacheStatus = "MISS";
        EventResponse response = null;

        if (cache != null) {
            Cache.ValueWrapper cachedValue = cache.get(cacheKey);
            if (cachedValue != null) {
                response = (EventResponse) cachedValue.get();
                cacheStatus = "HIT";
                log.debug("Cache HIT for event: {}", eventCode);
            }
        }

        if (response == null) {
            log.debug("Cache MISS for event: {}", eventCode);

            Event event = eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

            response = toResponse(event);
            if (include != null && !include.trim().isEmpty()) {
                applyExpansions(event, include, response, organizerEndpointView());
            }

            if (cache != null) {
                cache.put(cacheKey, response);
            }
        }

        return new Detail(forAudience(eventGeneratedMapper.toEventDetail(response)), cacheStatus);
    }

    /**
     * GET /events/current (Apple Watch, #1070): the event {@link #findCurrentEvent} features; the
     * organizer zone sees every session, the public zone the public view by publishing state.
     */
    public Optional<EventDetail> current(String include) {
        log.debug("GET /api/v1/events/current - include: {}", include);

        Event currentEvent = findCurrentEvent();
        if (currentEvent == null) {
            return Optional.empty();
        }

        EventResponse response = toResponse(currentEvent);
        if (include != null && !include.trim().isEmpty()) {
            View view = CallerRoles.isOrganizer() ? View.ALL : View.PUBLIC;
            applyExpansions(currentEvent, include, response, view);
        }

        return Optional.of(forAudience(eventGeneratedMapper.toEventDetail(response)));
    }

    /**
     * The event the homepage features. Afterglow takes precedence: a completed event within its
     * 14-day post-event window; otherwise the next upcoming event with a published phase.
     *
     * <p>Phase 1 keeps a completed event on the homepage for 14 days (Q&A, slides online, thanks),
     * even when the next event has already published; the scheduler archives it after 14 days.
     * Phase 2 is the next upcoming event that was explicitly published (currentPublishedPhase set),
     * which also serves the event day (EVENT_LIVE, date == today). Afterglow first since 2026-06-20:
     * BATbern is quarterly, so the two windows never overlap.
     *
     * @return the event, or {@code null} if none qualifies
     */
    public Event findCurrentEvent() {
        List<EventWorkflowState> activeWorkflowStates = List.of(
                EventWorkflowState.SPEAKER_IDENTIFICATION,
                EventWorkflowState.SLOT_ASSIGNMENT,
                EventWorkflowState.AGENDA_PUBLISHED,
                EventWorkflowState.EVENT_LIVE
        );
        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        Instant startOfToday = LocalDate.now(bernZone).atStartOfDay(bernZone).toInstant();

        // Phase 1: afterglow — most recently completed event within the 14-day post-event window
        Instant twoWeeksAgo = startOfToday.minus(14, ChronoUnit.DAYS);
        Event currentEvent = eventRepository
                .findFirstByWorkflowStateAndDateGreaterThanEqualAndDateBeforeOrderByDateDesc(
                        EventWorkflowState.EVENT_COMPLETED,
                        twoWeeksAgo,
                        startOfToday
                )
                .orElse(null);
        if (currentEvent != null) {
            log.debug("Afterglow: showing recently completed event {} within 14-day window",
                    currentEvent.getEventCode());
        }

        // Phase 2: fallback — next upcoming published event (today or future, currentPublishedPhase set)
        if (currentEvent == null) {
            currentEvent = eventRepository
                    .findFirstByWorkflowStateInAndDateGreaterThanEqualAndCurrentPublishedPhaseIsNotNullOrderByDateAsc(
                            activeWorkflowStates, startOfToday)
                    .orElse(null);
        }

        if (currentEvent == null) {
            log.debug("No current event found with workflow states: {}", activeWorkflowStates);
            return null;
        }

        log.debug("Found current event: {} with workflowState: {}",
                currentEvent.getEventCode(), currentEvent.getWorkflowState());
        return currentEvent;
    }

    /** A single-event response with the given includes expanded in the given view (public read model). */
    public EventResponse detailResponse(Event event, String include, View view) {
        EventResponse response = toResponse(event);
        applyExpansions(event, include, response, view);
        return response;
    }

    /**
     * Responses for a page of events, the includes expanded in batch queries: a fixed number of
     * queries whatever the page size, and no HTTP call. {@code result.get(i)} belongs to
     * {@code events.get(i)}.
     */
    public List<EventResponse> batchResponses(List<Event> events, String include, View view) {
        Set<String> includes = Arrays.stream(include.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        List<EventResponse> responses = events.stream()
                .map(eventMapper::toDto)
                .collect(Collectors.toList());

        if (events.isEmpty()) {
            return responses;
        }

        contributors.forEach(contributor -> contributor.expandBatch(includes, events, responses, view));

        // Venue expansion is cheap (data is on the Event entity itself — no extra query)
        if (includes.contains(VENUE)) {
            for (int i = 0; i < events.size(); i++) {
                responses.get(i).setVenue(expandVenue(events.get(i)));
            }
        }

        return responses;
    }

    /**
     * The response every single-event read starts from: the mapped event, the fields each module
     * always adds (registration counts, teaser images) and the resolved start and end times.
     */
    public EventResponse toResponse(Event event) {
        EventResponse response = eventMapper.toDto(event);
        contributors.forEach(contributor -> contributor.enrich(event, response));
        enrichWithEventTimes(response, event);
        return response;
    }

    /** Organizer endpoints (/events/{code}, /events): sessions and speakers for organizers only. */
    private View organizerEndpointView() {
        return CallerRoles.isOrganizer() ? View.ALL : View.NONE;
    }

    /**
     * Applies each requested include in the order given (Story BAT-109); an include no one owns is
     * logged and ignored.
     */
    private void applyExpansions(Event event, String include, EventResponse response, View view) {
        for (String resource : include.split(",")) {
            String trimmed = resource.trim();
            if (VENUE.equals(trimmed)) {
                response.setVenue(expandVenue(event));
                continue;
            }
            EventResponseContributor contributor = contributorByInclude.get(trimmed);
            if (contributor != null) {
                contributor.expand(trimmed, event, response, view);
            } else {
                log.warn("Unknown include resource requested: {}", trimmed);
            }
        }
    }

    /** Venue data stored directly on the event. */
    private Map<String, Object> expandVenue(Event event) {
        Map<String, Object> venue = new HashMap<>();
        venue.put("id", event.getEventCode()); // Use eventCode as venue identifier
        venue.put("name", event.getVenueName());
        venue.put("capacity", event.getVenueCapacity());
        venue.put("address", event.getVenueAddress());
        return venue;
    }

    /** Resolved start/end times: session times, then the event type's config, then 16:00. */
    private void enrichWithEventTimes(EventResponse response, Event event) {
        response.setTypicalStartTime(eventTimeResolver.formatStartTime(event));
        response.setTypicalEndTime(eventTimeResolver.formatEndTime(event));
    }

    /**
     * GHSA-qhqv: callers other than organizers get the public projection. Who runs the event, who
     * edited it last and the real attendee count are organizer data; the public website needs the
     * workflow state and the registration counters, which stay. Applied to the wire object after the
     * cache, so cached responses are never altered.
     */
    private ch.batbern.events.core.dto.generated.Event forAudience(ch.batbern.events.core.dto.generated.Event event) {
        if (!CallerRoles.isOrganizer()) {
            event.setOrganizerUsername(null);
            event.setUpdatedBy(null);
            event.setRealAttendeeCount(null);
        }
        return event;
    }

    private EventDetail forAudience(EventDetail event) {
        if (!CallerRoles.isOrganizer()) {
            event.setOrganizerUsername(null);
            event.setUpdatedBy(null);
            event.setRealAttendeeCount(null);
        }
        return event;
    }
}
