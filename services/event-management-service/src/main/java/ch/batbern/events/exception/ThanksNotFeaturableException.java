package ch.batbern.events.exception;

/**
 * Story 7.7 "Curated Thank-You Notes": thrown when an organizer tries to feature an ANONYMOUS
 * thank-you ({@code thanked_by_username IS NULL}). Anonymous claps have no name and can never be
 * featured on the public marquee — this preserves Story 7.4 AC6's anti-troll guarantee.
 *
 * <p>Translated to HTTP 409 Conflict by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_NOT_FEATURABLE"}.
 */
public class ThanksNotFeaturableException extends RuntimeException {

    public ThanksNotFeaturableException(String message) {
        super(message);
    }
}
