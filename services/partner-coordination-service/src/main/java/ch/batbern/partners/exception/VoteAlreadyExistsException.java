package ch.batbern.partners.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a partner attempts to vote on a topic they've already voted on.
 * Extends shared-kernel BATbernException with ERR_DUPLICATE error code.
 *
 * HTTP Status: 409 CONFLICT (vote uniqueness constraint violation)
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class VoteAlreadyExistsException extends BATbernException {

    public VoteAlreadyExistsException(String message) {
        super(ErrorCode.ERR_DUPLICATE, message, Severity.LOW);
    }
}
