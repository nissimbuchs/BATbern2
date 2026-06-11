package ch.batbern.events.repository;

import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.SessionQnaWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SessionQnaWindow} (Story 7.5).
 */
@Repository
public interface SessionQnaWindowRepository extends JpaRepository<SessionQnaWindow, UUID> {

    Optional<SessionQnaWindow> findBySessionId(UUID sessionId);

    boolean existsBySessionId(UUID sessionId);

    /** Story 7.5 rework: all of an event's session windows, for event-level open/close. */
    List<SessionQnaWindow> findByEventCode(String eventCode);

    /** Freeze-job scan: open windows whose close time has passed. */
    List<SessionQnaWindow> findByStatusAndClosesAtBefore(QnaWindowStatus status, Instant cutoff);
}
