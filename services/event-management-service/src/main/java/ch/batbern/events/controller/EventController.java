package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Logo;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.BatchUpdateRequest;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.CreateRegistrationResponse;
import ch.batbern.events.dto.generated.BatchRegistrationRequest;
import ch.batbern.events.dto.generated.BatchRegistrationResponse;
import ch.batbern.events.dto.generated.CreateRegistrationRequest;
import ch.batbern.events.dto.generated.MyRegistrationResponse;
import ch.batbern.events.dto.generated.topics.SelectTopicForEventRequest;
import ch.batbern.events.dto.generated.topics.TopicSelectionResponse;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.mapper.EventMapper;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.event.EventUpdatedEvent;
import ch.batbern.events.exception.BusinessValidationException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.service.EventSearchService;
import ch.batbern.events.service.EventWorkflowStateMachine;
import jakarta.validation.Valid;
import ch.batbern.shared.dto.PaginatedResponse;
import ch.batbern.shared.types.EventWorkflowState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Event REST Controller
 * Story 1.15a.1: Events API Consolidation
 *
 * Provides consolidated Events API endpoints with:
 * - Resource expansion (?include=venue,speakers,...)
 * - Rich filtering (?filter={JSON})
 * - Sorting (?sort=-date,+title)
 * - Pagination (?page=1&limit=20)
 * - Field selection (?fields=id,title,date)
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Events", description = "Event management API - consolidated endpoints")
public class EventController {

    private final EventSearchService eventSearchService;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final LogoRepository logoRepository;
    private final EventWorkflowStateMachine eventWorkflowStateMachine;
    private final ch.batbern.events.repository.SessionRepository sessionRepository;
    private final ch.batbern.events.service.EventAnalyticsService eventAnalyticsService;
    private final ch.batbern.shared.events.DomainEventPublisher eventPublisher;
    private final ch.batbern.events.security.SecurityContextHelper securityContextHelper;
    private final CacheManager cacheManager;
    private final ch.batbern.events.service.GenericLogoService genericLogoService;
    private final ch.batbern.events.client.UserApiClient userApiClient;
    private final ch.batbern.events.service.RegistrationService registrationService;
    private final ch.batbern.events.repository.RegistrationRepository registrationRepository;
    private final ch.batbern.events.service.ConfirmationTokenService confirmationTokenService;
    private final ch.batbern.events.service.RegistrationEmailService registrationEmailService;
    private final ch.batbern.events.service.TopicService topicService;
    private final ch.batbern.events.repository.TopicRepository topicRepository;
    private final ch.batbern.events.repository.SessionUserRepository sessionUserRepository;
    private final ch.batbern.events.repository.SessionMaterialsRepository sessionMaterialsRepository;
    private final ch.batbern.events.service.SpeakerPoolService speakerPoolService;
    private final ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;
    private final ch.batbern.events.repository.EventTypeRepository eventTypeRepository;
    private final ch.batbern.events.service.SessionService sessionService;
    private final ch.batbern.events.service.WaitlistPromotionService waitlistPromotionService;
    private final ch.batbern.events.service.EventTeaserImageService eventTeaserImageService;

    @Value("${app.base-url:https://batbern.ch}")
    private String appBaseUrl;

    /**
     * List/Search Events (AC1)
     *
     * GET /api/v1/events?filter={}&sort={}&page={}&limit={}
     *
     * Examples:
     * - List all: GET /api/v1/events
     * - Filter by workflow state: GET /api/v1/events?filter={"workflowState":"PUBLISHED"}
     * - Filter by year: GET /api/v1/events?filter={"date":{"$gte":"2025-01-01T00:00:00Z",
     *   "$lt":"2026-01-01T00:00:00Z"}}
     * - Sort descending: GET /api/v1/events?sort=-date
     * - Paginate: GET /api/v1/events?page=2&limit=10
     * - Combined: GET /api/v1/events?filter={"workflowState":"PUBLISHED"}&sort=-date&page=1&limit=20
     */
    @GetMapping
    @Operation(
            summary = "List/Search Events",
            description = "Retrieve events with optional filtering, sorting, and pagination. "
                    + "Uses MongoDB-style filter syntax for rich querying. "
                    + "Supports resource expansion via ?include parameter (e.g., ?include=topics,sessions,speakers)"
    )
    public ResponseEntity<PaginatedResponse<EventResponse>> listEvents(
            @Parameter(description = "JSON filter object (e.g., {\"status\":\"published\"})")
            @RequestParam(required = false) String filter,

            @Parameter(description = "Sort specification (e.g., -date for descending, +title for ascending)")
            @RequestParam(required = false) String sort,

            @Parameter(description = "Page number (1-indexed, default: 1)")
            @RequestParam(required = false) Integer page,

            @Parameter(description = "Items per page (default: 20, max: 100)")
            @RequestParam(required = false) Integer limit,

            @Parameter(description = "Include archived events (default: false)")
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,

            @Parameter(description = "Comma-separated list of resources to include "
                               + "(e.g., topics,sessions,speakers,registrations)")
            @RequestParam(required = false) String include
    ) {
        log.debug("GET /api/v1/events - filter: {}, sort: {}, page: {}, limit: {}, includeArchived: {}, include: {}",
                filter, sort, page, limit, includeArchived, include);

        // Search events using EventSearchService
        PaginatedResponse<Event> result = eventSearchService.searchEvents(filter, sort, page, limit, includeArchived);

        // Convert entities to EventResponse DTOs
        // When includes are requested, use batch loading to avoid N+1 queries
        List<EventResponse> eventResponses;
        if (include != null && !include.trim().isEmpty()) {
            eventResponses = buildBatchExpandedResponses(result.getData(), include);
        } else {
            eventResponses = result.getData().stream()
                    .map(eventMapper::toDto)
                    .collect(Collectors.toList());
        }

        // Build response
        PaginatedResponse<EventResponse> response = PaginatedResponse.<EventResponse>builder()
                .data(eventResponses)
                .pagination(result.getPagination())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get Event Detail (AC2 + AC15)
     *
     * GET /api/v1/events/{eventCode}?include=venue,speakers,sessions
     *
     * Examples:
     * - Basic: GET /api/v1/events/BATbern56
     * - With venue: GET /api/v1/events/BATbern56?include=venue
     * - Multiple: GET /api/v1/events/BATbern56?include=venue,speakers,sessions
     *
     * Caching: Results with includes are cached for 15 minutes using Caffeine in-memory
     * cache. Cache key includes both event code and include parameters to ensure correct
     * cache hits.
     *
     * Story 1.16.2: Uses eventCode (String) instead of UUID
     */
    @GetMapping("/{eventCode}")
    @Operation(
            summary = "Get Event Detail",
            description = "Retrieve a single event by event code with optional resource expansion "
                + "using ?include parameter. Cached for 15 minutes."
    )
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable String eventCode,
            @Parameter(description = "Comma-separated list of resources to include (e.g., venue,speakers,sessions)")
            @RequestParam(required = false) String include
    ) {
        log.debug("GET /api/v1/events/{} - include: {}", eventCode, include);

        // Generate cache key
        String cacheKey = eventCode + "_" + (include != null ? include : "none");

        // Check cache first
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

        // If not in cache, fetch from database
        if (response == null) {
            log.debug("Cache MISS for event: {}", eventCode);

            // Find event by event code
            Event event = eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

            // Build response using EventMapper (BAT-91 Phase 3)
            response = eventMapper.toDto(event);
            enrichWithRegistrationCounts(response, event.getId());
            enrichWithTeaserImages(response);

            // Apply resource expansions if requested
            if (include != null && !include.trim().isEmpty()) {
                applyResourceExpansionsToDTO(event, include, response);
            }

            // Store in cache
            if (cache != null) {
                cache.put(cacheKey, response);
            }
        }

        // Add cache status header
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Cache-Status", cacheStatus);

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * Build EventResponse DTOs for a list of events using batch DB queries.
     *
     * Replaces the per-event N+1 expansion loop in listEvents(). For a page of N events the
     * previous implementation issued ~27N DB queries plus ~3N HTTP calls to company-user-management.
     * This method reduces that to exactly 4 DB queries and 0 HTTP calls regardless of page size:
     *
     *   1. Events (already fetched by caller)
     *   2. Topics    — topicRepository.findByTopicCodeIn()
     *   3. Sessions + session_users — sessionRepository.findByEventIdInWithSpeakers()
     *   4. Materials — sessionMaterialsRepository.findBySessionIdIn()
     *
     * Speaker names are read from the cached session_users.speaker_first_name/last_name columns
     * (populated during assignment, V38 migration). profilePictureUrl is intentionally omitted
     * from list responses; the frontend lazy-loads portraits via GET /api/v1/speakers/{username}.
     */
    private List<EventResponse> buildBatchExpandedResponses(List<Event> events, String include) {
        Set<String> includes = java.util.Arrays.stream(include.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        // Build base DTOs
        List<EventResponse> responses = events.stream()
                .map(eventMapper::toDto)
                .collect(Collectors.toList());

        if (events.isEmpty()) {
            return responses;
        }

        // --- BATCH 1: Topics (1 query) ---
        if (includes.contains("topics")) {
            Set<String> topicCodes = events.stream()
                    .map(Event::getTopicCode)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!topicCodes.isEmpty()) {
                Map<String, Map<String, Object>> topicByCode = topicRepository.findByTopicCodeIn(topicCodes)
                        .stream()
                        .collect(Collectors.toMap(
                                ch.batbern.events.domain.Topic::getTopicCode,
                                t -> {
                                    Map<String, Object> m = new HashMap<>();
                                    m.put("code", t.getTopicCode());
                                    m.put("name", t.getTitle());
                                    m.put("description", t.getDescription());
                                    m.put("category", t.getCategory());
                                    return m;
                                }
                        ));

                for (int i = 0; i < events.size(); i++) {
                    String code = events.get(i).getTopicCode();
                    if (code != null) {
                        responses.get(i).setTopic(topicByCode.get(code));
                    }
                }
            }
        }

        // --- BATCH 2: Registrations count (1 query via IN) ---
        if (includes.contains("registrations")) {
            // Existing registrationRepository doesn't have a batch count; fall back to per-event
            // (registrations are rarely requested on archive list, so this is acceptable)
            for (int i = 0; i < events.size(); i++) {
                long regCount = registrationRepository.countByEventId(events.get(i).getId());
                responses.get(i).setCurrentAttendeeCount((int) regCount);
            }
        }

        // --- BATCH 3+4+5: Sessions + session_users + user_portraits + materials (3 queries) ---
        if (includes.contains("sessions") || includes.contains("speakers")) {
            Set<UUID> eventIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toSet());

            // Query 3: all sessions for all events with session_users eagerly loaded
            List<ch.batbern.events.domain.Session> allSessions =
                    sessionRepository.findByEventIdInWithSpeakers(eventIds);

            // Group sessions by event ID
            Map<UUID, List<ch.batbern.events.domain.Session>> sessionsByEventId = allSessions.stream()
                    .collect(Collectors.groupingBy(ch.batbern.events.domain.Session::getEventId));

            // Query 4 (intentional architecture break): cross-service join into user_profiles
            // (owned by company-user-management-service) to get portrait URLs and company names.
            // Both services share the same PostgreSQL DB in this monorepo deployment.
            Set<String> allUsernames = allSessions.stream()
                    .flatMap(s -> s.getSessionUsers().stream())
                    .map(ch.batbern.events.domain.SessionUser::getUsername)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, ch.batbern.events.repository.UserPortraitProjection> portraitByUsername =
                    java.util.Collections.emptyMap();
            if (!allUsernames.isEmpty()) {
                portraitByUsername = sessionUserRepository.findUserPortraitsByUsernames(allUsernames)
                        .stream()
                        .collect(Collectors.toMap(
                                ch.batbern.events.repository.UserPortraitProjection::getUsername,
                                p -> p,
                                (a, b) -> a // keep first on duplicate username
                        ));
            }

            // Query 5: all materials for all sessions
            Set<UUID> sessionIds = allSessions.stream()
                    .map(ch.batbern.events.domain.Session::getId)
                    .collect(Collectors.toSet());

            Map<UUID, List<ch.batbern.events.domain.SessionMaterial>> materialsBySessionId =
                    java.util.Collections.emptyMap();
            if (!sessionIds.isEmpty()) {
                materialsBySessionId = sessionMaterialsRepository.findBySessionIdIn(sessionIds)
                        .stream()
                        .collect(Collectors.groupingBy(ch.batbern.events.domain.SessionMaterial::getSessionId));
            }

            // Assemble session maps per event entirely in-memory — 0 HTTP calls
            final Map<String, ch.batbern.events.repository.UserPortraitProjection> finalPortraits =
                    portraitByUsername;
            final Map<UUID, List<ch.batbern.events.domain.SessionMaterial>> finalMaterials =
                    materialsBySessionId;

            for (int i = 0; i < events.size(); i++) {
                UUID eventId = events.get(i).getId();
                String eventCode = events.get(i).getEventCode();
                List<ch.batbern.events.domain.Session> eventSessions =
                        sessionsByEventId.getOrDefault(eventId, List.of());

                List<Map<String, Object>> sessionMaps = eventSessions.stream()
                        .map(s -> buildSessionMapBatch(
                                s, eventCode,
                                finalMaterials.getOrDefault(s.getId(), List.of()),
                                finalPortraits))
                        .collect(Collectors.toList());

                responses.get(i).setSessions(sessionMaps);
            }
        }

        // Venue expansion is cheap (data is on the Event entity itself — no extra query)
        if (includes.contains("venue")) {
            for (int i = 0; i < events.size(); i++) {
                responses.get(i).setVenue(expandVenue(events.get(i)));
            }
        }

        return responses;
    }

