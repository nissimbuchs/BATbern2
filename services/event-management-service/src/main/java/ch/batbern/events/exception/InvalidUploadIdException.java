package ch.batbern.events.exception;

import ch.batbern.shared.exception.ValidationException;

/**
 * Exception thrown when an upload ID is invalid or not found
 * Story 5.9: Session Materials Upload
 */
public class InvalidUploadIdException extends ValidationException {
    public InvalidUploadIdException(String uploadId) {
        super("Invalid upload ID: " + uploadId);
    }
}
