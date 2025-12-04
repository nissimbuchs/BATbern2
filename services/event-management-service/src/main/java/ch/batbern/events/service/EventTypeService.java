package ch.batbern.events.service;

import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.UpdateEventSlotConfigurationRequest;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.mapper.EventTypeMapper;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Event Type Configuration (Story 5.1).
 *
 * Provides business logic for:
 * - Retrieving all event types (cached)
 * - Retrieving specific event type (cached)
 * - Updating event type configuration (cache invalidation)
 * - Validating slot count against event type requirements
 *
 * Caching Strategy:
 * - Caffeine cache with 1 hour TTL (event types rarely change)
 * - Cache key: event type enum value
 * - Cache invalidation on PUT operations
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class EventTypeService {

    private final EventTypeRepository eventTypeRepository;
    private final EventTypeMapper eventTypeMapper;

    /**
     * Get all event type configurations.
     * Cached response (1 hour TTL).
     *
     * @return List of all event type configurations
     */
    @Cacheable(value = "eventTypes", key = "'all'")
    public List<EventSlotConfigurationResponse> getAllEventTypes() {
        log.debug("Fetching all event types from database");
        return eventTypeRepository.findAll().stream()
                .map(eventTypeMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get specific event type configuration.
     * Cached response (1 hour TTL).
     *
     * @param type EventType enum
     * @return Event type configuration
     * @throws NotFoundException if event type not found
     */
    @Cacheable(value = "eventTypes", key = "#type")
    public EventSlotConfigurationResponse getEventType(EventType type) {
        log.debug("Fetching event type configuration for: {}", type);
        EventTypeConfiguration config = eventTypeRepository.findByType(type)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event type configuration not found: %s", type)));
        return eventTypeMapper.toResponse(config);
    }

    /**
     * Update event type configuration (ORGANIZER only).
     * Invalidates cache on successful update.
     *
     * @param type EventType enum
     * @param request Update request with new configuration
     * @return Updated event type configuration
     * @throws NotFoundException if event type not found
     * @throws ValidationException if validation fails
     */
    @Transactional
    @CacheEvict(value = "eventTypes", allEntries = true)
    public EventSlotConfigurationResponse updateEventType(EventType type, UpdateEventSlotConfigurationRequest request) {
        log.debug("Updating event type configuration for: {}", type);

        // Validate request
        validateUpdateRequest(request);

        // Find existing configuration
        EventTypeConfiguration config = eventTypeRepository.findByType(type)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event type configuration not found: %s", type)));

        // Update fields from request
        eventTypeMapper.updateEntity(config, request);

        // Save updated configuration
        EventTypeConfiguration savedConfig = eventTypeRepository.save(config);
        log.info("Updated event type configuration: {} - minSlots={}, maxSlots={}, capacity={}",
                type, savedConfig.getMinSlots(), savedConfig.getMaxSlots(), savedConfig.getDefaultCapacity());

        return eventTypeMapper.toResponse(savedConfig);
    }

    /**
     * Validate slot count against event type requirements.
     *
     * @param type EventType enum
     * @param slotCount Number of slots
     * @throws ValidationException if slot count is out of range
     */
    public void validateSlotCount(EventType type, int slotCount) {
        EventSlotConfigurationResponse config = getEventType(type);

        if (slotCount < config.getMinSlots()) {
            throw new ValidationException(
                    String.format("Slot count %d is below minimum %d for %s event type",
                            slotCount, config.getMinSlots(), type));
        }

        if (slotCount > config.getMaxSlots()) {
            throw new ValidationException(
                    String.format("Slot count %d exceeds maximum %d for %s event type",
                            slotCount, config.getMaxSlots(), type));
        }
    }

    /**
     * Validate update request (business rules).
     *
     * @param request Update request
     * @throws ValidationException if validation fails
     */
    private void validateUpdateRequest(UpdateEventSlotConfigurationRequest request) {
        // Validate minSlots <= maxSlots
        if (request.getMinSlots() > request.getMaxSlots()) {
            throw new ValidationException(
                    String.format("minSlots (%d) must be <= maxSlots (%d)",
                            request.getMinSlots(), request.getMaxSlots()));
        }

        // Validate slotDuration >= 15 minutes (also enforced by @Min annotation, but double-check)
        if (request.getSlotDuration() < 15) {
            throw new ValidationException(
                    String.format("slotDuration must be >= 15 minutes (provided: %d)",
                            request.getSlotDuration()));
        }

        // Validate defaultCapacity > 0 (also enforced by @Min annotation, but double-check)
        if (request.getDefaultCapacity() <= 0) {
            throw new ValidationException(
                    String.format("defaultCapacity must be > 0 (provided: %d)",
                            request.getDefaultCapacity()));
        }
    }
}
