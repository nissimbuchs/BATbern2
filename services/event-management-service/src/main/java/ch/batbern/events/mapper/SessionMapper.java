package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
                .startTime(toOffset(entity.getStartTime()))
                .endTime(toOffset(entity.getEndTime()))
                .room(entity.getRoom())
                .capacity(entity.getCapacity())
                .language(entity.getLanguage())
                .createdAt(toOffset(entity.getCreatedAt()))
                .updatedAt(toOffset(entity.getUpdatedAt()))
                .materialsCount(entity.getMaterialsCount())
                .materialsStatus(entity.getMaterialsStatus())
                // speakers and materials are populated by service layer
                .build();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Convert a stored {@link Instant} to a UTC {@link OffsetDateTime} for the wire DTO.
     * Serialises to the same ISO-8601 {@code …Z} string the previous String field produced.
     *
     * @param instant the Instant to convert
     * @return UTC OffsetDateTime, or null if input is null
     */
    private OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }
}
