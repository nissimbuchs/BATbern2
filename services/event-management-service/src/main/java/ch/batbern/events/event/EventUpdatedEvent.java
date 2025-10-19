package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain Event: EventUpdated
 * Published when an event is updated
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String eventCode and String username instead of UUID wrappers
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventUpdatedEvent extends DomainEvent<String> {
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
    private final Integer currentAttendeeCount;

    public EventUpdatedEvent(
            String eventCode,
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
            Integer currentAttendeeCount,
            String username) {
        super(eventCode, "EventUpdated", username);
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
        this.currentAttendeeCount = currentAttendeeCount;
    }
}
