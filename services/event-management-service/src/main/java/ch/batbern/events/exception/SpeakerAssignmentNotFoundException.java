package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a speaker assignment is not found
 * Story 1.16.2: Migrate to API-based user data
 *
 * Extends shared-kernel NotFoundException for consistent error handling across services
 */
public class SpeakerAssignmentNotFoundException extends DomainException {

    public SpeakerAssignmentNotFoundException(UUID sessionId, String username) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null,
                "Speaker assignment not found for user " + username + " and session " + sessionId);
    }

    public SpeakerAssignmentNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, message);
    }
}
