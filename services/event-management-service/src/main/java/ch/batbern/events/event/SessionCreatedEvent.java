package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Domain Event: SessionCreated
 * Published when a new session is added to an event
 *
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String sessionSlug and eventCode instead of UUIDs
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SessionCreatedEvent extends DomainEvent<String> {
    private final String relatedEventCode;
    private final String title;
    private final String description;
    private final String sessionType;
    private final Instant startTime;
    private final Instant endTime;
    private final String room;
    private final Integer capacity;

    public SessionCreatedEvent(
            String sessionSlug,
            String eventCode,
            String title,
            String description,
            String sessionType,
            Instant startTime,
            Instant endTime,
            String room,
            Integer capacity,
            String username) {
        super(sessionSlug, "SessionCreated", username);
        this.relatedEventCode = eventCode;
        this.title = title;
        this.description = description;
        this.sessionType = sessionType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.capacity = capacity;
    }
}
