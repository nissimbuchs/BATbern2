package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a partner casts a vote on a topic.
 *
 * Event includes:
 * - Vote ID, Topic ID, Partner ID
 * - Company name (meaningful ID per ADR-003)
 * - Vote value and calculated weight
 * - Timestamp when vote was cast
 */
@Getter
public class TopicVoteSubmittedEvent extends DomainEvent<UUID> {

    private final UUID voteId;
    private final UUID topicId;
    private final UUID partnerId;
    private final String companyName;
    private final Integer voteValue;
    private final Integer voteWeight;
    private final Instant votedAt;
    private final Instant timestamp;

    @Builder
    public TopicVoteSubmittedEvent(
            UUID voteId,
            UUID topicId,
            UUID partnerId,
            String companyName,
            Integer voteValue,
            Integer voteWeight,
            Instant votedAt,
            Instant timestamp
    ) {
        super(voteId, "TopicVoteSubmitted", null);  // userId can be extracted from SecurityContext if needed
        this.voteId = voteId;
        this.topicId = topicId;
        this.partnerId = partnerId;
        this.companyName = companyName;
        this.voteValue = voteValue;
        this.voteWeight = voteWeight;
        this.votedAt = votedAt;
        this.timestamp = timestamp;
    }
}
