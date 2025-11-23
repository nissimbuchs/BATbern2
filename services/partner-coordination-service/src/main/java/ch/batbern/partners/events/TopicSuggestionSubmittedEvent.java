package ch.batbern.partners.events;

import ch.batbern.partners.domain.SuggestionStatus;
import ch.batbern.shared.events.DomainEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a partner submits a topic suggestion.
 *
 * Event includes:
 * - Suggestion ID, Partner ID
 * - Company name (meaningful ID per ADR-003)
 * - Suggested topic, description, business justification
 * - Status and timestamp
 */
@Getter
public class TopicSuggestionSubmittedEvent extends DomainEvent<UUID> {

    private final UUID suggestionId;
    private final UUID partnerId;
    private final String companyName;
    private final String suggestedTopic;
    private final String description;
    private final String businessJustification;
    private final SuggestionStatus status;
    private final Instant suggestedAt;
    private final Instant timestamp;

    @Builder
    public TopicSuggestionSubmittedEvent(
            UUID suggestionId,
            UUID partnerId,
            String companyName,
            String suggestedTopic,
            String description,
            String businessJustification,
            SuggestionStatus status,
            Instant suggestedAt,
            Instant timestamp
    ) {
        // userId can be extracted from SecurityContext if needed
        super(suggestionId, "TopicSuggestionSubmitted", null);
        this.suggestionId = suggestionId;
        this.partnerId = partnerId;
        this.companyName = companyName;
        this.suggestedTopic = suggestedTopic;
        this.description = description;
        this.businessJustification = businessJustification;
        this.status = status;
        this.suggestedAt = suggestedAt;
        this.timestamp = timestamp;
    }
}
