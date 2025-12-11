package ch.batbern.events.exception;

import ch.batbern.shared.types.EventWorkflowState;

/**
 * Exception thrown when an invalid state transition is attempted.
 *
 * This exception is thrown when attempting to transition an event from one workflow
 * state to another state that is not allowed by the state machine rules.
 *
 * Example: Attempting to transition from CREATED directly to ARCHIVED (skipping all intermediate states)
 *
 * Story 5.1a: Workflow State Machine Foundation - AC6
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final EventWorkflowState fromState;
    private final EventWorkflowState toState;

    public InvalidStateTransitionException(EventWorkflowState fromState, EventWorkflowState toState) {
        super(String.format("Invalid transition from %s to %s", fromState, toState));
        this.fromState = fromState;
        this.toState = toState;
    }

    public InvalidStateTransitionException(
            EventWorkflowState fromState,
            EventWorkflowState toState,
            String additionalMessage) {
        super(String.format(
                "Invalid transition from %s to %s: %s",
                fromState,
                toState,
                additionalMessage));
        this.fromState = fromState;
        this.toState = toState;
    }

    public EventWorkflowState getFromState() {
        return fromState;
    }

    public EventWorkflowState getToState() {
        return toState;
    }
}
