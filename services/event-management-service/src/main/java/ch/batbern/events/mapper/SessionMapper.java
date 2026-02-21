package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.CreateSessionRequest;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.UpdateSessionRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Pure mapper for converting between Session entities and DTOs.
 *
 * Pattern: Pure Mapper (follows TopicMapper/EventMapper pattern)
 * - Field mapping only
 * - Type conversions (String dates ↔ Instant)
 * - NO repository dependencies
 * - NO complex business logic
 *
 * Note: Speakers and materials are NOT mapped here - they require
 * repository access and should be populated by the service layer.
 *
 * Story BAT-90 Phase 2: Service Layer Migration
 *
 * @see Session (JPA entity)
 * @see SessionResponse (response DTO)
 * @see EventMapper (pattern reference)
 */
@Component
public class SessionMapper {

    /**
     * Convert Session entity to SessionResponse DTO.
     *
     * Note: speakers and materials fields are left null.
     * The service layer must populate these if needed.
     *
     * @param entity the Session entity
     * @return the SessionResponse DTO
     */
    public SessionResponse toDto(Session entity) {
        if (entity == null) {
            return null;
        }

        return SessionResponse.builder()
                .sessionSlug(entity.getSessionSlug())
                .eventCode(entity.getEventCode())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .sessionType(entity.getSessionType())
                .startTime(formatInstant(entity.getStartTime()))
                .endTime(formatInstant(entity.getEndTime()))
                .room(entity.getRoom())
                .capacity(entity.getCapacity())
                .language(entity.getLanguage())
                .createdAt(formatInstant(entity.getCreatedAt()))
                .updatedAt(formatInstant(entity.getUpdatedAt()))
                .materialsCount(entity.getMaterialsCount())
                .materialsStatus(entity.getMaterialsStatus())
                // speakers and materials are populated by service layer
                .build();
    }

    /**
     * Convert CreateSessionRequest DTO to Session entity.
     *
     * Note: eventId and eventCode must be set by the caller.
     *
     * @param request the CreateSessionRequest DTO
     * @return a new Session entity (not yet persisted)
     */
    public Session toEntity(CreateSessionRequest request) {
        if (request == null) {
            return null;
        }

        return Session.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .sessionType(request.getSessionType())
                .startTime(parseInstant(request.getStartTime()))
                .endTime(parseInstant(request.getEndTime()))
                .room(request.getRoom())
                .capacity(request.getCapacity())
                .language(request.getLanguage())
                .build();
    }

    /**
     * Apply UpdateSessionRequest to an existing Session entity (full replacement).
     *
     * @param entity the existing Session entity to update
     * @param request the UpdateSessionRequest DTO
     */
    public void applyUpdateRequest(Session entity, UpdateSessionRequest request) {
        if (entity == null || request == null) {
            return;
        }

        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setSessionType(request.getSessionType());
        entity.setStartTime(parseInstant(request.getStartTime()));
        entity.setEndTime(parseInstant(request.getEndTime()));
        entity.setRoom(request.getRoom());
        entity.setCapacity(request.getCapacity());
        entity.setLanguage(request.getLanguage());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Format Instant to ISO-8601 string.
     *
     * @param instant the Instant to format
     * @return ISO-8601 formatted string, or null if input is null
     */
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.toString();
    }

    /**
     * Parse ISO-8601 date string to Instant.
     *
     * @param dateString ISO-8601 formatted date string
     * @return Instant, or null if input is null or blank
     */
    private Instant parseInstant(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        return Instant.parse(dateString);
    }
}
