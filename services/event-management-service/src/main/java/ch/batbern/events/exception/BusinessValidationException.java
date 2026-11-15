package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when business logic validation fails
 * Story 1.15a.1: Events API Consolidation - AC7
 *
 * Results in HTTP 422 Unprocessable Entity response with error code VALIDATION_ERROR
 * Extends shared-kernel ValidationException for consistent error handling across services
 */
@SuppressWarnings("deprecation") // UNPROCESSABLE_ENTITY keeps the error text "Unprocessable Entity" clients see
public class BusinessValidationException extends DomainException {

    private final Map<String, Object> details;

    public BusinessValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.MEDIUM, null, message);
        this.details = Map.of();
    }

    public BusinessValidationException(String field, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.MEDIUM, null,
                String.format("Validation failed for field '%s': %s", field, message));
        this.details = Map.of("field", field);
    }

    public BusinessValidationException(String message, Map<String, Object> details) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.MEDIUM, null, message);
        this.details = details != null ? Collections.unmodifiableMap(new HashMap<>(details)) : Map.of();
    }

    /** Context for logs and callers; the error response does not render it. */
    public Map<String, Object> getDetails() {
        return details;
    }
}
