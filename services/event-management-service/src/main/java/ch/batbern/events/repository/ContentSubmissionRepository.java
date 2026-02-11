package ch.batbern.events.repository;

import ch.batbern.events.domain.ContentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ContentSubmission entity (Story 6.3).
 *
 * Provides data access for speaker content submissions with versioning support.
 */
@Repository
public interface ContentSubmissionRepository extends JpaRepository<ContentSubmission, UUID> {

    /**
     * Find all content submissions for a specific speaker pool entry.
     *
     * @param speakerPoolId the speaker pool ID
     * @return list of content submissions ordered by version descending
     */
    List<ContentSubmission> findBySpeakerPoolIdOrderBySubmissionVersionDesc(UUID speakerPoolId);

    /**
     * Find the latest content submission for a speaker pool entry.
     *
     * @param speakerPoolId the speaker pool ID
     * @return optional latest content submission
     */
    Optional<ContentSubmission> findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(UUID speakerPoolId);

    /**
     * Find content submission by speaker pool and version.
     *
     * @param speakerPoolId the speaker pool ID
     * @param version the submission version
     * @return optional content submission
     */
    Optional<ContentSubmission> findBySpeakerPoolIdAndSubmissionVersion(UUID speakerPoolId, Integer version);

    /**
     * Count submissions for a speaker pool entry.
     *
     * @param speakerPoolId the speaker pool ID
     * @return count of submissions
     */
    long countBySpeakerPoolId(UUID speakerPoolId);

    /**
     * Find all content submissions for a specific session.
     *
     * @param sessionId the session ID
     * @return list of content submissions
     */
    List<ContentSubmission> findBySessionId(UUID sessionId);

    /**
     * Delete all content submissions for a specific session.
     * Used when deleting a session to maintain referential integrity.
     *
     * @param sessionId the session ID
     */
    void deleteBySessionId(UUID sessionId);

    /**
     * Get the maximum submission version for a speaker pool entry.
     * Used to determine next version number on resubmission.
     *
     * @param speakerPoolId the speaker pool ID
     * @return maximum version or null if no submissions exist
     */
    @Query("SELECT MAX(cs.submissionVersion) FROM ContentSubmission cs WHERE cs.speakerPool.id = :speakerPoolId")
    Integer findMaxVersionBySpeakerPoolId(@Param("speakerPoolId") UUID speakerPoolId);

    /**
     * Check if any submission exists for a speaker pool entry.
     *
     * @param speakerPoolId the speaker pool ID
     * @return true if submission exists
     */
    boolean existsBySpeakerPoolId(UUID speakerPoolId);
}
