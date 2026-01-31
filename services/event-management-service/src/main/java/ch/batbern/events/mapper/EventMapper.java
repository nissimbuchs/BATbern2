package ch.batbern.events.mapper;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Pure mapper for converting between Event entities and DTOs.
 *
 * Pattern: Pure Mapper (follows TopicMapper pattern)
 * - Field mapping only
 * - Type conversions (String dates → Instant, enums ↔ strings)
 * - NO repository dependencies
 * - NO complex business logic
 *
 * Story BAT-90 Phase 2: Service Layer Migration
 *
 * @see Event (JPA entity)
 * @see EventResponse (response DTO)
 * @see TopicMapper (pattern reference)
 */
@Component
public class EventMapper {

    /**
     * Convert Event entity to EventResponse DTO.
     *
     * @param entity the Event entity
     * @return the EventResponse DTO
     */
    public EventResponse toDto(Event entity) {
        if (entity == null) {
            return null;
        }

        return EventResponse.builder()
                .eventCode(entity.getEventCode())
                .title(entity.getTitle())
                .eventNumber(entity.getEventNumber())
                .date(entity.getDate())
                .registrationDeadline(entity.getRegistrationDeadline())
                .venueName(entity.getVenueName())
                .venueAddress(entity.getVenueAddress())
                .venueCapacity(entity.getVenueCapacity())
                .organizerUsername(entity.getOrganizerUsername())
                .currentAttendeeCount(entity.getCurrentAttendeeCount())
                .publishedAt(entity.getPublishedAt())
                .metadata(entity.getMetadata())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .themeImageUrl(entity.getThemeImageUrl())
                .themeImageUploadId(entity.getThemeImageUploadId())
                .eventType(entity.getEventType() != null ? entity.getEventType().getValue() : null)
                .topicCode(entity.getTopicCode())
                .workflowState(entity.getWorkflowState() != null ? entity.getWorkflowState().name() : null)
                .currentPublishedPhase(entity.getCurrentPublishedPhase() != null
                        ? entity.getCurrentPublishedPhase().toUpperCase() : null)
                .build();
    }

    /**
     * Convert Event entity to EventResponse DTO with actual registration count.
     * Used when displaying event lists and details to show accurate registration numbers.
     *
     * @param entity the Event entity
     * @param actualRegistrationCount the actual count from registrations table
     * @return EventResponse with accurate registration count
     */
    public EventResponse toDto(Event entity, long actualRegistrationCount) {
        if (entity == null) {
            return null;
        }

        return EventResponse.builder()
                .eventCode(entity.getEventCode())
                .title(entity.getTitle())
                .eventNumber(entity.getEventNumber())
                .date(entity.getDate())
                .registrationDeadline(entity.getRegistrationDeadline())
                .venueName(entity.getVenueName())
                .venueAddress(entity.getVenueAddress())
                .venueCapacity(entity.getVenueCapacity())
                .organizerUsername(entity.getOrganizerUsername())
                .currentAttendeeCount((int) actualRegistrationCount)
                .publishedAt(entity.getPublishedAt())
                .metadata(entity.getMetadata())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .themeImageUrl(entity.getThemeImageUrl())
                .themeImageUploadId(entity.getThemeImageUploadId())
                .eventType(entity.getEventType() != null ? entity.getEventType().getValue() : null)
                .topicCode(entity.getTopicCode())
                .workflowState(entity.getWorkflowState() != null ? entity.getWorkflowState().name() : null)
                .currentPublishedPhase(entity.getCurrentPublishedPhase() != null
                        ? entity.getCurrentPublishedPhase().toUpperCase() : null)
                .build();
    }

