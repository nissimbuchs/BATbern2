package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventPublished
 * Published when an event transitions to 'published' status
 *
 * Story 2.2: Architecture Compliance Refactoring
 * Extends DomainEvent<UUID> with internal UUID as aggregate ID and eventCode as business identifier
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventPublishedEvent extends DomainEvent<UUID> {
    private final String eventCode;  // Public business identifier (e.g., "BATbern56")
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final Instant publishedAt;
    private final String previousStatus;

    public EventPublishedEvent(
            UUID eventId,               // Internal database UUID
            String eventCode,           // Public business identifier
            String title,
            Integer eventNumber,
            Instant date,
            Instant publishedAt,
            String previousStatus,
            String username) {
        super(eventId, "EventPublished", username);  // aggregateId is UUID
        this.eventCode = eventCode;
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.publishedAt = publishedAt;
        this.previousStatus = previousStatus;
    }
}
