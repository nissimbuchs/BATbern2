package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Speaker Invitation Sent Event - Story 6.1b AC6
 *
 * Domain event published when a speaker invitation email is sent.
 * This event enables event-driven architecture and allows other services to react to
 * speaker invitation events (e.g., task tracking, notifications).
 *
 * Example usage:
 * <pre>
 * SpeakerInvitationSentEvent event = new SpeakerInvitationSentEvent(
 *     speakerPoolId,
 *     "BATbern56",
 *     "john.doe",
 *     "john.doe@example.com",
 *     "organizer.user"
 * );
 * eventPublisher.publishEvent(event);
 * </pre>
 *
 * SECURITY NOTE: This event does NOT contain the token itself, only audit information.
 * The token is only returned to the caller during generation and sent in the email.
 *
 * @see DomainEvent
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerInvitationSentEvent extends DomainEvent<UUID> {

    @JsonProperty("speakerPoolId")
    @NonNull
    private UUID speakerPoolId;

    @JsonProperty("eventCode")
    @NonNull
    private String eventCode;

    @JsonProperty("username")
    @NonNull
    private String username;

    @JsonProperty("email")
    @NonNull
    private String email;

    @JsonProperty("sentAt")
    @NonNull
    private Instant sentAt;

    @JsonProperty("context")
    @NonNull
    private Map<String, Object> context;

    /**
     * Creates a new SpeakerInvitationSentEvent.
     *
     * @param speakerPoolId Speaker pool entry ID
     * @param eventCode Event code (e.g., "BATbern56")
     * @param username Username of the invited speaker
     * @param email Email address of the invited speaker
     * @param invitedBy Username of the organizer who sent the invitation
     */
    public SpeakerInvitationSentEvent(
            UUID speakerPoolId,
            String eventCode,
            String username,
            String email,
            String invitedBy
    ) {
        super(speakerPoolId, "SpeakerInvitationSent", invitedBy);

        if (speakerPoolId == null) {
            throw new NullPointerException("speakerPoolId is marked non-null but is null");
        }
        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (username == null) {
            throw new NullPointerException("username is marked non-null but is null");
        }
        if (email == null) {
            throw new NullPointerException("email is marked non-null but is null");
        }

        this.speakerPoolId = speakerPoolId;
        this.eventCode = eventCode;
        this.username = username;
        this.email = email;
        this.sentAt = Instant.now();

        // Build context map with invitation details (no sensitive data)
        this.context = Map.of(
            "speakerPoolId", speakerPoolId.toString(),
            "eventCode", eventCode,
            "username", username,
            "invitedBy", invitedBy != null ? invitedBy : "system"
        );
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerInvitationSentEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "SpeakerPool";
    }
}
