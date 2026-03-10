package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for newsletter_recipients audit rows (Story 10.7 — AC10).
 */
@Repository
public interface NewsletterRecipientRepository extends JpaRepository<NewsletterRecipient, NewsletterRecipientId> {

    /** All recipients of a send that failed delivery — used by the retry logic. */
    java.util.List<NewsletterRecipient> findByIdSendIdAndDeliveryStatus(java.util.UUID sendId, String deliveryStatus);
}
