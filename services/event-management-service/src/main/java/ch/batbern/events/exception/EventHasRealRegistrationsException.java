package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException;
import ch.batbern.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an event cannot be deleted because it still has real (self-registered)
 * attendees.
 *
 * <p>Programmatic registrations — organizers/partners auto-enrolled at event creation and
 * auto-registered speakers (those carrying an {@code autoRegisteredFrom} metadata marker) — do
 * NOT count and never block deletion. Only genuine attendee sign-ups protect an event from
 * deletion, so the organizer must cancel the event instead.
 *
 * HTTP Status: 409 CONFLICT
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EventHasRealRegistrationsException extends BATbernException {

    public EventHasRealRegistrationsException(String eventCode, long realAttendeeCount) {
        super(ErrorCode.ERR_CONFLICT,
                String.format(
                        "Event '%s' has %d real attendee registration(s) and cannot be deleted. "
                                + "Cancel the event instead.",
                        eventCode, realAttendeeCount),
                Severity.LOW);
    }
}
