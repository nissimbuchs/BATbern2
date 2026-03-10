package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /** Active subscribers paginated — use for bulk-send jobs to avoid loading 3000+ rows at once. */
    Page<NewsletterSubscriber> findByUnsubscribedAtIsNull(Pageable pageable);

    long countByUnsubscribedAtIsNull();

    /**
     * Active subscribers who have NO recipient record for the given send.
     * Used by retry-after-orphan-recovery to find subscribers that were never contacted
     * because the service was killed mid-send.
     */
    @Query("SELECT s FROM NewsletterSubscriber s WHERE s.unsubscribedAt IS NULL "
            + "AND s.email NOT IN ("
            + "  SELECT r.id.email FROM NewsletterRecipient r WHERE r.id.sendId = :sendId"
            + ")")
    List<NewsletterSubscriber> findActiveSubscribersNotInSend(@Param("sendId") UUID sendId);
}
