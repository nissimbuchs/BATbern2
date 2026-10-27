package ch.batbern.events.converter;

import ch.batbern.events.domain.EmailForwardingOutcome;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * JPA converter for {@link EmailForwardingOutcome}: Java {@code UPPER_CASE} ↔ DB {@code lowercase},
 * matching the {@code chk_email_forwarding_log_outcome} CHECK constraint.
 */
@Converter(autoApply = true)
public class EmailForwardingOutcomeConverter implements AttributeConverter<EmailForwardingOutcome, String> {

    @Override
    public String convertToDatabaseColumn(EmailForwardingOutcome attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public EmailForwardingOutcome convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return EmailForwardingOutcome.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid email forwarding outcome in database: '%s'. Must be one of: %s",
                            dbData, Arrays.toString(EmailForwardingOutcome.values())),
                    e);
        }
    }
}
