package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.4 "Thank the Organizers": thrown when a thank-you is submitted for an event that is
 * not yet live or completed.
 *
 * <p>Thank-yous only open once the event has actually happened — i.e.
 * {@code workflowState IN (EVENT_LIVE, EVENT_COMPLETED)} (AC1). Calling before then is rejected
 * as HTTP 409 Conflict by {@code GlobalExceptionHandler} with body
 * {@code details.code = "THANKS_NOT_ALLOWED"}; no row is created (AC7).
 */
public class ThanksNotAllowedException extends DomainException {

    static final String CODE = "THANKS_NOT_ALLOWED";

    public ThanksNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, message);
    }
}
