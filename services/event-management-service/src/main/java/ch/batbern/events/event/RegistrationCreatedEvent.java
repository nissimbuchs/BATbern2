package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain Event: RegistrationCreated
 * Published when a new registration is created for an event
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String registrationCode, eventCode, and attendeeUsername instead of UUIDs
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RegistrationCreatedEvent extends DomainEvent<String> {
    private final String relatedEventCode;
    private final String attendeeUsername;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String status;
    private final Instant registrationDate;

    public RegistrationCreatedEvent(
            String registrationCode,
            String eventCode,
            String attendeeUsername,
            String attendeeName,
            String attendeeEmail,
            String status,
            Instant registrationDate,
            String username) {
        super(registrationCode, "RegistrationCreated", username);
        this.relatedEventCode = eventCode;
        this.attendeeUsername = attendeeUsername;
        this.attendeeName = attendeeName;
        this.attendeeEmail = attendeeEmail;
        this.status = status;
        this.registrationDate = registrationDate;
    }
}
