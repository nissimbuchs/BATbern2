package ch.batbern.events.exception;

/**
 * Exception thrown when workflow state transition is invalid
 * Story 1.15a.1: Events API Consolidation - AC8
 *
 * Results in HTTP 422 Unprocessable Entity response
 */
public class WorkflowException extends RuntimeException {

    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
