package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.PromoteSpeakerRequest;
import ch.batbern.events.dto.ReviewRequest;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.dto.SpeakerStatusResponse;
import ch.batbern.events.dto.StatusHistoryItem;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.SubmitContentRequest;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.exception.InvalidPromotionStateException;
import ch.batbern.events.exception.ReadyRequiresPromoteException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.ContentSubmissionService;
import ch.batbern.events.service.QualityReviewService;
import ch.batbern.events.service.SpeakerStatusService;
import ch.batbern.events.service.SpeakerWorkflowService;
import ch.batbern.events.service.content.ContentSubmissionPayload;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Speaker Status Management and Content Submission
 * Story 5.4: Speaker Status Management (AC1-18)
 * Story 5.5: Speaker Content Submission (AC6-10, AC34, AC37) + Quality Review (AC11-15)
 *
 * Endpoints:
 * - PUT  /api/v1/events/{code}/speakers/{speakerId}/status - Update speaker status
 * - GET  /api/v1/events/{code}/speakers/{speakerId}/status/history - Get status history
 * - GET  /api/v1/events/{code}/speakers/status-summary - Get status dashboard summary
 * - POST /api/v1/events/{code}/speakers/{speakerId}/content - Submit speaker content
 * - GET  /api/v1/events/{code}/speakers/{speakerId}/content - Get speaker content
 * - GET  /api/v1/events/{code}/speakers/review-queue - Get quality review queue
 * - POST /api/v1/events/{code}/speakers/{speakerId}/review - Approve/reject content
 *
 * Security: All endpoints require ORGANIZER role
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/speakers")
@RequiredArgsConstructor
@Slf4j
public class SpeakerStatusController {

    private final SpeakerStatusService speakerStatusService;
    private final ContentSubmissionService contentSubmissionService;
    private final QualityReviewService qualityReviewService;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final ch.batbern.events.security.SecurityContextHelper securityContextHelper;

