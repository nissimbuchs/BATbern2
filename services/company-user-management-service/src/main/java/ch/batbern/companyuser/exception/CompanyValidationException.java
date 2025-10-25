package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.ValidationException;

/**
 * Exception thrown when company data fails validation
 * AC3: Data validation and business rules
 *
 * Extends shared-kernel ValidationException for consistent error handling
 */
public class CompanyValidationException extends ValidationException {

    public CompanyValidationException(String message) {
        super(message);
    }
}
