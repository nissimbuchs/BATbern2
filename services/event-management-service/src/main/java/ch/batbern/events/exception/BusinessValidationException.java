package ch.batbern.events.exception;

import ch.batbern.shared.exception.ValidationException;

import java.util.Map;

/**
 * Exception thrown when business logic validation fails
 * Story 1.15a.1: Events API Consolidation - AC7
 *
 * Results in HTTP 422 Unprocessable Entity response with error code VALIDATION_ERROR
 * Extends shared-kernel ValidationException for consistent error handling across services
 */
public class BusinessValidationException extends ValidationException {

    public BusinessValidationException(String message) {
        super(message);
    }

    public BusinessValidationException(String field, String message) {
        super(field, message);
    }

    public BusinessValidationException(String message, Map<String, Object> details) {
        super(message, details);
    }
}
