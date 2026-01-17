package ch.batbern.events.converter;

import ch.batbern.events.domain.ResponseType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * JPA AttributeConverter for ResponseType enum - Story 6.1.
 *
 * Converts between Java enum (UPPER_CASE) and database VARCHAR (lowercase).
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: ResponseType.ACCEPTED (UPPER_CASE)
 * - Database: 'accepted' (lowercase)
 *
 * Example conversions:
 * - ACCEPTED ↔ 'accepted'
 * - DECLINED ↔ 'declined'
 * - TENTATIVE ↔ 'tentative'
 */
@Converter(autoApply = false)
public class ResponseTypeConverter implements AttributeConverter<ResponseType, String> {

    @Override
    public String convertToDatabaseColumn(ResponseType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public ResponseType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return ResponseType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid response type in database: '%s'. Must be one of: %s",
                            dbData, Arrays.toString(ResponseType.values())),
                    e
            );
        }
    }
}
