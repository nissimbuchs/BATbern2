package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when someone tries to unregister on or after the event day (#1238 E2): unregistering is
 * possible until the end of the day before the event, Bern time. Mapped to HTTP 409 Conflict with
 * {@code details.code = "DEREGISTRATION_CLOSED"} by {@code GlobalExceptionHandler}.
 */
public class DeregistrationClosedException extends DomainException {

    static final String CODE = "DEREGISTRATION_CLOSED";

    public DeregistrationClosedException(String eventCode) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE,
                "Unregistering from event " + eventCode + " is closed from the event day on");
    }
}
