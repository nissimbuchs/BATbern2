package ch.batbern.shared.events;

import ch.batbern.shared.types.SpeakerWorkflowState;
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
 * Speaker Workflow State Change Event - Story 5.5 AC10
 *
 * Domain event published when a speaker transitions from one workflow state to another.
 * This event enables event-driven architecture and allows other services to react to
 * speaker workflow state changes (e.g., task creation, notifications).
 *
 * Example usage:
 * <pre>
 * SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
 *     speakerPoolId,
 *     eventId,
 *     SpeakerWorkflowState.ACCEPTED,
 *     SpeakerWorkflowState.CONTENT_SUBMITTED,
 *     "john.doe"
 * );
 * eventPublisher.publishEvent(event);
 * </pre>
 *
 * @see SpeakerWorkflowState
 * @see DomainEvent
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerWorkflowStateChangeEvent extends DomainEvent<UUID> {

    @JsonProperty("speakerPoolId")
    @NonNull
    private UUID speakerPoolId;

    @JsonProperty("eventId")
    @NonNull
    private UUID relatedEventId; // BATbern event ID (avoiding conflict with DomainEvent.eventId)

    @JsonProperty("fromState")
    @NonNull
    private SpeakerWorkflowState fromState;

    @JsonProperty("toState")
    @NonNull
    private SpeakerWorkflowState toState;

    @JsonProperty("username")
    private String username; // Speaker username (optional - may not exist yet)

    @JsonProperty("transitionedAt")
    @NonNull
    private Instant transitionedAt;

    @JsonProperty("context")
    @NonNull
    private Map<String, Object> context;

    /**
     * Creates a new SpeakerWorkflowStateChangeEvent.
     *
     * @param speakerPoolId Speaker pool entry ID
     * @param eventId BATbern event ID
     * @param fromState Previous workflow state
     * @param toState New workflow state
     * @param username Username of the speaker (if available)
     */
    public SpeakerWorkflowStateChangeEvent(
            UUID speakerPoolId,
            UUID eventId,
            SpeakerWorkflowState fromState,
            SpeakerWorkflowState toState,
            String username
    ) {
        super(speakerPoolId, "SpeakerWorkflowStateChange", username);

        if (speakerPoolId == null) {
            throw new NullPointerException("speakerPoolId is marked non-null but is null");
        }
        if (eventId == null) {
            throw new NullPointerException("eventId is marked non-null but is null");
        }
        if (fromState == null) {
            throw new NullPointerException("fromState is marked non-null but is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is marked non-null but is null");
        }

        this.speakerPoolId = speakerPoolId;
        this.relatedEventId = eventId;
        this.fromState = fromState;
        this.toState = toState;
        this.username = username;
        this.transitionedAt = Instant.now();

        // Build context map with transition details
        this.context = Map.of(
            "speakerPoolId", speakerPoolId.toString(),
            "eventId", eventId.toString(),
            "fromState", fromState.name(),
            "toState", toState.name(),
            "username", username != null ? username : "Not yet assigned"
        );
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerWorkflowStateChangeEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "SpeakerPool";
    }
}
