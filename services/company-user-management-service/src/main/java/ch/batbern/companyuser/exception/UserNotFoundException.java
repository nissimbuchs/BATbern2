package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a user is not found.
 * Extends shared-kernel NotFoundException for consistent error handling.
 */
public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(String userId) {
        super("User", userId);
    }

    public UserNotFoundException(String message, boolean customMessage) {
        super(message);
    }
}
