package ch.batbern.events.exception;

import java.time.Instant;

/**
 * Thrown when a PUBLIC self-registration (or waitlist join) is attempted after the event's
 * registration deadline has passed (post-event-#2 feedback #1).
 *
 * <p>Closes BOTH the "registered" and "waitlist" paths — the deadline is checked before the
 * capacity branching, so a full event past its deadline returns this rather than a waitlist
 * placement. Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler} with
 * {@code details.code = "REGISTRATION_CLOSED"} and {@code details.deadline}, so the public page can
 * show a "registration closed on {date}" state. Distinct from the capacity 409
 * ({@code capacity_exceeded}). The organizer add-participant path ({@code addParticipant}) is
 * intentionally NOT subject to this guard.
 */
public class RegistrationClosedException extends RuntimeException {

    private final transient Instant deadline;

    public RegistrationClosedException(String eventCode, Instant deadline) {
        super("Registration for event " + eventCode + " closed on " + deadline);
        this.deadline = deadline;
    }

    public Instant getDeadline() {
        return deadline;
    }
}
