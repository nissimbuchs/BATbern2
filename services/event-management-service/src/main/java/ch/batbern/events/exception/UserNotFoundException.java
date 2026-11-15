package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user is not found in the User Management Service.
 *
 * This exception is thrown when the User Management Service API returns a 404 status,
 * indicating that the requested user does not exist.
 */
public class UserNotFoundException extends DomainException {

    private final String username;

    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "User not found: " + username);
        this.username = username;
    }

    public UserNotFoundException(String username, Throwable cause) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "User not found: " + username, cause);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
