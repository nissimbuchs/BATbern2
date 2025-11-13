package ch.batbern.partners.exception;

/**
 * Exception thrown when communication with User Service fails.
 *
 * Thrown when User Service API returns 5xx or network errors occur.
 */
public class UserServiceException extends RuntimeException {

    private final Integer statusCode;

    public UserServiceException(String message) {
        super(message);
        this.statusCode = null;
    }

    public UserServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public UserServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
