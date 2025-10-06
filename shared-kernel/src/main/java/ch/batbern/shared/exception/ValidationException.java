package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends BATbernException {
    public ValidationException(String message) {
        super(ErrorCode.ERR_VALIDATION, message, Severity.MEDIUM);
    }

    public ValidationException(String field, String message) {
        super(
            ErrorCode.ERR_VALIDATION,
            String.format("Validation failed for field '%s': %s", field, message),
            Map.of("field", field),
            Severity.MEDIUM
        );
    }

    public ValidationException(String message, Map<String, Object> details) {
        super(ErrorCode.ERR_VALIDATION, message, details, Severity.MEDIUM);
    }
}
