package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when someone registers for, or an organizer adds a participant to, an event that was
 * cancelled (#824). HTTP 409 with {@code details.code = "EVENT_CANCELLED"}.
 */
public class EventCancelledException extends DomainException {

    static final String CODE = "EVENT_CANCELLED";

    public EventCancelledException(String eventCode) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, "Event " + eventCode + " was cancelled");
    }
}
