package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an email is already an active newsletter subscriber.
 * Story 10.7: Newsletter Subscription — AC1 (409 on duplicate active subscriber).
 *
 * Results in HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateSubscriberException extends RuntimeException {

    public DuplicateSubscriberException(String email) {
        super("Email is already an active newsletter subscriber: " + email);
    }
}
