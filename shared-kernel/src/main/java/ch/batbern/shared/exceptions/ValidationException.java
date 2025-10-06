package ch.batbern.shared.exceptions;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends BATbernException {
    public ValidationException(String message) {
        super(ErrorCode.ERR_VALIDATION, message, Severity.MEDIUM);
    }

    public ValidationException(String message, Throwable cause) {
        super(ErrorCode.ERR_VALIDATION, message, Severity.MEDIUM);
        initCause(cause);
    }

    public ValidationException(String field, String message, Map<String, Object> details) {
        super(
            ErrorCode.ERR_VALIDATION,
            String.format("Validation failed for field '%s': %s", field, message),
            details,
            Severity.MEDIUM
        );
    }

    public static ValidationException nullValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be null");
    }

    public static ValidationException emptyValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be empty");
    }

    public static ValidationException blankValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be blank");
    }

    public static ValidationException invalidFormat(String fieldName, String expectedFormat) {
        return new ValidationException(String.format("Invalid %s format. Expected: %s", fieldName, expectedFormat));
    }

    public static ValidationException invalidValue(String fieldName, String value, String reason) {
        return new ValidationException(String.format("Invalid %s: '%s'. %s", fieldName, value, reason));
    }
}
