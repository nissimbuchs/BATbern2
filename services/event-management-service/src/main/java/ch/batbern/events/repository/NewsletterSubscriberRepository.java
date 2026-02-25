package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for newsletter subscribers (Story 10.7).
 */
@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {

    Optional<NewsletterSubscriber> findByEmail(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    Optional<NewsletterSubscriber> findByUsername(String username);

    /** All active subscribers (not unsubscribed). */
    List<NewsletterSubscriber> findByUnsubscribedAtIsNull();

    long countByUnsubscribedAtIsNull();
}
