package ch.batbern.events.exception;

/**
 * Exception thrown when there is an error communicating with the User Management Service.
 *
 * This exception wraps any errors that occur during REST API communication,
 * including network errors, timeouts, and 5xx HTTP status codes.
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

    public UserServiceException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public UserServiceException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
