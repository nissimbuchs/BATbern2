package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

/**
 * Domain Event: SpeakerCreated
 * Published when a new speaker profile is created.
 *
 * Story 6.0: Speaker Profile Foundation
 * Extends DomainEvent<UUID> with speaker ID as aggregate ID and username as business identifier.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SpeakerCreatedEvent extends DomainEvent<UUID> {
    private final String username;           // Public business identifier (ADR-003)
    private final String availability;
    private final String workflowState;
    private final List<String> expertiseAreas;
    private final List<String> speakingTopics;
    private final List<String> languages;

    public SpeakerCreatedEvent(
            UUID speakerId,                  // Internal database UUID
            String username,                 // Public business identifier
            String availability,
            String workflowState,
            List<String> expertiseAreas,
            List<String> speakingTopics,
            List<String> languages,
            String triggeredByUsername) {
        super(speakerId, "SpeakerCreated", triggeredByUsername);
        this.username = username;
        this.availability = availability;
        this.workflowState = workflowState;
        this.expertiseAreas = expertiseAreas != null ? List.copyOf(expertiseAreas) : List.of();
        this.speakingTopics = speakingTopics != null ? List.copyOf(speakingTopics) : List.of();
        this.languages = languages != null ? List.copyOf(languages) : List.of();
    }

    @Override
    public String getAggregateType() {
        return "Speaker";
    }
}
