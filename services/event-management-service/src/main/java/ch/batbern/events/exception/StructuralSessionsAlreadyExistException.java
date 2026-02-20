package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when structural sessions (moderation/break/lunch) already exist
 * for an event and overwrite=false was requested.
 *
 * HTTP Status: 409 CONFLICT
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class StructuralSessionsAlreadyExistException extends BATbernException {

    public StructuralSessionsAlreadyExistException(String eventCode) {
        super(ErrorCode.ERR_CONFLICT,
                String.format(
                        "Structural sessions already exist for event '%s'. Use overwrite=true to replace them.",
                        eventCode),
                Severity.LOW);
    }
}