    /**
     * Promote a CONTACTED speaker to READY (Story 11.D.1).
     *
     * <p>Drives the workflow transition {@code CONTACTED → READY} via
     * {@link SpeakerWorkflowService#transition} — the sole status writer per
     * ADR-009 §0.1. The READY hook calls
     * {@code UserApiClient.provisionUserWithRole(...)} to materialise the speaker
     * as a User + SPEAKER role (idempotent at the user layer per Story 11.C.2's NFR3).
     *
     * <p>Pre-check returns HTTP 409 with {@code details.code = INVALID_PROMOTION_STATE}
     * for any state other than {@code CONTACTED}. Re-promoting an already-READY speaker
     * is also rejected as 409 — same-state would route through {@code handleSameStateTransition}
     * which skips the side-effect hook, silently dropping the email payload; rejecting at
     * the controller surface avoids that hidden-no-op contract trap.
     *
     * @param eventCode event code in path (e.g., {@code BATbern56})
     * @param speakerId speaker pool ID in path
     * @param request   email, firstName, lastName — all required and {@code @NotBlank}
     *                  (Story 11.E.4 AC4). firstName / lastName populate Cognito's
     *                  {@code given_name} / {@code family_name} attributes; blank values
     *                  fail {@code GlobalExceptionHandler}'s 400 mapping.
     * @return 200 OK with the updated {@link SpeakerPoolResponse}
     */
    @PostMapping("/{speakerId}/promote")
    @PreAuthorize("hasRole('ORGANIZER')")
    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(value = CacheConfig.STATUS_SUMMARY_CACHE, key = "#eventCode"),
        @CacheEvict(value = CacheConfig.STATUS_HISTORY_CACHE,
                key = "#eventCode + ':' + #speakerId")
    })
    public ResponseEntity<SpeakerPoolResponse> promoteSpeakerToReady(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody PromoteSpeakerRequest request) {

        log.info("POST /api/v1/events/{}/speakers/{}/promote", eventCode, speakerId);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker pool entry not found: " + speakerId));

        if (!event.getId().equals(speaker.getEventId())) {
            throw new NotFoundException(
                    "Speaker " + speakerId + " does not belong to event " + eventCode);
        }

        SpeakerWorkflowState current = speaker.getStatus();
        if (current != SpeakerWorkflowState.CONTACTED) {
            throw new InvalidPromotionStateException(current,
                    buildInvalidPromotionMessage(current));
        }

        String username = securityContextHelper.getCurrentUsername();
        String trimmedEmail = request.email() != null ? request.email().trim() : null;
        TransitionPayload payload = TransitionPayload.builder()
                .email(trimmedEmail)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .build();

        SpeakerPool promoted;
        try {
            promoted = speakerWorkflowService
                    .transition(speakerId, SpeakerWorkflowState.READY, username, payload)
                    .speakerPool();
        } catch (InvalidStateTransitionException ex) {
            // TOCTOU: state changed between the pre-check and the transition reload. Re-classify
            // the generic 422 as the tailored 409 the dialog renders, using the latest known state.
            SpeakerWorkflowState latest = speakerPoolRepository.findById(speakerId)
                    .map(SpeakerPool::getStatus)
                    .orElse(current);
            throw new InvalidPromotionStateException(latest,
                    buildInvalidPromotionMessage(latest));
        }

        return ResponseEntity.ok(SpeakerPoolResponse.fromEntity(promoted));
    }

    private static String buildInvalidPromotionMessage(SpeakerWorkflowState state) {
        return switch (state) {
            case IDENTIFIED ->
                    "speaker must be in CONTACTED before promoting; log outreach first";
            case DECLINED ->
                    "speaker is DECLINED; cannot be promoted; create a new pool entry instead";
            case READY ->
                    "speaker is already READY; promote is only valid from CONTACTED";
            default ->
                    String.format("speaker is already in %s; promote is only valid from CONTACTED",
                            state.name());
        };
    }

    /**
     * Update speaker status
     * Story 5.4 AC1-2: Manual status updates with workflow validation
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param request Update status request with new status and optional reason
     * @return Updated speaker status response
     */
    @PutMapping("/{speakerId}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerStatusResponse> updateStatus(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody UpdateStatusRequest request) {

        // Story 11.B.3 AC5: READY requires an email payload for User provisioning and is
        // reachable only via POST /promote (Story 11.D.1). Jackson accepts READY as a valid
        // SpeakerWorkflowState enum value, so the rejection happens here (not at the
        // deserialization layer where the 5 removed legacy values are rejected — see
        // GlobalExceptionHandler.handleHttpMessageNotReadableException).
        if (request.getNewStatus() == SpeakerWorkflowState.READY) {
            throw new ReadyRequiresPromoteException(eventCode);
        }

        log.info("PUT /api/v1/events/{}/speakers/{}/status - newStatus: {}",
                eventCode, speakerId, request.getNewStatus());

        // Extract organizer username from security context
        String organizerUsername = getCurrentUsername();

        SpeakerStatusResponse response = speakerStatusService.updateStatus(
                eventCode,
                speakerId,
                organizerUsername,
                request
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get speaker status history
     * Story 5.4 AC15: Query status history timeline
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @return List of status changes ordered by time descending
     */
    @GetMapping("/{speakerId}/status/history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<StatusHistoryItem>> getStatusHistory(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId) {

        log.info("GET /api/v1/events/{}/speakers/{}/status/history", eventCode, speakerId);

        List<StatusHistoryItem> history = speakerStatusService.getStatusHistory(eventCode, speakerId);

        return ResponseEntity.ok(history);
    }

    /**
     * Get status dashboard summary
     * Story 5.4 AC5-6: Visual dashboard with status counts and acceptance rate
     * Story 5.4 AC13: Overflow detection
     *
     * @param eventCode Event code
     * @return Status summary with counts, acceptance rate, and overflow detection
     */
    @GetMapping("/status-summary")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<StatusSummaryResponse> getStatusSummary(
            @PathVariable String eventCode) {

        log.info("GET /api/v1/events/{}/speakers/status-summary", eventCode);

        StatusSummaryResponse summary = speakerStatusService.getStatusSummary(eventCode);

        return ResponseEntity.ok(summary);
    }

    /**
     * Submit speaker content (presentation title and abstract)
     * Story 5.5 AC6-10, AC33-34, AC37
     *
     * Creates a session with the presentation details and links the speaker.
     * Updates speaker status to CONTENT_SUBMITTED.
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param request Content submission request with title, abstract, and speaker details
     * @return Speaker content response with created session details
     */
    @PostMapping("/{speakerId}/content")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<ContentSubmitResponse> submitContent(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody SubmitContentRequest request) {

        log.info("POST /api/v1/events/{}/speakers/{}/content - title: {}",
                eventCode, speakerId, request.getPresentationTitle());

        // Story 11.C.2 — both content-submission endpoints share ContentSubmissionService.submit().
        // The organizer username is read from SecurityContext; the consolidated service handles
        // session/content/profile-patch/workflow-transition in a single transaction.
        String username = securityContextHelper.getCurrentUsername();
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                request.getPresentationTitle(),
                request.getPresentationAbstract(),
                request.getBio(),
                request.getProfilePictureUrl(),
                request.getPresentationUploadId()
        );

        ContentSubmitResponse response = contentSubmissionService.submit(
                speakerId, eventCode, payload, username);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * Get speaker content (presentation details)
     * Story 5.5 AC34
     *
     * Retrieves existing presentation content for a speaker.
     * Handles orphaned session references by resetting speaker state.
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @return Speaker content response or empty if no content submitted
     */
    @GetMapping("/{speakerId}/content")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerContentResponse> getContent(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId) {

        log.info("GET /api/v1/events/{}/speakers/{}/content", eventCode, speakerId);

        SpeakerContentResponse response = contentSubmissionService.getSpeakerContent(
                speakerId.toString()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get review queue for quality review
     * Story 5.5 AC11: Review queue shows all speakers with content_submitted status
     *
     * @param eventCode Event code
     * @return List of speakers pending quality review, ordered by submission date (oldest first)
     */
    @GetMapping("/review-queue")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<SpeakerPool>> getReviewQueue(
            @PathVariable String eventCode) {

        log.info("GET /api/v1/events/{}/speakers/review-queue", eventCode);

        // Get event ID from event code (delegated to service)
        List<SpeakerPool> reviewQueue = qualityReviewService.getReviewQueue(eventCode);

        return ResponseEntity.ok(reviewQueue);
    }

    /**
     * Approve or reject speaker content quality review
     * Story 5.5 AC13-14: Content approval/rejection workflow
     * Story 5.5 AC17: Auto-update to confirmed when both quality_reviewed AND slot_assigned
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param request Review request with action (approve/reject) and optional feedback
     * @return 204 No Content on success
     */
    @PostMapping("/{speakerId}/review")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> reviewContent(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody ReviewRequest request) {

        log.info("POST /api/v1/events/{}/speakers/{}/review - action: {}",
                eventCode, speakerId, request.getAction());

        String moderatorUsername = getCurrentUsername();

        if (request.getAction() == ReviewRequest.ReviewAction.APPROVE) {
            qualityReviewService.approveContent(speakerId.toString(), moderatorUsername);
        } else if (request.getAction() == ReviewRequest.ReviewAction.REJECT) {
            qualityReviewService.rejectContent(speakerId.toString(), request.getFeedback(), moderatorUsername);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Extract current username from Spring Security context
     */
    private String getCurrentUsername() {
        // Use SecurityContextHelper to extract username from JWT custom:username claim (ADR-001)
        return securityContextHelper.getCurrentUsername();
    }
}
