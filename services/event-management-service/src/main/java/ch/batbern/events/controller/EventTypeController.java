package ch.batbern.events.controller;

import ch.batbern.events.api.generated.EventTypesApi;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.UpdateEventSlotConfigurationRequest;
import ch.batbern.events.service.EventTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for Event Type Configuration API (Story 5.1).
 *
 * Implements generated EventTypesApi interface from OpenAPI spec.
 * Contract-first approach (ADR-006) ensures API consistency.
 *
 * Endpoints:
 * - GET /api/v1/events/types - List all event types (AC1)
 * - GET /api/v1/events/types/{type} - Get specific event type (AC2)
 * - PUT /api/v1/events/types/{type} - Update event type config (AC3, AC8, ORGANIZER only)
 *
 * Authorization:
 * - GET endpoints: Any authenticated role
 * - PUT endpoint: ORGANIZER role only (@PreAuthorize)
 *
 * Caching:
 * - GET operations return cached responses (Caffeine, 1 hour TTL)
 * - PUT operations invalidate cache
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventTypeController implements EventTypesApi {

    private final EventTypeService eventTypeService;

    /**
     * GET /api/v1/events/types
     * List all event type configurations.
     *
     * @return 200 OK with list of event type configurations
     */
    @Override
    public ResponseEntity<List<EventSlotConfigurationResponse>> listEventTypes() {
        log.debug("GET /api/v1/events/types - List all event types");
        List<EventSlotConfigurationResponse> eventTypes = eventTypeService.getAllEventTypes();
        log.debug("Returning {} event types", eventTypes.size());
        return ResponseEntity.ok(eventTypes);
    }

    /**
     * GET /api/v1/events/types/{type}
     * Get specific event type configuration.
     *
     * @param type EventType enum (FULL_DAY, AFTERNOON, EVENING)
     * @return 200 OK with event type configuration
     *         404 NOT FOUND if event type doesn't exist
     */
    @Override
    public ResponseEntity<EventSlotConfigurationResponse> getEventType(EventType type) {
        log.debug("GET /api/v1/events/types/{} - Get event type configuration", type);
        EventSlotConfigurationResponse config = eventTypeService.getEventType(type);
        return ResponseEntity.ok(config);
    }

    /**
     * PUT /api/v1/events/types/{type}
     * Update event type configuration (ORGANIZER only).
     *
     * @param type EventType enum
     * @param updateRequest Update request with new configuration
     * @return 200 OK with updated configuration
     *         400 BAD REQUEST if validation fails
     *         403 FORBIDDEN if user doesn't have ORGANIZER role
     *         404 NOT FOUND if event type doesn't exist
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventSlotConfigurationResponse> updateEventType(
            EventType type,
            UpdateEventSlotConfigurationRequest updateRequest) {
        log.debug("PUT /api/v1/events/types/{} - Update event type configuration", type);
        EventSlotConfigurationResponse updatedConfig = eventTypeService.updateEventType(type, updateRequest);
        log.info("Successfully updated event type configuration: {}", type);
        return ResponseEntity.ok(updatedConfig);
    }
}
