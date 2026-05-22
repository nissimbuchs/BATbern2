package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a speaker is promoted from {@code CONTACTED} to
 * {@code READY} — the provisioning gate of the unified speaker workflow (ADR-009 §0.2).
 *
 * The {@code CONTACTED → READY} transition is the moment at which:
 * <ul>
 *   <li>The User account is created or looked up by username.</li>
 *   <li>The Cognito user is provisioned (with {@code FORCE_CHANGE_PASSWORD}) — Phase E.</li>
 *   <li>The SPEAKER role is granted.</li>
 *   <li>{@code speaker_pool.username} is persisted.</li>
 * </ul>
 *
 * This event is published by {@code SpeakerWorkflowService.transition()} on the
 * {@code CONTACTED → READY} transition. It is consumed by Phase E (Story 11.E.2) to
 * send the Cognito invitation email containing the login link and temporary password.
 *
 * <strong>Idempotency note:</strong> if {@code transition()} is called for a speaker
 * already in {@code READY}, no event is emitted — Phase B Story 11.B.2 will enforce
 * this on the workflow-service side.
 *
 * Example usage:
 * <pre>
 * SpeakerPromotedToReadyEvent event = SpeakerPromotedToReadyEvent.builder()
 *     .speakerPoolId(speakerPoolId)
 *     .eventCode("BATbern56")
 *     .username("john.doe")
 *     .email("john.doe@example.com")
 *     .promotedAt(Instant.now())
 *     .promotedByUsername("organizer.user")
 *     .build();
 * eventPublisher.publishEvent(event);
 * </pre>
 *
 * @see SpeakerInvitationSentEvent
 * @see DomainEvent
 * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.2</a>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerPromotedToReadyEvent extends DomainEvent<UUID> {

    @JsonProperty("speakerPoolId")
    private UUID speakerPoolId;

    @JsonProperty("eventCode")
    private String eventCode;

    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("promotedAt")
    private Instant promotedAt;

    @JsonProperty("promotedByUsername")
    private String promotedByUsername;

    @Builder
    public SpeakerPromotedToReadyEvent(
            UUID speakerPoolId,
            String eventCode,
            String username,
            String email,
            Instant promotedAt,
            String promotedByUsername) {
        super(speakerPoolId, "SpeakerPromotedToReadyEvent", promotedByUsername);

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
        if (promotedByUsername == null) {
            throw new NullPointerException("promotedByUsername is marked non-null but is null");
        }

        this.speakerPoolId = speakerPoolId;
        this.eventCode = eventCode;
        this.username = username;
        this.email = email;
        this.promotedAt = promotedAt != null ? promotedAt : Instant.now();
        this.promotedByUsername = promotedByUsername;
    }

    @Override
    public UUID getAggregateId() {
        return speakerPoolId;
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerPromotedToReadyEvent";
    }
}
