package ch.batbern.events.converter;

import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for SpeakerWorkflowState enum.
 *
 * Converts between Java enum (UPPER_CASE) and database VARCHAR (lowercase_with_underscores).
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: SpeakerWorkflowState.CONTACTED (UPPER_CASE with underscores)
 * - Database: 'contacted' (lowercase with underscores)
 *
 * Example conversions:
 * - IDENTIFIED ↔ 'identified'
 * - CONTACTED ↔ 'contacted'
 * - CONTENT_SUBMITTED ↔ 'content_submitted'
 *
 * Story 5.3: Speaker Outreach Tracking
 *
 * Note: Stored in speaker_pool.status column (event-management-service database)
 *
 * @see SpeakerWorkflowState
 * @see ch.batbern.events.domain.SpeakerPool
 */
@Converter(autoApply = true)
public class SpeakerWorkflowStateConverter implements AttributeConverter<SpeakerWorkflowState, String> {

    /**
     * Converts SpeakerWorkflowState enum to database column value.
     *
     * @param attribute SpeakerWorkflowState enum value (e.g., CONTACTED)
     * @return Database string value (e.g., 'contacted'), or null if attribute is null
     */
    @Override
    public String convertToDatabaseColumn(SpeakerWorkflowState attribute) {
        if (attribute == null) {
            return null;
        }
        // Convert UPPER_CASE to lowercase_with_underscores
        return attribute.name().toLowerCase();
    }

    /**
     * Converts database column value to SpeakerWorkflowState enum.
     *
     * @param dbData Database string value (e.g., 'contacted')
     * @return SpeakerWorkflowState enum value (e.g., CONTACTED), or null if dbData is null
     * @throws IllegalArgumentException if dbData doesn't match any enum constant
     */
    @Override
    public SpeakerWorkflowState convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        // Convert lowercase_with_underscores to UPPER_CASE
        try {
            return SpeakerWorkflowState.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid speaker workflow state in database: '%s'. Must be one of: %s",
                            dbData, java.util.Arrays.toString(SpeakerWorkflowState.values())),
                    e
            );
        }
    }
}
