package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a magic link token is invalid.
 * Story 6.2a: Invitation Response Portal - AC1
 *
 * Results in HTTP 401 Unauthorized response.
 * Error codes: TOKEN_EXPIRED, TOKEN_USED, TOKEN_NOT_FOUND
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends RuntimeException {

    private final String errorCode;

    public InvalidTokenException(String errorCode) {
        super("Invalid token: " + errorCode);
        this.errorCode = errorCode;
    }

    public InvalidTokenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Create exception for expired token.
     */
    public static InvalidTokenException expired() {
        return new InvalidTokenException("EXPIRED", "Token has expired. Please contact the organizer for a new invitation.");
    }

    /**
     * Create exception for already used token.
     */
    public static InvalidTokenException alreadyUsed() {
        return new InvalidTokenException("ALREADY_USED", "This invitation link has already been used.");
    }

    /**
     * Create exception for not found token.
     */
    public static InvalidTokenException notFound() {
        return new InvalidTokenException("NOT_FOUND", "Invalid invitation link. Please check the URL or contact the organizer.");
    }
}
