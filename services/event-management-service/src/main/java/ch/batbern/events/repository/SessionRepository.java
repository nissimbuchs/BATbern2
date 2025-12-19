package ch.batbern.events.repository;

import ch.batbern.events.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Session entities
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Story 1.16.2: Uses sessionSlug as public identifier
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

    /**
     * Find a session by its slug (public identifier)
     * Story 1.16.2: Public API uses sessionSlug instead of UUID
     */
    Optional<Session> findBySessionSlug(String sessionSlug);

    /**
     * Check if a session slug already exists
     * Story 1.16.2: For collision detection during slug generation
     */
    boolean existsBySessionSlug(String sessionSlug);

    /**
     * Find all sessions for a specific event
     */
    List<Session> findByEventId(UUID eventId);

    /**
     * Find all sessions for a specific event and session type
     */
    List<Session> findByEventIdAndSessionType(UUID eventId, String sessionType);

    /**
     * Delete all sessions for a specific event
     */
    void deleteByEventId(UUID eventId);

    /**
     * Find a session by event ID and title (for duplicate detection during batch import)
     */
    Optional<Session> findByEventIdAndTitle(UUID eventId, String title);
}
