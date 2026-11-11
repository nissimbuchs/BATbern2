package ch.batbern.events.controller;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.events.newsletter.api.generated.NewsletterApi;
import ch.batbern.events.newsletter.dto.generated.ListNewsletterSubscribers200Response;
import ch.batbern.events.newsletter.dto.generated.MyNewsletterSubscriptionsResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterSubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.NewsletterUnsubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.PatchMyNewsletterSubscriptionRequest;
import ch.batbern.events.newsletter.dto.generated.SubscriberResponse;
import ch.batbern.events.newsletter.dto.generated.VerifyUnsubscribeToken200Response;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.NewsletterSubscriberService;
import ch.batbern.events.service.PersonAddresses;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for Newsletter Subscription &amp; Sending endpoints (Story 10.7).
 * Implements the generated {@link NewsletterApi} contract (event-newsletter-api.openapi.yml),
 * so verb/path/validation annotations are inherited from the interface (Phase 7 — ADR-006).
 *
 * <p>Public endpoints (no auth): subscribe ({@code POST /newsletter/subscribers}, Turnstile and
 * rate limit at the gateway), unsubscribe verify/confirm.
 * Authenticated: {@code /newsletter/my-subscriptions} GET and PATCH, per address (4.10c).
 * ORGANIZER-only: subscriber management. Sending, preview and retry are the Mail Center composer's
 * ({@code /events/{eventCode}/mails}, Story 16.8; the old endpoints went with 16.8c).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class NewsletterController implements NewsletterApi {

    private final NewsletterSubscriberService subscriberService;
    private final SecurityContextHelper securityContextHelper;
    private final PersonAddresses personAddresses;

    // ── Public endpoints ──────────────────────────────────────────────────────

    /**
     * AC2: Subscribe to the newsletter (no auth), {@code POST /newsletter/subscribers} since 4.10c.
     * 200 on success, 409 if already subscribed, 400 for a reserved domain. Sends no mail.
     */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> createNewsletterSubscriber(NewsletterSubscribeRequest request) {
        log.info("Newsletter subscribe request for email: {}", LoggingUtils.maskEmail(request.getEmail()));
        subscriberService.subscribe(
                request.getEmail(),
                request.getFirstName(),
                request.getLanguage() != null ? request.getLanguage().getValue() : null,
                "explicit",
                null
        );
        return ResponseEntity.ok().build();
    }

    /** AC3: Verify an unsubscribe token (no auth). 200 with {email} on valid token, 404 otherwise. */
    @Override
    public ResponseEntity<VerifyUnsubscribeToken200Response> verifyUnsubscribeToken(String token) {
        log.debug("Newsletter unsubscribe verify for token: {}", token);
        Optional<String> email = subscriberService.verifyToken(token);
        return email.map(e -> ResponseEntity.ok(new VerifyUnsubscribeToken200Response().email(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** AC3: Unsubscribe via token (no auth). 200 on success, 404 if token not found. */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> unsubscribeNewsletter(NewsletterUnsubscribeRequest request) {
        log.info("Newsletter unsubscribe via token");
        try {
            subscriberService.unsubscribeByToken(request.getToken());
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Authenticated user endpoints ──────────────────────────────────────────

    /**
     * #1244, 4.10c: the caller's newsletter state per address: primary first, then confirmed
     * ones, then addresses still subscribed under the username that are no longer theirs.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyNewsletterSubscriptionsResponse> getMyNewsletterSubscriptions() {
        return ResponseEntity.ok(myAddressSubscriptions(securityContextHelper.getCurrentUsername()));
    }

    /**
     * 4.10c: change one address, only the fields present. Allowed for the caller's primary or a
     * confirmed address; a stale address (still subscribed under the username, no longer the
     * caller's) may only be unsubscribed. Anything else is 403 and changes nothing.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyNewsletterSubscriptionsResponse> patchMyNewsletterSubscriptions(
            PatchMyNewsletterSubscriptionRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean mine = myAddresses(username).contains(email);
        boolean staleUnsubscribe = !mine
                && Boolean.FALSE.equals(request.getSubscribed())
                && request.getLanguage() == null
                && subscriberService.staleAddresses(username, List.of()).contains(email);
        if (!mine && !staleUnsubscribe) {
            log.warn("Newsletter change refused: {} is not an address of {}", LoggingUtils.maskEmail(email), username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        subscriberService.patchAddressSubscription(username, email, request.getSubscribed(),
                request.getLanguage() != null ? request.getLanguage().getValue() : null);
        return ResponseEntity.ok(myAddressSubscriptions(username));
    }

    /** Primary first, then confirmed additional addresses; the login address if CUMS is unreachable. */
    private List<String> myAddresses(String username) {
        List<String> addresses = new ArrayList<>(personAddresses.verified(username));
        if (addresses.isEmpty()) {
            String email = securityContextHelper.getCurrentUserEmail();
            if (email != null && !email.isBlank()) {
                addresses.add(email.trim().toLowerCase(Locale.ROOT));
            }
        }
        return addresses;
    }

    private MyNewsletterSubscriptionsResponse myAddressSubscriptions(String username) {
        List<String> addresses = myAddresses(username);
        String primary = addresses.isEmpty() ? null : addresses.get(0);
        return MyNewsletterSubscriptionsResponse.builder()
                .addresses(subscriberService.addressSubscriptions(username, addresses, primary))
                .build();
    }

    // ── Organizer endpoints ───────────────────────────────────────────────────

    /** Story 10.28: Paginated, searchable, sortable subscriber list (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ListNewsletterSubscribers200Response> listNewsletterSubscribers(
            Integer page,
            Integer limit,
            String search,
            String status,
            String sort) {

        // ADR-013 §3: single `sort` vocabulary. Default -subscribedAt = newest first.
        // `status` (all/active/unsubscribed) and `search` stay as typed params per the consolidation plan.
        List<SortCriteria> sortCriteria = SortParser.parse(sort);
        SortCriteria primary = sortCriteria.isEmpty()
                ? new SortCriteria("subscribedAt", SortDirection.DESC) : sortCriteria.get(0);
        String sortBy = primary.getField();
        String sortDir = primary.getDirection() == SortDirection.DESC ? "desc" : "asc";

        PaginationParams params = PaginationUtils.parseParams(page, limit);
        List<SubscriberResponse> data = subscriberService
                .findSubscribers(search, status, sortBy, sortDir, params)
                .stream().map(subscriberService::toResponse).toList();
        long total = subscriberService.countSubscribers(search, status);
        PaginationMetadata meta = PaginationUtils.generateMetadata(params, total);
        return ResponseEntity.ok(new ListNewsletterSubscribers200Response().data(data).pagination(meta));
    }

    /** Story 10.28: Unsubscribe a subscriber by ID (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberResponse> unsubscribeSubscriberById(UUID id) {
        return ResponseEntity.ok(subscriberService.toResponse(subscriberService.unsubscribeById(id)));
    }

    /** Story 10.28: Re-subscribe a subscriber by ID (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberResponse> resubscribeSubscriberById(UUID id) {
        return ResponseEntity.ok(subscriberService.toResponse(subscriberService.resubscribeById(id)));
    }

    /**
     * Story 10.29 AC8: Unsuppress a subscriber by ID (ORGANIZER).
     * Clears suppressed_at, resets bounce_count, clears bounce_type. 404 if not found, 409 if not suppressed.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberResponse> unsuppressSubscriberById(UUID id) {
        return ResponseEntity.ok(subscriberService.toResponse(subscriberService.unsuppressById(id)));
    }

    /** Story 10.28: Delete a subscriber by ID (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteSubscriberById(UUID id) {
        subscriberService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
