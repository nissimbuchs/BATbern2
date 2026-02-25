package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.dto.NewsletterSubscriptionStatusResponse;
import ch.batbern.events.dto.SubscriberResponse;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for newsletter subscription management (Story 10.7).
 *
 * <p>Handles subscribe/unsubscribe/reactivate logic, token verification,
 * and authenticated user subscription management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterSubscriberService {

    private final NewsletterSubscriberRepository subscriberRepository;

    /**
     * Subscribe an email to the newsletter.
     *
     * <ul>
     *   <li>New email → create with a fresh UUID token</li>
     *   <li>Previously unsubscribed email → reactivate (clear unsubscribedAt, preserve token)</li>
     *   <li>Already active subscriber → throw {@link DuplicateSubscriberException} (409)</li>
     * </ul>
     *
     * @param email    subscriber email
     * @param firstName optional first name
     * @param language  "de" or "en" (defaults to "de" if blank)
     * @param source    "explicit" | "registration" | "account"
     * @param username  cognito username for authenticated users; null for anonymous
     * @return saved subscriber entity
     */
    @Transactional
    public NewsletterSubscriber subscribe(String email, String firstName, String language,
                                          String source, String username) {
        Optional<NewsletterSubscriber> existing = subscriberRepository.findByEmail(email);

        if (existing.isPresent()) {
            NewsletterSubscriber sub = existing.get();
            if (sub.getUnsubscribedAt() == null) {
                // Already active — 409
                throw new DuplicateSubscriberException(email);
            }
            // Reactivate: clear unsubscribedAt, preserve original token
            sub.setUnsubscribedAt(null);
            sub.setSubscribedAt(Instant.now());
            if (firstName != null && !firstName.isBlank()) {
                sub.setFirstName(firstName);
            }
            if (language != null && !language.isBlank()) {
                sub.setLanguage(language);
            }
            sub.setSource(source != null ? source : "explicit");
            if (username != null) {
                sub.setUsername(username);
            }
            log.info("Reactivated newsletter subscriber: {}", email);
            return subscriberRepository.save(sub);
        }

        // New subscriber
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .email(email)
                .firstName(firstName)
                .language(language != null && !language.isBlank() ? language : "de")
                .source(source != null ? source : "explicit")
                .username(username)
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();
        log.info("Created newsletter subscriber: {}", email);
        return subscriberRepository.save(sub);
    }

    /**
     * Verify an unsubscribe token.
     *
     * @param token the unsubscribe token
     * @return Optional containing subscriber email if token is valid and subscriber is active;
     *         empty if token not found or subscriber is already unsubscribed
     */
    @Transactional(readOnly = true)
    public Optional<String> verifyToken(String token) {
        return subscriberRepository.findByUnsubscribeToken(token)
                .filter(sub -> sub.getUnsubscribedAt() == null)
                .map(NewsletterSubscriber::getEmail);
    }

    /**
     * Unsubscribe by token (sets unsubscribedAt to now).
     *
     * @param token the unsubscribe token
     * @throws NoSuchElementException if token not found
     */
    @Transactional
    public void unsubscribeByToken(String token) {
        NewsletterSubscriber sub = subscriberRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new NoSuchElementException("Unsubscribe token not found: " + token));
        sub.setUnsubscribedAt(Instant.now());
        subscriberRepository.save(sub);
        log.info("Unsubscribed newsletter subscriber via token: {}", sub.getEmail());
    }

    /**
     * Get subscription status for an authenticated user.
     * Looks up by username first, then falls back to email.
     *
     * @param username cognito username
     * @param email    user's email address
     */
    @Transactional(readOnly = true)
    public NewsletterSubscriptionStatusResponse getMySubscription(String username, String email) {
        // Try by username first
        Optional<NewsletterSubscriber> byUsername = subscriberRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            NewsletterSubscriber sub = byUsername.get();
            return NewsletterSubscriptionStatusResponse.builder()
                    .subscribed(sub.getUnsubscribedAt() == null)
                    .email(sub.getEmail())
                    .build();
        }
        // Fallback: look up by email
        Optional<NewsletterSubscriber> byEmail = subscriberRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            NewsletterSubscriber sub = byEmail.get();
            return NewsletterSubscriptionStatusResponse.builder()
                    .subscribed(sub.getUnsubscribedAt() == null)
                    .email(sub.getEmail())
                    .build();
        }
        return NewsletterSubscriptionStatusResponse.builder()
                .subscribed(false)
                .build();
    }

    /**
     * Update subscription status for an authenticated user.
     *
     * @param username  cognito username
     * @param email     user's email address
     * @param subscribed desired subscription state
     * @param language  language preference (used when subscribing)
     */
    @Transactional
    public NewsletterSubscriptionStatusResponse patchMySubscription(String username, String email,
                                                                    boolean subscribed, String language) {
        if (subscribed) {
            // Subscribe — may reactivate or create (409 is suppressed here since it is a
            // settings toggle: silently ignore if already active)
            try {
                subscribe(email, null, language, "account", username);
            } catch (DuplicateSubscriberException e) {
                // Already active — idempotent for authenticated users
                log.debug("Authenticated user {} is already subscribed, ignoring", username);
            }
        } else {
            // Unsubscribe by token or by email lookup
            Optional<NewsletterSubscriber> sub = subscriberRepository.findByUsername(username);
            if (sub.isEmpty()) {
                sub = subscriberRepository.findByEmail(email);
            }
            sub.ifPresent(s -> {
                s.setUnsubscribedAt(Instant.now());
                subscriberRepository.save(s);
                log.info("Authenticated user {} unsubscribed from newsletter", username);
            });
        }
        return getMySubscription(username, email);
    }

    /** Returns total count of active (non-unsubscribed) subscribers. */
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return subscriberRepository.countByUnsubscribedAtIsNull();
    }

    /** Returns all active subscribers (for bulk send). */
    @Transactional(readOnly = true)
    public List<NewsletterSubscriber> findActiveSubscribers() {
        return subscriberRepository.findByUnsubscribedAtIsNull();
    }

    /** Maps a subscriber entity to its response DTO. */
    public SubscriberResponse toResponse(NewsletterSubscriber sub) {
        return SubscriberResponse.builder()
                .id(sub.getId())
                .email(sub.getEmail())
                .firstName(sub.getFirstName())
                .language(sub.getLanguage())
                .source(sub.getSource())
                .username(sub.getUsername())
                .subscribedAt(sub.getSubscribedAt())
                .build();
    }
}
