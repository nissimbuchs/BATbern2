package ch.batbern.companyuser.exception;

/**
 * Exception thrown when Swiss UID validation fails
 * AC3: Swiss UID validation
 */
public class InvalidUIDException extends RuntimeException {

    public InvalidUIDException(String uid) {
        super("Invalid Swiss UID: " + uid);
    }

    public InvalidUIDException(String uid, Throwable cause) {
        super("Invalid Swiss UID: " + uid, cause);
    }
}
