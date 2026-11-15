package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

/**
 * Exception thrown when a workflow state transition fails business rule validation.
 *
 * This exception is thrown when attempting to transition to a state but the event
 * doesn't meet the required conditions (e.g., insufficient speakers, content not submitted).
 *
 * Example: Attempting to transition to SPEAKER_OUTREACH but only 3 speakers identified (need 6)
 *
 * Story 5.1a: Workflow State Machine Foundation - AC5
 */
@SuppressWarnings("deprecation") // UNPROCESSABLE_ENTITY keeps the error text "Unprocessable Entity" clients see
public class WorkflowValidationException extends DomainException {

    private final Map<String, Object> context;

    public WorkflowValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.HIGH, null, message);
        this.context = Collections.emptyMap();
    }

    public WorkflowValidationException(String message, Map<String, Object> context) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.HIGH, null, message);
        this.context = context != null ? context : Collections.emptyMap();
    }

    public WorkflowValidationException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.HIGH, null, message, cause);
        this.context = Collections.emptyMap();
    }

    public WorkflowValidationException(String message, Map<String, Object> context, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, Severity.HIGH, null, message, cause);
        this.context = context != null ? context : Collections.emptyMap();
    }

    /**
     * Gets additional context information about the validation failure.
     *
     * @return Map containing context details (e.g., {"required": 6, "identified": 3})
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
