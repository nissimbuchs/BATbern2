package ch.batbern.gateway.auth.exception;

/**
 * Exception thrown when rate limit is exceeded for password reset requests
 *
 * Story 1.2.2 - AC13: Rate limiting (3 requests per hour)
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
