package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.4 "Thank the Organizers": thrown when anonymous thank-you submissions for one event
 * from a single client IP exceed the per-(event,IP) cap (Resolved Decision #3, AC3/AC5).
 *
 * <p>Rejected as HTTP 429 Too Many Requests by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_RATE_LIMITED"}. The submission is rejected BEFORE any row is
 * inserted, so the aggregate counter is not incremented (AC3 — "without incrementing").
 */
public class ThanksRateLimitedException extends DomainException {

    static final String CODE = "THANKS_RATE_LIMITED";

    public ThanksRateLimitedException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, Severity.LOW, CODE, message);
    }
}
