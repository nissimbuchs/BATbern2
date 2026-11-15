package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.2 "I Could Speak on That": thrown when an attendee who already self-nominated for an
 * event tries to do so again. An attendee may have at most one self-nomination per event (AC8).
 *
 * <p>Enforced both by a friendly service-level pre-check and, as a race-safe backstop, by the
 * partial unique index {@code ux_speaker_pool_self_nom}. Mapped to HTTP 409 Conflict by
 * {@code GlobalExceptionHandler} with body {@code details.code = "DUPLICATE_SELF_NOMINATION"}.
 */
public class DuplicateSelfNominationException extends DomainException {

    static final String CODE = "DUPLICATE_SELF_NOMINATION";

    public DuplicateSelfNominationException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, message);
    }
}
