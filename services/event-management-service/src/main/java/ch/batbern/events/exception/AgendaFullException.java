package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an INSERT slot assignment (Story 15.3) cannot find a free speaker slot
 * at or after the target position to absorb the reflow — i.e. the agenda is full.
 *
 * HTTP Status: 409 CONFLICT
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AgendaFullException extends BATbernException {

    public AgendaFullException(String eventCode) {
        super(ErrorCode.ERR_CONFLICT,
                String.format(
                        "Agenda is full for event '%s' — add a slot in Edit event type before inserting.",
                        eventCode),
                Severity.LOW);
    }
}
