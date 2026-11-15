package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.7 "Curated Thank-You Notes": thrown when an organizer tries to feature an ANONYMOUS
 * thank-you ({@code thanked_by_username IS NULL}). Anonymous claps have no name and can never be
 * featured on the public marquee — this preserves Story 7.4 AC6's anti-troll guarantee.
 *
 * <p>Translated to HTTP 409 Conflict by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_NOT_FEATURABLE"}.
 */
public class ThanksNotFeaturableException extends DomainException {

    static final String CODE = "THANKS_NOT_FEATURABLE";

    public ThanksNotFeaturableException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, message);
    }
}
