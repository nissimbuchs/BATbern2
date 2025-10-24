package ch.batbern.events.exception;

import ch.batbern.shared.exception.ValidationException;

import java.util.Map;

/**
 * Exception thrown when workflow state transition is invalid
 * Story 1.15a.1: Events API Consolidation - AC8
 *
 * Results in HTTP 422 Unprocessable Entity response
 * Extends shared-kernel ValidationException for consistent error handling across services
 */
public class WorkflowException extends ValidationException {

    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Map<String, Object> details) {
        super(message, details);
    }
}
