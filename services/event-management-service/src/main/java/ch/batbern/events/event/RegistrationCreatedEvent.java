package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: RegistrationCreated
 * Published when a new registration is created for an event
 *
 * Story 1.15a.1: Events API Consolidation - AC11-12
 * Extends shared-kernel DomainEvent for consistent event structure
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RegistrationCreatedEvent extends DomainEvent<UUID> {
    private final UUID relatedEventId;
    private final UUID attendeeId;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String status;
    private final Instant registrationDate;

    public RegistrationCreatedEvent(
            UUID registrationId,
            UUID eventId,
            UUID attendeeId,
            String attendeeName,
            String attendeeEmail,
            String status,
            Instant registrationDate,
            UserId userId) {
        super(registrationId, "RegistrationCreated", userId);
        this.relatedEventId = eventId;
        this.attendeeId = attendeeId;
        this.attendeeName = attendeeName;
        this.attendeeEmail = attendeeEmail;
        this.status = status;
        this.registrationDate = registrationDate;
    }
}
