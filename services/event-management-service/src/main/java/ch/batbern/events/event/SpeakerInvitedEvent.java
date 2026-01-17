package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: SpeakerInvited
 * Published when an invitation is sent to a speaker.
 *
 * Story 6.1: Automated Speaker Invitation System
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SpeakerInvitedEvent extends DomainEvent<UUID> {
    private final String username;       // Speaker username (ADR-003)
    private final String eventCode;      // Event code (ADR-003)
    private final Instant expiresAt;     // Invitation expiration
    private final Instant sentAt;        // When invitation was sent

    public SpeakerInvitedEvent(
            UUID invitationId,
            String username,
            String eventCode,
            Instant expiresAt,
            Instant sentAt,
            String triggeredByUsername) {
        super(invitationId, "SpeakerInvited", triggeredByUsername);
        this.username = username;
        this.eventCode = eventCode;
        this.expiresAt = expiresAt;
        this.sentAt = sentAt;
    }

    @Override
    public String getAggregateType() {
        return "SpeakerInvitation";
    }
}
