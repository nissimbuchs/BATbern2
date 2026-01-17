package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when uploaded file type is not allowed
 * Story 5.9: Session Materials Upload
 *
 * Allowed file types (AC6):
 * - Presentations: .pptx, .ppt, .key, .odp
 * - Documents: .pdf, .doc, .docx, .txt
 * - Videos: .mp4, .mov, .avi, .mkv, .webm
 * - Archives: .zip, .tar.gz
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFileTypeException extends RuntimeException {

    public InvalidFileTypeException(String message) {
        super(message);
    }

    public InvalidFileTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
