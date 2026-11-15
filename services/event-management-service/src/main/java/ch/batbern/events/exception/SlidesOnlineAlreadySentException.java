package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.3 — thrown when an organizer attempts to send the "slides are online" mail for an
 * event that already has a terminal slides-online send (COMPLETED or PARTIAL).
 *
 * <p>Complements the in-progress guard (which reuses
 * {@link DuplicateNewsletterSendException}): together they ensure the one-shot, event-triggered
 * slides-online mail can never be sent twice for the same event. Results in HTTP 409 Conflict.
 */
public class SlidesOnlineAlreadySentException extends DomainException {

    public SlidesOnlineAlreadySentException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, null, message);
    }
}
