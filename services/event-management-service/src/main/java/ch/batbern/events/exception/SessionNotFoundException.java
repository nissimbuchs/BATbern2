package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a session cannot be found
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public class SessionNotFoundException extends DomainException {

    public SessionNotFoundException(String sessionSlug) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Session not found: " + sessionSlug);
    }

    /**
     * W4.2 review fix item 3: includes eventCode so multi-event debugging is faster.
     */
    public SessionNotFoundException(String sessionSlug, String eventCode) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null,
                String.format("sessionSlug '%s' not found in event '%s'", sessionSlug, eventCode));
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, message, cause);
    }
}
