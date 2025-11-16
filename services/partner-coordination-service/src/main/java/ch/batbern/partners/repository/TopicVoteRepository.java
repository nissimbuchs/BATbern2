package ch.batbern.partners.repository;

import ch.batbern.partners.domain.TopicVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TopicVote entity.
 *
 * Provides data access operations for partner topic votes.
 */
@Repository
public interface TopicVoteRepository extends JpaRepository<TopicVote, UUID> {

    /**
     * Find all votes for a specific partner.
     *
     * @param partnerId Partner ID
     * @return List of votes cast by the partner
     */
    List<TopicVote> findByPartnerId(UUID partnerId);

    /**
     * Find all votes for a specific topic.
     *
     * @param topicId Topic ID
     * @return List of votes cast for the topic
     */
    List<TopicVote> findByTopicId(UUID topicId);

    /**
     * Find a specific vote by topic and partner (for uniqueness check).
     *
     * @param topicId   Topic ID
     * @param partnerId Partner ID
     * @return Optional containing the vote if exists
     */
    Optional<TopicVote> findByTopicIdAndPartnerId(UUID topicId, UUID partnerId);

    /**
     * Check if a partner has already voted on a topic.
     *
     * @param topicId   Topic ID
     * @param partnerId Partner ID
     * @return true if vote exists, false otherwise
     */
    boolean existsByTopicIdAndPartnerId(UUID topicId, UUID partnerId);
}
