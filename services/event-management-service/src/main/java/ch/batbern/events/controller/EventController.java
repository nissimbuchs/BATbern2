package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.BatchUpdateRequest;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.event.EventUpdatedEvent;
import ch.batbern.events.exception.BusinessValidationException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.WorkflowException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.EventSearchService;
import jakarta.validation.Valid;
import ch.batbern.shared.dto.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final ch.batbern.events.service.EventAnalyticsService eventAnalyticsService;
    private final ch.batbern.shared.events.DomainEventPublisher eventPublisher;
    private final ch.batbern.events.security.SecurityContextHelper securityContextHelper;
    private final CacheManager cacheManager;

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
     * Expand sessions data for an event
     * TODO: Replace with actual domain model when sessions are implemented
     */
    private java.util.List<Map<String, Object>> expandSessions(Event event) {
        // Stub implementation - will be replaced with actual domain model
        return new java.util.ArrayList<>();
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
                .build();

        // Save event
        Event savedEvent = eventRepository.save(event);

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
        event.setEventNumber(request.getEventNumber());
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

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

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
            event.setEventNumber(request.getEventNumber());
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
}
