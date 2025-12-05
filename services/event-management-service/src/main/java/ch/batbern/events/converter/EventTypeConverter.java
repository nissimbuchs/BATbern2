package ch.batbern.events.converter;

import ch.batbern.events.dto.generated.EventType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for EventType enum.
 *
 * Converts between Java enum (UPPER_CASE) and database value (lowercase_snake_case).
 * Follows ADR-003 and coding standards: Enums stored as lowercase_snake_case in database.
 *
 * Story 5.1: Event Type Definition
 */
@Converter(autoApply = true)
public class EventTypeConverter implements AttributeConverter<EventType, String> {

    @Override
    public String convertToDatabaseColumn(EventType eventType) {
        if (eventType == null) {
            return null;
        }
        // Convert FULL_DAY -> full_day, AFTERNOON -> afternoon, EVENING -> evening
        return eventType.name().toLowerCase();
    }

    @Override
    public EventType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        // Convert full_day -> FULL_DAY, afternoon -> AFTERNOON, evening -> EVENING
        return EventType.fromValue(dbData.toUpperCase());
    }
}
