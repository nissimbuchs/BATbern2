package ch.batbern.events.repository;

import ch.batbern.events.domain.TopicUsageHistory;
import ch.batbern.events.dto.TopicUsageHistoryWithEventDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    /**
     * Find all usage history for multiple topics with event details in a single JOIN query.
     * GitHub Issue #379: Optimized query to avoid N+1 problem.
     *
     * This query JOINs topic_usage_history with events table to fetch:
     * - topicId, eventNumber, eventCode, eventDate (from events)
     * - usedDate, attendeeCount, engagementScore (from topic_usage_history)
     *
     * Returns eventNumber instead of UUID per architectural requirement.
     *
     * @param topicIds List of topic IDs to fetch usage history for
     * @return List of usage history with event details, ordered by usedDate DESC
     */
    @Query("""
        SELECT new ch.batbern.events.dto.TopicUsageHistoryWithEventDetails(
            h.topicId,
            e.eventNumber,
            e.eventCode,
            e.date,
            h.usedDate,
            h.attendeeCount,
            h.engagementScore
        )
        FROM TopicUsageHistory h
        JOIN Event e ON h.eventId = e.id
        WHERE h.topicId IN :topicIds
        ORDER BY h.topicId, h.usedDate DESC
        """)
    List<TopicUsageHistoryWithEventDetails> findUsageHistoryWithEventDetailsByTopicIds(
            @Param("topicIds") List<UUID> topicIds);

    /**
     * Find the most recent usage date for a topic.
     * Used as the authoritative source for staleness calculation —
     * avoids relying on the denormalized last_used_date column on topics.
     *
     * @param topicId UUID of the topic
     * @return Most recent usedDate, or empty if never used
     */
    @Query("SELECT MAX(h.usedDate) FROM TopicUsageHistory h WHERE h.topicId = :topicId")
    Optional<LocalDateTime> findMaxUsedDateByTopicId(@Param("topicId") UUID topicId);
}
