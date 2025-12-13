package ch.batbern.events.repository;

import ch.batbern.events.domain.TopicUsageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TopicUsageHistory entity (Story 5.2).
 *
 * Provides queries for retrieving topic usage patterns:
 * - Historical usage for heat map visualization (AC2)
 * - Usage data for staleness calculation (AC1)
 */
@Repository
public interface TopicUsageHistoryRepository extends JpaRepository<TopicUsageHistory, UUID> {

    /**
     * Find all usage history for a specific topic, ordered by usage date descending.
     * Used for heat map visualization (AC2) and usage pattern analysis.
     *
     * @param topicId UUID of the topic
     * @return List of usage history records, most recent first
     */
    @Query("SELECT h FROM TopicUsageHistory h WHERE h.topicId = :topicId ORDER BY h.usedDate DESC")
    List<TopicUsageHistory> findByTopicIdOrderByUsedDateDesc(@Param("topicId") UUID topicId);

    /**
     * Find all usage history for a specific topic.
     * Convenience method for backwards compatibility.
     *
     * @param topicId UUID of the topic
     * @return List of usage history records
     */
    List<TopicUsageHistory> findByTopicId(UUID topicId);
}
