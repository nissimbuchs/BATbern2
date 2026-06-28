package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.newsletter.api.generated.NewsletterApi;
import ch.batbern.events.newsletter.dto.generated.ListNewsletterSubscribers200Response;
import ch.batbern.events.newsletter.dto.generated.NewsletterPreviewResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterSendRequest;
import ch.batbern.events.newsletter.dto.generated.NewsletterSendResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterSendStatusResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterSubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.NewsletterSubscriptionStatusResponse;
import ch.batbern.events.newsletter.dto.generated.NewsletterUnsubscribeRequest;
import ch.batbern.events.newsletter.dto.generated.PatchNewsletterSubscriptionRequest;
import ch.batbern.events.newsletter.dto.generated.SubscriberCountResponse;
import ch.batbern.events.newsletter.dto.generated.SubscriberResponse;
import ch.batbern.events.newsletter.dto.generated.VerifyUnsubscribeToken200Response;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.NewsletterEmailService;
import ch.batbern.events.service.NewsletterSubscriberService;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Controller for Newsletter Subscription &amp; Sending endpoints (Story 10.7).
 * Implements the generated {@link NewsletterApi} contract (event-newsletter-api.openapi.yml),
 * so verb/path/validation annotations are inherited from the interface (Phase 7 — ADR-006).
 *
 * <p>Public endpoints (no auth): subscribe, unsubscribe verify/confirm.
 * Authenticated: my-subscription get/patch.
 * ORGANIZER-only: subscriber management, preview, send, history.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class NewsletterController implements NewsletterApi {

    private final NewsletterSubscriberService subscriberService;
    private final NewsletterEmailService emailService;
    private final EventRepository eventRepository;
    private final NewsletterSendRepository sendRepository;
    private final SecurityContextHelper securityContextHelper;

    // ── Public endpoints ──────────────────────────────────────────────────────

    /** AC2: Subscribe to the newsletter (no auth). 200 on success, 409 if already subscribed. */
    @Override
    public ResponseEntity<Void> subscribeNewsletter(NewsletterSubscribeRequest request) {
        log.info("Newsletter subscribe request for email: {}", request.getEmail());
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

    /** AC10: Active subscriber count only (ORGANIZER). Cheap COUNT query for the newsletter tab. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberCountResponse> getSubscriberCount() {
        return ResponseEntity.ok(new SubscriberCountResponse().totalActive(subscriberService.getActiveCount()));
    }

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

    /** AC10: Preview newsletter for an event (ORGANIZER, no sending). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterPreviewResponse> previewEventNewsletter(
            String eventCode,
            NewsletterSendRequest request) {
        Event event = findEventOrThrow(eventCode);
        NewsletterPreviewResponse preview = emailService.preview(
                event,
                Boolean.TRUE.equals(request.getIsReminder()),
                localeOf(request),
                request.getTemplateKey(),
                Boolean.TRUE.equals(request.getTestMode())
        );
        return ResponseEntity.ok(preview);
    }

    /** AC10: Send newsletter for an event (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendResponse> sendEventNewsletter(
            String eventCode,
            NewsletterSendRequest request) {
        Event event = findEventOrThrow(eventCode);
        String sentByUsername = securityContextHelper.getCurrentUsername();
        NewsletterSendResponse response = emailService.sendNewsletter(
                event,
                Boolean.TRUE.equals(request.getIsReminder()),
                localeOf(request),
                sentByUsername,
                request.getTemplateKey(),
                request.getMaxRecipients(),
                Boolean.TRUE.equals(request.getTestMode())
        );
        return ResponseEntity.ok(response);
    }

    /** AC9: Get send history for an event (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<NewsletterSendResponse>> getNewsletterHistory(String eventCode) {
        Event event = findEventOrThrow(eventCode);
        List<NewsletterSend> sends = sendRepository.findByEventIdOrderBySentAtDesc(event.getId());
        List<NewsletterSendResponse> responses = sends.stream()
                .map(emailService::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Poll send-job progress (ORGANIZER). Frontend calls this every 3s while PENDING or IN_PROGRESS.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendStatusResponse> getNewsletterSendStatus(
            String eventCode,
            UUID sendId) {
        Event event = findEventOrThrow(eventCode);
        NewsletterSend send = sendRepository.findByIdAndEventId(sendId, event.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Send not found: " + sendId + " for event " + eventCode));
        return ResponseEntity.ok(emailService.toStatusResponse(send));
    }

    /** Retry failed recipients for a PARTIAL or FAILED send (ORGANIZER). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendResponse> retryNewsletterSend(
            String eventCode,
            UUID sendId) {
        Event event = findEventOrThrow(eventCode);
        NewsletterSend send = sendRepository.findByIdAndEventId(sendId, event.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Send not found: " + sendId + " for event " + eventCode));
        try {
            String sentByUsername = securityContextHelper.getCurrentUsername();
            NewsletterSendResponse response = emailService.retryFailedRecipients(send, event, sentByUsername);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event findEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));
    }

    /** Generated locale is the de/en LocaleEnum; the email service operates on the wire string. */
    private static String localeOf(NewsletterSendRequest request) {
        return request.getLocale() != null ? request.getLocale().getValue() : null;
    }
}
