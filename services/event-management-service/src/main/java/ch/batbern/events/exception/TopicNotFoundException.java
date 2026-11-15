package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a topic is not found.
 * Used to indicate HTTP 404 Not Found responses.
 */
public class TopicNotFoundException extends DomainException {

    public TopicNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, message);
    }

    public TopicNotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, message, cause);
    }
}
