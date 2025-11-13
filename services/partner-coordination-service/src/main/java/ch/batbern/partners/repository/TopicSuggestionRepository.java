package ch.batbern.partners.repository;

import ch.batbern.partners.domain.SuggestionStatus;
import ch.batbern.partners.domain.TopicSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TopicSuggestion entity operations.
 *
 * Provides database access for partner topic suggestions with
 * query methods for filtering by partner and status.
 */
@Repository
public interface TopicSuggestionRepository extends JpaRepository<TopicSuggestion, UUID> {

    /**
     * Find all suggestions submitted by a specific partner.
     *
     * @param partnerId the UUID of the partner
     * @return list of suggestions for the partner
     */
    List<TopicSuggestion> findByPartnerId(UUID partnerId);

    /**
     * Find all suggestions with a specific status.
     *
     * @param status the suggestion status
     * @return list of suggestions with the given status
     */
    List<TopicSuggestion> findByStatus(SuggestionStatus status);

    /**
     * Find all suggestions by a specific partner with a specific status.
     *
     * @param partnerId the UUID of the partner
     * @param status the suggestion status
     * @return list of matching suggestions
     */
    List<TopicSuggestion> findByPartnerIdAndStatus(UUID partnerId, SuggestionStatus status);
}
