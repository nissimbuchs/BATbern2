package ch.batbern.events.repository;

import ch.batbern.events.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Session entities
 * Story 1.15a.1: Events API Consolidation - AC9-10
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, String>, JpaSpecificationExecutor<Session> {

    /**
     * Find all sessions for a specific event
     */
    List<Session> findByEventId(String eventId);

    /**
     * Find all sessions for a specific event and type
     */
    List<Session> findByEventIdAndType(String eventId, String type);

    /**
     * Delete all sessions for a specific event
     */
    void deleteByEventId(String eventId);
}
