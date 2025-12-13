package ch.batbern.events.mapper;

import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.UpdateEventSlotConfigurationRequest;
import ch.batbern.events.entity.EventTypeConfiguration;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Mapper between EventTypeConfiguration entity and generated DTOs (Story 5.1).
 *
 * Handles conversion:
 * - Entity → EventSlotConfigurationResponse (for GET operations)
 * - UpdateEventSlotConfigurationRequest → Entity (for PUT operations)
 *
 * LocalTime conversion:
 * - Database stores TIME type
 * - OpenAPI spec uses string format: "HH:mm" (e.g., "09:00")
 * - Mapper handles conversion between LocalTime and String
 */
@Component
public class EventTypeMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Convert entity to response DTO.
     *
     * @param entity EventTypeConfiguration entity
     * @return EventSlotConfigurationResponse DTO
     */
    public EventSlotConfigurationResponse toResponse(EventTypeConfiguration entity) {
        return new EventSlotConfigurationResponse()
                .type(entity.getType())
                .minSlots(entity.getMinSlots())
                .maxSlots(entity.getMaxSlots())
                .slotDuration(entity.getSlotDuration())
                .theoreticalSlotsAM(entity.getTheoreticalSlotsAM())
                .breakSlots(entity.getBreakSlots())
                .lunchSlots(entity.getLunchSlots())
                .defaultCapacity(entity.getDefaultCapacity())
                .typicalStartTime(formatTime(entity.getTypicalStartTime()))
                .typicalEndTime(formatTime(entity.getTypicalEndTime()));
    }

    /**
     * Update entity from request DTO.
     *
     * @param entity EventTypeConfiguration entity to update
     * @param request UpdateEventSlotConfigurationRequest with new values
     */
    public void updateEntity(EventTypeConfiguration entity, UpdateEventSlotConfigurationRequest request) {
        entity.setMinSlots(request.getMinSlots());
        entity.setMaxSlots(request.getMaxSlots());
        entity.setSlotDuration(request.getSlotDuration());
        entity.setTheoreticalSlotsAM(request.getTheoreticalSlotsAM());
        entity.setBreakSlots(request.getBreakSlots());
        entity.setLunchSlots(request.getLunchSlots());
        entity.setDefaultCapacity(request.getDefaultCapacity());
        entity.setTypicalStartTime(parseTime(request.getTypicalStartTime()));
        entity.setTypicalEndTime(parseTime(request.getTypicalEndTime()));
    }

    /**
     * Format LocalTime to string (HH:mm format).
     *
     * @param time LocalTime or null
     * @return Formatted time string or null
     */
    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : null;
    }

    /**
     * Parse string to LocalTime (HH:mm format).
     *
     * @param timeStr Time string (e.g., "09:00") or null
     * @return LocalTime or null
     */
    private LocalTime parseTime(String timeStr) {
        return timeStr != null ? LocalTime.parse(timeStr, TIME_FORMATTER) : null;
    }
}
