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

    /** All active subscribers (not unsubscribed, not suppressed — Story 10.29). */
    List<NewsletterSubscriber> findByUnsubscribedAtIsNullAndSuppressedAtIsNull();

    /** Active subscribers paginated (Story 10.29: excludes suppressed). */
    Page<NewsletterSubscriber> findByUnsubscribedAtIsNullAndSuppressedAtIsNull(Pageable pageable);

    /** Count active subscribers (excludes unsubscribed + suppressed — Story 10.29). */
    long countByUnsubscribedAtIsNullAndSuppressedAtIsNull();

    /**
     * Active subscribers who have NO recipient record for the given send.
     * Used by retry-after-orphan-recovery to find subscribers that were never contacted
     * because the service was killed mid-send.
     * Story 10.29: also excludes suppressed subscribers.
     */
    @Query("SELECT s FROM NewsletterSubscriber s WHERE s.unsubscribedAt IS NULL AND s.suppressedAt IS NULL "
            + "AND s.email NOT IN ("
            + "  SELECT r.id.email FROM NewsletterRecipient r WHERE r.id.sendId = :sendId"
            + ")")
    List<NewsletterSubscriber> findActiveSubscribersNotInSend(@Param("sendId") UUID sendId);

    /** Active subscribers whose username matches one of the given values (test mode — organizer-only send). */
    List<NewsletterSubscriber> findByUsernameInAndUnsubscribedAtIsNullAndSuppressedAtIsNull(List<String> usernames);

    /** Story 10.29 AC8: Find suppressed subscribers for admin listing. */
    List<NewsletterSubscriber> findBySuppressedAtIsNotNull();

    /**
     * Find subscribers with search, status filter, and pagination (Story 10.28).
     * Dynamic ORDER BY comes from Spring Data Sort on the Pageable parameter.
     */
    @Query("""
            SELECT s FROM NewsletterSubscriber s
            WHERE (:search IS NULL
                   OR LOWER(s.email) LIKE :searchLike
                   OR LOWER(s.firstName) LIKE :searchLike)
              AND (:status = 'all'
                   OR (:status = 'active'       AND s.unsubscribedAt IS NULL AND s.suppressedAt IS NULL)
                   OR (:status = 'unsubscribed' AND s.unsubscribedAt IS NOT NULL)
                   OR (:status = 'suppressed'   AND s.suppressedAt IS NOT NULL))
            """)
    List<NewsletterSubscriber> findFiltered(
            @Param("search") String search,
            @Param("searchLike") String searchLike,
            @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Count subscribers matching search + status filter (Story 10.28, extended Story 10.29).
     */
    @Query("""
            SELECT COUNT(s) FROM NewsletterSubscriber s
            WHERE (:search IS NULL
                   OR LOWER(s.email) LIKE :searchLike
                   OR LOWER(s.firstName) LIKE :searchLike)
              AND (:status = 'all'
                   OR (:status = 'active'       AND s.unsubscribedAt IS NULL AND s.suppressedAt IS NULL)
                   OR (:status = 'unsubscribed' AND s.unsubscribedAt IS NOT NULL)
                   OR (:status = 'suppressed'   AND s.suppressedAt IS NOT NULL))
            """)
    long countFiltered(
            @Param("search") String search,
            @Param("searchLike") String searchLike,
            @Param("status") String status);
}
