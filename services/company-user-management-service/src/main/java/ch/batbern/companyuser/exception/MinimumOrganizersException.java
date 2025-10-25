package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * Exception thrown when business rules are violated (e.g., minimum organizers).
 * Returns HTTP 422 (Unprocessable Entity) for business rule violations.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class MinimumOrganizersException extends BATbernException {
    public MinimumOrganizersException(String message) {
        super(ErrorCode.ERR_BUSINESS_RULE, message, Severity.HIGH);
    }

    public MinimumOrganizersException(String message, Map<String, Object> details) {
        super(ErrorCode.ERR_BUSINESS_RULE, message, details, Severity.HIGH);
    }
}
