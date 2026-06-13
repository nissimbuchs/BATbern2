package ch.batbern.events.exception;

/**
 * Story 7.7 "Curated Thank-You Notes": thrown when an organizer tries to feature/un-feature a
 * thank-you note that does not exist for the given event.
 *
 * <p>Translated to HTTP 404 Not Found by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_NOT_FOUND"}.
 */
public class ThanksNotFoundException extends RuntimeException {

    public ThanksNotFoundException(String message) {
        super(message);
    }
}
