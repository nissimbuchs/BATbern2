package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionQnaPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SessionQnaPost} (Story 7.5).
 */
@Repository
public interface SessionQnaPostRepository extends JpaRepository<SessionQnaPost, UUID> {

    /** All posts for a window, oldest first (thread render order). */
    List<SessionQnaPost> findByWindowIdOrderByCreatedAtAsc(UUID windowId);

    /**
     * Story 15.7 — NEW top-level questions in a window that a given recipient has not yet been
     * notified about: top-level (no parent), not removed, created strictly after the recipient's
     * high-water mark, and not authored by the recipient themselves (a self-post never notifies
     * its own author — AC9). Oldest first so the caller can take the last one as the new water mark.
     */
    @Query("SELECT p FROM SessionQnaPost p "
        + "WHERE p.windowId = :windowId "
        + "AND p.parentPostId IS NULL "
        + "AND p.removedAt IS NULL "
        + "AND p.createdAt > :since "
        + "AND p.postedByUsername <> :recipientUsername "
        + "ORDER BY p.createdAt ASC")
    List<SessionQnaPost> findNewTopLevelQuestionsForRecipient(
            @Param("windowId") UUID windowId,
            @Param("since") Instant since,
            @Param("recipientUsername") String recipientUsername);
}
