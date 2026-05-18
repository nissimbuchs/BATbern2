package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentDraftRequest;
import ch.batbern.events.dto.ContentDraftResponse;
import ch.batbern.events.dto.ContentSubmitRequest;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.SpeakerMaterialConfirmRequest;
import ch.batbern.events.dto.SpeakerMaterialConfirmResponse;
import ch.batbern.events.dto.SpeakerMaterialUploadRequest;
import ch.batbern.events.dto.SpeakerMaterialUploadResponse;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.service.ContentSubmissionService;
import ch.batbern.events.service.SpeakerPortalAuthorizationService;
import ch.batbern.events.service.SpeakerPortalMaterialsService;
import ch.batbern.events.service.content.ContentSubmissionPayload;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal content submission.
 *
 * <p>Story 6.3: Speaker Content Self-Submission Portal.
 *
 * <p>Story 11.E.3 (ADR-009 §Decision 3): Cognito Bearer + {@code @PreAuthorize("hasRole('SPEAKER')")}
 * replace the previous magic-link token path. Every endpoint takes {@code eventCode} as a
 * path parameter; {@link SpeakerPortalAuthorizationService} gates access by pool ownership.
 *
 * <p>Endpoints (Story 11.E.3):
 * <ul>
 *   <li>{@code GET    /api/v1/speaker-portal/events/{eventCode}/content}</li>
 *   <li>{@code POST   /api/v1/speaker-portal/events/{eventCode}/content/draft}</li>
 *   <li>{@code POST   /api/v1/speaker-portal/events/{eventCode}/content/submit}</li>
 *   <li>{@code POST   /api/v1/speaker-portal/events/{eventCode}/materials/presigned-url}</li>
 *   <li>{@code POST   /api/v1/speaker-portal/events/{eventCode}/materials/confirm}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalContentController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalContentController.class);

    private final ContentSubmissionService contentSubmissionService;
    private final SpeakerPortalMaterialsService materialsService;
    private final SpeakerPortalAuthorizationService authorizationService;

    public SpeakerPortalContentController(
            ContentSubmissionService contentSubmissionService,
            SpeakerPortalMaterialsService materialsService,
            SpeakerPortalAuthorizationService authorizationService) {
        this.contentSubmissionService = contentSubmissionService;
        this.materialsService = materialsService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/events/{eventCode}/content")
    public ResponseEntity<SpeakerContentInfo> getContentInfo(
            @PathVariable String eventCode,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        LOG.info("Content info request: username={} eventCode={} ip={}",
                actor.username(), eventCode, getClientIp(httpRequest));

        SpeakerContentInfo contentInfo = contentSubmissionService.getContentInfo(speaker);
        return ResponseEntity.ok(contentInfo);
    }

    @PostMapping("/events/{eventCode}/content/draft")
    public ResponseEntity<ContentDraftResponse> saveDraft(
            @PathVariable String eventCode,
            @Valid @RequestBody ContentDraftRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        LOG.debug("Draft save request: username={} eventCode={} ip={}",
                actor.username(), eventCode, getClientIp(httpRequest));

        try {
            ContentDraftResponse response = contentSubmissionService.saveDraft(speaker, request);
            LOG.info("Draft saved - draftId: {} eventCode={}", response.draftId(), eventCode);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOG.warn("Draft save failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        }
    }

    @PostMapping("/events/{eventCode}/content/submit")
    public ResponseEntity<ContentSubmitResponse> submitContent(
            @PathVariable String eventCode,
            @Valid @RequestBody ContentSubmitRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        LOG.info("Content submission request: username={} eventCode={} ip={}",
                actor.username(), eventCode, getClientIp(httpRequest));

        // Code review 2026-05-18 (P1): the redundant per-endpoint username precheck has been
        // lifted into SpeakerPortalAuthorizationService.resolveSpeakerPool, which now throws
        // IllegalStateException (→ 409) on a pool row with null/blank username. Every speaker-
        // portal endpoint shares the same enforcement point instead of only this one carrying
        // the guard (D1-from-Story-11.C.2).

        try {
            ContentSubmissionPayload payload = new ContentSubmissionPayload(
                    request.title(),
                    request.contentAbstract(),
                    request.bio(),
                    request.profilePictureUrl(),
                    request.presentationUploadId());

            ContentSubmitResponse response = contentSubmissionService.submit(
                    speaker.getId(), eventCode, payload, actor);

            LOG.info("Content submitted - submissionId: {} version: {} eventCode={}",
                    response.submissionId(), response.version(), eventCode);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            LOG.warn("Content submission failed - validation error: {} eventCode={}",
                    e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        } catch (IllegalStateException e) {
            LOG.warn("Content submission failed - state error: {} eventCode={}",
                    e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        }
    }

    @PostMapping("/events/{eventCode}/materials/presigned-url")
    public ResponseEntity<SpeakerMaterialUploadResponse> generatePresignedUrl(
            @PathVariable String eventCode,
            @Valid @RequestBody SpeakerMaterialUploadRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        LOG.info("Material presigned URL request: username={} eventCode={} ip={}",
                actor.username(), eventCode, getClientIp(httpRequest));

        try {
            SpeakerMaterialUploadResponse response =
                    materialsService.generatePresignedUrl(speaker, request);
            LOG.info("Presigned URL generated - uploadId: {} eventCode={}",
                    response.uploadId(), eventCode);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOG.warn("Material upload failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        } catch (FileSizeExceededException | InvalidFileTypeException e) {
            LOG.warn("Material upload failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        }
    }

    @PostMapping("/events/{eventCode}/materials/confirm")
    public ResponseEntity<SpeakerMaterialConfirmResponse> confirmUpload(
            @PathVariable String eventCode,
            @Valid @RequestBody SpeakerMaterialConfirmRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        LOG.info("Material confirm request: username={} eventCode={} uploadId={} ip={}",
                actor.username(), eventCode, request.uploadId(), getClientIp(httpRequest));

        try {
            SpeakerMaterialConfirmResponse response =
                    materialsService.confirmUpload(speaker, request);
            LOG.info("Material confirmed - materialId: {} eventCode={}",
                    response.materialId(), eventCode);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            LOG.warn("Material confirm failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        } catch (IllegalStateException e) {
            LOG.warn("Material confirm failed - state error: {} eventCode={}",
                    e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Extract client IP address from request. Honours {@code X-Forwarded-For} for proxied calls.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
