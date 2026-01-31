package ch.batbern.shared.events;

import ch.batbern.shared.types.SpeakerResponseType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a speaker responds to an invitation.
 * Story 6.2a: Invitation Response Portal - AC6 (Organizer Notification)
 *
 * This event is published for all response types (ACCEPT, DECLINE, TENTATIVE)
 * and triggers organizer notification via email.
 *
 * Event consumers:
 * - OrganizerNotificationService: Sends email to event organizers
 * - EventBridge (optional): For cross-service integration
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerResponseReceivedEvent extends DomainEvent<UUID> {

    @JsonProperty("speakerPoolId")
    private UUID speakerPoolId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("eventCode")
    private String eventCode;

    @JsonProperty("responseType")
    private SpeakerResponseType responseType;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("respondedAt")
    private Instant respondedAt;

    @Builder
    public SpeakerResponseReceivedEvent(
            UUID speakerPoolId,
            String username,
            String eventCode,
            SpeakerResponseType responseType,
            String reason,
            Instant respondedAt) {
        super(speakerPoolId, "SpeakerResponseReceivedEvent", "speaker-portal");

        if (speakerPoolId == null) {
            throw new NullPointerException("speakerPoolId is marked non-null but is null");
        }
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (responseType == null) {
            throw new NullPointerException("responseType is marked non-null but is null");
        }

        this.speakerPoolId = speakerPoolId;
        this.username = username;
        this.eventCode = eventCode;
        this.responseType = responseType;
        this.reason = reason;
        this.respondedAt = respondedAt != null ? respondedAt : Instant.now();
    }

    @Override
    public UUID getAggregateId() {
        return speakerPoolId;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerResponseReceivedEvent";
    }
}
