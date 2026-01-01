package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionTimingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SessionTimingHistory entity
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Repository
public interface SessionTimingHistoryRepository extends JpaRepository<SessionTimingHistory, UUID> {

    /**
     * Find all timing history for a session, ordered by most recent first
     */
    List<SessionTimingHistory> findBySessionIdOrderByChangedAtDesc(UUID sessionId);

    /**
     * Find recent timing changes across all sessions (for audit dashboard)
     */
    List<SessionTimingHistory> findTop50ByOrderByChangedAtDesc();
}
