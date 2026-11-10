package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterSend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for newsletter send audit records (Story 10.7).
 */
@Repository
public interface NewsletterSendRepository extends JpaRepository<NewsletterSend, UUID> {

    /** Returns the first IN_PROGRESS send for this event (for duplicate-send prevention). */
    Optional<NewsletterSend> findFirstByEventIdAndStatus(UUID eventId, String status);

    /**
     * Story 7.3: template-scoped in-progress guard for the dedicated slides-online send.
     * Lets the slides-online send guard against its OWN concurrent send without being blocked
     * by (or blocking) an unrelated subscriber-newsletter send for the same event.
     */
    Optional<NewsletterSend> findFirstByEventIdAndTemplateKeyAndStatus(
            UUID eventId, String templateKey, String status);

    /**
     * Story 7.3: "already sent" guard — true when a terminal slides-online send already exists
     * for this event (COMPLETED or PARTIAL), so the organizer cannot re-fire it.
     */
    boolean existsByEventIdAndTemplateKeyAndStatusIn(
            UUID eventId, String templateKey, Collection<String> statuses);

    /**
     * Story 16.5 AC9: the latest own-text registrant send of this event with exactly this subject in
     * one of {@code statuses} (one-shot guard by subject).
     */
    Optional<NewsletterSend> findFirstByEventIdAndTemplateKeyAndOwnSubjectAndStatusInOrderBySentAtDesc(
            UUID eventId, String templateKey, String ownSubject, Collection<String> statuses);

    /** All sends currently in one of the given statuses — used for startup orphan recovery. */
    List<NewsletterSend> findByStatusIn(Collection<String> statuses);
}
