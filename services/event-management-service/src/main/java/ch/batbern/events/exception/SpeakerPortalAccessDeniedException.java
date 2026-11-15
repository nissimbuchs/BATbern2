package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 11.E.3: thrown when an authenticated SPEAKER calls a speaker-portal endpoint for an
 * {@code eventCode} they have no pool row for (or an event that doesn't exist).
 *
 * <p>Maps to HTTP 403 by {@link GlobalExceptionHandler}. The exception is deliberately
 * coarser than 404 — exposing whether the event exists separately from whether the speaker
 * has access is an information-disclosure risk.
 */
public class SpeakerPortalAccessDeniedException extends DomainException {

    public SpeakerPortalAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, Severity.MEDIUM, null, message);
    }
}
