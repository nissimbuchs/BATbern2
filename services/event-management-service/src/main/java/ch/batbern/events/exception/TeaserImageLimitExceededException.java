package ch.batbern.events.exception;

/**
 * Exception thrown when the maximum number of teaser images (10) per event is reached.
 * Maps to HTTP 422 Unprocessable Entity via GlobalExceptionHandler.
 * Story 10.22: Event Teaser Images — AC6
 */
public class TeaserImageLimitExceededException extends RuntimeException {

    public TeaserImageLimitExceededException(String eventCode, int maxImages) {
        super("Maximum of " + maxImages + " teaser images reached for event: " + eventCode);
    }

    public TeaserImageLimitExceededException(int maxImages) {
        super("Maximum of " + maxImages + " global teaser images reached");
    }
}
