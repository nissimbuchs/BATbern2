package ch.batbern.events.exception;

/**
 * Exception thrown when business logic validation fails
 * Story 1.15a.1: Events API Consolidation - AC7
 *
 * Results in HTTP 422 Unprocessable Entity response with error code VALIDATION_ERROR
 */
public class BusinessValidationException extends RuntimeException {

    public BusinessValidationException(String message) {
        super(message);
    }

    public BusinessValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
