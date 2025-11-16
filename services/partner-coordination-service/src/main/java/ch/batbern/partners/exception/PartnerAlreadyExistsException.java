package ch.batbern.partners.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting to create a partner that already exists.
 * Extends shared-kernel BATbernException with ERR_DUPLICATE error code.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PartnerAlreadyExistsException extends BATbernException {

    public PartnerAlreadyExistsException(String message) {
        super(ErrorCode.ERR_DUPLICATE, message, Severity.MEDIUM);
    }
}
