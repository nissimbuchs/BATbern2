package ch.batbern.partners.repository;

import ch.batbern.partners.domain.TopicSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TopicSuggestion — Story 8.2.
 * Includes a custom query to fetch topics with vote counts in a single SQL statement.
 */
@Repository
public interface TopicRepository extends JpaRepository<TopicSuggestion, UUID> {

    /**
     * Fetch all topics with their vote counts, sorted by vote count descending.
     * Returns a projection: [TopicSuggestion, Long voteCount, Boolean callerVoted].
     *
     * @param callerCompanyName company name of the authenticated caller (for currentPartnerHasVoted flag)
     */
    @Query("""
            SELECT t, COUNT(v.companyName), SUM(CASE WHEN v.companyName = :callerCompanyName THEN 1 ELSE 0 END)
            FROM TopicSuggestion t
            LEFT JOIN TopicVote v ON v.topicId = t.id
            GROUP BY t
            ORDER BY COUNT(v.companyName) DESC
            """)
    List<Object[]> findAllWithVoteCounts(String callerCompanyName);
}
