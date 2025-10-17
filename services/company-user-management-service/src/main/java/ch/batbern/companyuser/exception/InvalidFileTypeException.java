package ch.batbern.companyuser.exception;

/**
 * Exception thrown when uploaded file type is not allowed
 * Allowed types for company logos: PNG, JPG, JPEG, SVG
 */
public class InvalidFileTypeException extends RuntimeException {

    public InvalidFileTypeException(String message) {
        super(message);
    }

    public InvalidFileTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
