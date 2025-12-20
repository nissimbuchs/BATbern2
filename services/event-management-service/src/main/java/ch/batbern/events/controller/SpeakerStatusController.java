package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ReviewRequest;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.dto.SpeakerStatusResponse;
import ch.batbern.events.dto.StatusHistoryItem;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.SubmitContentRequest;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.service.QualityReviewService;
import ch.batbern.events.service.SpeakerContentSubmissionService;
import ch.batbern.events.service.SpeakerStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final SpeakerContentSubmissionService contentSubmissionService;
    private final QualityReviewService qualityReviewService;

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
    public ResponseEntity<SpeakerContentResponse> submitContent(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody SubmitContentRequest request) {

        log.info("POST /api/v1/events/{}/speakers/{}/content - title: {}",
                eventCode, speakerId, request.getPresentationTitle());

        SpeakerContentResponse response = contentSubmissionService.submitContent(
                speakerId.toString(),
                request.getPresentationTitle(),
                request.getPresentationAbstract(),
                request.getUsername(),
                request.getSpeakerName(),
                request.getEmail(),
                request.getCompany()
        );

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system";  // Fallback for tests
    }
}
