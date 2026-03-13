package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.dto.NewsletterPreviewResponse;
import ch.batbern.events.dto.NewsletterSendRequest;
import ch.batbern.events.dto.NewsletterSendResponse;
import ch.batbern.events.dto.NewsletterSendStatusResponse;
import ch.batbern.events.dto.NewsletterSubscribeRequest;
import ch.batbern.events.dto.NewsletterSubscriptionStatusResponse;
import ch.batbern.events.dto.NewsletterUnsubscribeRequest;
import ch.batbern.events.dto.PatchMySubscriptionRequest;
import ch.batbern.events.dto.SubscriberResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.NewsletterEmailService;
import ch.batbern.events.service.NewsletterSubscriberService;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.dto.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Controller for Newsletter Subscription & Sending endpoints (Story 10.7).
 *
 * <p>Public endpoints (no auth):
 * - POST /api/v1/newsletter/subscribe
 * - GET  /api/v1/newsletter/unsubscribe/verify?token=...
 * - POST /api/v1/newsletter/unsubscribe
 *
 * <p>Authenticated user endpoints:
 * - GET   /api/v1/newsletter/my-subscription
 * - PATCH /api/v1/newsletter/my-subscription
 *
 * <p>ORGANIZER-only endpoints:
 * - GET  /api/v1/newsletter/subscribers
 * - POST /api/v1/events/{eventCode}/newsletter/send
 * - POST /api/v1/events/{eventCode}/newsletter/preview
 * - GET  /api/v1/events/{eventCode}/newsletter/history
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class NewsletterController {

    private final NewsletterSubscriberService subscriberService;
    private final NewsletterEmailService emailService;
    private final EventRepository eventRepository;
    private final NewsletterSendRepository sendRepository;
    private final SecurityContextHelper securityContextHelper;

    // ── Public endpoints ──────────────────────────────────────────────────────

    /**
     * AC2: Subscribe to the newsletter (no auth required).
     * Returns 200 on success, 409 if already subscribed (handled by @ResponseStatus on exception).
     */
    @PostMapping("/newsletter/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody NewsletterSubscribeRequest request) {
        log.info("Newsletter subscribe request for email: {}", request.getEmail());
        subscriberService.subscribe(
                request.getEmail(),
                request.getFirstName(),
                request.getLanguage(),
                "explicit",
                null
        );
        return ResponseEntity.ok().build();
    }

    /**
     * AC3: Verify an unsubscribe token (no auth required).
     * Returns 200 with {email} on valid token, 404 if not found or already unsubscribed.
     */
    @GetMapping("/newsletter/unsubscribe/verify")
    public ResponseEntity<Map<String, String>> verifyUnsubscribe(@RequestParam String token) {
        log.debug("Newsletter unsubscribe verify for token: {}", token);
        Optional<String> email = subscriberService.verifyToken(token);
        return email.map(e -> ResponseEntity.ok(Map.of("email", e)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * AC3: Unsubscribe via token (no auth required).
     * Returns 200 on success, 404 if token not found.
     */
    @PostMapping("/newsletter/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody NewsletterUnsubscribeRequest request) {
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
     * AC7: Get authenticated user's subscription status.
     */
    @GetMapping("/newsletter/my-subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NewsletterSubscriptionStatusResponse> getMySubscription() {
        String username = securityContextHelper.getCurrentUsername();
        String email = securityContextHelper.getCurrentUserEmail();
        return ResponseEntity.ok(subscriberService.getMySubscription(username, email));
    }

    /**
     * AC7: Update authenticated user's subscription status.
     */
    @PatchMapping("/newsletter/my-subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NewsletterSubscriptionStatusResponse> patchMySubscription(
            @Valid @RequestBody PatchMySubscriptionRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        String email = securityContextHelper.getCurrentUserEmail();
        boolean subscribed = Boolean.TRUE.equals(request.getSubscribed());
        String language = request.getLanguage() != null ? request.getLanguage() : "de";
        return ResponseEntity.ok(subscriberService.patchMySubscription(username, email, subscribed, language));
    }

    // ── Organizer endpoints ───────────────────────────────────────────────────

    /**
     * AC10: Active subscriber count only (ORGANIZER only).
     * Cheap COUNT query — used by the newsletter tab to display subscriber totals.
     */
    @GetMapping("/newsletter/subscribers/count")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, Long>> getSubscriberCount() {
        return ResponseEntity.ok(Map.of("totalActive", subscriberService.getActiveCount()));
    }

    /**
     * Story 10.28: Paginated, searchable, sortable subscriber list (ORGANIZER only).
     */
    @GetMapping("/newsletter/subscribers")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PaginatedResponse<SubscriberResponse>> listSubscribers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "subscribedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {

        PaginationParams params = PaginationUtils.parseParams(page, limit);
        List<SubscriberResponse> data = subscriberService
                .findSubscribers(search, status, sortBy, sortDir, params)
                .stream().map(subscriberService::toResponse).toList();
        long total = subscriberService.countSubscribers(search, status);
        PaginationMetadata meta = PaginationUtils.generateMetadata(params, total);
        return ResponseEntity.ok(new PaginatedResponse<>(data, meta));
    }

    /**
     * Story 10.28: Unsubscribe a subscriber by ID (ORGANIZER only).
     */
    @PostMapping("/newsletter/subscribers/{id}/unsubscribe")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberResponse> unsubscribeSubscriber(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriberService.toResponse(subscriberService.unsubscribeById(id)));
    }

    /**
     * Story 10.28: Re-subscribe a subscriber by ID (ORGANIZER only).
     */
    @PostMapping("/newsletter/subscribers/{id}/resubscribe")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SubscriberResponse> resubscribeSubscriber(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriberService.toResponse(subscriberService.resubscribeById(id)));
    }

    /**
     * Story 10.28: Delete a subscriber by ID (ORGANIZER only).
     */
    @DeleteMapping("/newsletter/subscribers/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable UUID id) {
        subscriberService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * AC10: Preview newsletter for an event (ORGANIZER only, no sending).
     */
    @PostMapping("/events/{eventCode}/newsletter/preview")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterPreviewResponse> previewNewsletter(
            @PathVariable String eventCode,
            @Valid @RequestBody NewsletterSendRequest request) {
        Event event = findEventOrThrow(eventCode);
        NewsletterPreviewResponse preview = emailService.preview(
                event,
                Boolean.TRUE.equals(request.getIsReminder()),
                request.getLocale(),
                request.getTemplateKey()
        );
        return ResponseEntity.ok(preview);
    }

    /**
     * AC10: Send newsletter for an event (ORGANIZER only).
     */
    @PostMapping("/events/{eventCode}/newsletter/send")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendResponse> sendNewsletter(
            @PathVariable String eventCode,
            @Valid @RequestBody NewsletterSendRequest request) {
        Event event = findEventOrThrow(eventCode);
        String sentByUsername = securityContextHelper.getCurrentUsername();
        NewsletterSendResponse response = emailService.sendNewsletter(
                event,
                Boolean.TRUE.equals(request.getIsReminder()),
                request.getLocale(),
                sentByUsername,
                request.getTemplateKey()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * AC9: Get send history for an event (ORGANIZER only).
     */
    @GetMapping("/events/{eventCode}/newsletter/history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<NewsletterSendResponse>> getHistory(@PathVariable String eventCode) {
        Event event = findEventOrThrow(eventCode);
        List<NewsletterSend> sends = sendRepository.findByEventIdOrderBySentAtDesc(event.getId());
        List<NewsletterSendResponse> responses = sends.stream()
                .map(emailService::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Poll send-job progress (ORGANIZER only).
     * Frontend calls this every 3 seconds while status is PENDING or IN_PROGRESS.
     */
    @GetMapping("/events/{eventCode}/newsletter/sends/{sendId}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendStatusResponse> getSendStatus(
            @PathVariable String eventCode,
            @PathVariable UUID sendId) {
        Event event = findEventOrThrow(eventCode);
        NewsletterSend send = sendRepository.findByIdAndEventId(sendId, event.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Send not found: " + sendId + " for event " + eventCode));
        return ResponseEntity.ok(emailService.toStatusResponse(send));
    }

    /**
     * Retry failed recipients for a PARTIAL or FAILED send (ORGANIZER only).
     */
    @PostMapping("/events/{eventCode}/newsletter/sends/{sendId}/retry")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<NewsletterSendResponse> retryFailedRecipients(
            @PathVariable String eventCode,
            @PathVariable UUID sendId) {
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
}
