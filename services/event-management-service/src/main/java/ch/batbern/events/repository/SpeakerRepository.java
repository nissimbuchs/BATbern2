package ch.batbern.events.repository;

import ch.batbern.events.domain.Speaker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Speaker entity - Story 6.0.
 *
 * Provides CRUD operations and custom queries for global speaker profiles.
 * Extends JpaSpecificationExecutor for advanced filtering support (AC4).
 */
@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID>, JpaSpecificationExecutor<Speaker> {

    /**
     * Batch-load speakers by a set of usernames.
     * Used by WatchEventController to avoid N+1 queries when mapping session speakers.
     *
     * @param usernames Collection of usernames to load
     * @return List of matching speakers (may be shorter than input if some have no profile)
     */
    List<Speaker> findAllByUsernameIn(Collection<String> usernames);

    /**
     * Find speaker by username (ADR-003: meaningful identifier).
     *
     * @param username the username to search for
     * @return Optional containing the speaker if found
     */
    Optional<Speaker> findByUsername(String username);

    /**
     * Check if a speaker exists with the given username.
     *
     * @param username the username to check
     * @return true if speaker exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find speaker by username, excluding soft-deleted records.
     *
     * @param username the username to search for
     * @return Optional containing the active speaker if found
     */
    Optional<Speaker> findByUsernameAndDeletedAtIsNull(String username);

    /**
     * Find speakers with advanced filtering including array fields (AC4).
     * Uses PostgreSQL array operators for expertise_areas, languages, and speaking_topics.
     *
     * @param availability Optional availability filter (null to skip)
     * @param workflowState Optional workflow state filter (null to skip)
     * @param expertiseArea Optional expertise area to filter by (array contains)
     * @param language Optional language to filter by (array contains)
     * @param speakingTopic Optional speaking topic to filter by (array contains)
     * @param pageable Pagination parameters
     * @return Page of matching speakers
     */
    @Query(value = """
        SELECT * FROM speakers s
        WHERE s.deleted_at IS NULL
          AND (:availability IS NULL OR s.availability = :availability)
          AND (:workflowState IS NULL OR s.workflow_state = :workflowState)
          AND (:expertiseArea IS NULL OR :expertiseArea = ANY(s.expertise_areas))
          AND (:language IS NULL OR :language = ANY(s.languages))
          AND (:speakingTopic IS NULL OR :speakingTopic = ANY(s.speaking_topics))
        """,
        countQuery = """
        SELECT COUNT(*) FROM speakers s
        WHERE s.deleted_at IS NULL
          AND (:availability IS NULL OR s.availability = :availability)
          AND (:workflowState IS NULL OR s.workflow_state = :workflowState)
          AND (:expertiseArea IS NULL OR :expertiseArea = ANY(s.expertise_areas))
          AND (:language IS NULL OR :language = ANY(s.languages))
          AND (:speakingTopic IS NULL OR :speakingTopic = ANY(s.speaking_topics))
        """,
        nativeQuery = true)
    Page<Speaker> findWithAdvancedFilters(
            @Param("availability") String availability,
            @Param("workflowState") String workflowState,
            @Param("expertiseArea") String expertiseArea,
            @Param("language") String language,
            @Param("speakingTopic") String speakingTopic,
            Pageable pageable);
}
