package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an organizer attempts to start a newsletter send while one is already IN_PROGRESS
 * for the same event. Prevents accidental duplicate sends.
 *
 * Story 10.7 robustness — results in HTTP 409 Conflict.
 */
public class DuplicateNewsletterSendException extends DomainException {

    public DuplicateNewsletterSendException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, null, message);
    }
}
