package ch.batbern.companyuser.exception;

/**
 * Exception thrown when company data fails validation
 * AC3: Data validation and business rules
 */
public class CompanyValidationException extends RuntimeException {

    public CompanyValidationException(String message) {
        super(message);
    }

    public CompanyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