    /**
     * Build a session response map from already-loaded entities (no DB or HTTP calls).
     *
     * Speaker names, portrait URLs, and company logo URLs all come from the batch
     * cross-service join result (user_profiles + logos tables).
     */
    private Map<String, Object> buildSessionMapBatch(
            ch.batbern.events.domain.Session session,
            String eventCode,
            List<ch.batbern.events.domain.SessionMaterial> materials,
            Map<String, ch.batbern.events.repository.UserPortraitProjection> portraitByUsername) {

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("id", session.getId());
        sessionMap.put("sessionSlug", session.getSessionSlug());
        sessionMap.put("eventCode", eventCode);
        sessionMap.put("title", session.getTitle());
        sessionMap.put("description", session.getDescription());
        sessionMap.put("sessionType", session.getSessionType());
        sessionMap.put("startTime", session.getStartTime());
        sessionMap.put("endTime", session.getEndTime());
        sessionMap.put("room", session.getRoom());
        sessionMap.put("capacity", session.getCapacity());
        sessionMap.put("language", session.getLanguage());
        sessionMap.put("createdAt", session.getCreatedAt());
        sessionMap.put("updatedAt", session.getUpdatedAt());

        // Materials (batch-loaded, sorted by creation time)
        List<Map<String, Object>> materialMaps = materials.stream()
                .sorted(java.util.Comparator.comparing(
                        ch.batbern.events.domain.SessionMaterial::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(m -> {
                    Map<String, Object> mm = new HashMap<>();
                    mm.put("id", m.getId());
                    mm.put("fileName", m.getFileName());
                    mm.put("materialType", m.getMaterialType());
                    mm.put("cloudFrontUrl", m.getCloudFrontUrl());
                    mm.put("fileSize", m.getFileSize());
                    mm.put("createdAt", m.getCreatedAt());
                    return mm;
                })
                .collect(Collectors.toList());

        sessionMap.put("materials", materialMaps);
        sessionMap.put("materialsCount", materialMaps.size());
        sessionMap.put("materialsStatus", materialMaps.isEmpty() ? "NONE" : "COMPLETE");

        // Speakers — portrait URL, company name, and company logo URL all from
        // the cross-service DB join (user_profiles + logos). Zero HTTP calls.
        List<Map<String, Object>> speakerMaps = session.getSessionUsers().stream()
                .map(su -> {
                    ch.batbern.events.repository.UserPortraitProjection portrait =
                            su.getUsername() != null ? portraitByUsername.get(su.getUsername()) : null;
                    Map<String, Object> sm = new HashMap<>();
                    sm.put("username", su.getUsername());
                    sm.put("firstName",
                            su.getSpeakerFirstName() != null ? su.getSpeakerFirstName() : "");
                    sm.put("lastName",
                            su.getSpeakerLastName() != null ? su.getSpeakerLastName() : "");
                    sm.put("speakerRole",
                            su.getSpeakerRole() != null ? su.getSpeakerRole().name() : null);
                    sm.put("presentationTitle", su.getPresentationTitle());
                    sm.put("isConfirmed", su.isConfirmed());
                    sm.put("profilePictureUrl", portrait != null ? portrait.getProfilePictureUrl() : null);
                    sm.put("company",          portrait != null ? portrait.getCompanyId() : null);
                    sm.put("companyLogoUrl",   portrait != null ? portrait.getCompanyLogoUrl() : null);
                    sm.put("bio", null); // Only needed on detail page
                    return sm;
                })
                .collect(Collectors.toList());

        sessionMap.put("speakers", speakerMaps);
        return sessionMap;
    }

    /**
     * Apply resource expansions to EventResponse DTO
     * Story BAT-109: Archive browsing with resource expansion
     * Populates optional fields (topic, sessions, venue) when requested via ?include parameter
     */
    private void applyResourceExpansionsToDTO(Event event, String include, EventResponse response) {
        String[] includes = include.split(",");
        for (String resource : includes) {
            String trimmed = resource.trim();
            switch (trimmed) {
                case "topics":
                    response.setTopic(expandTopic(event));
                    break;
                case "venue":
                    response.setVenue(expandVenue(event));
                    break;
                case "sessions":
                case "speakers":
                    // Sessions include speakers automatically
                    response.setSessions(expandSessions(event));
                    break;
                case "metrics":
                    // Add speaker metrics (BAT-91 Phase 3)
                    expandMetricsToDTO(event, response);
                    break;
                case "registrations":
                    // Active (non-cancelled) registrations only — cancelled ones must not inflate counts
                    long registrationCount = registrationRepository.countByEventIdAndStatusIn(
                            event.getId(), java.util.List.of("registered", "confirmed", "waitlist"));
                    response.setCurrentAttendeeCount((int) registrationCount);
                    break;
                default:
                    log.warn("Unknown include resource requested: {}", trimmed);
                    break;
            }
        }
    }

    /**
     * Expand metrics to EventResponse DTO
     * Story BAT-91 Phase 3: Typed DTO migration
     */
    private void expandMetricsToDTO(Event event, EventResponse response) {
        UUID eventId = event.getId();

        // Count speakers who accepted invitation (ACCEPTED or higher in workflow)
        long acceptedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED);
        long contentSubmittedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED);
        long qualityReviewedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED);
        long slotAssignedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, ch.batbern.shared.types.SpeakerWorkflowState.SLOT_ASSIGNED);
        long confirmedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED);

        // Total confirmed speakers (accepted or higher)
        long totalConfirmedSpeakers = acceptedCount + contentSubmittedCount
                + qualityReviewedCount + slotAssignedCount + confirmedCount;

        // Speakers with complete info (submitted materials - CONTENT_SUBMITTED or higher)
        long speakersWithCompleteInfo = contentSubmittedCount + qualityReviewedCount
                + slotAssignedCount + confirmedCount;

        // Pending materials = accepted but haven't submitted content yet
        long pendingMaterials = acceptedCount;

        // Get max speaker slots from event type configuration
        Integer maxSpeakerSlots = null;
        if (event.getEventType() != null) {
            maxSpeakerSlots = eventTypeRepository.findByType(event.getEventType())
                    .map(ch.batbern.events.entity.EventTypeConfiguration::getMaxSlots)
                    .orElse(null);
        }

        // Story 5.9: Calculate session materials metrics
        // Total = speaker sessions with a timeslot assigned (excludes structural: moderation/break/lunch)
        List<ch.batbern.events.domain.Session> allSessions = sessionRepository.findByEventId(eventId);
        Set<String> structuralTypes = Set.of("moderation", "break", "lunch");
        long totalSessions = allSessions.stream()
                .filter(session -> session.getStartTime() != null)
                .filter(session -> !structuralTypes.contains(session.getSessionType()))
                .count();
        long sessionsWithMaterials = allSessions.stream()
                .filter(session -> session.getStartTime() != null)
                .filter(session -> !structuralTypes.contains(session.getSessionType()))
                .filter(session -> {
                    // Count sessions with materialsStatus = COMPLETE
                    List<ch.batbern.events.dto.SessionMaterialResponse> materials =
                            sessionService.toSessionResponse(session, event.getEventCode(), false)
                                    .getMaterials();
                    return materials != null && !materials.isEmpty();
                })
                .count();

        // Set metrics on EventResponse
        response.setConfirmedSpeakersCount((int) totalConfirmedSpeakers);
        response.setSpeakersWithCompleteInfoCount((int) speakersWithCompleteInfo);
        response.setPendingMaterialsCount((int) pendingMaterials);
        response.setMaxSpeakerSlots(maxSpeakerSlots);
        response.setSessionsWithMaterialsCount((int) sessionsWithMaterials);
        response.setTotalSessionsCount((int) totalSessions);

        log.debug("Event {} metrics - confirmed: {}, complete info: {}, "
                        + "pending materials: {}, max slots: {}, sessions with materials: {}/{}",
                event.getEventCode(), totalConfirmedSpeakers, speakersWithCompleteInfo,
                pendingMaterials, maxSpeakerSlots, sessionsWithMaterials, totalSessions);
    }

    /**
     * Expand topic data for an event
     * Story BAT-109: Archive browsing with topic expansion
     */
    private Map<String, Object> expandTopic(Event event) {
        if (event.getTopicCode() == null) {
            return null;
        }

        // Fetch topic from repository
        return topicService.getTopicByCode(event.getTopicCode())
                .map(topic -> {
                    Map<String, Object> topicMap = new HashMap<>();
                    topicMap.put("code", topic.getTopicCode());
                    topicMap.put("name", topic.getTitle());
                    topicMap.put("description", topic.getDescription());
                    topicMap.put("category", topic.getCategory());
                    return topicMap;
                })
                .orElse(null);
    }

    /**
     * Expand venue data for an event
     * TODO: Replace with actual service call when Company Management Service is available
     */
    private Map<String, Object> expandVenue(Event event) {
        // Use venue data stored directly on the Event entity
        // In the future, this could be enhanced to fetch from a separate Venue service
        Map<String, Object> venue = new java.util.HashMap<>();
        venue.put("id", event.getEventCode()); // Use eventCode as venue identifier
        venue.put("name", event.getVenueName());
        venue.put("capacity", event.getVenueCapacity());
        venue.put("address", event.getVenueAddress());
        return venue;
    }

    /**
     * Expand sessions for an event
     * Story 1.15a.1: Resource expansion for sessions
     * Story 1.15a.1b: Include enriched speaker data
     *
     * @param event The event to expand sessions for
     * @return List of session maps with public fields and speakers
     */
    private java.util.List<Map<String, Object>> expandSessions(Event event) {
        // Find all sessions for this event with speakers eagerly loaded (Story 5.5)
        List<ch.batbern.events.domain.Session> sessions = sessionRepository.findByEventIdWithSpeakers(event.getId());

        // Convert to response format using SessionService (includes materials - Story 5.9)
        return sessions.stream()
                .map(session -> {
                    // Use SessionService.toSessionResponse() to get materials included
                    ch.batbern.events.dto.SessionResponse sessionResponse =
                            sessionService.toSessionResponse(session, event.getEventCode());

                    // Convert SessionResponse to Map for backward compatibility
                    Map<String, Object> sessionMap = new HashMap<>();
                    sessionMap.put("id", session.getId()); // Story 5.6: Include UUID for speaker pool matching
                    sessionMap.put("sessionSlug", sessionResponse.getSessionSlug());
                    sessionMap.put("eventCode", sessionResponse.getEventCode());
                    sessionMap.put("title", sessionResponse.getTitle());
                    sessionMap.put("description", sessionResponse.getDescription());
                    sessionMap.put("sessionType", sessionResponse.getSessionType());
                    sessionMap.put("startTime", sessionResponse.getStartTime());
                    sessionMap.put("endTime", sessionResponse.getEndTime());
                    sessionMap.put("room", sessionResponse.getRoom());
                    sessionMap.put("capacity", sessionResponse.getCapacity());
                    sessionMap.put("language", sessionResponse.getLanguage());
                    sessionMap.put("createdAt", sessionResponse.getCreatedAt());
                    sessionMap.put("updatedAt", sessionResponse.getUpdatedAt());

                    // Story 5.9: Include materials data
                    sessionMap.put("materials", sessionResponse.getMaterials());
                    sessionMap.put("materialsCount", sessionResponse.getMaterialsCount());
                    sessionMap.put("materialsStatus", sessionResponse.getMaterialsStatus());

                    // Story 1.15a.1b: Use speakers from SessionResponse
                    sessionMap.put("speakers", sessionResponse.getSpeakers());

                    return sessionMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Expand speakers for a session with enriched User data
     * Story 1.15a.1b: Combines SessionUser (role, confirmation) with User entity (name, company, photo)
     *
     * @param session The session to expand speakers for
     * @return List of speaker maps with enriched user data
     */
    private java.util.List<Map<String, Object>> expandSessionSpeakers(
        ch.batbern.events.domain.Session session) {
        return session.getSessionUsers().stream()
                .map(sessionUser -> {
                    Map<String, Object> speakerMap = new HashMap<>();

                    // Add SessionUser data (role, confirmation)
                    speakerMap.put("speakerRole", sessionUser.getSpeakerRole().name());
                    speakerMap.put("isConfirmed", sessionUser.isConfirmed());
                    speakerMap.put("presentationTitle", sessionUser.getPresentationTitle());

                    // Fetch and add enriched User data
                    try {
                        if (sessionUser.getUsername() != null) {
                            ch.batbern.events.dto.generated.users.UserResponse userProfile =
                                userApiClient.getUserByUsername(sessionUser.getUsername());
                            speakerMap.put("username", userProfile.getId());
                            speakerMap.put("firstName", userProfile.getFirstName());
                            speakerMap.put("lastName", userProfile.getLastName());
                            speakerMap.put("company", userProfile.getCompanyId());
                            speakerMap.put("profilePictureUrl", userProfile.getProfilePictureUrl());
                            speakerMap.put("bio", userProfile.getBio());
                        } else {
                            // Fallback: username not set (shouldn't happen with V8 migration)
                            log.warn("SessionUser {} has no username set", sessionUser.getId());
                            speakerMap.put("username", null);
                            speakerMap.put("firstName", "Unknown");
                            speakerMap.put("lastName", "Speaker");
                            speakerMap.put("company", null);
                            speakerMap.put("profilePictureUrl", null);
                            speakerMap.put("bio", null);
                        }
                    } catch (Exception e) {
                        // Log error but don't fail the entire request
                        log.error("Failed to fetch user profile for username {}: {}",
                                  sessionUser.getUsername(), e.getMessage());
                        speakerMap.put("username", sessionUser.getUsername());
                        speakerMap.put("firstName", "Unknown");
                        speakerMap.put("lastName", "Speaker");
                        speakerMap.put("company", null);
                        speakerMap.put("profilePictureUrl", null);
                        speakerMap.put("bio", null);
                    }

                    return speakerMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get Current Published Event (PUBLIC ACCESS - Story 4.1.3)
     *
     * GET /api/v1/events/current?include=topics,venue,speakers,sessions
     *
     * Returns the next upcoming event with status published, registration_open, or
     * registration_closed. If multiple events match, returns the one nearest to the current
     * date. This is a public endpoint (no authentication required) used by the public website.
     *
     * @param include Comma-separated list of resources to expand
     * @return Current event or 404 if none exists
     */
    @GetMapping("/current")
    @Operation(
            summary = "Get Current Event",
            description = "Retrieve the next upcoming event (published, registration_open, or "
                + "registration_closed) for the public website. No authentication required."
    )
    public ResponseEntity<EventResponse> getCurrentEvent(
            @Parameter(description = "Comma-separated list of resources to include "
                + "(e.g., topics,venue,speakers,sessions)")
            @RequestParam(required = false) String include
    ) {
        log.debug("GET /api/v1/events/current - include: {}", include);

        // Find the next event with active workflow states (V17: changed from status to workflowState)
        // Returns the event nearest to current date, but only if it occurs today or in the future.
        // Events whose date was yesterday or earlier are no longer shown on the homepage.
        // 9-State Model: NEWSLETTER_SENT and EVENT_READY consolidated into AGENDA_FINALIZED
        List<EventWorkflowState> activeWorkflowStates = List.of(
                EventWorkflowState.SPEAKER_IDENTIFICATION,
                EventWorkflowState.SLOT_ASSIGNMENT,
                EventWorkflowState.AGENDA_PUBLISHED,
                EventWorkflowState.AGENDA_FINALIZED,
                EventWorkflowState.EVENT_LIVE,
                EventWorkflowState.EVENT_COMPLETED
        );
        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        Instant startOfToday = LocalDate.now(bernZone).atStartOfDay(bernZone).toInstant();
        Event currentEvent = eventRepository
                .findFirstByWorkflowStateInAndDateGreaterThanEqualOrderByDateAsc(activeWorkflowStates, startOfToday)
                .orElse(null);

        if (currentEvent == null) {
            log.debug("No current event found with workflow states: {}", activeWorkflowStates);
            return ResponseEntity.notFound().build();
        }

        log.debug("Found current event: {} with workflowState: {}",
                currentEvent.getEventCode(), currentEvent.getWorkflowState());

        // Build response using EventMapper (BAT-91 Phase 3)
        EventResponse response = eventMapper.toDto(currentEvent);
        enrichWithRegistrationCounts(response, currentEvent.getId());
        enrichWithTeaserImages(response);

        // Apply resource expansions if requested
        if (include != null && !include.trim().isEmpty()) {
            applyResourceExpansionsToDTO(currentEvent, include, response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Create Event (AC3)
     *
     * POST /api/v1/events
     *
     * @param request Event creation data
     * @return Created event with generated eventCode
     *
     * Story 1.16.2: Generates eventCode in format "BATbern{number}"
     */
    @PostMapping
    @Operation(summary = "Create Event", description = "Create a new event")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        log.debug("POST /api/v1/events - title: {}", request.getTitle());

        // Generate eventCode from event number (format: "BATbern{number}")
        Integer eventNumber = request.getEventNumber();
        if (eventNumber == null) {
            // Auto-generate event number if not provided
            eventNumber = generateNextEventNumber();
        }
        String eventCode = "BATbern" + eventNumber;

        // Validate eventCode is unique
        if (eventRepository.existsByEventCode(eventCode)) {
            throw new BusinessValidationException("eventCode",
                "Event code already exists: " + eventCode);
        }

        // Create new event entity
        Event event = Event.builder()
                .eventCode(eventCode)
                .title(request.getTitle())
                .eventNumber(eventNumber)
                .date(parseDate(request.getDate()))
                .registrationDeadline(request.getRegistrationDeadline() != null
                    ? parseDate(request.getRegistrationDeadline()) : null)
                .venueName(request.getVenueName())
                .venueAddress(request.getVenueAddress())
                .venueCapacity(request.getVenueCapacity())
                .organizerUsername(request.getOrganizerUsername())
                .currentAttendeeCount(request.getCurrentAttendeeCount() != null ? request.getCurrentAttendeeCount() : 0)
                .publishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null)
                .metadata(request.getMetadata())
                .description(request.getDescription())
                .eventType(request.getEventType())
                .themeImageUploadId(request.getThemeImageUploadId())
                .workflowState(request.getWorkflowState()) // Use workflowState from request directly
                .registrationCapacity(request.getRegistrationCapacity())
                .build();

        // Save event
        Event savedEvent = eventRepository.save(event);

        // Associate theme image if provided (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            associateThemeImage(savedEvent, request.getThemeImageUploadId());
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUsername();
            EventCreatedEvent domainEvent = new EventCreatedEvent(
                    savedEvent.getId(),           // Internal UUID
                    savedEvent.getEventCode(),    // Public business identifier
                    savedEvent.getTitle(),
                    savedEvent.getEventNumber(),
                    savedEvent.getDate(),
                    savedEvent.getRegistrationDeadline(),
                    savedEvent.getVenueName(),
                    savedEvent.getVenueAddress(),
                    savedEvent.getVenueCapacity(),
                    savedEvent.getWorkflowState() != null ? savedEvent.getWorkflowState().name() : null,
                    savedEvent.getOrganizerUsername(),
                    savedEvent.getDescription(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventCreatedEvent for event: {}", savedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventCreatedEvent for event {}: {}", savedEvent.getEventCode(), e.getMessage());
            // Continue - event creation succeeded, publishing failure is non-critical
        }

        // Build response using EventMapper (Phase 3: BAT-91)
        EventResponse response = eventMapper.toDto(savedEvent);
        enrichWithRegistrationCounts(response, savedEvent.getId());
        enrichWithTeaserImages(response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Generate the next event number
     * Finds the maximum existing event number and adds 1
     */
    private Integer generateNextEventNumber() {
        return eventRepository.findAll().stream()
                .map(Event::getEventNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1); // Start with 1 if no events exist
    }

    /**
     * Update Event - Full Replacement (AC4 + AC15 cache invalidation)
     *
     * PUT /api/v1/events/{eventCode}
     *
     * @param eventCode Event code
     * @param request Complete event data for replacement
     * @return Updated event
     *
     * Story 1.16.2: Uses eventCode instead of UUID
     */
    @PutMapping("/{eventCode}")
    @Operation(summary = "Update Event", description = "Fully replace an existing event")
    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true),
        @CacheEvict(value = CacheConfig.ARCHIVE_EVENTS_CACHE, allEntries = true)
    })
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable String eventCode,
            @Valid @RequestBody UpdateEventRequest request) {
        log.debug("PUT /api/v1/events/{} - title: {}", eventCode, request.getTitle());

        // Find existing event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(
                    "Event not found with code: " + eventCode));

        // Track original themeImageUploadId before updates (Story 2.5.3a bug fix)
        String originalUploadId = event.getThemeImageUploadId();

        // Replace all fields
        event.setTitle(request.getTitle());

        // Validate that the new event number is not already in use by another event
        // Only update eventNumber if provided in request (null-safe check)
        if (request.getEventNumber() != null && !request.getEventNumber().equals(event.getEventNumber())) {
            Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
            if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                throw new BusinessValidationException("Event number",
                    "Event number " + request.getEventNumber() + " is already in use by event "
                    + existingEvent.get().getEventCode());
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {} via PUT, regenerated eventCode to: {}",
                request.getEventNumber(), newEventCode);
        }
        event.setDate(parseDate(request.getDate()));
        event.setRegistrationDeadline(request.getRegistrationDeadline() != null
            ? parseDate(request.getRegistrationDeadline()) : null);
        event.setVenueName(request.getVenueName());
        event.setVenueAddress(request.getVenueAddress());
        event.setVenueCapacity(request.getVenueCapacity());
        event.setOrganizerUsername(request.getOrganizerUsername());
        event.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        event.setPublishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null);
        event.setMetadata(request.getMetadata());
        event.setDescription(request.getDescription());
        if (request.getEventType() != null) {
            event.setEventType(ch.batbern.events.dto.generated.EventType.fromValue(request.getEventType()));
        }
        if (request.getWorkflowState() != null) {
            event.setWorkflowState(request.getWorkflowState());
        }
        event.setRegistrationCapacity(request.getRegistrationCapacity()); // null = unlimited

        // Set theme image upload ID before save (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

        // Associate theme image if new uploadId provided (Story 2.5.3a)
        // Must be done after save to ensure event has persisted eventCode
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            // Only associate if uploadId has changed (avoid re-associating already associated images)
            boolean uploadIdChanged = !request.getThemeImageUploadId().equals(originalUploadId);
            if (uploadIdChanged) {
                log.info("Theme image uploadId changed from {} to {} for event {}, associating new image",
                        originalUploadId, request.getThemeImageUploadId(), eventCode);
                associateThemeImage(updatedEvent, request.getThemeImageUploadId());
                // Reload event to get updated themeImageUrl from database
                updatedEvent = eventRepository.findByEventCode(updatedEvent.getEventCode())
                        .orElseThrow(() -> new EventNotFoundException("Event not found after theme image association"));

                // Manually evict cache after theme image association (Story 2.5.3a)
                // The @CacheEvict on the method runs before associateThemeImage, so we need to evict again
                Cache cache = cacheManager.getCache(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
                if (cache != null) {
                    cache.clear();
                    log.debug("Cache cleared after theme image association for event: {}", eventCode);
                }
            } else {
                log.debug("Theme image uploadId unchanged ({}), skipping re-association for event {}",
                        request.getThemeImageUploadId(), eventCode);
            }
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUsername();
            EventUpdatedEvent domainEvent = new EventUpdatedEvent(
                    updatedEvent.getId(),           // Internal UUID
                    updatedEvent.getEventCode(),    // Public business identifier
                    updatedEvent.getTitle(),
                    updatedEvent.getEventNumber(),
                    updatedEvent.getDate(),
                    updatedEvent.getRegistrationDeadline(),
                    updatedEvent.getVenueName(),
                    updatedEvent.getVenueAddress(),
                    updatedEvent.getVenueCapacity(),
                    updatedEvent.getWorkflowState() != null ? updatedEvent.getWorkflowState().name() : null,
                    updatedEvent.getOrganizerUsername(),
                    updatedEvent.getDescription(),
                    updatedEvent.getCurrentAttendeeCount(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventUpdatedEvent for event: {}", updatedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventUpdatedEvent for event {}: {}",
                updatedEvent.getEventCode(), e.getMessage());
            // Continue - event update succeeded, publishing failure is non-critical
        }

        // Build response using EventMapper (BAT-91 Phase 3)
        EventResponse response = eventMapper.toDto(updatedEvent);
        enrichWithRegistrationCounts(response, updatedEvent.getId());
        enrichWithTeaserImages(response);

        return ResponseEntity.ok(response);
    }

    /**
     * Partial Update Event (AC5 + AC15 cache invalidation)
     *
     * PATCH /api/v1/events/{eventCode}
     *
     * @param eventCode Event code
     * @param request Partial event data (only provided fields will be updated)
     * @return Partially updated event
     *
     * Story 1.16.2: Uses eventCode instead of UUID
     */
    @PatchMapping("/{eventCode}")
    @Operation(summary = "Patch Event", description = "Partially update an existing event")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<EventResponse> patchEvent(
            @PathVariable String eventCode,
            @Valid @RequestBody PatchEventRequest request) {
        log.debug("PATCH /api/v1/events/{}", eventCode);

        // Find existing event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Track original themeImageUploadId before updates (Story 2.5.3a bug fix)
        String originalUploadId = event.getThemeImageUploadId();

        // Update only provided fields
        applyPatchUpdates(event, request);

        // Save patched event
        Event patchedEvent = eventRepository.save(event);

        // Handle theme image changes (Story 2.5.3a)
        // Check if themeImageUploadId was provided in the request (including null to clear it)
        if (request.getThemeImageUploadId() != null) {
            if (!request.getThemeImageUploadId().isBlank()) {
                // Only associate if uploadId has changed (avoid re-associating already associated images)
                boolean uploadIdChanged = !request.getThemeImageUploadId().equals(originalUploadId);
                if (uploadIdChanged) {
                    // Associate new theme image
                    log.info("Theme image uploadId changed from {} to {} for event {}, associating new image",
                            originalUploadId, request.getThemeImageUploadId(), eventCode);
                    associateThemeImage(patchedEvent, request.getThemeImageUploadId());
                    // Reload event to get updated themeImageUrl from database
                    patchedEvent = eventRepository.findByEventCode(patchedEvent.getEventCode())
                            .orElseThrow(() -> new EventNotFoundException(
                                    "Event not found after theme image association"));
                } else {
                    log.debug("Theme image uploadId unchanged ({}), skipping re-association for event {}",
                            request.getThemeImageUploadId(), eventCode);
                }
            } else {
                // Clear theme image (blank/empty string means remove)
                log.info("Clearing theme image for event: {}", eventCode);
                patchedEvent.setThemeImageUploadId(null);
                patchedEvent.setThemeImageUrl(null);
                patchedEvent = eventRepository.save(patchedEvent);
            }

            // Manually evict cache after theme image changes (Story 2.5.3a)
            // The @CacheEvict on the method runs before these operations, so we need to evict again
            Cache cache = cacheManager.getCache(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
            if (cache != null) {
                cache.clear();
                log.debug("Cache cleared after theme image changes for event: {}", eventCode);
            }
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUsername();
            EventUpdatedEvent domainEvent = new EventUpdatedEvent(
                    patchedEvent.getId(),           // Internal UUID
                    patchedEvent.getEventCode(),    // Public business identifier
                    patchedEvent.getTitle(),
                    patchedEvent.getEventNumber(),
                    patchedEvent.getDate(),
                    patchedEvent.getRegistrationDeadline(),
                    patchedEvent.getVenueName(),
                    patchedEvent.getVenueAddress(),
                    patchedEvent.getVenueCapacity(),
                    patchedEvent.getWorkflowState() != null ? patchedEvent.getWorkflowState().name() : null,
                    patchedEvent.getOrganizerUsername(),
                    patchedEvent.getDescription(),
                    patchedEvent.getCurrentAttendeeCount(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventUpdatedEvent (patch) for event: {}", patchedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventUpdatedEvent (patch) for event {}: {}",
                patchedEvent.getEventCode(), e.getMessage());
            // Continue - event patch succeeded, publishing failure is non-critical
        }

        // Build response using EventMapper (BAT-91 Phase 3)
        EventResponse response = eventMapper.toDto(patchedEvent);
        enrichWithRegistrationCounts(response, patchedEvent.getId());
        enrichWithTeaserImages(response);

        return ResponseEntity.ok(response);
    }

    /**
     * Bulk Update Events (AC14 + AC15 cache invalidation)
     *
     * PATCH /api/v1/events
     *
     * Performs batch updates on multiple events. Returns success/failure status for each update.
     *
     * @param requests List of event updates to apply
     * @return Batch operation results with successful and failed updates
     */
    @PatchMapping
    @Operation(
            summary = "Batch Update Events",
            description = "Update multiple events in a single request. Returns partial success if some updates fail."
    )
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> batchUpdateEvents(
            @Valid @RequestBody List<BatchUpdateRequest> requests) {
        log.debug("PATCH /api/v1/events - batch update {} events", requests.size());

        List<Map<String, Object>> successful = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        // Process each update request
        for (BatchUpdateRequest request : requests) {
            try {
                // Find existing event by eventCode (Story 1.16.2)
                Event event = eventRepository.findByEventCode(request.getEventCode())
                        .orElseThrow(() -> new EventNotFoundException(
                            "Event not found with code: " + request.getEventCode()));

                // Apply updates (similar to PATCH)
                if (request.getTitle() != null) {
                    event.setTitle(request.getTitle());
                }
                if (request.getDate() != null) {
                    event.setDate(parseDate(request.getDate()));
                }
                if (request.getDescription() != null) {
                    event.setDescription(request.getDescription());
                }

                // Save updated event
                Event updatedEvent = eventRepository.save(event);

                // Add to successful list (Story 1.16.2: use eventCode)
                Map<String, Object> successResult = new HashMap<>();
                successResult.put("eventCode", updatedEvent.getEventCode());
                successResult.put("status", "updated");
                successful.add(successResult);

            } catch (EventNotFoundException e) {
                // Add to failed list
                Map<String, Object> failureResult = new HashMap<>();
                failureResult.put("eventCode", request.getEventCode());
                failureResult.put("error", "Event not found");
                failed.add(failureResult);
            } catch (Exception e) {
                // Add to failed list
                Map<String, Object> failureResult = new HashMap<>();
                failureResult.put("eventCode", request.getEventCode());
                failureResult.put("error", e.getMessage());
                failed.add(failureResult);
            }
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("successful", successful);
        response.put("failed", failed);

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", requests.size());
        summary.put("successful", successful.size());
        summary.put("failed", failed.size());
        response.put("summary", summary);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete Event (AC6 + AC15 cache invalidation)
     * Story 1.16.2: Updated to use eventCode instead of UUID
     *
     * DELETE /api/v1/events/{eventCode}
     */
    @DeleteMapping("/{eventCode}")
    @Operation(summary = "Delete Event", description = "Delete an event by eventCode (Story 1.16.2)")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> deleteEvent(@PathVariable String eventCode) {
        log.debug("DELETE /api/v1/events/{}", eventCode);

        // Find event by eventCode
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Delete event
        eventRepository.deleteById(event.getId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Publish Event (AC7 + AC15 cache invalidation)
     * Story 1.16.2: Updated to use eventCode instead of UUID
     *
     * POST /api/v1/events/{eventCode}/publish
     *
     * Validates event meets publication requirements and changes status to "published"
     */
    @PostMapping("/{eventCode}/publish")
    @Operation(summary = "Publish Event", description = "Publish an event after validation (Story 1.16.2)")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<EventResponse> publishEvent(@PathVariable String eventCode) {
        log.debug("POST /api/v1/events/{}/publish", eventCode);

        // Find event by eventCode
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Validate event can be published
        validateForPublishing(event);

        // Store previous workflow state for domain event
        EventWorkflowState previousWorkflowState = event.getWorkflowState();

        // Transition to AGENDA_PUBLISHED workflow state
        String userId = securityContextHelper.getCurrentUsername();
        Event publishedEvent = eventWorkflowStateMachine.transitionToState(
                eventCode,
                EventWorkflowState.AGENDA_PUBLISHED,
                userId
        );

        // Set published timestamp
        publishedEvent.setPublishedAt(Instant.now());
        publishedEvent = eventRepository.save(publishedEvent);

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            EventPublishedEvent domainEvent = new EventPublishedEvent(
                    publishedEvent.getId(),           // Internal UUID
                    publishedEvent.getEventCode(),    // Public business identifier
                    publishedEvent.getTitle(),
                    publishedEvent.getEventNumber(),
                    publishedEvent.getDate(),
                    publishedEvent.getPublishedAt(),
                    previousWorkflowState != null ? previousWorkflowState.name() : null,
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventPublishedEvent for event: {}", publishedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventPublishedEvent for event {}: {}",
                publishedEvent.getEventCode(), e.getMessage());
            // Continue - event publishing succeeded, domain event publishing failure is non-critical
        }

        // Build response using EventMapper (BAT-91 Phase 3)
        EventResponse response = eventMapper.toDto(publishedEvent);
        enrichWithRegistrationCounts(response, publishedEvent.getId());
        enrichWithTeaserImages(response);

        return ResponseEntity.ok(response);
    }

    /**
     * Validate event meets requirements for publishing
     *
     * @param event Event to validate
     * @throws BusinessValidationException if validation fails
     */
    private void validateForPublishing(Event event) {
        // Check required fields for publishing
        if (event.getDate() == null) {
            throw new BusinessValidationException("Event date is required for publishing");
        }
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new BusinessValidationException("Event title is required for publishing");
        }
        // Check that event date is in the future (unless event is archived)
        // Historical events can have past dates when imported with archived status
        if (event.getWorkflowState() != EventWorkflowState.ARCHIVED && event.getDate().isBefore(Instant.now())) {
            throw new BusinessValidationException("Event date must be in the future");
        }
    }

    /**
     * Parse ISO 8601 date string to Instant
     *
     * @param dateString ISO 8601 formatted date string
     * @return Instant object
     */
    private Instant parseDate(String dateString) {
        return Instant.parse(dateString);
    }

    /**
     * Apply patch updates to an event entity
     * Only updates fields that are present in the request
     *
     * @param event The event to update
     * @param request The patch request with optional fields
     */
    /**
     * Enrich an EventResponse with live registration counts (Story 10.11).
     * Adds confirmedCount, waitlistCount, and spotsRemaining (null when capacity is unlimited).
     * Cost: 2 DB queries per call — acceptable for single-event endpoints.
     */
    private void enrichWithRegistrationCounts(EventResponse response, java.util.UUID eventId) {
        long confirmed = registrationRepository.countByEventIdAndStatusIn(
                eventId, java.util.List.of("registered", "confirmed"));
        long waitlisted = registrationRepository.countByEventIdAndStatus(eventId, "waitlist");
        response.setConfirmedCount((int) confirmed);
        response.setWaitlistCount((int) waitlisted);
        if (response.getRegistrationCapacity() != null) {
            response.setSpotsRemaining((int) (response.getRegistrationCapacity() - confirmed));
        }
    }

    /**
     * Populate teaserImages list on EventResponse from EventTeaserImageService.
     * Story 10.22: Event Teaser Images — AC4
     */
    private void enrichWithTeaserImages(EventResponse response) {
        if (response.getEventCode() != null) {
            response.setTeaserImages(eventTeaserImageService.listByEventCode(response.getEventCode()));
        }
    }

    private void applyPatchUpdates(Event event, PatchEventRequest request) {
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getEventNumber() != null) {
            // Validate that the new event number is not already in use by another event
            if (!request.getEventNumber().equals(event.getEventNumber())) {
                Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
                if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                    throw new BusinessValidationException("Event number",
                        "Event number " + request.getEventNumber() + " is already in use by event "
                        + existingEvent.get().getEventCode());
                }

                // Validate that the generated event code is not already in use by another event
                String newEventCode = "BATbern" + request.getEventNumber();
                Optional<Event> existingCodeEvent = eventRepository.findByEventCode(newEventCode);
                if (existingCodeEvent.isPresent() && !existingCodeEvent.get().getId().equals(event.getId())) {
                    throw new BusinessValidationException("Event code",
                        "Generated event code " + newEventCode + " is already in use by another event");
                }
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {}, regenerated eventCode to: {}",
                request.getEventNumber(), newEventCode);
        }
        if (request.getDate() != null) {
            event.setDate(parseDate(request.getDate()));
        }
        if (request.getRegistrationDeadline() != null) {
            event.setRegistrationDeadline(parseDate(request.getRegistrationDeadline()));
        }
        if (request.getVenueName() != null) {
            event.setVenueName(request.getVenueName());
        }
        if (request.getVenueAddress() != null) {
            event.setVenueAddress(request.getVenueAddress());
        }
        if (request.getVenueCapacity() != null) {
            event.setVenueCapacity(request.getVenueCapacity());
        }
        if (request.getOrganizerUsername() != null) {
            event.setOrganizerUsername(request.getOrganizerUsername());
        }
        if (request.getCurrentAttendeeCount() != null) {
            event.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        }
        if (request.getPublishedAt() != null) {
            event.setPublishedAt(parseDate(request.getPublishedAt()));
        }
        if (request.getMetadata() != null) {
            event.setMetadata(request.getMetadata());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getEventType() != null) {
            event.setEventType(ch.batbern.events.dto.generated.EventType.fromValue(request.getEventType()));
        }
        // Story 2.5.3a: Set theme image upload ID (association happens after save in PATCH handler)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }
        // Update workflowState if provided
        if (request.getWorkflowState() != null) {
            event.setWorkflowState(request.getWorkflowState());
        }
        // Story 10.4: Set topicCode directly via PATCH (blob selector accept flow)
        if (request.getTopicCode() != null) {
            event.setTopicCode(request.getTopicCode());
        }
        // Story 10.4: Record selection note from blob selector session
        if (request.getTopicSelectionNote() != null) {
            event.setTopicSelectionNote(request.getTopicSelectionNote());
        }
        // Story 10.11: only set if explicitly provided (non-null); to clear use PUT with null
        if (request.getRegistrationCapacity() != null) {
            event.setRegistrationCapacity(request.getRegistrationCapacity());
        }
    }

    /**
     * Manually promote a waitlisted registration to registered (Story 10.11 — AC3, AC5)
     *
     * POST /api/v1/events/{eventCode}/registrations/{registrationCode}/promote
     *
     * Organizer-only: promotes the specified waitlisted registration to status=registered.
     * Returns 204 on success, 404 if registration not found, 409 if not on waitlist.
     */
    @PostMapping("/{eventCode}/registrations/{registrationCode}/promote")
    @Operation(summary = "Promote Waitlisted Registration",
            description = "Organizer-only: promote a waitlisted registration to registered status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> promoteFromWaitlist(
            @PathVariable String eventCode,
            @PathVariable String registrationCode) {
        log.debug("POST /api/v1/events/{}/registrations/{}/promote", eventCode, registrationCode);
        // M2 fix: validate registration belongs to this event before promoting
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        Registration reg = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new RegistrationNotFoundException(registrationCode));
        if (!event.getId().equals(reg.getEventId())) {
            // Don't reveal existence of cross-event registrations — treat as not found
            throw new RegistrationNotFoundException(registrationCode);
        }
        waitlistPromotionService.manuallyPromote(registrationCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get Event Analytics (AC13)
     *
     * GET /api/v1/events/{id}/analytics?metrics=attendance,registrations,engagement&timeframe=start,end
     *
     * Examples:
     * - All metrics: GET /api/v1/events/123/analytics?metrics=attendance,registrations,engagement
     * - With timeframe: GET /api/v1/events/123/analytics?metrics=registrations&timeframe=
     *   2025-04-01T00:00:00Z,2025-05-31T23:59:59Z
     *
     * @param id Event ID
     * @param metrics Comma-separated list of metrics to return
     * @param timeframe Optional timeframe as "startTime,endTime" in ISO-8601 format
     * @return Analytics data for the event
     */
    @GetMapping("/{eventCode}/analytics")
    @Operation(
            summary = "Get Event Analytics",
            description = "Retrieve analytics data for an event with optional metrics and timeframe "
                + "filtering (Story 1.16.2)"
    )
    public ResponseEntity<Map<String, Object>> getEventAnalytics(
            @PathVariable String eventCode,
            @Parameter(description = "Comma-separated list of metrics (attendance, registrations, engagement)")
            @RequestParam(required = false, defaultValue = "attendance,registrations,engagement") String metrics,
            @Parameter(description = "Timeframe as 'startTime,endTime' in ISO-8601 format")
            @RequestParam(required = false) String timeframe
    ) {
        log.debug("GET /api/v1/events/{}/analytics - metrics: {}, timeframe: {}", eventCode, metrics, timeframe);

        // Find event by eventCode to get UUID for analytics service
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Generate analytics using EventAnalyticsService (still uses internal UUID)
        Map<String, Object> analytics = eventAnalyticsService.generateAnalytics(event.getId(), metrics, timeframe);

        // Story 1.16.2: Replace eventId (UUID) with eventCode in response
        analytics.remove("eventId");
        analytics.put("eventCode", eventCode);

        return ResponseEntity.ok(analytics);
    }

    // ================================
    // Registration Endpoints (Story 2.2a: Anonymous Event Registration)
    // ================================

    /**
     * Get My Registration Status - Story 10.10 (AC1)
     *
     * GET /api/v1/events/{eventCode}/my-registration
     *
     * Returns the authenticated user's registration status for the specified event.
     * ADR-003: Uses eventCode (meaningful ID). ADR-004: Minimal response (no user profile fields).
     *
     * Authentication: Required. @PreAuthorize("isAuthenticated()") provides method-level guard
     * (production URL-level security via SecurityConfig.anyRequest().authenticated() also applies).
     *
     * Always returns 200. Use the {@code registered} boolean in the response to determine
     * if the user has a registration record — avoids browser console 404 noise.
     *
     * @param eventCode Event code to check registration for
     * @return 200 always — registered=true with full data if found, registered=false if not
     */
    @GetMapping("/{eventCode}/my-registration")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get my registration status for an event",
            description = "Returns the authenticated user's registration status. "
                    + "Always 200 — use `registered` field to check enrollment. Requires authentication."
    )
    public ResponseEntity<MyRegistrationResponse> getMyRegistration(
            @PathVariable String eventCode) {
        log.debug("GET /api/v1/events/{}/my-registration", eventCode);

        // ADR-001: Use custom:username claim (not sub UUID) — same pattern as all other endpoints
        String username = securityContextHelper.getCurrentUsername();

        return ResponseEntity.ok(registrationService.getMyRegistration(eventCode, username));
    }

    /**
     * Create Event Registration (Anonymous) - Story 2.2a (ADR-005)
     *
     * POST /api/v1/events/{eventCode}/registrations
     *
     * Creates a new registration for an event with anonymous user support.
     * Users can register without creating a Cognito account (anonymous users).
     *
     * @param eventCode Event code to register for
     * @param request Registration request with attendee details
     * @return Created registration with enriched user data
     */
    @PostMapping("/{eventCode}/registrations")
    @Operation(
            summary = "Create Event Registration (Anonymous)",
            description = "Register for an event without requiring authentication. "
                    + "Creates anonymous user profile automatically. "
                    + "Registration starts in PENDING status. User must confirm via email link."
    )
    public ResponseEntity<CreateRegistrationResponse> createRegistration(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateRegistrationRequest request) {
        log.debug("POST /api/v1/events/{}/registrations", eventCode);

        // QA Fix (VALID-001): Check if this is a resend (existing pending registration)
        // Service returns existing registration if status is "registered" (pending)
        Instant beforeCreate = Instant.now();

        // Create registration with status=PENDING (will be confirmed via email)
        Registration registration = registrationService.createRegistration(eventCode, request);

        // QA Fix (VALID-001): Detect if this is a resend (registration existed before this call)
        boolean isResend = registration.getCreatedAt() != null
            && registration.getCreatedAt().isBefore(beforeCreate);

        // Generate confirmation token (48h validity)
        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(),
                eventCode
        );

        // Generate cancellation token (48h validity)
        String cancellationToken = confirmationTokenService.generateCancellationToken(
                registration.getId(),
                eventCode
        );

        // Fetch event for email
        ch.batbern.events.domain.Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));

        // Fetch user profile for email
        ch.batbern.events.dto.generated.users.UserResponse userProfile =
                userApiClient.getUserByUsername(registration.getAttendeeUsername());

        // Story 10.11: Waitlist registrations get waitlist-confirmation email (sent by service).
        // Regular registrations get normal confirmation email with JWT tokens.
        if (!"waitlist".equals(registration.getStatus())) {
            // Send confirmation email with JWT tokens (Story 4.1.5c + Anonymous Cancellation)
            // Email includes:
            //   - Confirmation link: https://batbern.ch/events/{eventCode}/confirm-registration?token={confirmationToken}
            //   - Cancellation link: https://batbern.ch/cancel-registration?token={cancellationToken}
            //   - Deregistration link: https://batbern.ch/deregister?token={deregistrationToken} (Story 10.12)
            String deregistrationUrl = registration.getDeregistrationToken() != null
                    ? appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                    : null;
            registrationEmailService.sendRegistrationConfirmation(
                    registration,
                    userProfile,
                    event,
                    confirmationToken,
                    cancellationToken,
                    deregistrationUrl,
                    java.util.Locale.GERMAN // Default to German for BATbern events
            );

            log.info("Confirmation and cancellation tokens generated, email queued for registration {}: "
                            + "confirm={}, cancel={}",
                    registration.getId(),
                    confirmationToken.substring(0, 20) + "...",
                    cancellationToken.substring(0, 20) + "...");
        }

        // QA Fix (VALID-001): Return different status for resend vs new registration
        if (isResend) {
            if ("waitlist".equals(registration.getStatus())) {
                // Story 10.11 (T10.4): Duplicate waitlist registration — return 200 OK.
                // Waitlist-confirmation email already resent by RegistrationService.
                log.info("Duplicate waitlist registration for event: {} by user: {}, returning existing waitlist entry",
                        eventCode, registration.getAttendeeUsername());
                CreateRegistrationResponse response = CreateRegistrationResponse.builder()
                        .message("You are already on the waitlist for this event."
                                + " A new confirmation email has been sent.")
                        .email(request.getEmail())
                        .build();
                return ResponseEntity.ok(response);
            }
            // For other statuses (e.g., "registered"), return 409
            log.info("Duplicate registration attempt for event: {} by user: {} (status: {})",
                    eventCode, registration.getAttendeeUsername(), registration.getStatus());
            throw new IllegalStateException("User " + registration.getAttendeeUsername()
                + " is already registered for event " + eventCode);
        }

        // Return minimal response (no sensitive data)
        CreateRegistrationResponse response = CreateRegistrationResponse.builder()
                .message("Registration submitted successfully. Check your email to confirm.")
                .email(request.getEmail())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List Event Registrations - Story 2.2a, Story 3.3 (Pagination)
     *
     * GET /api/v1/events/{eventCode}/registrations?page=1&limit=25
     *
     * @param eventCode Event code to list registrations for
     * @param page Page number (1-indexed, default: 1)
     * @param limit Items per page (default: 25, max: 100)
     * @return Paginated list of registrations enriched with user data
     */
    @GetMapping("/{eventCode}/registrations")
    @Operation(
            summary = "List Event Registrations",
            description = "Retrieve registrations for a specific event with "
                    + "enriched user data, filtering, and pagination"
    )
    public ResponseEntity<PaginatedResponse<RegistrationResponse>> listRegistrations(
            @PathVariable String eventCode,
            @Parameter(description = "Page number (1-indexed, default: 1)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Items per page (default: 25, max: 100)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Filter by registration status (can specify multiple)")
            @RequestParam(required = false) List<String> status,
            @Parameter(description = "Search in attendee name or email")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by company ID")
            @RequestParam(required = false) String companyId
    ) {
        log.debug("GET /api/v1/events/{}/registrations - page: {}, limit: {}, status: {}, search: {}, companyId: {}",
                eventCode, page, limit, status, search, companyId);

        // Default pagination values
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // Convert 1-indexed to 0-indexed
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 100) : 25; // Default 25, max 100

        // Find event to get UUID
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Build specification for database-level filters
        // Performance Optimization: All filters now applied at database level using denormalized fields
        org.springframework.data.jpa.domain.Specification<Registration> spec =
                ch.batbern.events.specification.RegistrationSpecification.buildSpecification(
                        event.getId(),
                        status,
                        search,      // Now searches denormalized firstName/lastName/email fields
                        companyId    // Now filters using denormalized companyId field
                );

        // Create Pageable with sorting by last name, first name
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageNumber,
                pageSize,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("attendeeLastName"),
                        org.springframework.data.domain.Sort.Order.asc("attendeeFirstName")
                )
        );

        // Fetch filtered and paginated registrations from database (NOT all registrations!)
        org.springframework.data.domain.Page<Registration> registrationsPage =
                registrationRepository.findAll(spec, pageable);

        // Enrich only the current page of registrations with user data
        List<RegistrationResponse> paginatedResponses = registrationsPage.getContent().stream()
                .peek(reg -> reg.setEventCode(eventCode)) // Set transient field
                .map(registrationService::enrichRegistrationWithUserData)
                .collect(Collectors.toList());

        // Build pagination metadata from Page object
        ch.batbern.shared.api.PaginationMetadata paginationMetadata =
                ch.batbern.shared.api.PaginationMetadata.builder()
                        .page(pageNumber + 1) // Convert back to 1-indexed for response
                        .limit(pageSize)
                        .totalItems(registrationsPage.getTotalElements())
                        .totalPages(registrationsPage.getTotalPages())
                        .hasNext(registrationsPage.hasNext())
                        .hasPrev(registrationsPage.hasPrevious())
                        .build();

        // Build paginated response
        PaginatedResponse<RegistrationResponse> response = PaginatedResponse.<RegistrationResponse>builder()
                .data(paginatedResponses)
                .pagination(paginationMetadata)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * List User Registrations - Story BAT-15
     *
     * GET /api/v1/events/registrations?attendeeUsername={username}
     *
     * @param attendeeUsername Username to list registrations for
     * @return List of registrations enriched with event data
     */
    @GetMapping("/registrations")
    @Operation(
            summary = "List User Registrations",
            description = "Retrieve all registrations for a specific user across all events"
    )
    public ResponseEntity<List<RegistrationResponse>> listUserRegistrations(
            @RequestParam String attendeeUsername) {
        log.debug("GET /api/v1/events/registrations?attendeeUsername={}", attendeeUsername);

        // Fetch registrations for this user
        List<Registration> registrations = registrationRepository.findByAttendeeUsername(attendeeUsername);

        // Fetch all events in one query to avoid N+1 (collect unique event IDs)
        Set<UUID> eventIds = registrations.stream()
                .map(Registration::getEventId)
                .collect(Collectors.toSet());

        Map<UUID, Event> eventsMap = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        // Enrich each registration with event data
        List<RegistrationResponse> responses = registrations.stream()
                .map(reg -> {
                    // Get event from map (avoids N+1)
                    Event event = eventsMap.get(reg.getEventId());
                    if (event != null) {
                        reg.setEventCode(event.getEventCode()); // Set transient field
                    }

                    // Enrich with user data
                    RegistrationResponse response = registrationService.enrichRegistrationWithUserData(reg);

                    // Add event details to response
                    if (event != null) {
                        response.setEventTitle(event.getTitle());
                        response.setEventDate(event.getDate() != null
                                ? event.getDate().toString() : null);
                    }

                    return response;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Create Batch Registrations - Story BAT-14
     *
     * POST /api/v1/events/batch_registrations
     *
     * Creates multiple event registrations for a single participant in a single transaction.
     * Designed for historical data migration where participants attended multiple events.
     *
     * Features:
     * - Creates or retrieves user by email (anonymous users with cognitoSync=false)
     * - Creates registrations for all specified events
     * - Idempotent: Skips duplicate registrations without error
     * - Partial success: Some registrations may succeed while others fail
     * - Transactional: All-or-nothing for database operations
     *
     * Authorization: Requires ORGANIZER role
     *
     * @param request Batch registration request with participant data and event list
     * @return Batch registration response with detailed success/failure information
     */
    @PostMapping("/batch_registrations")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Create Batch Event Registrations",
            description = "Create multiple event registrations for a single participant. "
                    + "Designed for historical data migration. Returns partial success details. "
                    + "Requires ORGANIZER role."
    )
    public ResponseEntity<BatchRegistrationResponse> createBatchRegistrations(
            @Valid @RequestBody BatchRegistrationRequest request) {
        log.debug("POST /api/v1/events/batch_registrations - Participant: {}, Events: {}",
                request.getParticipantEmail(), request.getRegistrations().size());

        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        log.info("Batch registration completed - User: {}, Total: {}, Successful: {}, Failed: {}",
                response.getUsername(),
                response.getTotalRegistrations(),
                response.getSuccessfulRegistrations(),
                response.getFailedRegistrations().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get Registration Detail - Story 2.2a
     *
     * GET /api/v1/events/{eventCode}/registrations/{registrationCode}
     *
     * @param eventCode Event code
     * @param registrationCode Registration code (e.g., BATbern142-reg-A3X9K2)
     * @return Registration detail enriched with user data
     */
    @GetMapping("/{eventCode}/registrations/{registrationCode}")
    @Operation(
            summary = "Get Registration Detail",
            description = "Retrieve a specific registration by registration code with enriched user data"
    )
    public ResponseEntity<RegistrationResponse> getRegistration(
            @PathVariable String eventCode,
            @PathVariable String registrationCode) {
        log.debug("GET /api/v1/events/{}/registrations/{}", eventCode, registrationCode);

        // Find registration by code
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationCode));

        // Verify registration belongs to this event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        if (!registration.getEventId().equals(event.getId())) {
            throw new NoSuchElementException("Registration " + registrationCode
                + " does not belong to event " + eventCode);
        }

        // Set transient field and enrich
        registration.setEventCode(eventCode);
        RegistrationResponse response = registrationService.enrichRegistrationWithUserData(registration);

        return ResponseEntity.ok(response);
    }

    /**
     * Confirm Event Registration - Email Confirmation Flow
     *
     * POST /api/v1/events/{eventCode}/registrations/confirm?token={JWT}
     *
     * Confirms a pending registration using the JWT token from the confirmation email.
     * Updates registration status from PENDING to CONFIRMED.
     * Token is valid for 48 hours and event code in URL must match event code in token.
     *
     * @param eventCode Event code to confirm registration for
     * @param token JWT confirmation token from email
     * @return Success message
     */
    @PostMapping("/{eventCode}/registrations/confirm")
    @Operation(
            summary = "Confirm Registration",
            description = "Confirm a pending registration using the token from the confirmation "
                + "email. Token is valid for 48 hours and event code must match."
    )
    public ResponseEntity<Map<String, String>> confirmRegistration(
            @PathVariable String eventCode,
            @RequestParam("token") String token) {
        log.debug("POST /api/v1/events/{}/registrations/confirm", eventCode);

        try {
            // Validate token
            io.jsonwebtoken.Claims claims = confirmationTokenService.validateConfirmationToken(token);

            // Extract event code from token and verify it matches URL
            String tokenEventCode = confirmationTokenService.getEventCode(claims);
            if (!eventCode.equals(tokenEventCode)) {
                throw new IllegalArgumentException("Event code mismatch: URL has " + eventCode
                    + " but token has " + tokenEventCode);
            }

            // Extract registration ID
            UUID registrationId = confirmationTokenService.getRegistrationId(claims);

            // Find registration
            Registration registration = registrationRepository.findById(registrationId)
                    .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationId));

            // Fetch the event to verify registration belongs to it and to get event code
            Event event = eventRepository.findById(registration.getEventId())
                    .orElseThrow(() -> new NoSuchElementException(
                            "Event not found for registration: " + registrationId));

            // Verify registration belongs to this event
            if (!event.getEventCode().equals(eventCode)) {
                throw new IllegalArgumentException("Registration does not belong to event: " + eventCode);
            }

            // Check if already confirmed
            if ("confirmed".equalsIgnoreCase(registration.getStatus())) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Registration already confirmed");
                response.put("status", "CONFIRMED");
                return ResponseEntity.ok(response);
            }

            // Update status to confirmed (Story 4.1.5c: registered → confirmed)
            registration.setStatus("confirmed"); // Lowercase per DB constraint
            registration.setUpdatedAt(Instant.now());
            registrationRepository.save(registration);

            log.info("Registration confirmed: {} for event: {}", registrationId, eventCode);

            // Return success response
            Map<String, String> response = new HashMap<>();
            response.put("message", "Registration confirmed successfully!");
            response.put("status", "CONFIRMED");

            return ResponseEntity.ok(response);

        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Invalid confirmation token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid or expired confirmation token");
        }
    }

    /**
     * Cancel Event Registration - Email Cancellation Flow (Story 4.1.5d)
     *
     * POST /api/v1/events/{eventCode}/registrations/cancel?token={JWT}
     *
     * Cancels a registration using the JWT token from the cancellation email.
     * Deletes the registration record from the database.
     * Token is valid for 48 hours.
     *
     * @param eventCode Event code (for URL consistency)
     * @param token JWT cancellation token from email
     * @return Success message
     */
    @PostMapping("/{eventCode}/registrations/cancel")
    @Operation(
            summary = "Cancel Registration",
            description = "Cancel a registration using the token from the cancellation email. "
                + "Token is valid for 48 hours. Registration will be permanently deleted."
    )
    public ResponseEntity<Map<String, String>> cancelRegistration(
            @PathVariable String eventCode,
            @RequestParam("token") String token) {
        log.debug("POST /api/v1/events/{}/registrations/cancel", eventCode);

        try {
            // Validate token
            io.jsonwebtoken.Claims claims = confirmationTokenService.validateCancellationToken(token);

            // Extract registration ID and event code from token
            UUID registrationId = confirmationTokenService.getRegistrationId(claims);
            String tokenEventCode = confirmationTokenService.getEventCode(claims);

            // Verify event code in URL matches token
            if (!eventCode.equals(tokenEventCode)) {
                throw new IllegalArgumentException(
                    "Event code in URL does not match token: " + eventCode);
            }

            // Find registration
            Registration registration = registrationRepository.findById(registrationId)
                    .orElseThrow(() -> new NoSuchElementException(
                        "Registration not found: " + registrationId));

            // Verify registration belongs to the event
            Event event = eventRepository.findById(registration.getEventId())
                    .orElseThrow(() -> new NoSuchElementException(
                        "Event not found for registration: " + registrationId));

            if (!event.getEventCode().equals(eventCode)) {
                throw new IllegalArgumentException(
                    "Registration does not belong to event: " + eventCode);
            }

            // Story 10.12: Soft-cancel (status = "cancelled") instead of hard-delete.
            // Triggers waitlist promotion via cancelRegistration().
            registrationService.cancelRegistration(registration);

            log.info("Registration cancelled successfully: registrationId={}, eventCode={}",
                    registrationId, eventCode);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Registration cancelled successfully");
            response.put("status", "CANCELLED");
            return ResponseEntity.ok(response);

        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Invalid or expired cancellation token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid or expired cancellation token");
        }
    }

    /**
     * Update Event Registration (Partial Update)
     *
     * PATCH /api/v1/events/{eventCode}/registrations/{registrationCode}
     *
     * Updates specific fields of a registration (e.g., status)
     *
     * @param eventCode Event code
     * @param registrationCode Registration code
     * @param updates Map of fields to update
     * @return Updated registration with enriched user data
     */
    @PatchMapping("/{eventCode}/registrations/{registrationCode}")
    @Operation(
            summary = "Update Event Registration",
            description = "Partially update a registration (e.g., change status)"
    )
    public ResponseEntity<RegistrationResponse> updateRegistration(
            @PathVariable String eventCode,
            @PathVariable String registrationCode,
            @RequestBody Map<String, Object> updates) {
        log.debug("PATCH /api/v1/events/{}/registrations/{}", eventCode, registrationCode);

        // Find registration
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationCode));

        // Verify registration belongs to this event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        if (!registration.getEventId().equals(event.getId())) {
            throw new NoSuchElementException("Registration " + registrationCode
                    + " does not belong to event " + eventCode);
        }

        // Apply updates
        boolean becomingCancelled = false;
        if (updates.containsKey("status")) {
            String newStatus = (String) updates.get("status");
            String previousStatus = registration.getStatus();
            registration.setStatus(newStatus);
            // Story 10.12 (CR fix): Trigger waitlist promotion for ANY non-cancelled → cancelled
            // transition, including waitlist → cancelled. Previously missed the waitlist case.
            becomingCancelled = "cancelled".equalsIgnoreCase(newStatus)
                    && !"cancelled".equalsIgnoreCase(previousStatus);
        }

        // Save updated registration
        registration = registrationRepository.save(registration);

        // Story 10.11: Auto-promote next waitlisted attendee when a spot is freed
        if (becomingCancelled) {
            waitlistPromotionService.promoteFromWaitlist(event.getId());
        }

        // Enrich with user data
        registration.setEventCode(eventCode);
        RegistrationResponse response = registrationService.enrichRegistrationWithUserData(registration);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete Event Registration
     *
     * DELETE /api/v1/events/{eventCode}/registrations/{registrationCode}
     *
     * Deletes a registration from the event
     *
     * @param eventCode Event code
     * @param registrationCode Registration code
     * @return 204 No Content
     */
    @DeleteMapping("/{eventCode}/registrations/{registrationCode}")
    @Operation(
            summary = "Delete Event Registration",
            description = "Delete a registration from the event"
    )
    public ResponseEntity<Void> deleteRegistration(
            @PathVariable String eventCode,
            @PathVariable String registrationCode) {
        log.debug("DELETE /api/v1/events/{}/registrations/{}", eventCode, registrationCode);

        // Find registration
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationCode));

        // Verify registration belongs to this event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        if (!registration.getEventId().equals(event.getId())) {
            throw new NoSuchElementException("Registration " + registrationCode
                    + " does not belong to event " + eventCode);
        }

        // Delete registration
        registrationRepository.delete(registration);

        return ResponseEntity.noContent().build();
    }

    /**
     * Associate theme image with event
     * Story 2.5.3a: Event Theme Image Upload
     *
     * @param event Event entity to associate image with
     * @param uploadId Upload ID from three-phase upload pattern
     */
    private void associateThemeImage(Event event, String uploadId) {
        log.info("Starting theme image association for event {} with uploadId {}", event.getEventCode(), uploadId);
        try {
            // Fetch Logo to get file extension (Story 2.5.3a: preserve original file type)
            Logo logo = logoRepository.findByUploadId(uploadId)
                    .orElseThrow(() -> new RuntimeException("Logo not found for uploadId: " + uploadId));

            String fileExtension = logo.getFileExtension();
            log.info("Retrieved file extension from Logo: {}", fileExtension);

            // Generate final S3 key with correct file extension
            int year = java.time.LocalDate.now().getYear();
            String finalS3Key = String.format(
                "logos/%d/events/%s/theme-%s.%s",
                year,
                event.getEventCode(),
                uploadId,
                fileExtension
            );
            log.info("Generated final S3 key: {}", finalS3Key);

            // Call GenericLogoService to associate
            log.info("Calling GenericLogoService.associateLogoWithEntity...");
            String cloudFrontUrl = genericLogoService.associateLogoWithEntity(
                uploadId,
                "EVENT",
                event.getEventCode(),
                finalS3Key
            );
            log.info("GenericLogoService returned CloudFront URL: {}", cloudFrontUrl);

            // Update event with CloudFront URL
            event.setThemeImageUrl(cloudFrontUrl);
            log.info("Set themeImageUrl on event entity: {}", cloudFrontUrl);

            Event savedEvent = eventRepository.save(event);
            log.info("Saved event to database. themeImageUrl in saved entity: {}", savedEvent.getThemeImageUrl());

            log.info("Theme image associated successfully for event {}: {}", event.getEventCode(), cloudFrontUrl);

        } catch (Exception e) {
            log.error("FAILED to associate theme image for event {} with uploadId {}: {}",
                    event.getEventCode(), uploadId, e.getMessage(), e);
            // Don't fail event creation - theme image is optional
        }
    }

    /**
     * Select topic for event (Story 5.2 AC14-16).
     *
     * POST /api/v1/events/{eventCode}/topics
     *
     * This endpoint:
     * - Validates event and topic exist
     * - Validates event is in valid state (CREATED or TOPIC_SELECTION)
     * - Assigns topic to event
     * - Transitions event workflow state to TOPIC_SELECTION
     * - Publishes EventWorkflowTransitionEvent domain event
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param request Request body with topicCode (ADR-003 compliant, generated DTO)
     * @return TopicSelectionResponse with event and topic details
     */
    @PostMapping("/{eventCode}/topics")
    @Operation(summary = "Select topic for event",
            description = "Assign a topic to an event and transition to TOPIC_SELECTION state")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TopicSelectionResponse> selectTopicForEvent(
            @PathVariable String eventCode,
            @RequestBody @Valid SelectTopicForEventRequest request) {

        // Extract topicCode from request (ADR-003: use meaningful identifiers)
        // Validation handled by @Valid and DTO annotations (@NotNull, @Pattern)
        String topicCode = request.getTopicCode();

        // Get current user from security context
        String organizerUsername = securityContextHelper.getCurrentUsername();

        // Select topic for event (calls workflow state machine)
        // ADR-003: Pass topicCode directly instead of UUID
        // Exceptions (EventNotFoundException, TopicNotFoundException, ValidationException)
        // are handled by GlobalExceptionHandler
        Event updatedEvent = topicService.selectTopicForEvent(eventCode, topicCode, organizerUsername);

        // Build response using generated DTO (ADR-006: contract-first)
        TopicSelectionResponse response = new TopicSelectionResponse(
            updatedEvent.getEventCode(),
            topicCode,
            updatedEvent.getWorkflowState().name(),
            "Topic selected successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get speaker pool for event (Story 5.2 AC9-13).
     *
     * GET /api/v1/events/{eventCode}/speakers/pool
     *
     * Returns list of potential speakers being brainstormed for the event.
     */
    @GetMapping("/{eventCode}/speakers/pool")
    @Operation(summary = "Get speaker pool",
            description = "Get list of potential speakers in the event speaker pool")
    public ResponseEntity<java.util.List<ch.batbern.events.dto.SpeakerPoolResponse>> getSpeakerPool(
            @PathVariable String eventCode) {

        // Get speaker pool
        // Exceptions are handled by GlobalExceptionHandler:
        // - EventNotFoundException → HTTP 404
        java.util.List<ch.batbern.events.dto.SpeakerPoolResponse> speakerPool =
                speakerPoolService.getSpeakerPoolForEvent(eventCode);

        return ResponseEntity.ok(speakerPool);
    }

    /**
     * Add speaker to event speaker pool (Story 5.2 AC9-13).
     *
     * POST /api/v1/events/{eventCode}/speakers/pool
     *
     * Allows organizers to brainstorm and add potential speakers during event planning.
     */
    @PostMapping("/{eventCode}/speakers/pool")
    @Operation(summary = "Add speaker to pool",
            description = "Add a potential speaker to the event speaker pool during brainstorming phase")
    public ResponseEntity<ch.batbern.events.dto.SpeakerPoolResponse> addSpeakerToPool(
            @PathVariable String eventCode,
            @RequestBody ch.batbern.events.dto.AddSpeakerToPoolRequest request) {

        // Add speaker to pool
        // Exceptions are handled by GlobalExceptionHandler:
        // - EventNotFoundException → HTTP 404
        // - IllegalArgumentException → HTTP 400
        ch.batbern.events.dto.SpeakerPoolResponse response = speakerPoolService.addSpeakerToPool(eventCode, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Delete speaker from event speaker pool.
     *
     * DELETE /api/v1/events/{eventCode}/speakers/pool/{speakerId}
     *
     * Removes a speaker from the event speaker pool.
     */
    @DeleteMapping("/{eventCode}/speakers/pool/{speakerId}")
    @Operation(summary = "Delete speaker from pool",
            description = "Remove a speaker from the event speaker pool")
    public ResponseEntity<Void> deleteSpeakerFromPool(
            @PathVariable String eventCode,
            @PathVariable String speakerId) {

        // Delete speaker from pool
        // Exceptions are handled by GlobalExceptionHandler:
        // - EventNotFoundException → HTTP 404
        // - IllegalArgumentException → HTTP 404 (speaker not found)
        speakerPoolService.deleteSpeakerFromPool(eventCode, speakerId);

        return ResponseEntity.noContent().build();
    }

    // ================================
    // Partner Analytics Endpoints (Story 8.1: Partner Attendance Dashboard)
    // ================================

    /**
     * Get attendance summary per event for a given company.
     * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5, AC6
     *
     * GET /api/v1/events/attendance-summary?companyName={name}&fromYear={year}
     *
     * Server-to-server endpoint: called by partner-coordination-service.
     * Returns one row per event with total and company-specific attendee counts.
     * Isolation is enforced at partner-coordination-service level (PARTNER sees only own company).
     *
     * @param companyName Company identifier (ADR-003 meaningful ID)
     * @param fromYear    Earliest year to include (defaults to current year - 5)
     * @return List of attendance summaries ordered by event date descending
     */
    @GetMapping("/attendance-summary")
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Operation(
            summary = "Get attendance summary per event for a company",
            description = "Returns per-event attendee counts (total and company-specific). "
                    + "Called server-to-server by partner-coordination-service. "
                    + "Requires PARTNER or ORGANIZER role."
    )
    public ResponseEntity<List<ch.batbern.events.dto.AttendanceSummaryDTO>> getAttendanceSummary(
            @Parameter(description = "Company identifier (ADR-003 meaningful ID, e.g. 'GoogleZH')",
                       required = true)
            @RequestParam String companyName,
            @Parameter(description = "Earliest year to include (default: current year - 5)")
            @RequestParam(required = false) Integer fromYear) {

        log.debug("GET /api/v1/events/attendance-summary - companyName: {}, fromYear: {}",
                companyName, fromYear);

        int resolvedFromYear = (fromYear != null) ? fromYear : (LocalDate.now().getYear() - 5);
        Instant fromDate = LocalDate.of(resolvedFromYear, 1, 1)
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant();

        List<ch.batbern.events.dto.AttendanceSummaryDTO> result =
                registrationRepository.findAttendanceSummary(companyName, fromDate);

        return ResponseEntity.ok(result);
    }
}
