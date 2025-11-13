package ch.batbern.partners.exception;

/**
 * Exception thrown when a user is not found in the User Service.
 *
 * Thrown when User Service API returns 404.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String username) {
        super("User not found: " + username);
    }

    public UserNotFoundException(String username, Throwable cause) {
        super("User not found: " + username, cause);
    }
}
