package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: SessionCreated
 * Published when a new session is added to an event
 *
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Extends shared-kernel DomainEvent for consistent event structure
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SessionCreatedEvent extends DomainEvent<UUID> {
    private final UUID relatedEventId;
    private final String title;
    private final String description;
    private final String sessionType;
    private final Instant startTime;
    private final Instant endTime;
    private final String room;
    private final Integer capacity;

    public SessionCreatedEvent(
            UUID sessionId,
            UUID eventId,
            String title,
            String description,
            String sessionType,
            Instant startTime,
            Instant endTime,
            String room,
            Integer capacity,
            UserId userId) {
        super(sessionId, "SessionCreated", userId);
        this.relatedEventId = eventId;
        this.title = title;
        this.description = description;
        this.sessionType = sessionType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.capacity = capacity;
    }
}
