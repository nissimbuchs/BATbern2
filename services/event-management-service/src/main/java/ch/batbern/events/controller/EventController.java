package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Logo;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.BatchUpdateRequest;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.CreateRegistrationResponse;
import ch.batbern.events.dto.generated.CreateRegistrationRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.event.EventUpdatedEvent;
import ch.batbern.events.exception.BusinessValidationException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.WorkflowException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.service.EventSearchService;
import jakarta.validation.Valid;
import ch.batbern.shared.dto.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    private final LogoRepository logoRepository;
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

    /**
     * List/Search Events (AC1)
     *
     * GET /api/v1/events?filter={}&sort={}&page={}&limit={}
     *
     * Examples:
     * - List all: GET /api/v1/events
     * - Filter by status: GET /api/v1/events?filter={"status":"published"}
     * - Filter by year: GET /api/v1/events?filter={"date":{"$gte":"2025-01-01T00:00:00Z","$lt":"2026-01-01T00:00:00Z"}}
     * - Sort descending: GET /api/v1/events?sort=-date
     * - Paginate: GET /api/v1/events?page=2&limit=10
     * - Combined: GET /api/v1/events?filter={"status":"published"}&sort=-date&page=1&limit=20
     */
    @GetMapping
    @Operation(
            summary = "List/Search Events",
            description = "Retrieve events with optional filtering, sorting, and pagination. " +
                    "Uses MongoDB-style filter syntax for rich querying."
    )
    public ResponseEntity<PaginatedResponse<EventResponse>> listEvents(
            @Parameter(description = "JSON filter object (e.g., {\"status\":\"published\"})")
            @RequestParam(required = false) String filter,

            @Parameter(description = "Sort specification (e.g., -date for descending, +title for ascending)")
            @RequestParam(required = false) String sort,

            @Parameter(description = "Page number (1-indexed, default: 1)")
            @RequestParam(required = false) Integer page,

            @Parameter(description = "Items per page (default: 20, max: 100)")
            @RequestParam(required = false) Integer limit
    ) {
        log.debug("GET /api/v1/events - filter: {}, sort: {}, page: {}, limit: {}", filter, sort, page, limit);

        // Search events using EventSearchService
        PaginatedResponse<Event> result = eventSearchService.searchEvents(filter, sort, page, limit);

        // Convert entities to DTOs
        List<EventResponse> eventResponses = result.getData().stream()
                .map(EventResponse::fromEntity)
                .collect(Collectors.toList());

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
     * Caching: Results with includes are cached for 15 minutes using Caffeine in-memory cache.
     * Cache key includes both event code and include parameters to ensure correct cache hits.
     *
     * Story 1.16.2: Uses eventCode (String) instead of UUID
     */
    @GetMapping("/{eventCode}")
    @Operation(
            summary = "Get Event Detail",
            description = "Retrieve a single event by event code with optional resource expansion using ?include parameter. Cached for 15 minutes."
    )
    public ResponseEntity<Map<String, Object>> getEvent(
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
        Map<String, Object> response = null;

        if (cache != null) {
            Cache.ValueWrapper cachedValue = cache.get(cacheKey);
            if (cachedValue != null) {
                response = (Map<String, Object>) cachedValue.get();
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

            // Build response with basic event data
            response = buildBasicEventResponse(event);

            // Apply resource expansions if requested
            if (include != null && !include.trim().isEmpty()) {
                applyResourceExpansions(event, include, response);
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
     * Build basic event response without expanded resources
     * Story 1.16.2: Uses eventCode and organizerUsername instead of UUIDs
     */
    private Map<String, Object> buildBasicEventResponse(Event event) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("eventCode", event.getEventCode());
        response.put("title", event.getTitle());
        response.put("eventNumber", event.getEventNumber());
        response.put("status", event.getStatus());
        response.put("description", event.getDescription());
        response.put("date", event.getDate());
        response.put("registrationDeadline", event.getRegistrationDeadline());
        response.put("venueName", event.getVenueName());
        response.put("venueAddress", event.getVenueAddress());
        response.put("venueCapacity", event.getVenueCapacity());
        response.put("organizerUsername", event.getOrganizerUsername());
        response.put("currentAttendeeCount", event.getCurrentAttendeeCount());
        response.put("publishedAt", event.getPublishedAt());
        response.put("metadata", event.getMetadata());
        // Story 2.5.3a: Include theme image fields if present
        if (event.getThemeImageUrl() != null) {
            response.put("themeImageUrl", event.getThemeImageUrl());
        }
        if (event.getThemeImageUploadId() != null) {
            response.put("themeImageUploadId", event.getThemeImageUploadId());
        }
        return response;
    }

    /**
     * Apply requested resource expansions to the response
     *
     * @param event The event entity
     * @param include Comma-separated list of resources to include
     * @param response The response map to populate
     */
    private void applyResourceExpansions(Event event, String include, Map<String, Object> response) {
        String[] includes = include.split(",");
        for (String resource : includes) {
            String trimmed = resource.trim();
            switch (trimmed) {
                case "venue":
                    response.put("venue", expandVenue(event));
                    break;
                case "speakers":
                    response.put("speakers", expandSpeakers(event));
                    break;
                case "sessions":
                    response.put("sessions", expandSessions(event));
                    break;
                // Additional resources can be added here as needed
                default:
                    log.warn("Unknown include resource requested: {}", trimmed);
            }
        }
    }

    /**
     * Expand venue data for an event
     * TODO: Replace with actual service call when Company Management Service is available
     */
    private Map<String, Object> expandVenue(Event event) {
        // Stub implementation - will be replaced with actual service call
        Map<String, Object> venue = new java.util.HashMap<>();
        venue.put("id", "ven-001");
        venue.put("name", "Bern Convention Center");
        venue.put("capacity", 500);
        venue.put("address", "Mingerstrasse 6, 3014 Bern");
        return venue;
    }

    /**
     * Expand speakers data for an event
     * TODO: Replace with actual service call when Speaker Coordination Service is available
     */
    private java.util.List<Map<String, Object>> expandSpeakers(Event event) {
        // Stub implementation - will be replaced with actual service call
        return new java.util.ArrayList<>();
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
        // Find all sessions for this event
        List<ch.batbern.events.domain.Session> sessions = sessionRepository.findByEventId(event.getId());

        // Convert to response format
        return sessions.stream()
                .map(session -> {
                    Map<String, Object> sessionMap = new HashMap<>();
                    sessionMap.put("sessionSlug", session.getSessionSlug());
                    sessionMap.put("eventCode", event.getEventCode());
                    sessionMap.put("title", session.getTitle());
                    sessionMap.put("description", session.getDescription());
                    sessionMap.put("sessionType", session.getSessionType());
                    sessionMap.put("startTime", session.getStartTime());
                    sessionMap.put("endTime", session.getEndTime());
                    sessionMap.put("room", session.getRoom());
                    sessionMap.put("capacity", session.getCapacity());
                    sessionMap.put("language", session.getLanguage());

                    // Story 1.15a.1b: Enrich with speaker data
                    List<Map<String, Object>> speakers = expandSessionSpeakers(session);
                    sessionMap.put("speakers", speakers);

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
    private java.util.List<Map<String, Object>> expandSessionSpeakers(ch.batbern.events.domain.Session session) {
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
                            ch.batbern.events.dto.generated.users.UserResponse userProfile = userApiClient.getUserByUsername(sessionUser.getUsername());
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
     * Returns the next upcoming event with status published, registration_open, or registration_closed.
     * If multiple events match, returns the one nearest to the current date.
     * This is a public endpoint (no authentication required) used by the public website.
     *
     * @param include Comma-separated list of resources to expand
     * @return Current event or 404 if none exists
     */
    @GetMapping("/current")
    @Operation(
            summary = "Get Current Event",
            description = "Retrieve the next upcoming event (published, registration_open, or registration_closed) for the public website. No authentication required."
    )
    public ResponseEntity<Map<String, Object>> getCurrentEvent(
            @Parameter(description = "Comma-separated list of resources to include (e.g., topics,venue,speakers,sessions)")
            @RequestParam(required = false) String include
    ) {
        log.debug("GET /api/v1/events/current - include: {}", include);

        // Find the next event with status: published, registration_open, or registration_closed
        // Returns the event nearest to current date
        List<String> activeStatuses = List.of("published", "registration_open", "registration_closed");
        Event currentEvent = eventRepository
                .findFirstByStatusInOrderByDateAsc(activeStatuses)
                .orElse(null);

        if (currentEvent == null) {
            log.debug("No current event found with statuses: {}", activeStatuses);
            return ResponseEntity.notFound().build();
        }

        log.debug("Found current event: {} with status: {}", currentEvent.getEventCode(), currentEvent.getStatus());

        // Build response with basic event data
        Map<String, Object> response = buildBasicEventResponse(currentEvent);

        // Apply resource expansions if requested
        if (include != null && !include.trim().isEmpty()) {
            applyResourceExpansions(currentEvent, include, response);
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
    public ResponseEntity<Map<String, Object>> createEvent(@Valid @RequestBody CreateEventRequest request) {
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
            throw new BusinessValidationException("eventCode", "Event code already exists: " + eventCode);
        }

        // Create new event entity
        Event event = Event.builder()
                .eventCode(eventCode)
                .title(request.getTitle())
                .eventNumber(eventNumber)
                .date(parseDate(request.getDate()))
                .registrationDeadline(request.getRegistrationDeadline() != null ? parseDate(request.getRegistrationDeadline()) : null)
                .venueName(request.getVenueName())
                .venueAddress(request.getVenueAddress())
                .venueCapacity(request.getVenueCapacity())
                .status(request.getStatus() != null ? request.getStatus() : "planning")
                .organizerUsername(request.getOrganizerUsername())
                .currentAttendeeCount(request.getCurrentAttendeeCount() != null ? request.getCurrentAttendeeCount() : 0)
                .publishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null)
                .metadata(request.getMetadata())
                .description(request.getDescription())
                .themeImageUploadId(request.getThemeImageUploadId())
                .build();

        // Save event
        Event savedEvent = eventRepository.save(event);

        // Associate theme image if provided (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            associateThemeImage(savedEvent, request.getThemeImageUploadId());
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUserId();
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
                    savedEvent.getStatus(),
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

        // Build response
        Map<String, Object> response = buildBasicEventResponse(savedEvent);

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
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> updateEvent(
            @PathVariable String eventCode,
            @Valid @RequestBody UpdateEventRequest request) {
        log.debug("PUT /api/v1/events/{} - title: {}", eventCode, request.getTitle());

        // Find existing event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Replace all fields
        event.setTitle(request.getTitle());

        // Validate that the new event number is not already in use by another event
        // Only update eventNumber if provided in request (null-safe check)
        if (request.getEventNumber() != null && !request.getEventNumber().equals(event.getEventNumber())) {
            Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
            if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                throw new BusinessValidationException("Event number " + request.getEventNumber() + " is already in use by event " + existingEvent.get().getEventCode());
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {} via PUT, regenerated eventCode to: {}", request.getEventNumber(), newEventCode);
        }
        event.setDate(parseDate(request.getDate()));
        event.setRegistrationDeadline(request.getRegistrationDeadline() != null ? parseDate(request.getRegistrationDeadline()) : null);
        event.setVenueName(request.getVenueName());
        event.setVenueAddress(request.getVenueAddress());
        event.setVenueCapacity(request.getVenueCapacity());
        event.setStatus(request.getStatus());
        event.setOrganizerUsername(request.getOrganizerUsername());
        event.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        event.setPublishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null);
        event.setMetadata(request.getMetadata());
        event.setDescription(request.getDescription());

        // Set theme image upload ID before save (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

        // Associate theme image if new uploadId provided (Story 2.5.3a)
        // Must be done after save to ensure event has persisted eventCode
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
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
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUserId();
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
                    updatedEvent.getStatus(),
                    updatedEvent.getOrganizerUsername(),
                    updatedEvent.getDescription(),
                    updatedEvent.getCurrentAttendeeCount(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventUpdatedEvent for event: {}", updatedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventUpdatedEvent for event {}: {}", updatedEvent.getEventCode(), e.getMessage());
            // Continue - event update succeeded, publishing failure is non-critical
        }

        // Build response
        Map<String, Object> response = buildBasicEventResponse(updatedEvent);

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
    public ResponseEntity<Map<String, Object>> patchEvent(
            @PathVariable String eventCode,
            @Valid @RequestBody PatchEventRequest request) {
        log.debug("PATCH /api/v1/events/{}", eventCode);

        // Find existing event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Update only provided fields
        applyPatchUpdates(event, request);

        // Save patched event
        Event patchedEvent = eventRepository.save(event);

        // Handle theme image changes (Story 2.5.3a)
        // Check if themeImageUploadId was provided in the request (including null to clear it)
        if (request.getThemeImageUploadId() != null) {
            if (!request.getThemeImageUploadId().isBlank()) {
                // Associate new theme image
                associateThemeImage(patchedEvent, request.getThemeImageUploadId());
                // Reload event to get updated themeImageUrl from database
                patchedEvent = eventRepository.findByEventCode(patchedEvent.getEventCode())
                        .orElseThrow(() -> new EventNotFoundException("Event not found after theme image association"));
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
            String userId = securityContextHelper.getCurrentUserId();
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
                    patchedEvent.getStatus(),
                    patchedEvent.getOrganizerUsername(),
                    patchedEvent.getDescription(),
                    patchedEvent.getCurrentAttendeeCount(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventUpdatedEvent (patch) for event: {}", patchedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventUpdatedEvent (patch) for event {}: {}", patchedEvent.getEventCode(), e.getMessage());
            // Continue - event patch succeeded, publishing failure is non-critical
        }

        // Build response
        Map<String, Object> response = buildBasicEventResponse(patchedEvent);

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
                        .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + request.getEventCode()));

                // Apply updates (similar to PATCH)
                if (request.getTitle() != null) {
                    event.setTitle(request.getTitle());
                }
                if (request.getDate() != null) {
                    event.setDate(parseDate(request.getDate()));
                }
                if (request.getStatus() != null) {
                    event.setStatus(request.getStatus());
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
    public ResponseEntity<Map<String, Object>> publishEvent(@PathVariable String eventCode) {
        log.debug("POST /api/v1/events/{}/publish", eventCode);

        // Find event by eventCode
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Validate event can be published
        validateForPublishing(event);

        // Store previous status for domain event
        String previousStatus = event.getStatus();

        // Change status to published
        event.setStatus("published");
        event.setPublishedAt(Instant.now());

        // Save updated event
        Event publishedEvent = eventRepository.save(event);

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUserId();
            EventPublishedEvent domainEvent = new EventPublishedEvent(
                    publishedEvent.getId(),           // Internal UUID
                    publishedEvent.getEventCode(),    // Public business identifier
                    publishedEvent.getTitle(),
                    publishedEvent.getEventNumber(),
                    publishedEvent.getDate(),
                    publishedEvent.getPublishedAt(),
                    previousStatus,
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventPublishedEvent for event: {}", publishedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventPublishedEvent for event {}: {}", publishedEvent.getEventCode(), e.getMessage());
            // Continue - event publishing succeeded, domain event publishing failure is non-critical
        }

        // Build response
        Map<String, Object> response = buildBasicEventResponse(publishedEvent);

        return ResponseEntity.ok(response);
    }

    /**
     * Advance Event Workflow (AC8 + AC15 cache invalidation)
     * Story 1.16.2: Updated to use eventCode instead of UUID
     *
     * POST /api/v1/events/{eventCode}/workflow/advance
     *
     * Advances the event status through workflow stages
     */
    @PostMapping("/{eventCode}/workflow/advance")
    @Operation(summary = "Advance Workflow", description = "Advance event to next workflow stage (Story 1.16.2)")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> advanceWorkflow(@PathVariable String eventCode) {
        log.debug("POST /api/v1/events/{}/workflow/advance", eventCode);

        // Find event by eventCode
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Validate workflow transition is valid and get next status
        String nextStatus = getNextWorkflowStatus(event);

        // Update status
        event.setStatus(nextStatus);

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

        // Build response
        Map<String, Object> response = buildBasicEventResponse(updatedEvent);
        response.put("workflowState", nextStatus); // Add workflowState for test compatibility

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
        // Check that event date is in the future
        if (event.getDate().isBefore(Instant.now())) {
            throw new BusinessValidationException("Event date must be in the future");
        }
    }

    /**
     * Get next workflow status based on current status
     *
     * @param event Event to advance
     * @return Next workflow status
     * @throws WorkflowException if workflow cannot be advanced
     */
    private String getNextWorkflowStatus(Event event) {
        // Check if workflow can be advanced based on current status
        String currentStatus = event.getStatus();

        // Cannot advance archived or cancelled events
        if ("archived".equals(currentStatus)) {
            throw new WorkflowException("Cannot advance workflow for archived events");
        }
        if ("cancelled".equals(currentStatus)) {
            throw new WorkflowException("Cannot advance workflow for cancelled events");
        }

        // Simple workflow status progression
        // planning -> published -> archived
        if (currentStatus == null || currentStatus.isEmpty() || "planning".equals(currentStatus)) {
            return "published";
        }

        switch (currentStatus) {
            case "published":
                return "archived";
            default:
                return "published";
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
    private void applyPatchUpdates(Event event, PatchEventRequest request) {
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getEventNumber() != null) {
            // Validate that the new event number is not already in use by another event
            if (!request.getEventNumber().equals(event.getEventNumber())) {
                Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
                if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                    throw new BusinessValidationException("Event number " + request.getEventNumber() + " is already in use by event " + existingEvent.get().getEventCode());
                }
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {}, regenerated eventCode to: {}", request.getEventNumber(), newEventCode);
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
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
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
        // Story 2.5.3a: Set theme image upload ID (association happens after save in PATCH handler)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }
    }

    /**
     * Get Event Analytics (AC13)
     *
     * GET /api/v1/events/{id}/analytics?metrics=attendance,registrations,engagement&timeframe=start,end
     *
     * Examples:
     * - All metrics: GET /api/v1/events/123/analytics?metrics=attendance,registrations,engagement
     * - With timeframe: GET /api/v1/events/123/analytics?metrics=registrations&timeframe=2025-04-01T00:00:00Z,2025-05-31T23:59:59Z
     *
     * @param id Event ID
     * @param metrics Comma-separated list of metrics to return
     * @param timeframe Optional timeframe as "startTime,endTime" in ISO-8601 format
     * @return Analytics data for the event
     */
    @GetMapping("/{eventCode}/analytics")
    @Operation(
            summary = "Get Event Analytics",
            description = "Retrieve analytics data for an event with optional metrics and timeframe filtering (Story 1.16.2)"
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
            description = "Register for an event without requiring authentication. " +
                    "Creates anonymous user profile automatically. " +
                    "Registration starts in PENDING status. User must confirm via email link."
    )
    public ResponseEntity<CreateRegistrationResponse> createRegistration(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateRegistrationRequest request) {
        log.debug("POST /api/v1/events/{}/registrations", eventCode);

        // Create registration with status=PENDING (will be confirmed via email)
        Registration registration = registrationService.createRegistration(eventCode, request);

        // Generate confirmation token (48h validity)
        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(),
                eventCode
        );

        // Fetch event for email
        ch.batbern.events.domain.Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));

        // Fetch user profile for email
        ch.batbern.events.dto.generated.users.UserResponse userProfile =
                userApiClient.getUserByUsername(registration.getAttendeeUsername());

        // Send confirmation email with JWT token (Story 4.1.5c)
        // Email includes confirmation link: https://batbern.ch/events/{eventCode}/confirm-registration?token={confirmationToken}
        registrationEmailService.sendRegistrationConfirmation(
                registration,
                userProfile,
                event,
                confirmationToken,
                java.util.Locale.GERMAN // Default to German for BATbern events
        );

        log.info("Confirmation token generated and email queued for registration {}: {}",
                registration.getId(), confirmationToken.substring(0, 20) + "...");

        // Return minimal response (no sensitive data)
        CreateRegistrationResponse response = CreateRegistrationResponse.builder()
                .message("Registration submitted successfully. Check your email to confirm.")
                .email(request.getEmail())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List Event Registrations - Story 2.2a
     *
     * GET /api/v1/events/{eventCode}/registrations
     *
     * @param eventCode Event code to list registrations for
     * @return List of registrations enriched with user data
     */
    @GetMapping("/{eventCode}/registrations")
    @Operation(
            summary = "List Event Registrations",
            description = "Retrieve all registrations for a specific event with enriched user data"
    )
    public ResponseEntity<List<RegistrationResponse>> listRegistrations(@PathVariable String eventCode) {
        log.debug("GET /api/v1/events/{}/registrations", eventCode);

        // Find event to get UUID
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Fetch registrations
        List<Registration> registrations = registrationRepository.findByEventId(event.getId());

        // Enrich each registration with user data
        List<RegistrationResponse> responses = registrations.stream()
                .peek(reg -> reg.setEventCode(eventCode)) // Set transient field
                .map(registrationService::enrichRegistrationWithUserData)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
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
            throw new NoSuchElementException("Registration " + registrationCode + " does not belong to event " + eventCode);
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
            description = "Confirm a pending registration using the token from the confirmation email. " +
                    "Token is valid for 48 hours and event code must match."
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
                throw new IllegalArgumentException("Event code mismatch: URL has " + eventCode + " but token has " + tokenEventCode);
            }

            // Extract registration ID
            UUID registrationId = confirmationTokenService.getRegistrationId(claims);

            // Find registration
            Registration registration = registrationRepository.findById(registrationId)
                    .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationId));

            // Fetch the event to verify registration belongs to it and to get event code
            Event event = eventRepository.findById(registration.getEventId())
                    .orElseThrow(() -> new NoSuchElementException("Event not found for registration: " + registrationId));

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
            throw new NoSuchElementException("Registration " + registrationCode + " does not belong to event " + eventCode);
        }

        // Apply updates
        if (updates.containsKey("status")) {
            registration.setStatus((String) updates.get("status"));
        }

        // Save updated registration
        registration = registrationRepository.save(registration);

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
            throw new NoSuchElementException("Registration " + registrationCode + " does not belong to event " + eventCode);
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
}