    /**
     * Convert CreateEventRequest DTO to Event entity.
     *
     * @param request the CreateEventRequest DTO
     * @return a new Event entity (not yet persisted)
     */
    public Event toEntity(CreateEventRequest request) {
        if (request == null) {
            return null;
        }

        Event entity = new Event();
        entity.setTitle(request.getTitle());
        entity.setEventNumber(request.getEventNumber());
        entity.setDate(parseInstant(request.getDate()));
        entity.setRegistrationDeadline(parseInstant(request.getRegistrationDeadline()));
        entity.setVenueName(request.getVenueName());
        entity.setVenueAddress(request.getVenueAddress());
        entity.setVenueCapacity(request.getVenueCapacity());
        entity.setOrganizerUsername(request.getOrganizerUsername());
        entity.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        entity.setPublishedAt(parseInstant(request.getPublishedAt()));
        entity.setMetadata(request.getMetadata());
        entity.setDescription(request.getDescription());
        entity.setEventType(request.getEventType());
        entity.setThemeImageUploadId(request.getThemeImageUploadId());
        entity.setWorkflowState(request.getWorkflowState() != null
                ? request.getWorkflowState()
                : EventWorkflowState.CREATED);

        return entity;
    }

    /**
     * Apply UpdateEventRequest to an existing Event entity (full replacement).
     *
     * @param entity the existing Event entity to update
     * @param request the UpdateEventRequest DTO
     */
    public void applyUpdateRequest(Event entity, UpdateEventRequest request) {
        if (entity == null || request == null) {
            return;
        }

        entity.setTitle(request.getTitle());
        entity.setEventNumber(request.getEventNumber());
        entity.setDate(parseInstant(request.getDate()));
        entity.setRegistrationDeadline(parseInstant(request.getRegistrationDeadline()));
        entity.setVenueName(request.getVenueName());
        entity.setVenueAddress(request.getVenueAddress());
        entity.setVenueCapacity(request.getVenueCapacity());
        entity.setOrganizerUsername(request.getOrganizerUsername());
        entity.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        entity.setPublishedAt(parseInstant(request.getPublishedAt()));
        entity.setMetadata(request.getMetadata());
        entity.setDescription(request.getDescription());
        entity.setThemeImageUploadId(request.getThemeImageUploadId());

        // EventType is a String in UpdateEventRequest, need to convert
        if (request.getEventType() != null) {
            entity.setEventType(EventType.fromValue(request.getEventType()));
        }

        if (request.getWorkflowState() != null) {
            entity.setWorkflowState(request.getWorkflowState());
        }
    }

    /**
     * Apply PatchEventRequest to an existing Event entity (partial update).
     * Only non-null fields in the request are applied.
     *
     * @param entity the existing Event entity to patch
     * @param request the PatchEventRequest DTO
     */
    public void applyPatchRequest(Event entity, PatchEventRequest request) {
        if (entity == null || request == null) {
            return;
        }

        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getEventNumber() != null) {
            entity.setEventNumber(request.getEventNumber());
        }
        if (request.getDate() != null) {
            entity.setDate(parseInstant(request.getDate()));
        }
        if (request.getRegistrationDeadline() != null) {
            entity.setRegistrationDeadline(parseInstant(request.getRegistrationDeadline()));
        }
        if (request.getVenueName() != null) {
            entity.setVenueName(request.getVenueName());
        }
        if (request.getVenueAddress() != null) {
            entity.setVenueAddress(request.getVenueAddress());
        }
        if (request.getVenueCapacity() != null) {
            entity.setVenueCapacity(request.getVenueCapacity());
        }
        if (request.getOrganizerUsername() != null) {
            entity.setOrganizerUsername(request.getOrganizerUsername());
        }
        if (request.getCurrentAttendeeCount() != null) {
            entity.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        }
        if (request.getPublishedAt() != null) {
            entity.setPublishedAt(parseInstant(request.getPublishedAt()));
        }
        if (request.getMetadata() != null) {
            entity.setMetadata(request.getMetadata());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getThemeImageUploadId() != null) {
            entity.setThemeImageUploadId(request.getThemeImageUploadId());
        }
        if (request.getEventType() != null) {
            entity.setEventType(EventType.fromValue(request.getEventType()));
        }
        if (request.getWorkflowState() != null) {
            entity.setWorkflowState(request.getWorkflowState());
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Parse ISO-8601 date string to Instant.
     *
     * @param dateString ISO-8601 formatted date string
     * @return Instant, or null if input is null
     */
    private Instant parseInstant(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        return Instant.parse(dateString);
    }
}
