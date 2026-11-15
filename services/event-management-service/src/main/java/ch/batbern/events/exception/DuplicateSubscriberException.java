package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an email is already an active newsletter subscriber.
 * Story 10.7: Newsletter Subscription — AC1 (409 on duplicate active subscriber).
 *
 * Results in HTTP 409 Conflict.
 */
public class DuplicateSubscriberException extends DomainException {

    public DuplicateSubscriberException(String email) {
        super(HttpStatus.CONFLICT, Severity.LOW, null, "Email is already an active newsletter subscriber: " + email);
    }
}
