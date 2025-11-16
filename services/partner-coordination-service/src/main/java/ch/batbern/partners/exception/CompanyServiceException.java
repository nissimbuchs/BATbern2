package ch.batbern.partners.exception;

/**
 * Exception thrown when communication with Company Service fails.
 *
 * Thrown when Company Service API returns 5xx or network errors occur.
 */
public class CompanyServiceException extends RuntimeException {

    private final Integer statusCode;

    public CompanyServiceException(String message) {
        super(message);
        this.statusCode = null;
    }

    public CompanyServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public CompanyServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
