package ch.batbern.events.repository;

import ch.batbern.events.domain.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Find topics with optional category and status filters, with pagination support.
     * Used for topic backlog with database-level pagination (AC1).
     *
     * @param category Optional category filter (null to skip)
     * @param active Optional active status filter (null to skip)
     * @param pageable Pagination and sort parameters
     * @return Page of topics matching filters
     */
    @Query("SELECT t FROM Topic t WHERE "
            + "(:category IS NULL OR t.category = :category) "
            + "AND (:active IS NULL OR t.active = :active)")
    Page<Topic> findByFilters(
            @Param("category") String category,
            @Param("active") Boolean active,
            Pageable pageable);

    /**
     * Find topics by category and staleness score range (Story 5.2a - Fix #5).
     * Used for status-based filtering (available/caution/unavailable).
     * Shows all topics regardless of active status (partners can add topics for future use).
     *
     * @param category Optional category filter (null to skip)
     * @param minStaleness Minimum staleness score (inclusive)
     * @param maxStaleness Maximum staleness score (inclusive)
     * @param pageable Pagination and sort parameters
     * @return Page of topics matching filters
     */
    @Query("SELECT t FROM Topic t WHERE "
            + "(:category IS NULL OR t.category = :category) "
            + "AND t.stalenessScore BETWEEN :minStaleness AND :maxStaleness")
    Page<Topic> findByCategoryAndStalenessRange(
            @Param("category") String category,
            @Param("minStaleness") Integer minStaleness,
            @Param("maxStaleness") Integer maxStaleness,
            Pageable pageable);
}
