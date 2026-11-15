package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an organizer adds a participant to an event that is already at capacity
 * and did NOT pass {@code force=true}. Mapped to HTTP 409 with
 * {@code details.code = "capacity_exceeded"} so the "Add participant" dialog can offer an
 * explicit "add anyway" (re-submit with force) instead of a generic error.
 *
 * <p>Distinct from the duplicate-registration 409 (a plain {@link IllegalStateException}):
 * the frontend keys on the {@code code} to decide whether to show the force-confirm.
 */
public class RegistrationCapacityExceededException extends DomainException {

    static final String CODE = "capacity_exceeded";

    private final long activeCount;
    private final int capacity;

    public RegistrationCapacityExceededException(String eventCode, long activeCount, int capacity) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE,
                "Event " + eventCode + " is at capacity (" + activeCount + "/" + capacity
                + "); pass force=true to add over capacity");
        this.activeCount = activeCount;
        this.capacity = capacity;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public int getCapacity() {
        return capacity;
    }
}
