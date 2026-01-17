package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a speaker invitation is not found - Story 6.1.
 *
 * Extends shared-kernel NotFoundException for consistent error handling.
 */
public class InvitationNotFoundException extends NotFoundException {

    public InvitationNotFoundException(String token) {
        super("SpeakerInvitation", token);
    }

    public InvitationNotFoundException(String username, String eventCode) {
        super("SpeakerInvitation for " + username + "/" + eventCode);
    }
}
