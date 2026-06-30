package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.ContentSubmissionService;
import ch.batbern.events.service.SpeakerPortalAuthorizationService;
import ch.batbern.events.service.SpeakerPortalMaterialsService;
import ch.batbern.events.service.content.ContentSubmissionPayload;
import ch.batbern.events.speakers.api.generated.SpeakerPortalContentApi;
import ch.batbern.events.speakers.dto.generated.ContentSubmitRequest;
import ch.batbern.events.speakers.dto.generated.ContentSubmitResponse;
import ch.batbern.events.speakers.dto.generated.SpeakerContentInfo;
import ch.batbern.events.speakers.dto.generated.SpeakerMaterialConfirmRequest;
import ch.batbern.events.speakers.dto.generated.SpeakerMaterialConfirmResponse;
import ch.batbern.events.speakers.dto.generated.SpeakerMaterialUploadRequest;
import ch.batbern.events.speakers.dto.generated.SpeakerMaterialUploadResponse;
import ch.batbern.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <p>API-consolidation Phase 7: implements the generated {@link SpeakerPortalContentApi}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalContentController implements SpeakerPortalContentApi {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalContentController.class);

    private final ContentSubmissionService contentSubmissionService;
    private final SpeakerPortalMaterialsService materialsService;
    private final SpeakerPortalAuthorizationService authorizationService;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerPortalContentController(
            ContentSubmissionService contentSubmissionService,
            SpeakerPortalMaterialsService materialsService,
            SpeakerPortalAuthorizationService authorizationService,
            SecurityContextHelper securityContextHelper) {
        this.contentSubmissionService = contentSubmissionService;
        this.materialsService = materialsService;
        this.authorizationService = authorizationService;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<SpeakerContentInfo> getContentInfo(String eventCode) {
        String username = securityContextHelper.getCurrentUsername();
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(username, eventCode);

        LOG.info("Content info request: username={} eventCode={} ip={}",
                username, eventCode, SpeakerPortalHttp.clientIp());

        SpeakerContentInfo contentInfo = contentSubmissionService.getContentInfo(speaker);
        return ResponseEntity.ok(contentInfo);
    }

    // Story 11.E.8 follow-up — speaker-self submit must evict the eventWithIncludes
    // Caffeine cache (15-min TTL) so the organizer's GET /events/{code}?include=sessions
    // serves the freshly-updated session.title and session.description on the next read.
    // The organizer-on-behalf submitContent at SpeakerStatusController.java:265 has the
    // same annotation; without this matching one, speaker self-submissions left the
    // cache stale and organizers kept seeing the prior reviewed title until cache TTL.
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<ContentSubmitResponse> submitContent(
            String eventCode,
            ContentSubmitRequest request) {

        String username = securityContextHelper.getCurrentUsername();
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(username, eventCode);

        LOG.info("Content submission request: username={} eventCode={} ip={}",
                username, eventCode, SpeakerPortalHttp.clientIp());

        // Code review 2026-05-18 (P1): the redundant per-endpoint username precheck has been
        // lifted into SpeakerPortalAuthorizationService.resolveSpeakerPool, which now throws
        // IllegalStateException (→ 409) on a pool row with null/blank username. Every speaker-
        // portal endpoint shares the same enforcement point instead of only this one carrying
        // the guard (D1-from-Story-11.C.2).

        try {
            ContentSubmissionPayload payload = new ContentSubmissionPayload(
                    request.getTitle(),
                    request.getContentAbstract(),
                    request.getBio(),
                    request.getProfilePictureUrl(),
                    request.getPresentationUploadId());

            ContentSubmitResponse response = contentSubmissionService.submit(
                    speaker.getId(), eventCode, payload, username);

            LOG.info("Content submitted - submissionId: {} version: {} eventCode={}",
                    response.getSubmissionId(), response.getVersion(), eventCode);

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

    @Override
    public ResponseEntity<SpeakerMaterialUploadResponse> generatePresignedUrl(
            String eventCode,
            SpeakerMaterialUploadRequest request) {

        String username = securityContextHelper.getCurrentUsername();
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(username, eventCode);

        LOG.info("Material presigned URL request: username={} eventCode={} ip={}",
                username, eventCode, SpeakerPortalHttp.clientIp());

        try {
            SpeakerMaterialUploadResponse response =
                    materialsService.generatePresignedUrl(speaker, request);
            LOG.info("Presigned URL generated - uploadId: {} eventCode={}",
                    response.getUploadId(), eventCode);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOG.warn("Material upload failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        } catch (FileSizeExceededException | InvalidFileTypeException e) {
            LOG.warn("Material upload failed - {}: eventCode={}", e.getMessage(), eventCode);
            throw new ValidationException(e.getMessage());
        }
    }

    // Story 11.E.8 follow-up — materials/confirm also mutates session-level state
    // (session_materials → materialsCount, materialsStatus, hasPresentation in the
    // event response). Evict the same Caffeine cache so the organizer's Sessions tab
    // reflects new uploads without waiting 15 min for the TTL to expire.
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SpeakerMaterialConfirmResponse> confirmUpload(
            String eventCode,
            SpeakerMaterialConfirmRequest request) {

        String username = securityContextHelper.getCurrentUsername();
        SpeakerPool speaker = authorizationService.resolveSpeakerPool(username, eventCode);

        LOG.info("Material confirm request: username={} eventCode={} uploadId={} ip={}",
                username, eventCode, request.getUploadId(), SpeakerPortalHttp.clientIp());

        try {
            SpeakerMaterialConfirmResponse response =
                    materialsService.confirmUpload(speaker, request);
            LOG.info("Material confirmed - materialId: {} eventCode={}",
                    response.getMaterialId(), eventCode);
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
}
