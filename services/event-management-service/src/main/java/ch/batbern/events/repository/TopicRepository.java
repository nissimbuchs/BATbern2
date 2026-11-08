package ch.batbern.events.repository;

import ch.batbern.events.domain.Topic;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Topic entity (Story 5.2).
 *
 * Provides CRUD operations and custom queries for topic management:
 * - Topic listing with filtering
 * - Staleness-based sorting
 * - Full-text search (using PostgreSQL tsvector)
 *
 * ADR-003: Use topicCode (slug-format) as the primary external identifier.
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID>, JpaSpecificationExecutor<Topic> {

    /**
     * Find topic by topicCode (ADR-003 primary lookup).
     * Used for all API operations by external identifier.
     */
    Optional<Topic> findByTopicCode(String topicCode);

    /**
     * Batch-load topics by a set of topic codes.
     * Used by EventController.buildBatchExpandedResponses() to avoid N+1 topic queries.
     */
    List<Topic> findByTopicCodeIn(java.util.Collection<String> topicCodes);

    /**
     * Check if topic exists by topicCode.
     */
    boolean existsByTopicCode(String topicCode);

    /**
     * Find topic by title (for duplicate detection).
     */
    Optional<Topic> findByTitleIgnoreCase(String title);

    /**
     * Find all active topics ordered by creation date.
     * Used for topic backlog display (AC1).
     */
    @Query("SELECT t FROM Topic t WHERE t.active = true ORDER BY t.createdAt DESC")
    List<Topic> findAllActive();

    /**
     * Find topics by category.
     * Used for category filtering (AC1).
     */
    @Query("SELECT t FROM Topic t WHERE t.category = :category AND t.active = true ORDER BY t.createdAt DESC")
    List<Topic> findByCategory(@Param("category") String category);

    /**
     * Find all topics for similarity calculation.
     * Returns all topics to build TF-IDF corpus (AC4).
     */
    @Query("SELECT t FROM Topic t WHERE t.active = true")
    List<Topic> findAllForSimilarityCalculation();

    /**
     * Find topics with optional category, active and free-text filters, with pagination support,
     * all in SQL before paging (AC1).
     *
     * @param category Optional category filter (null to skip)
     * @param active Optional active status filter (null to skip)
     * @param search Optional search: every word occurs in the title or description, folded by
     *     {@code batbern_fold}, in any order (blank to skip)
     * @param pageable Pagination and sort parameters
     * @return Page of topics matching filters
     */
    default Page<Topic> findByFilters(String category, Boolean active, String search, Pageable pageable) {
        SearchTerms terms = SearchTerms.parse(search);
        Specification<Topic> spec = (root, query, cb) -> cb.and(
                category != null ? cb.equal(root.get("category"), category) : cb.conjunction(),
                active != null ? cb.equal(root.get("active"), active) : cb.conjunction(),
                SearchPredicates.allTermsInAnyField(cb, terms, root.get("title"), root.get("description")));
        return findAll(spec, pageable);
    }

}
