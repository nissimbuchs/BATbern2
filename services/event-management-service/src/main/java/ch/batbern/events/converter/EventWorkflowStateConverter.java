package ch.batbern.events.converter;

import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for EventWorkflowState enum.
 *
 * Converts between Java enum (UPPER_CASE) and database VARCHAR (lowercase_with_underscores).
 *
 * Database Storage Pattern (per coding-standards.md):
 * - Java: EventWorkflowState.SPEAKER_OUTREACH (UPPER_CASE with underscores)
 * - Database: 'speaker_outreach' (lowercase with underscores)
 *
 * Example conversions:
 * - CREATED ↔ 'created'
 * - SPEAKER_OUTREACH ↔ 'speaker_outreach'
 * - AGENDA_PUBLISHED ↔ 'agenda_published'
 *
 * Story 5.1a: Workflow State Machine Foundation - AC8
 *
 * @see EventWorkflowState
 * @see ch.batbern.events.domain.Event
 */
@Converter(autoApply = true)
public class EventWorkflowStateConverter implements AttributeConverter<EventWorkflowState, String> {

    /**
     * Converts EventWorkflowState enum to database column value.
     *
     * @param attribute EventWorkflowState enum value (e.g., SPEAKER_OUTREACH)
     * @return Database string value (e.g., 'speaker_outreach'), or null if attribute is null
     */
    @Override
    public String convertToDatabaseColumn(EventWorkflowState attribute) {
        if (attribute == null) {
            return null;
        }
        // Convert UPPER_CASE to lowercase_with_underscores
        return attribute.name().toLowerCase();
    }

    /**
     * Converts database column value to EventWorkflowState enum.
     *
     * @param dbData Database string value (e.g., 'speaker_outreach')
     * @return EventWorkflowState enum value (e.g., SPEAKER_OUTREACH), or null if dbData is null
     * @throws IllegalArgumentException if dbData doesn't match any enum constant
     */
    @Override
    public EventWorkflowState convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        // Convert lowercase_with_underscores to UPPER_CASE
        try {
            return EventWorkflowState.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid workflow state in database: '%s'. Must be one of: %s",
                            dbData, java.util.Arrays.toString(EventWorkflowState.values())),
                    e
            );
        }
    }
}
