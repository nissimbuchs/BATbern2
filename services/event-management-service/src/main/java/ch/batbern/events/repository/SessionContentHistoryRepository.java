package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SessionContentVersion} — the versioned audit log of title/abstract
 * submissions per session.
 *
 * <p>Story 11.E.8 consolidation: renamed from {@code ContentSubmissionRepository}, and
 * re-keyed from speaker_pool_id to session_id. The latest row is the "current"
 * version (mirrored on {@code sessions.title}); older rows preserve prior versions and
 * reviewer feedback.
 */
@Repository
public interface SessionContentHistoryRepository extends JpaRepository<SessionContentVersion, UUID> {

    /**
     * Find all content versions for a session, latest first.
     */
    List<SessionContentVersion> findBySessionIdOrderBySubmissionVersionDesc(UUID sessionId);

    /**
     * Find the latest content version for a session — the one that mirrors
     * {@code sessions.title} and carries any pending reviewer feedback.
     */
    Optional<SessionContentVersion> findFirstBySessionIdOrderBySubmissionVersionDesc(UUID sessionId);

    /**
     * Find a specific version for a session.
     */
    Optional<SessionContentVersion> findBySessionIdAndSubmissionVersion(UUID sessionId, Integer version);

    /**
     * Count versions for a session.
     */
    long countBySessionId(UUID sessionId);

    /**
     * Maximum version number for a session — used to derive the next version on submit.
     */
    @Query("SELECT MAX(v.submissionVersion) FROM SessionContentVersion v WHERE v.session.id = :sessionId")
    Integer findMaxVersionBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * Does any version exist for the session? Equivalent to "speaker has submitted content".
     */
    boolean existsBySessionId(UUID sessionId);

    /**
     * Delete all version rows for a session — used when a session is hard-deleted.
     * Cascade is also configured on the FK, but this method is here for explicit
     * service-level orchestration.
     */
    @Modifying
    @Query("DELETE FROM SessionContentVersion v WHERE v.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
