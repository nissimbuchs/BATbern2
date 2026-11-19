package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Newsletter subscriber entity (Story 10.7).
 *
 * <p>Tracks community members who have subscribed to BATbern newsletters.
 * Active subscribers have {@code confirmedAt != null}, {@code unsubscribedAt = null} and
 * {@code suppressedAt = null}. A subscription is pending (double opt-in, #1288) until its owner
 * follows the link with {@code confirmationToken}; a pending row gets no newsletter.
 * Re-subscribing a previously unsubscribed record clears {@code unsubscribedAt}
 * and preserves the original {@code unsubscribeToken}.
 *
 * <p>Database table: newsletter_subscribers (created by migration V67)
 */
@Entity
@Table(name = "newsletter_subscribers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "language", nullable = false, length = 5)
    @Builder.Default
    private String language = "de";

    /** Source of subscription: 'explicit' | 'registration' | 'account' */
    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private String source = "explicit";

    /** Cognito username for authenticated users; null for anonymous subscribers. */
    @Column(name = "username", length = 100)
    private String username;

    /** UUID token for token-based unsubscribe; never expires; preserved on re-subscribe. */
    @Column(name = "unsubscribe_token", nullable = false, length = 255, unique = true)
    private String unsubscribeToken;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    /** Null = active subscriber. Set to now() on unsubscribe; cleared on re-subscribe. */
    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    /**
     * #1288: when the owner confirmed the subscription (link in the confirmation mail, a confirmed
     * registration, their account, or an organizer). Null = pending, never sent a newsletter.
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** #1288: single-use token of the confirmation link while a confirmation is outstanding; else null. */
    @Column(name = "confirmation_token", length = 255, unique = true)
    private String confirmationToken;

    /** #1288: when the confirmation mail last went out; at most one per address and hour. */
    @Column(name = "confirmation_sent_at")
    private Instant confirmationSentAt;

    // Story 10.29: Bounce tracking fields (V91 migration)
    /** Bounce type: 'hard', 'soft', 'complaint', or null if never bounced. */
    @Column(name = "bounce_type", length = 20)
    private String bounceType;

    @Column(name = "bounce_count", nullable = false)
    @Builder.Default
    private int bounceCount = 0;

    @Column(name = "last_bounced_at")
    private Instant lastBouncedAt;

    /** Non-null = excluded from sends. Set on hard bounce, complaint, or soft-bounce threshold. */
    @Column(name = "suppressed_at")
    private Instant suppressedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** #1288: confirmed, not unsubscribed, not suppressed; the only rows that get a newsletter. */
    public boolean isActive() {
        return confirmedAt != null && unsubscribedAt == null && suppressedAt == null;
    }

    /** #1288: never confirmed, not unsubscribed, not suppressed: waits for its owner's confirmation. */
    public boolean isPending() {
        return confirmedAt == null && unsubscribedAt == null && suppressedAt == null;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (subscribedAt == null) {
            subscribedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
