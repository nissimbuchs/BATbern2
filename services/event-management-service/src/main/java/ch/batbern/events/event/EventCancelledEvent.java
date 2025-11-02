package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventCancelled
 * Published when an event is cancelled
 *
 * Story 2.2: Architecture Compliance Refactoring
 * Extends DomainEvent<UUID> with internal UUID as aggregate ID and eventCode as business identifier
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCancelledEvent extends DomainEvent<UUID> {
    private final String eventCode;  // Public business identifier (e.g., "BATbern56")
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final String previousStatus;
    private final String cancellationReason;

    public EventCancelledEvent(
            UUID eventId,               // Internal database UUID
            String eventCode,           // Public business identifier
            String title,
            Integer eventNumber,
            Instant date,
            String previousStatus,
            String cancellationReason,
            String username) {
        super(eventId, "EventCancelled", username);  // aggregateId is UUID
        this.eventCode = eventCode;
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.previousStatus = previousStatus;
        this.cancellationReason = cancellationReason;
    }
}
