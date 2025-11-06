package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Exception thrown when a speaker assignment is not found
 * Story 1.16.2: Migrate to API-based user data
 *
 * Extends shared-kernel NotFoundException for consistent error handling across services
 */
public class SpeakerAssignmentNotFoundException extends NotFoundException {

    public SpeakerAssignmentNotFoundException(UUID sessionId, String username) {
        super("Speaker assignment not found for user " + username + " and session " + sessionId);
    }

    public SpeakerAssignmentNotFoundException(String message) {
        super(message);
    }
}
