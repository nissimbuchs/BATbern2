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

    /** Validates that a send record belongs to a specific event (security guard). */
    java.util.Optional<NewsletterSend> findByIdAndEventId(UUID id, UUID eventId);
}
