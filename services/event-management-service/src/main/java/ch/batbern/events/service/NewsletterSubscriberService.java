package ch.batbern.events.service;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.newsletter.dto.generated.NewsletterAddressSubscription;
import ch.batbern.events.newsletter.dto.generated.SubscriberResponse;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.exception.ReservedEmailDomainException;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.specification.NewsletterSubscriberSpecification;
import ch.batbern.shared.api.DeterministicPaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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

    /** RFC 2606 reserved second-level domains. */
    private static final Set<String> RESERVED_DOMAINS = Set.of(
            "example.com", "example.org", "example.net", "localhost"
    );

    /** RFC 2606 / RFC 6761 reserved TLDs (matched as suffix). */
    private static final List<String> RESERVED_TLD_SUFFIXES = List.of(
            ".example", ".test", ".invalid", ".localhost"
    );

    static boolean isReservedDomain(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        if (RESERVED_DOMAINS.contains(domain)) {
            return true;
        }
        for (String suffix : RESERVED_TLD_SUFFIXES) {
            if (domain.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

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
    @Transactional(noRollbackFor = {DuplicateSubscriberException.class, ReservedEmailDomainException.class})
    public NewsletterSubscriber subscribe(String email, String firstName, String language,
                                          String source, String username) {
        // Block RFC 2606 / RFC 6761 reserved domains so test fixtures and scanners
        // (e.g. zaproxy@example.com, speaker-test-…@example.com) can't pollute prod.
        if (isReservedDomain(email)) {
            log.warn("Rejected newsletter subscribe for reserved domain: {}", LoggingUtils.maskEmail(email));
            throw new ReservedEmailDomainException(email);
        }
        // Addresses are stored lower-cased and matched case-insensitively (#1099): "X@ace.ch" and
        // "x@ace.ch" are one subscriber, not two rows that each get the newsletter.
        email = email.trim().toLowerCase(Locale.ROOT);

        // Infer name from email when not provided (e.g. "david.baumgartner@ace.ch" → "David Baumgartner")
        String resolvedName = (firstName != null && !firstName.isBlank()) ? firstName : inferNameFromEmail(email);

        Optional<NewsletterSubscriber> existing = subscriberRepository.findByEmailIgnoreCase(email);

        if (existing.isPresent()) {
            NewsletterSubscriber sub = existing.get();
            if (sub.getUnsubscribedAt() == null) {
                // Already active — 409
                throw new DuplicateSubscriberException(email);
            }
            // Reactivate: clear unsubscribedAt, preserve original token
            sub.setUnsubscribedAt(null);
            sub.setSubscribedAt(Instant.now());
            if (resolvedName != null && !resolvedName.isBlank()) {
                sub.setFirstName(resolvedName);
            }
            if (language != null && !language.isBlank()) {
                sub.setLanguage(language);
            }
            sub.setSource(source != null ? source : "explicit");
            if (username != null) {
                sub.setUsername(username);
            }
            log.info("Reactivated newsletter subscriber: {}", LoggingUtils.maskEmail(email));
            return subscriberRepository.save(sub);
        }

        // New subscriber
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .email(email)
                .firstName(resolvedName)
                .language(language != null && !language.isBlank() ? language : "de")
                .source(source != null ? source : "explicit")
                .username(username)
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();
        log.info("Created newsletter subscriber: {}", LoggingUtils.maskEmail(email));
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
        log.info("Unsubscribed newsletter subscriber via token: {}", LoggingUtils.maskEmail(sub.getEmail()));
    }

    /**
     * #1244, 4.10c — the newsletter state of each given address, in the given order (primary
     * first), then every address still subscribed under this username that is not among them
     * (a stale row: the address left the account), so the user can still unsubscribe it.
     *
     * @param username  the caller
     * @param addresses lower-cased addresses of the caller
     * @param primary   the primary address, or {@code null}
     */
    @Transactional(readOnly = true)
    public List<NewsletterAddressSubscription> addressSubscriptions(String username, List<String> addresses,
                                                                    String primary) {
        List<NewsletterAddressSubscription> result = new ArrayList<>();
        for (String address : addresses) {
            Optional<NewsletterSubscriber> row = subscriberRepository.findByEmailIgnoreCase(address);
            result.add(NewsletterAddressSubscription.builder()
                    .email(address)
                    .primary(address.equals(primary))
                    .subscribed(row.map(s -> s.getUnsubscribedAt() == null).orElse(false))
                    .language(row.map(NewsletterSubscriber::getLanguage).orElse(null))
                    .build());
        }
        for (NewsletterSubscriber stale : staleRows(username, addresses)) {
            result.add(NewsletterAddressSubscription.builder()
                    .email(stale.getEmail())
                    .primary(false)
                    .subscribed(true)
                    .language(stale.getLanguage())
                    .build());
        }
        return result;
    }

    /** Active rows of this username whose address is not one of {@code own} (lower-cased). */
    @Transactional(readOnly = true)
    public List<String> staleAddresses(String username, Collection<String> own) {
        return staleRows(username, own).stream().map(NewsletterSubscriber::getEmail).toList();
    }

    private List<NewsletterSubscriber> staleRows(String username, Collection<String> own) {
        if (username == null) {
            return List.of();
        }
        return subscriberRepository.findAllByUsername(username).stream()
                .filter(s -> s.getUnsubscribedAt() == null)
                .filter(s -> !own.contains(s.getEmail().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * 4.10c — change exactly one address of an authenticated user, only the fields given; the
     * caller has checked that the address may be changed. Other addresses stay as they are.
     *
     * @param subscribed {@code true} subscribe (or reactivate), {@code false} unsubscribe,
     *                   {@code null} unchanged
     * @param language   {@code de} / {@code en}, or {@code null} unchanged (a new subscription
     *                   gets {@code de})
     */
    @Transactional
    public void patchAddressSubscription(String username, String email, Boolean subscribed, String language) {
        if (Boolean.TRUE.equals(subscribed)) {
            try {
                subscribe(email, null, language, "account", username);
            } catch (DuplicateSubscriberException e) {
                log.debug("Address of {} already subscribed, ignoring", username);
            }
        } else if (Boolean.FALSE.equals(subscribed)) {
            subscriberRepository.findByEmailIgnoreCase(email)
                    .filter(s -> s.getUnsubscribedAt() == null)
                    .ifPresent(s -> {
                        s.setUnsubscribedAt(Instant.now());
                        subscriberRepository.save(s);
                        log.info("Authenticated user {} unsubscribed one address from the newsletter", username);
                    });
        }
        if (language != null) {
            subscriberRepository.findByEmailIgnoreCase(email)
                    .filter(s -> !language.equals(s.getLanguage()))
                    .ifPresent(s -> {
                        s.setLanguage(language);
                        subscriberRepository.save(s);
                    });
        }
    }

    /** Returns total count of active (non-unsubscribed, non-suppressed) subscribers. */
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return subscriberRepository.countByUnsubscribedAtIsNullAndSuppressedAtIsNull();
    }

    /** Returns all active subscribers (for bulk send). Excludes suppressed (Story 10.29). */
    @Transactional(readOnly = true)
    public List<NewsletterSubscriber> findActiveSubscribers() {
        return subscriberRepository.findByUnsubscribedAtIsNullAndSuppressedAtIsNull();
    }

    /**
     * Unsubscribe a subscriber by email address (Story 10.17 — inbound email).
     * Silent no-op if email not found or already unsubscribed (anti-enumeration).
     */
    @Transactional
    public void unsubscribeByEmail(String email) {
        subscriberRepository.findByEmailIgnoreCase(email)
                .filter(sub -> sub.getUnsubscribedAt() == null)
                .ifPresent(sub -> {
                    sub.setUnsubscribedAt(Instant.now());
                    subscriberRepository.save(sub);
                    log.info("Unsubscribed newsletter subscriber via inbound email: {}",
                            LoggingUtils.maskEmail(sub.getEmail()));
                });
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
                .subscribedAt(toOffset(sub.getSubscribedAt()))
                .unsubscribedAt(toOffset(sub.getUnsubscribedAt()))
                .bounceType(sub.getBounceType())
                .bounceCount(sub.getBounceCount())
                .lastBouncedAt(toOffset(sub.getLastBouncedAt()))
                .suppressedAt(toOffset(sub.getSuppressedAt()))
                .build();
    }

    /** Convert a stored Instant to a UTC OffsetDateTime for the wire DTO (serialises identically as …Z). */
    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    // ── Story 10.28: Organizer subscriber management ────────────────────────

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS =
            java.util.Set.of("email", "firstName", "subscribedAt", "unsubscribedAt", "source", "language",
                    "suppressedAt", "bounceCount", "lastBouncedAt");

    /**
     * Find subscribers with filtering, search, sorting, and pagination.
     *
     * @param search   every word in e-mail, first name or username (folded, any order)
     * @param status   "active", "unsubscribed", or "all"
     * @param sortBy   field name (whitelisted)
     * @param sortDir  "asc" or "desc"
     * @param params   pagination params (1-based page + limit)
     */
    @Transactional(readOnly = true)
    public List<NewsletterSubscriber> findSubscribers(String search, String status,
                                                       String sortBy, String sortDir,
                                                       ch.batbern.shared.api.PaginationParams params) {
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "subscribedAt";
        org.springframework.data.domain.Sort.Direction direction =
                "asc".equalsIgnoreCase(sortDir) ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;

        int springPage = params.getOffset() / params.getLimit();
        // #1131: id breaks ties (1829 of 2087 production subscribers share one subscribed_at).
        org.springframework.data.domain.Pageable pageable = DeterministicPaging.pageRequest(
                springPage, params.getLimit(),
                org.springframework.data.domain.Sort.by(direction, safeSortBy));

        return subscriberRepository.findAll(subscriberFilter(search, status), pageable).getContent();
    }

    /**
     * Count subscribers matching search + status filter.
     */
    @Transactional(readOnly = true)
    public long countSubscribers(String search, String status) {
        return subscriberRepository.count(subscriberFilter(search, status));
    }

    /** Search (e-mail, first name, username; folded, any order) and status, in SQL. */
    private static Specification<NewsletterSubscriber> subscriberFilter(
            String search, String status) {
        return Specification.allOf(
                NewsletterSubscriberSpecification.matchesSearch(search),
                NewsletterSubscriberSpecification.hasStatus(status));
    }

    /**
     * Unsubscribe a subscriber by ID (organizer action).
     *
     * @throws NoSuchElementException if not found
     * @throws IllegalStateException if already unsubscribed (409)
     */
    @Transactional
    public NewsletterSubscriber unsubscribeById(UUID id) {
        NewsletterSubscriber sub = subscriberRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subscriber not found: " + id));
        if (sub.getUnsubscribedAt() != null) {
            throw new IllegalStateException("Subscriber is already unsubscribed: " + sub.getEmail());
        }
        sub.setUnsubscribedAt(Instant.now());
        log.info("Organizer unsubscribed subscriber: {}", LoggingUtils.maskEmail(sub.getEmail()));
        return subscriberRepository.save(sub);
    }

    /**
     * Re-subscribe a subscriber by ID (organizer action).
     *
     * @throws NoSuchElementException if not found
     * @throws IllegalStateException if already active (409)
     */
    @Transactional
    public NewsletterSubscriber resubscribeById(UUID id) {
        NewsletterSubscriber sub = subscriberRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subscriber not found: " + id));
        if (sub.getUnsubscribedAt() == null) {
            throw new IllegalStateException("Subscriber is already active: " + sub.getEmail());
        }
        sub.setUnsubscribedAt(null);
        sub.setSubscribedAt(Instant.now());
        log.info("Organizer re-subscribed subscriber: {}", LoggingUtils.maskEmail(sub.getEmail()));
        return subscriberRepository.save(sub);
    }

    /**
     * Unsuppress a subscriber by ID (organizer action — Story 10.29 AC8).
     * Clears suppressed_at, resets bounce_count to 0, clears bounce_type.
     *
     * @throws NoSuchElementException if not found
     * @throws IllegalStateException if not suppressed (409)
     */
    @Transactional
    public NewsletterSubscriber unsuppressById(UUID id) {
        NewsletterSubscriber sub = subscriberRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subscriber not found: " + id));
        if (sub.getSuppressedAt() == null) {
            throw new IllegalStateException("Subscriber is not suppressed: " + sub.getEmail());
        }
        sub.setSuppressedAt(null);
        sub.setBounceCount(0);
        sub.setBounceType(null);
        log.info("Organizer unsuppressed subscriber: {}", LoggingUtils.maskEmail(sub.getEmail()));
        return subscriberRepository.save(sub);
    }

    /**
     * Hard delete a subscriber by ID (organizer action).
     *
     * @throws NoSuchElementException if not found
     */
    @Transactional
    public void deleteById(UUID id) {
        NewsletterSubscriber sub = subscriberRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subscriber not found: " + id));
        subscriberRepository.delete(sub);
        log.info("Organizer deleted subscriber: {}", LoggingUtils.maskEmail(sub.getEmail()));
    }

    /**
     * Infer a display name from an email address local part.
     * E.g. "david.baumgartner@ace.ch" → "David Baumgartner",
     *      "john_doe@example.com" → "John Doe",
     *      "info@example.com" → "Info"
     */
    static String inferNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String local = email.substring(0, email.indexOf('@'));
        // Split on dots, underscores, hyphens
        String[] parts = local.split("[._-]");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    name.append(part.substring(1).toLowerCase());
                }
            }
        }
        return name.isEmpty() ? null : name.toString();
    }
}
