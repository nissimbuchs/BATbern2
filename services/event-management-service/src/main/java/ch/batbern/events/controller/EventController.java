package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.BatchUpdateRequest;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.UpdateEventRequest;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * GET /api/v1/events/{id}?include=venue,speakers,sessions
     *
     * Examples:
     * - Basic: GET /api/v1/events/123
     * - With venue: GET /api/v1/events/123?include=venue
     * - Multiple: GET /api/v1/events/123?include=venue,speakers,sessions
     *
     * Caching: Results with includes are cached for 15 minutes using Caffeine in-memory cache.
     * Cache key includes both event ID and include parameters to ensure correct cache hits.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get Event Detail",
            description = "Retrieve a single event by ID with optional resource expansion using ?include parameter. Cached for 15 minutes."
    )
    @Cacheable(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, key = "#id + '_' + (#include != null ? #include : 'none')")
    public ResponseEntity<Map<String, Object>> getEvent(
            @PathVariable String id,
            @Parameter(description = "Comma-separated list of resources to include (e.g., venue,speakers,sessions)")
            @RequestParam(required = false) String include
    ) {
        log.debug("GET /api/v1/events/{} - include: {}", id, include);

        // Find event by ID
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Build response with basic event data
        Map<String, Object> response = buildBasicEventResponse(event);

        // Apply resource expansions if requested
        if (include != null && !include.trim().isEmpty()) {
            applyResourceExpansions(event, include, response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Build basic event response without expanded resources
     */
    private Map<String, Object> buildBasicEventResponse(Event event) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", event.getId());
        response.put("title", event.getTitle());
        response.put("status", event.getStatus());
        response.put("description", event.getDescription());
        response.put("date", event.getDate() != null ? event.getDate().toString() : null);
        if (event.getWorkflowState() != null) {
            response.put("workflowState", event.getWorkflowState());
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
     * @return Created event with generated ID
     */
    @PostMapping
    @Operation(summary = "Create Event", description = "Create a new event")
    public ResponseEntity<Map<String, Object>> createEvent(@Valid @RequestBody CreateEventRequest request) {
        log.debug("POST /api/v1/events - title: {}", request.getTitle());

        // Create new event entity
        Event event = Event.builder()
                .title(request.getTitle())
                .date(parseDate(request.getDate()))
                .status(request.getStatus())
                .description(request.getDescription())
                .venueId(request.getVenueId())
                .build();

        // Save event
        Event savedEvent = eventRepository.save(event);

        // Build response
        Map<String, Object> response = buildBasicEventResponse(savedEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update Event - Full Replacement (AC4 + AC15 cache invalidation)
     *
     * PUT /api/v1/events/{id}
     *
     * @param id Event ID
     * @param request Complete event data for replacement
     * @return Updated event
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update Event", description = "Fully replace an existing event")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> updateEvent(
            @PathVariable String id,
            @Valid @RequestBody UpdateEventRequest request) {
        log.debug("PUT /api/v1/events/{} - title: {}", id, request.getTitle());

        // Find existing event
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Replace all fields
        event.setTitle(request.getTitle());
        event.setDate(parseDate(request.getDate()));
        event.setStatus(request.getStatus());
        event.setDescription(request.getDescription());
        event.setVenueId(request.getVenueId());

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

        // Build response
        Map<String, Object> response = buildBasicEventResponse(updatedEvent);

        return ResponseEntity.ok(response);
    }

    /**
     * Partial Update Event (AC5 + AC15 cache invalidation)
     *
     * PATCH /api/v1/events/{id}
     *
     * @param id Event ID
     * @param request Partial event data (only provided fields will be updated)
     * @return Partially updated event
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Patch Event", description = "Partially update an existing event")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> patchEvent(
            @PathVariable String id,
            @Valid @RequestBody PatchEventRequest request) {
        log.debug("PATCH /api/v1/events/{}", id);

        // Find existing event
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Update only provided fields
        applyPatchUpdates(event, request);

        // Save patched event
        Event patchedEvent = eventRepository.save(event);

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
                // Find existing event
                Event event = eventRepository.findById(request.getId())
                        .orElseThrow(() -> new EventNotFoundException(request.getId()));

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
                if (request.getVenueId() != null) {
                    event.setVenueId(request.getVenueId());
                }

                // Save updated event
                Event updatedEvent = eventRepository.save(event);

                // Add to successful list
                Map<String, Object> successResult = new HashMap<>();
                successResult.put("id", updatedEvent.getId());
                successResult.put("status", "updated");
                successful.add(successResult);

            } catch (EventNotFoundException e) {
                // Add to failed list
                Map<String, Object> failureResult = new HashMap<>();
                failureResult.put("id", request.getId());
                failureResult.put("error", "Event not found");
                failed.add(failureResult);
            } catch (Exception e) {
                // Add to failed list
                Map<String, Object> failureResult = new HashMap<>();
                failureResult.put("id", request.getId());
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
     *
     * DELETE /api/v1/events/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Event", description = "Delete an event by ID")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        log.debug("DELETE /api/v1/events/{}", id);

        // Verify event exists
        if (!eventRepository.existsById(id)) {
            throw new EventNotFoundException(id);
        }

        // Delete event
        eventRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Publish Event (AC7 + AC15 cache invalidation)
     *
     * POST /api/v1/events/{id}/publish
     *
     * Validates event meets publication requirements and changes status to "published"
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish Event", description = "Publish an event after validation")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> publishEvent(@PathVariable String id) {
        log.debug("POST /api/v1/events/{}/publish", id);

        // Find event
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Validate event can be published
        validateForPublishing(event);

        // Change status to published
        event.setStatus("published");

        // Save updated event
        Event publishedEvent = eventRepository.save(event);

        // Build response
        Map<String, Object> response = buildBasicEventResponse(publishedEvent);

        return ResponseEntity.ok(response);
    }

    /**
     * Advance Event Workflow (AC8 + AC15 cache invalidation)
     *
     * POST /api/v1/events/{id}/workflow/advance
     *
     * Advances the event to the next workflow state
     */
    @PostMapping("/{id}/workflow/advance")
    @Operation(summary = "Advance Workflow", description = "Advance event to next workflow state")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Map<String, Object>> advanceWorkflow(@PathVariable String id) {
        log.debug("POST /api/v1/events/{}/workflow/advance", id);

        // Find event
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Validate workflow transition is valid
        String nextState = getNextWorkflowState(event);

        // Update workflow state
        event.setWorkflowState(nextState);

        // Save updated event
        Event updatedEvent = eventRepository.save(event);

        // Build response
        Map<String, Object> response = buildBasicEventResponse(updatedEvent);

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
    }

    /**
     * Get next workflow state based on current status
     *
     * @param event Event to advance
     * @return Next workflow state
     * @throws WorkflowException if workflow cannot be advanced
     */
    private String getNextWorkflowState(Event event) {
        // Check if workflow can be advanced based on current status
        String currentStatus = event.getStatus();

        // Cannot advance archived or cancelled events
        if ("archived".equals(currentStatus)) {
            throw new WorkflowException("Cannot advance workflow for archived events");
        }
        if ("cancelled".equals(currentStatus)) {
            throw new WorkflowException("Cannot advance workflow for cancelled events");
        }

        // Simple workflow state progression
        // draft -> planning -> ready -> published
        String currentState = event.getWorkflowState();
        if (currentState == null || currentState.isEmpty()) {
            return "planning";
        }

        switch (currentState) {
            case "planning":
                return "ready";
            case "ready":
                return "published";
            case "published":
                return "completed";
            default:
                return "planning";
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
        if (request.getDate() != null) {
            event.setDate(parseDate(request.getDate()));
        }
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getVenueId() != null) {
            event.setVenueId(request.getVenueId());
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
    @GetMapping("/{id}/analytics")
    @Operation(
            summary = "Get Event Analytics",
            description = "Retrieve analytics data for an event with optional metrics and timeframe filtering"
    )
    public ResponseEntity<Map<String, Object>> getEventAnalytics(
            @PathVariable String id,
            @Parameter(description = "Comma-separated list of metrics (attendance, registrations, engagement)")
            @RequestParam(required = false, defaultValue = "attendance,registrations,engagement") String metrics,
            @Parameter(description = "Timeframe as 'startTime,endTime' in ISO-8601 format")
            @RequestParam(required = false) String timeframe
    ) {
        log.debug("GET /api/v1/events/{}/analytics - metrics: {}, timeframe: {}", id, metrics, timeframe);

        // Generate analytics using EventAnalyticsService
        Map<String, Object> analytics = eventAnalyticsService.generateAnalytics(id, metrics, timeframe);

        return ResponseEntity.ok(analytics);
    }
}
