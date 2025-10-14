package ch.batbern.events.repository;

import ch.batbern.events.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Session entities
 * Story 1.15a.1: Events API Consolidation - AC9-10
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

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
}
