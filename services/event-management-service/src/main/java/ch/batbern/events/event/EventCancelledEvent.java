package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventCancelled
 * Published when an event is cancelled
 *
 * Story 1.15a.1: Events API Consolidation
 * Extends shared-kernel DomainEvent for consistent event structure
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCancelledEvent extends DomainEvent<UUID> {
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final String previousStatus;
    private final String cancellationReason;

    public EventCancelledEvent(
            UUID eventId,
            String title,
            Integer eventNumber,
            Instant date,
            String previousStatus,
            String cancellationReason,
            UserId userId) {
        super(eventId, "EventCancelled", userId);
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.previousStatus = previousStatus;
        this.cancellationReason = cancellationReason;
    }
}
