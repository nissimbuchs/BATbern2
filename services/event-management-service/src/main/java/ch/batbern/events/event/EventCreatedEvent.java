package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: EventCreated
 * Published when a new event is created
 *
 * Story 2.2: Architecture Compliance Refactoring
 * Extends DomainEvent<UUID> with internal UUID as aggregate ID and eventCode as business identifier
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCreatedEvent extends DomainEvent<UUID> {
    private final String eventCode;  // Public business identifier (e.g., "BATbern56")
    private final String title;
    private final Integer eventNumber;
    private final Instant date;
    private final Instant registrationDeadline;
    private final String venueName;
    private final String venueAddress;
    private final Integer venueCapacity;
    private final String status;
    private final String organizerUsername;
    private final String description;

    public EventCreatedEvent(
            UUID eventId,               // Internal database UUID
            String eventCode,           // Public business identifier
            String title,
            Integer eventNumber,
            Instant date,
            Instant registrationDeadline,
            String venueName,
            String venueAddress,
            Integer venueCapacity,
            String status,
            String organizerUsername,
            String description,
            String username) {
        super(eventId, "EventCreated", username);  // aggregateId is UUID
        this.eventCode = eventCode;
        this.title = title;
        this.eventNumber = eventNumber;
        this.date = date;
        this.registrationDeadline = registrationDeadline;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.venueCapacity = venueCapacity;
        this.status = status;
        this.organizerUsername = organizerUsername;
        this.description = description;
    }
}
