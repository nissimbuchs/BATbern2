package ch.batbern.shared.upload;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** {@link UploadStatus} in the database: lower case, as every enum column (coding standards). */
@Converter
public class UploadStatusConverter implements AttributeConverter<UploadStatus, String> {

    @Override
    public String convertToDatabaseColumn(UploadStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public UploadStatus convertToEntityAttribute(String value) {
        return value == null ? null : UploadStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
