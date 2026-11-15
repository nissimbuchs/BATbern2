package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.7 "Curated Thank-You Notes": thrown when an organizer tries to feature/un-feature a
 * thank-you note that does not exist for the given event.
 *
 * <p>Translated to HTTP 404 Not Found by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_NOT_FOUND"}.
 */
public class ThanksNotFoundException extends DomainException {

    static final String CODE = "THANKS_NOT_FOUND";

    public ThanksNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, CODE, message);
    }
}
