package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * Exception thrown when an invalid state transition is attempted.
 * Story 5.4: Speaker Status Management - AC12
 *
 * Returns HTTP 422 (Unprocessable Entity) - The request was well-formed but semantically incorrect.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidStateTransitionException extends BATbernException {

    public InvalidStateTransitionException(String from, String to) {
        super(
            ErrorCode.ERR_INVALID_STATE_TRANSITION,
            String.format("Invalid state transition from '%s' to '%s'", from, to),
            Map.of("fromState", from, "toState", to),
            Severity.MEDIUM
        );
    }

    public InvalidStateTransitionException(String message) {
        super(ErrorCode.ERR_INVALID_STATE_TRANSITION, message, Severity.MEDIUM);
    }

    public InvalidStateTransitionException(String message, Map<String, Object> details) {
        super(ErrorCode.ERR_INVALID_STATE_TRANSITION, message, details, Severity.MEDIUM);
    }
}
