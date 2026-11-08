package ch.batbern.events.repository;

import ch.batbern.events.domain.NewsletterSubscriber;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for newsletter subscribers (Story 10.7). The organizer list filters through
 * {@link ch.batbern.events.specification.NewsletterSubscriberSpecification}.
 */
@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID>,
        JpaSpecificationExecutor<NewsletterSubscriber> {

    /**
     * Case-insensitive email lookup: the only email finder on purpose (#1099). Subscribe, bounce /
     * complaint handling and the slides-online opt-out check all match through it, because
     * registrant, SES and subscriber addresses may differ in case. Backed by the unique index on
     * {@code lower(email)} (V126).
     */
    Optional<NewsletterSubscriber> findByEmailIgnoreCase(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    /**
     * Every row of a user. {@code username} is NOT unique: a user who subscribed under two
     * addresses has two rows (one per address, unique by {@code lower(email)}). An
     * {@code Optional} finder here threw IncorrectResultSizeDataAccessException (#1143).
     */
    List<NewsletterSubscriber> findAllByUsername(String username);

    /** All active subscribers (not unsubscribed, not suppressed — Story 10.29). */
    List<NewsletterSubscriber> findByUnsubscribedAtIsNullAndSuppressedAtIsNull();

    /**
     * Next batch of active subscribers after {@code afterId}, in id order: keyset iteration for the
     * newsletter send loop (#1129). Unlike an OFFSET page, the cursor cannot skip or repeat a row
     * when subscribers unsubscribe or get suppressed while the send runs. Start with the nil UUID,
     * which PostgreSQL orders before every other UUID. Served by the primary key index.
     */
    @Query("SELECT s FROM NewsletterSubscriber s WHERE s.unsubscribedAt IS NULL AND s.suppressedAt IS NULL "
            + "AND s.id > :afterId ORDER BY s.id")
    List<NewsletterSubscriber> findActiveAfterId(@Param("afterId") UUID afterId, Limit limit);

    /** Whether the subscriber still exists and is active, re-checked right before each newsletter mail. */
    boolean existsByIdAndUnsubscribedAtIsNullAndSuppressedAtIsNull(UUID id);

    /** Count active subscribers (excludes unsubscribed + suppressed — Story 10.29). */
    long countByUnsubscribedAtIsNullAndSuppressedAtIsNull();

    /**
     * Active subscribers who have NO recipient record for the given send.
     * Used by retry-after-orphan-recovery to find subscribers that were never contacted
     * because the service was killed mid-send.
     * Story 10.29: also excludes suppressed subscribers. Emails compare case-insensitively (#1129),
     * like the unique index on {@code lower(email)}, so a recipient row never misses its subscriber.
     */
    @Query("SELECT s FROM NewsletterSubscriber s WHERE s.unsubscribedAt IS NULL AND s.suppressedAt IS NULL "
            + "AND LOWER(s.email) NOT IN ("
            + "  SELECT LOWER(r.id.email) FROM NewsletterRecipient r WHERE r.id.sendId = :sendId"
            + ")")
    List<NewsletterSubscriber> findActiveSubscribersNotInSend(@Param("sendId") UUID sendId);

    /** Active subscribers whose username matches one of the given values (test mode — organizer-only send). */
    List<NewsletterSubscriber> findByUsernameInAndUnsubscribedAtIsNullAndSuppressedAtIsNull(List<String> usernames);

    /** Story 10.29 AC8: Find suppressed subscribers for admin listing. */
    List<NewsletterSubscriber> findBySuppressedAtIsNotNull();
}
