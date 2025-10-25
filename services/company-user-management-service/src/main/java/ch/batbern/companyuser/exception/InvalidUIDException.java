package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.ValidationException;

/**
 * Exception thrown when Swiss UID validation fails
 * AC3: Swiss UID validation
 *
 * Extends shared-kernel ValidationException for consistent error handling
 */
public class InvalidUIDException extends ValidationException {

    public InvalidUIDException(String uid) {
        super("Invalid Swiss UID: " + uid);
    }
}
