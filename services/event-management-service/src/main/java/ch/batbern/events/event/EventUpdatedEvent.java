package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventUpdated
 * Published when an event is updated
 *
 * Story 1.15a.1: Events API Consolidation
 * Extends shared-kernel DomainEvent for consistent event structure
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventUpdatedEvent extends DomainEvent<UUID> {
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final Instant registrationDeadline;
    private final String venueName;
    private final String venueAddress;
    private final Integer venueCapacity;
    private final String status;
    private final UUID organizerId;
    private final String description;
    private final Integer currentAttendeeCount;

    public EventUpdatedEvent(
            UUID eventId,
            String title,
            Integer eventNumber,
            Instant date,
            Instant registrationDeadline,
            String venueName,
            String venueAddress,
            Integer venueCapacity,
            String status,
            UUID organizerId,
            String description,
            Integer currentAttendeeCount,
            UserId userId) {
        super(eventId, "EventUpdated", userId);
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.registrationDeadline = registrationDeadline;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.venueCapacity = venueCapacity;
        this.status = status;
        this.organizerId = organizerId;
        this.description = description;
        this.currentAttendeeCount = currentAttendeeCount;
    }
}
