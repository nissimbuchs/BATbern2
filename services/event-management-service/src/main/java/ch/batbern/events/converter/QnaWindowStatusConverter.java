package ch.batbern.events.converter;

import ch.batbern.events.domain.QnaWindowStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * JPA converter for {@link QnaWindowStatus} (Story 7.5).
 *
 * <p>Java {@code UPPER_CASE} ↔ DB {@code lowercase} — matches the project's enum-value-flow rule
 * and the {@code session_qna_window.status} CHECK constraint ({@code 'open' | 'frozen'}).
 */
@Converter(autoApply = true)
public class QnaWindowStatusConverter implements AttributeConverter<QnaWindowStatus, String> {

    @Override
    public String convertToDatabaseColumn(QnaWindowStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public QnaWindowStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return QnaWindowStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid Q&A window status in database: '%s'. Must be one of: %s",
                            dbData, Arrays.toString(QnaWindowStatus.values())),
                    e);
        }
    }
}
