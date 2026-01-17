package ch.batbern.events.exception;

import ch.batbern.shared.exception.InvalidStateTransitionException;

import java.util.Map;

/**
 * Exception thrown when attempting to respond to an expired invitation - Story 6.1.
 *
 * Uses InvalidStateTransitionException since expired is an invalid state for responding.
 */
public class InvitationExpiredException extends InvalidStateTransitionException {

    public InvitationExpiredException(String token) {
        super(
            "This invitation has expired and can no longer be responded to",
            Map.of("token", token, "issue", "expired")
        );
    }
}
