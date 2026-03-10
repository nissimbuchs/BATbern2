package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an organizer attempts to start a newsletter send while one is already IN_PROGRESS
 * for the same event. Prevents accidental duplicate sends.
 *
 * Story 10.7 robustness — results in HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateNewsletterSendException extends RuntimeException {

    public DuplicateNewsletterSendException(String message) {
        super(message);
    }
}
