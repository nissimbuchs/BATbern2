package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a speaker assignment is not found
 * Story 1.16.2: Migrate to API-based user data
 *
 * HTTP 404, severity LOW, without {@code details}: mapped through its {@link DomainException} base
 * (Story 17.14).
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
