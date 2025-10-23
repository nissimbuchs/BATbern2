package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.ValidationException;

import java.util.Map;

/**
 * Exception thrown for user validation failures.
 * Extends shared-kernel ValidationException for consistent error handling.
 */
public class UserValidationException extends ValidationException {
    public UserValidationException(String message) {
        super(message);
    }

    public UserValidationException(String field, String message) {
        super(field, message);
    }

    public UserValidationException(String message, Map<String, Object> details) {
        super(message, details);
    }
}
