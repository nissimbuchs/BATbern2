package ch.batbern.partners.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for TopicVote — Story 8.2.
 * One vote per partner-company per topic (toggle design).
 */
public class TopicVoteId implements Serializable {

    private UUID topicId;
    private String companyName;

    public TopicVoteId() {}

    public TopicVoteId(UUID topicId, String companyName) {
        this.topicId = topicId;
        this.companyName = companyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TopicVoteId other)) {
            return false;
        }
        return Objects.equals(topicId, other.topicId)
                && Objects.equals(companyName, other.companyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicId, companyName);
    }
}
