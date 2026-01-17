package ch.batbern.events.converter;

import ch.batbern.events.domain.InvitationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * JPA AttributeConverter for InvitationStatus enum - Story 6.1.
 *
 * Converts between Java enum (UPPER_CASE) and database VARCHAR (lowercase).
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: InvitationStatus.PENDING (UPPER_CASE)
 * - Database: 'pending' (lowercase)
 *
 * Example conversions:
 * - PENDING ↔ 'pending'
 * - SENT ↔ 'sent'
 * - OPENED ↔ 'opened'
 * - RESPONDED ↔ 'responded'
 * - EXPIRED ↔ 'expired'
 */
@Converter(autoApply = false)
public class InvitationStatusConverter implements AttributeConverter<InvitationStatus, String> {

    @Override
    public String convertToDatabaseColumn(InvitationStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public InvitationStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return InvitationStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid invitation status in database: '%s'. Must be one of: %s",
                            dbData, Arrays.toString(InvitationStatus.values())),
                    e
            );
        }
    }
}
