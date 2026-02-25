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
 * Active subscribers have {@code unsubscribedAt = null}.
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
