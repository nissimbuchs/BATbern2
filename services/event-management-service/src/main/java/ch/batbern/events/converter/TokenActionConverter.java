package ch.batbern.events.converter;

import ch.batbern.shared.types.TokenAction;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter for TokenAction enum.
 * Story 6.1a: Magic Link Infrastructure
 *
 * Converts between Java enum (TokenAction.RESPOND) and database string ("RESPOND").
 * Unlike other enums that use lowercase in DB, TokenAction is stored as UPPER_CASE
 * to match the CHECK constraint in V43 migration.
 *
 * @see ch.batbern.shared.types.TokenAction
 */
@Converter(autoApply = true)
public class TokenActionConverter implements AttributeConverter<TokenAction, String> {

    @Override
    public String convertToDatabaseColumn(TokenAction action) {
        if (action == null) {
            return null;
        }
        return action.name(); // RESPOND, SUBMIT, VIEW
    }

    @Override
    public TokenAction convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return TokenAction.valueOf(dbValue);
    }
}
