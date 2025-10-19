package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventPublished
 * Published when an event transitions to 'published' status
 *
 * Story 1.15a.1: Events API Consolidation
 * Extends shared-kernel DomainEvent for consistent event structure
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventPublishedEvent extends DomainEvent<UUID> {
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final Instant publishedAt;
    private final String previousStatus;

    public EventPublishedEvent(
            UUID eventId,
            String title,
            Integer eventNumber,
            Instant date,
            Instant publishedAt,
            String previousStatus,
            UserId userId) {
        super(eventId, "EventPublished", userId);
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.publishedAt = publishedAt;
        this.previousStatus = previousStatus;
    }
}
