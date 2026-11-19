package ch.batbern.events.service;

import ch.batbern.events.config.AfterCommit;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.newsletter.dto.generated.NewsletterAddressSubscription;
import ch.batbern.events.newsletter.dto.generated.SubscriberResponse;
import ch.batbern.events.exception.ReservedEmailDomainException;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.specification.NewsletterSubscriberSpecification;
import ch.batbern.shared.api.DeterministicPaging;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
 * and authenticated user subscription management. Double opt-in (#1288): an anonymous or
 * registration subscription stays pending ({@code confirmedAt} null, no newsletter) until its owner
 * confirms it; a signed-in owner's own confirmed address and an organizer's resubscribe are active
 * at once.
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
    private final NewsletterConfirmationEmailService confirmationEmailService;
    private final Clock clock;

    /** #1288: at most one confirmation mail per address in this window. */
    static final Duration CONFIRMATION_MAIL_INTERVAL = Duration.ofHours(1);

    /**
     * #1288, double opt-in: an anonymous subscribe ({@code POST /newsletter/subscribers}). A new,
     * pending or unsubscribed address becomes (or stays) pending, keeps or gets its confirmation
     * token, and gets the confirmation mail after the commit, in {@code language} ({@code en}, else
     * German), at most once per {@link #CONFIRMATION_MAIL_INTERVAL}. An active or suppressed address
     * is left exactly as it is and gets no mail, so the
     * caller cannot change or learn anything about someone else's subscription.
     *
     * @throws ReservedEmailDomainException for an RFC 2606 / RFC 6761 address (400)
     */
    @Transactional(noRollbackFor = ReservedEmailDomainException.class)
    public void requestSubscription(String email, String firstName, String language) {
        String address = normalise(email);
        String mailLanguage = "en".equals(language) ? "en" : "de";

        NewsletterSubscriber sub = subscriberRepository.findByEmailIgnoreCase(address).orElse(null);
        if (sub != null && (sub.isActive() || sub.getSuppressedAt() != null)) {
            log.info("Newsletter subscribe for an active or suppressed address {}: unchanged, no mail",
                    LoggingUtils.maskEmail(address));
            return;
        }
        if (sub == null) {
            sub = newRow(address, firstName, mailLanguage, "explicit", null);
        } else {
            // Pending or unsubscribed: the language of the newest request, the same link as before.
            sub.setLanguage(mailLanguage);
        }
        if (sub.getConfirmationToken() == null) {
            sub.setConfirmationToken(UUID.randomUUID().toString());
        }
        boolean mail = claimConfirmationMail(sub);
        NewsletterSubscriber saved = subscriberRepository.save(sub);
        if (mail) {
            log.info("Newsletter subscription of {} awaits confirmation; confirmation mail follows",
                    LoggingUtils.maskEmail(address));
            AfterCommit.run(() -> confirmationEmailService.send(saved));
        } else {
            log.info("Newsletter subscription of {} awaits confirmation; mail already sent within the last hour",
                    LoggingUtils.maskEmail(address));
        }
    }

    /**
     * #1288: an organizer confirmed a registration of this address. That does not prove the address,
     * so a pending registration opt-in is not activated: its owner gets the confirmation mail instead
     * (at most once per {@link #CONFIRMATION_MAIL_INTERVAL}).
     */
    @Transactional
    public void requestConfirmationOfRegistrationOptIn(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        subscriberRepository.findByEmailIgnoreCase(email.trim())
                .filter(sub -> sub.getConfirmationToken() != null)
                .filter(sub -> "registration".equals(sub.getSource()))
                .filter(sub -> sub.getSuppressedAt() == null)
                .filter(this::claimConfirmationMail)
                .ifPresent(sub -> {
                    NewsletterSubscriber saved = subscriberRepository.save(sub);
                    log.info("Newsletter opt-in of {}: registration confirmed by an organizer, mail follows",
                            LoggingUtils.maskEmail(sub.getEmail()));
                    AfterCommit.run(() -> confirmationEmailService.send(saved));
                });
    }

    /** True, and the send time recorded, when no confirmation mail went out in the last hour. */
    private boolean claimConfirmationMail(NewsletterSubscriber sub) {
        Instant now = clock.instant();
        Instant last = sub.getConfirmationSentAt();
        if (last != null && last.isAfter(now.minus(CONFIRMATION_MAIL_INTERVAL))) {
            return false;
        }
        sub.setConfirmationSentAt(now);
        return true;
    }

    /**
     * #1288: the newsletter box of a registration. Like {@link #requestSubscription} the address
     * stays pending, but no mail is sent: the registration's own confirmation link proves the
     * address, and {@link #confirmFromRegistration} activates it. An active or suppressed address is
     * left as it is.
     *
     * @throws ReservedEmailDomainException for an RFC 2606 / RFC 6761 address
     */
    @Transactional(noRollbackFor = ReservedEmailDomainException.class)
    public void subscribeFromRegistration(String email, String displayName, String username) {
        String address = normalise(email);
        NewsletterSubscriber sub = subscriberRepository.findByEmailIgnoreCase(address).orElse(null);
        if (sub != null && (sub.isActive() || sub.getSuppressedAt() != null)) {
            log.debug("Newsletter opt-in of a registration: {} already active or suppressed, unchanged",
                    LoggingUtils.maskEmail(address));
            return;
        }
        if (sub == null) {
            sub = newRow(address, displayName, "de", "registration", username);
        } else {
            sub.setSource("registration");
            if (sub.getUsername() == null) {
                sub.setUsername(username);
            }
        }
        if (sub.getConfirmationToken() == null) {
            sub.setConfirmationToken(UUID.randomUUID().toString());
        }
        subscriberRepository.save(sub);
        log.info("Newsletter opt-in of a registration for {} awaits the registration's confirmation",
                LoggingUtils.maskEmail(address));
    }

    /**
     * #1288: a registration was confirmed by its owner. The newsletter opt-in made with it
     * ({@link #subscribeFromRegistration}: awaiting confirmation, source {@code registration}) becomes
     * active. Any other row of the address is left alone: confirming a registration without the box
     * does not subscribe, and an anonymous request still needs its own link.
     */
    @Transactional
    public void confirmFromRegistration(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        subscriberRepository.findByEmailIgnoreCase(email.trim())
                .filter(sub -> sub.getConfirmationToken() != null)
                .filter(sub -> "registration".equals(sub.getSource()))
                .filter(sub -> sub.getSuppressedAt() == null)
                .ifPresent(sub -> {
                    activate(sub);
                    subscriberRepository.save(sub);
                    log.info("Newsletter opt-in of {} active: its registration was confirmed",
                            LoggingUtils.maskEmail(sub.getEmail()));
                });
    }

    /**
     * #1288: the link of the confirmation mail. The subscription becomes active and the token is
     * used up.
     *
     * @throws NoSuchElementException unknown or already used token (404), nothing changes
     */
    @Transactional
    public void confirm(String token) {
        NewsletterSubscriber sub = (token == null || token.isBlank() ? Optional.<NewsletterSubscriber>empty()
                : subscriberRepository.findByConfirmationToken(token))
                .orElseThrow(() -> new NoSuchElementException("Unknown or used newsletter confirmation token"));
        activate(sub);
        subscriberRepository.save(sub);
        log.info("Newsletter subscription of {} confirmed by its owner", LoggingUtils.maskEmail(sub.getEmail()));
    }

    /**
     * Subscribe on a trusted request, active at once: the signed-in owner of a confirmed address
     * (4.10c). A new address is created active, an unsubscribed or pending one is activated, an
     * active one stays as it is.
     */
    private void subscribeTrusted(String email, String language, String source, String username) {
        String address = normalise(email);
        Optional<NewsletterSubscriber> existing = subscriberRepository.findByEmailIgnoreCase(address);
        if (existing.isEmpty()) {
            NewsletterSubscriber sub = newRow(address, null,
                    language != null && !language.isBlank() ? language : "de", source, username);
            activate(sub);
            subscriberRepository.save(sub);
            log.info("Created newsletter subscriber: {}", LoggingUtils.maskEmail(address));
            return;
        }
        NewsletterSubscriber sub = existing.get();
        if (sub.getConfirmedAt() != null && sub.getUnsubscribedAt() == null) {
            log.debug("Address of {} already subscribed, unchanged", username);
            return;
        }
        activate(sub);
        sub.setSource(source);
        if (username != null) {
            sub.setUsername(username);
        }
        subscriberRepository.save(sub);
        log.info("Reactivated newsletter subscriber: {}", LoggingUtils.maskEmail(address));
    }

    /**
     * Unsubscribed from now, and an outstanding confirmation link is void (#1288): an old link must
     * not subscribe again an address its owner, an organizer or an inbound reply removed.
     */
    private static void unsubscribe(NewsletterSubscriber sub) {
        sub.setUnsubscribedAt(Instant.now());
        sub.setConfirmationToken(null);
    }

    /** Active from now: confirmed (the first time only), no longer unsubscribed, the link used up. */
    private static void activate(NewsletterSubscriber sub) {
        Instant now = Instant.now();
        if (sub.getConfirmedAt() == null) {
            sub.setConfirmedAt(now);
        }
        if (sub.getUnsubscribedAt() != null) {
            sub.setUnsubscribedAt(null);
            sub.setSubscribedAt(now);
        }
        sub.setConfirmationToken(null);
    }

    /** Rejects reserved domains, then lower-cases: one row per address (#1099). */
    private static String normalise(String email) {
        // Block RFC 2606 / RFC 6761 reserved domains so test fixtures and scanners
        // (e.g. zaproxy@example.com, speaker-test-…@example.com) can't pollute prod.
        if (isReservedDomain(email)) {
            log.warn("Rejected newsletter subscribe for reserved domain: {}", LoggingUtils.maskEmail(email));
            throw new ReservedEmailDomainException(email);
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** A new, not yet confirmed row; the name is inferred from the address when none is given. */
    private static NewsletterSubscriber newRow(String address, String firstName, String language, String source,
                                               String username) {
        String name = (firstName != null && !firstName.isBlank()) ? firstName : inferNameFromEmail(address);
        return NewsletterSubscriber.builder()
                .email(address)
                .firstName(name)
                .language(language)
                .source(source)
                .username(username)
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();
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
        unsubscribe(sub);
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
                    .subscribed(row.map(NewsletterSubscriberService::isSubscribed).orElse(false))
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

    /** Subscribed as the account shows it: confirmed and not unsubscribed; pending is not (#1288). */
    private static boolean isSubscribed(NewsletterSubscriber sub) {
        return sub.getConfirmedAt() != null && sub.getUnsubscribedAt() == null;
    }

    private List<NewsletterSubscriber> staleRows(String username, Collection<String> own) {
        if (username == null) {
            return List.of();
        }
        return subscriberRepository.findAllByUsername(username).stream()
                .filter(NewsletterSubscriberService::isSubscribed)
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
            // #1288: the caller checked that the address is the user's own, confirmed one.
            subscribeTrusted(email, language, "account", username);
        } else if (Boolean.FALSE.equals(subscribed)) {
            subscriberRepository.findByEmailIgnoreCase(email)
                    .filter(s -> s.getUnsubscribedAt() == null)
                    .ifPresent(s -> {
                        unsubscribe(s);
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

    /** Count of active subscribers: confirmed (#1288), not unsubscribed, not suppressed. */
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return subscriberRepository.countActive();
    }

    /** All active subscribers (for bulk send): confirmed, not unsubscribed, not suppressed. */
    @Transactional(readOnly = true)
    public List<NewsletterSubscriber> findActiveSubscribers() {
        return subscriberRepository.findAllActive();
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
                    unsubscribe(sub);
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
                .confirmedAt(toOffset(sub.getConfirmedAt()))
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
     * @param status   "active", "pending", "unsubscribed", "suppressed" or "all"
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
        unsubscribe(sub);
        log.info("Organizer unsubscribed subscriber: {}", LoggingUtils.maskEmail(sub.getEmail()));
        return subscriberRepository.save(sub);
    }

    /**
     * Re-subscribe a subscriber by ID (organizer action): active at once, confirmed if it never was
     * (#1288).
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
        activate(sub);
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
