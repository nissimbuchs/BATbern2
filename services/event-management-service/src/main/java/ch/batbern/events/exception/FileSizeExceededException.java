package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when uploaded file size exceeds the allowed limit
 * Story 5.9: Session Materials Upload
 *
 * Maximum file size: 100MB for materials (AC1)
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FileSizeExceededException extends RuntimeException {

    public FileSizeExceededException(String message) {
        super(message);
    }

    public FileSizeExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
