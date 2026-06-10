package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionQnaPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SessionQnaPost} (Story 7.5).
 */
@Repository
public interface SessionQnaPostRepository extends JpaRepository<SessionQnaPost, UUID> {

    /** All posts for a window, oldest first (thread render order). */
    List<SessionQnaPost> findByWindowIdOrderByCreatedAtAsc(UUID windowId);
}
