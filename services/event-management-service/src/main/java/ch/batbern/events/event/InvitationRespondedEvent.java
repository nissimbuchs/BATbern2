package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: InvitationResponded
 * Published when a speaker responds to an invitation.
 *
 * Story 6.1: Automated Speaker Invitation System
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InvitationRespondedEvent extends DomainEvent<UUID> {
    private final String username;       // Speaker username (ADR-003)
    private final String eventCode;      // Event code (ADR-003)
    private final String responseType;   // ACCEPTED, DECLINED, or TENTATIVE
    private final String declineReason;  // Reason if declined
    private final Instant respondedAt;   // When response was received

    public InvitationRespondedEvent(
            UUID invitationId,
            String username,
            String eventCode,
            String responseType,
            String declineReason,
            Instant respondedAt,
            String triggeredByUsername) {
        super(invitationId, "InvitationResponded", triggeredByUsername);
        this.username = username;
        this.eventCode = eventCode;
        this.responseType = responseType;
        this.declineReason = declineReason;
        this.respondedAt = respondedAt;
    }

    @Override
    public String getAggregateType() {
        return "SpeakerInvitation";
    }
}
