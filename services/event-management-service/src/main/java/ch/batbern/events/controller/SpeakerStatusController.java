package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.mapper.SpeakerPoolMapper;
import ch.batbern.events.speakers.api.generated.SpeakerStatusApi;
import ch.batbern.events.speakers.dto.generated.ContentSubmitResponse;
import ch.batbern.events.speakers.dto.generated.PromoteSpeakerRequest;
import ch.batbern.events.speakers.dto.generated.ReviewAction;
import ch.batbern.events.speakers.dto.generated.ReviewRequest;
import ch.batbern.events.speakers.dto.generated.SpeakerContentResponse;
import ch.batbern.events.speakers.dto.generated.SpeakerPoolResponse;
import ch.batbern.events.speakers.dto.generated.SpeakerStatusResponse;
import ch.batbern.events.speakers.dto.generated.StatusHistoryItem;
import ch.batbern.events.speakers.dto.generated.StatusSummaryResponse;
import ch.batbern.events.speakers.dto.generated.SubmitContentRequest;
import ch.batbern.events.speakers.dto.generated.UpdateStatusRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Speaker Status Management, Content Submission + Quality Review.
 * Stories 5.4 / 5.5 / 11.B-D.
 *
 * <p>API-consolidation Phase 7 (2026-06-28): implements the generated {@link SpeakerStatusApi}
 * (8 ops re-tagged into a 1:1 Speaker Status tag). Paths + {@code @Valid @RequestBody} binding
 * are inherited from the interface; the class carries only the {@code /api/v1} prefix, and the
 * overrides keep the method-level {@code @PreAuthorize} + cache eviction. All endpoints require
 * the ORGANIZER role.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerStatusController implements SpeakerStatusApi {

    private final SpeakerStatusService speakerStatusService;
    private final ContentSubmissionService contentSubmissionService;
    private final QualityReviewService qualityReviewService;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final ch.batbern.events.security.SecurityContextHelper securityContextHelper;
    private final ch.batbern.events.service.PrimarySpeakerResolver primarySpeakerResolver;
    private final SpeakerPoolMapper speakerPoolMapper;

    /**
     * Promote a CONTACTED speaker to READY (Story 11.D.1). Drives the {@code CONTACTED → READY}
     * transition (the User-provisioning gate per ADR-009 §0.2). Returns 409
     * {@code INVALID_PROMOTION_STATE} for any state other than CONTACTED.
     *
     * @return 200 OK with the updated {@link SpeakerPoolResponse}
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.STATUS_SUMMARY_CACHE, key = "#eventCode"),
        @CacheEvict(value = CacheConfig.STATUS_HISTORY_CACHE, key = "#eventCode + ':' + #speakerId")
    })
    public ResponseEntity<SpeakerPoolResponse> promoteSpeakerToReady(
            String eventCode, UUID speakerId, PromoteSpeakerRequest promoteSpeakerRequest) {

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
        String trimmedEmail = promoteSpeakerRequest.getEmail() != null
                ? promoteSpeakerRequest.getEmail().trim() : null;
        TransitionPayload payload = TransitionPayload.builder()
                .email(trimmedEmail)
                .firstName(promoteSpeakerRequest.getFirstName())
                .lastName(promoteSpeakerRequest.getLastName())
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

        // Story 11.E.9: apply the overlay so the response carries the live username/email.
        SpeakerPoolResponse response = speakerPoolMapper.toResponse(promoted);
        primarySpeakerResolver.applyOverlay(response, promoted);
        return ResponseEntity.ok(response);
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
     * Update speaker status (Story 5.4 AC1-2). READY is rejected here — it is reachable only via
     * POST /promote (Story 11.D.1).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerStatusResponse> updateSpeakerStatus(
            String eventCode, UUID speakerId, UpdateStatusRequest updateStatusRequest) {

        if (updateStatusRequest.getNewStatus() == SpeakerWorkflowState.READY) {
            throw new ReadyRequiresPromoteException(eventCode);
        }

        log.info("PUT /api/v1/events/{}/speakers/{}/status - newStatus: {}",
                eventCode, speakerId, updateStatusRequest.getNewStatus());

        String organizerUsername = getCurrentUsername();
        SpeakerStatusResponse response = speakerStatusService.updateStatus(
                eventCode, speakerId, organizerUsername, updateStatusRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Get speaker status history (Story 5.4 AC15).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<StatusHistoryItem>> getSpeakerStatusHistory(
            String eventCode, UUID speakerId) {
        log.info("GET /api/v1/events/{}/speakers/{}/status/history", eventCode, speakerId);
        return ResponseEntity.ok(speakerStatusService.getStatusHistory(eventCode, speakerId));
    }

    /**
     * Get status dashboard summary (Story 5.4 AC5-6, AC13).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<StatusSummaryResponse> getStatusSummary(String eventCode) {
        log.info("GET /api/v1/events/{}/speakers/status-summary", eventCode);
        return ResponseEntity.ok(speakerStatusService.getStatusSummary(eventCode));
    }

    /**
     * Submit speaker content on behalf (Story 5.5 AC6-10). Delegates to the consolidated
     * {@link ContentSubmissionService}; the organizer username is read from the security context.
     *
     * @return 201 Created with the {@link ContentSubmitResponse}
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<ContentSubmitResponse> submitSpeakerContent(
            String eventCode, UUID speakerId, SubmitContentRequest submitContentRequest) {

        log.info("POST /api/v1/events/{}/speakers/{}/content - title: {}",
                eventCode, speakerId, submitContentRequest.getPresentationTitle());

        String username = securityContextHelper.getCurrentUsername();
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                submitContentRequest.getPresentationTitle(),
                submitContentRequest.getPresentationAbstract(),
                submitContentRequest.getBio(),
                submitContentRequest.getProfilePictureUrl(),
                submitContentRequest.getPresentationUploadId()
        );

        ContentSubmitResponse response = contentSubmissionService.submit(
                speakerId, eventCode, payload, username);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * Get speaker content (Story 5.5 AC34).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerContentResponse> getSpeakerContent(
            String eventCode, UUID speakerId) {
        log.info("GET /api/v1/events/{}/speakers/{}/content", eventCode, speakerId);
        return ResponseEntity.ok(contentSubmissionService.getSpeakerContent(speakerId.toString()));
    }

    /**
     * Get quality-review queue (Story 5.5 AC11). API-consolidation Phase 7: maps the
     * CONTENT_SUBMITTED pool entries to {@link SpeakerPoolResponse} (the same shape as
     * GET /speakers/pool), replacing the prior raw-entity return.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<SpeakerPoolResponse>> getReviewQueue(String eventCode) {
        log.info("GET /api/v1/events/{}/speakers/review-queue", eventCode);
        List<SpeakerPoolResponse> queue = qualityReviewService.getReviewQueue(eventCode).stream()
                .map(speakerPoolMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(queue);
    }

    /**
     * Approve or reject speaker content (Story 5.5 AC13-14).
     *
     * @return 204 No Content on success
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> reviewSpeakerContent(
            String eventCode, UUID speakerId, ReviewRequest reviewRequest) {

        log.info("POST /api/v1/events/{}/speakers/{}/review - action: {}",
                eventCode, speakerId, reviewRequest.getAction());

        String moderatorUsername = getCurrentUsername();

        if (reviewRequest.getAction() == ReviewAction.APPROVE) {
            qualityReviewService.approveContent(speakerId.toString(), moderatorUsername);
        } else if (reviewRequest.getAction() == ReviewAction.REJECT) {
            qualityReviewService.rejectContent(
                    speakerId.toString(), reviewRequest.getFeedback(), moderatorUsername);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Extract current username from Spring Security context (ADR-001).
     */
    private String getCurrentUsername() {
        return securityContextHelper.getCurrentUsername();
    }
}
