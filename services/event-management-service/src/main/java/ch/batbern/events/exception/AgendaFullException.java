package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an INSERT slot assignment (Story 15.3) cannot find a free speaker slot
 * at or after the target position to absorb the reflow — i.e. the agenda is full.
 *
 * HTTP Status: 409 CONFLICT (until Story 17.14 it answered 500: no handler mapped it).
 */
public class AgendaFullException extends DomainException {

    public AgendaFullException(String eventCode) {
        super(HttpStatus.CONFLICT, Severity.LOW, null,
                String.format(
                        "Agenda is full for event '%s' — add a slot in Edit event type before inserting.",
                        eventCode));
    }
}
