package ch.batbern.events.exception;

/**
 * Exception thrown when a topic is not found.
 * Used to indicate HTTP 404 Not Found responses.
 */
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(String message) {
        super(message);
    }

    public TopicNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
