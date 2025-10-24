package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain Event: EventPublished
 * Published when an event transitions to 'published' status
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String eventCode and String username instead of UUID wrappers
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventPublishedEvent extends DomainEvent<String> {
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final Instant publishedAt;
    private final String previousStatus;

    public EventPublishedEvent(
            String eventCode,
            String title,
            Integer eventNumber,
            Instant date,
            Instant publishedAt,
            String previousStatus,
            String username) {
        super(eventCode, "EventPublished", username);
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.publishedAt = publishedAt;
        this.previousStatus = previousStatus;
    }
}
