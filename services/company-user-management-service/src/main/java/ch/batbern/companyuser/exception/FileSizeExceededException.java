package ch.batbern.companyuser.exception;

/**
 * Exception thrown when uploaded file size exceeds the maximum allowed limit
 * Maximum file size for company logos is 5 MB
 */
public class FileSizeExceededException extends RuntimeException {

    public FileSizeExceededException(String message) {
        super(message);
    }

    public FileSizeExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
