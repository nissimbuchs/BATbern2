package ch.batbern.shared.events;

import ch.batbern.shared.types.EventWorkflowState;
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
 * Event Workflow Transition Event - Story 5.1a AC2
 *
 * Domain event published when an event transitions from one workflow state to another.
 * This event enables event-driven architecture and allows other services to react to
 * workflow state changes.
 *
 * Example usage:
 * <pre>
 * EventWorkflowTransitionEvent event = new EventWorkflowTransitionEvent(
 *     "BATbern56",
 *     EventWorkflowState.CREATED,
 *     EventWorkflowState.TOPIC_SELECTION,
 *     "john.doe",
 *     Instant.now()
 * );
 * eventPublisher.publishEvent(event);
 * </pre>
 *
 * @see EventWorkflowState
 * @see DomainEventPublisher
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventWorkflowTransitionEvent extends DomainEvent<UUID> {

    @JsonProperty("eventCode")
    @NonNull
    private String eventCode;

    @JsonProperty("fromState")
    @NonNull
    private EventWorkflowState fromState;

    @JsonProperty("toState")
    @NonNull
    private EventWorkflowState toState;

    @JsonProperty("organizerUsername")
    @NonNull
    private String organizerUsername;

    @JsonProperty("transitionedAt")
    @NonNull
    private Instant transitionedAt;

    @JsonProperty("context")
    @NonNull
    private Map<String, Object> context;

    /**
     * Creates a new EventWorkflowTransitionEvent.
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param fromState Previous workflow state
     * @param toState New workflow state
     * @param organizerUsername Username of organizer who triggered the transition
     * @param transitionedAt Timestamp when the transition occurred
     */
    public EventWorkflowTransitionEvent(
            String eventCode,
            EventWorkflowState fromState,
            EventWorkflowState toState,
            String organizerUsername,
            Instant transitionedAt
    ) {
        super(UUID.randomUUID(), "EventWorkflowTransition", organizerUsername);

        if (eventCode == null) {
            throw new NullPointerException("eventCode is marked non-null but is null");
        }
        if (fromState == null) {
            throw new NullPointerException("fromState is marked non-null but is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is marked non-null but is null");
        }
        if (organizerUsername == null) {
            throw new NullPointerException("organizerUsername is marked non-null but is null");
        }
        if (transitionedAt == null) {
            throw new NullPointerException("transitionedAt is marked non-null but is null");
        }

        this.eventCode = eventCode;
        this.fromState = fromState;
        this.toState = toState;
        this.organizerUsername = organizerUsername;
        this.transitionedAt = transitionedAt;

        // Build context map with transition details
        this.context = Map.of(
            "eventCode", eventCode,
            "fromState", fromState.name(),
            "toState", toState.name(),
            "organizer", organizerUsername
        );
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "EventWorkflowTransitionEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "Event";
    }
}
