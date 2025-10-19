package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain Event: EventCancelled
 * Published when an event is cancelled
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String eventCode and String username instead of UUID wrappers
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCancelledEvent extends DomainEvent<String> {
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final String previousStatus;
    private final String cancellationReason;

    public EventCancelledEvent(
            String eventCode,
            String title,
            Integer eventNumber,
            Instant date,
            String previousStatus,
            String cancellationReason,
            String username) {
        super(eventCode, "EventCancelled", username);
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.previousStatus = previousStatus;
        this.cancellationReason = cancellationReason;
    }
}
