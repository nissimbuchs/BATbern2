package ch.batbern.partners.repository;

import ch.batbern.partners.domain.TopicVote;
import ch.batbern.partners.domain.TopicVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for TopicVote — Story 8.2.
 * Composite PK: (topicId, companyName).
 */
@Repository
public interface TopicVoteRepository extends JpaRepository<TopicVote, TopicVoteId> {

    /** Check if a vote exists for the given topic + company combination. */
    boolean existsByTopicIdAndCompanyName(UUID topicId, String companyName);

    /** Delete a specific vote (toggle-off). Silently succeeds if not found. */
    void deleteByTopicIdAndCompanyName(UUID topicId, String companyName);
}
