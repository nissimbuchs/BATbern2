package ch.batbern.events.exception;

/**
 * Exception thrown when a user is not found in the User Management Service.
 *
 * This exception is thrown when the User Management Service API returns a 404 status,
 * indicating that the requested user does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    private final String username;

    public UserNotFoundException(String username) {
        super("User not found: " + username);
        this.username = username;
    }

    public UserNotFoundException(String username, Throwable cause) {
        super("User not found: " + username, cause);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
