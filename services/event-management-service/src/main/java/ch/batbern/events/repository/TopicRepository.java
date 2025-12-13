package ch.batbern.events.repository;

import ch.batbern.events.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Topic entity (Story 5.2).
 *
 * Provides CRUD operations and custom queries for topic management:
 * - Topic listing with filtering
 * - Staleness-based sorting
 * - Full-text search (using PostgreSQL tsvector)
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    /**
     * Find all active topics.
     * Used for topic backlog display (AC1).
     */
    @Query("SELECT t FROM Topic t WHERE t.active = true ORDER BY t.stalenessScore DESC")
    List<Topic> findAllActive();

    /**
     * Find topics by category.
     * Used for category filtering (AC1).
     */
    @Query("SELECT t FROM Topic t WHERE t.category = :category AND t.active = true ORDER BY t.stalenessScore DESC")
    List<Topic> findByCategory(@Param("category") String category);

    /**
     * Find topics with staleness score above threshold.
     * Used for filtering safe-to-use topics (AC6).
     */
    @Query("SELECT t FROM Topic t WHERE t.stalenessScore >= :threshold "
            + "AND t.active = true ORDER BY t.stalenessScore DESC")
    List<Topic> findByStalenessScoreGreaterThanEqual(@Param("threshold") Integer threshold);

    /**
     * Find all topics for similarity calculation.
     * Returns all topics to build TF-IDF corpus (AC4).
     */
    @Query("SELECT t FROM Topic t WHERE t.active = true")
    List<Topic> findAllForSimilarityCalculation();
}
