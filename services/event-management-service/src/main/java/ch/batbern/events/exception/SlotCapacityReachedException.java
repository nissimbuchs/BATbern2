package ch.batbern.events.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when the {@code READY → INVITED} (or on-behalf {@code READY → ACCEPTED}) transition
 * would push the accepted track ({@code ACCEPTED}, {@code CONTENT_SUBMITTED},
 * {@code QUALITY_REVIEWED}) plus {@code INVITED} above the event's effective {@code maxSlots}
 * (per-event agenda override, else the event-type template) — see
 * ADR-009 §0.7 (slot-capacity gate replaces removed {@code OVERFLOW} state).
 *
 * <p>Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler}: the request is well-formed,
 * but the world isn't ready for it.
 */
@Getter
public class SlotCapacityReachedException extends RuntimeException {

    private final UUID eventId;
    private final long acceptedCount;
    private final long invitedCount;
    private final int maxSlots;

    public SlotCapacityReachedException(UUID eventId, long acceptedCount, long invitedCount, int maxSlots) {
        super(String.format(
                "Slot capacity reached for event %s: %d accepted + %d invited >= %d slots",
                eventId, acceptedCount, invitedCount, maxSlots));
        this.eventId = eventId;
        this.acceptedCount = acceptedCount;
        this.invitedCount = invitedCount;
        this.maxSlots = maxSlots;
    }
}
