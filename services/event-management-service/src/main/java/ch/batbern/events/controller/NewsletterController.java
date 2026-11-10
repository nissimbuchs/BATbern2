package ch.batbern.events.controller;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.events.newsletter.api.generated.NewsletterApi;
import ch.batbern.events.newsletter.dto.generated.ListNewsletterSubscribers200Response;
import ch.batbern.events.newsletter.dto.generated.MyNewsletterSubscriptionsResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterSubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.NewsletterSubscriptionStatusResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterUnsubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.PatchNewsletterSubscriptionRequest;
import ch.batbern.events.newsletter.dto.generated.PutMyNewsletterSubscriptionRequest;
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
 * <p>Public endpoints (no auth): subscribe, unsubscribe verify/confirm.
 * Authenticated: my-subscription get/patch.
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

    /** AC2: Subscribe to the newsletter (no auth). 200 on success, 409 if already subscribed. */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> subscribeNewsletter(NewsletterSubscribeRequest request) {
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

    /** AC7: Get authenticated user's subscription status. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NewsletterSubscriptionStatusResponse> getMyNewsletterSubscription() {
        String username = securityContextHelper.getCurrentUsername();
        String email = securityContextHelper.getCurrentUserEmail();
        return ResponseEntity.ok(subscriberService.getMySubscription(username, email));
    }

    /** #1244: the caller's newsletter state per address (primary first, then confirmed ones). */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyNewsletterSubscriptionsResponse> getMyNewsletterSubscriptions() {
        return ResponseEntity.ok(myAddressSubscriptions(securityContextHelper.getCurrentUsername()));
    }

    /** #1244: subscribe or unsubscribe one address; only the caller's primary or confirmed ones. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyNewsletterSubscriptionsResponse> putMyNewsletterSubscription(
            PutMyNewsletterSubscriptionRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!myAddresses(username).contains(email)) {
            log.warn("Newsletter change refused: {} is not an address of {}", LoggingUtils.maskEmail(email), username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String language = request.getLanguage() != null ? request.getLanguage() : "de";
        subscriberService.setAddressSubscription(username, email, Boolean.TRUE.equals(request.getSubscribed()),
                language);
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
                .addresses(subscriberService.addressSubscriptions(addresses, primary))
                .build();
    }

    /** AC7: Update authenticated user's subscription status. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NewsletterSubscriptionStatusResponse> patchMyNewsletterSubscription(
            PatchNewsletterSubscriptionRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        String email = securityContextHelper.getCurrentUserEmail();
        boolean subscribed = Boolean.TRUE.equals(request.getSubscribed());
        String language = request.getLanguage() != null ? request.getLanguage() : "de";
        return ResponseEntity.ok(subscriberService.patchMySubscription(username, email, subscribed, language));
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
