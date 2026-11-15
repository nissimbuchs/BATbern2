package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when structural sessions (moderation/break/lunch) already exist
 * for an event and overwrite=false was requested.
 *
 * HTTP Status: 409 CONFLICT
 */
public class StructuralSessionsAlreadyExistException extends DomainException {

    public StructuralSessionsAlreadyExistException(String eventCode) {
        super(HttpStatus.CONFLICT, Severity.MEDIUM, null,
                String.format(
                        "Structural sessions already exist for event '%s'. Use overwrite=true to replace them.",
                        eventCode));
    }
}
