package ch.batbern.events.converter;

import ch.batbern.events.domain.SpeakerAvailability;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for SpeakerAvailability enum - Story 6.0.
 *
 * Converts between Java enum (UPPER_CASE) and database VARCHAR (lowercase).
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: SpeakerAvailability.AVAILABLE (UPPER_CASE)
 * - Database: 'available' (lowercase)
 *
 * Example conversions:
 * - AVAILABLE ↔ 'available'
 * - BUSY ↔ 'busy'
 * - UNAVAILABLE ↔ 'unavailable'
 */
@Converter(autoApply = false)
public class SpeakerAvailabilityConverter implements AttributeConverter<SpeakerAvailability, String> {

    @Override
    public String convertToDatabaseColumn(SpeakerAvailability attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public SpeakerAvailability convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return SpeakerAvailability.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid speaker availability in database: '%s'. Must be one of: %s",
                            dbData, java.util.Arrays.toString(SpeakerAvailability.values())),
                    e
            );
        }
    }
}
