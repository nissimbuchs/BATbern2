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
}
