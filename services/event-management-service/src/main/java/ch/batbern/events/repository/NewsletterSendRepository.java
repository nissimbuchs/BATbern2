package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterSend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for newsletter send audit records (Story 10.7).
 */
@Repository
public interface NewsletterSendRepository extends JpaRepository<NewsletterSend, UUID> {

    List<NewsletterSend> findByEventIdOrderBySentAtDesc(UUID eventId);

    /** Returns the first IN_PROGRESS send for this event (for duplicate-send prevention). */
    java.util.Optional<NewsletterSend> findFirstByEventIdAndStatus(UUID eventId, String status);

    /**
     * Story 7.3: template-scoped in-progress guard for the dedicated slides-online send.
     * Lets the slides-online send guard against its OWN concurrent send without being blocked
     * by (or blocking) an unrelated subscriber-newsletter send for the same event.
     */
    java.util.Optional<NewsletterSend> findFirstByEventIdAndTemplateKeyAndStatus(
            UUID eventId, String templateKey, String status);

    /**
     * Story 7.3: "already sent" guard — true when a terminal slides-online send already exists
     * for this event (COMPLETED or PARTIAL), so the organizer cannot re-fire it.
     */
    boolean existsByEventIdAndTemplateKeyAndStatusIn(
            UUID eventId, String templateKey, java.util.Collection<String> statuses);

    /** Validates that a send record belongs to a specific event (security guard). */
    java.util.Optional<NewsletterSend> findByIdAndEventId(UUID id, UUID eventId);

    /** All sends currently in one of the given statuses — used for startup orphan recovery. */
    java.util.List<NewsletterSend> findByStatusIn(java.util.Collection<String> statuses);
}
