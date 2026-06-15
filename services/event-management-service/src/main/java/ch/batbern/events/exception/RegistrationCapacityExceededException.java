package ch.batbern.events.exception;

/**
 * Thrown when an organizer adds a participant to an event that is already at capacity
 * and did NOT pass {@code force=true}. Mapped to HTTP 409 with
 * {@code details.code = "capacity_exceeded"} so the "Add participant" dialog can offer an
 * explicit "add anyway" (re-submit with force) instead of a generic error.
 *
 * <p>Distinct from the duplicate-registration 409 (a plain {@link IllegalStateException}):
 * the frontend keys on the {@code code} to decide whether to show the force-confirm.
 */
public class RegistrationCapacityExceededException extends RuntimeException {

    private final long activeCount;
    private final int capacity;

    public RegistrationCapacityExceededException(String eventCode, long activeCount, int capacity) {
        super("Event " + eventCode + " is at capacity (" + activeCount + "/" + capacity
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
