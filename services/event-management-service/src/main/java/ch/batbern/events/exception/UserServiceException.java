package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when there is an error communicating with the User Management Service.
 *
 * This exception wraps any errors that occur during REST API communication,
 * including network errors, timeouts, and 5xx HTTP status codes.
 *
 * <p>The response repeats the status CUMS answered when it is an error status (4xx, 5xx), e.g. the 400
 * of a bio that is too long; anything else (no status, a redirect) answers 502 Bad Gateway. A 5xx is
 * severity HIGH, a 4xx MEDIUM.
 */
public class UserServiceException extends DomainException {

    private final Integer statusCode;

    public UserServiceException(String message) {
        this(message, (Integer) null);
    }

    public UserServiceException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public UserServiceException(String message, Integer statusCode) {
        super(responseStatus(statusCode), severity(statusCode), null, message);
        this.statusCode = statusCode;
    }

    public UserServiceException(String message, Integer statusCode, Throwable cause) {
        super(responseStatus(statusCode), severity(statusCode), null, message, cause);
        this.statusCode = statusCode;
    }

    /** The status CUMS answered, or {@code null} when the call got no HTTP answer. */
    public Integer getStatusCode() {
        return statusCode;
    }

    private static HttpStatus responseStatus(Integer cumsStatus) {
        HttpStatus status = cumsStatus != null && cumsStatus >= 400 && cumsStatus < 600
                ? HttpStatus.resolve(cumsStatus) : null;
        return status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    private static Severity severity(Integer cumsStatus) {
        return responseStatus(cumsStatus).is5xxServerError() ? Severity.HIGH : Severity.MEDIUM;
    }
}
