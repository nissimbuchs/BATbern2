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
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.ContentSubmissionService;
import ch.batbern.events.service.MagicLinkService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for speaker portal content submission.
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Provides content submission endpoints for the speaker portal.
 * This is a PUBLIC endpoint - no authentication required.
 * The magic link token IS the authentication mechanism.
 *
 * Endpoints:
 * - GET /api/v1/speaker-portal/content?token=xxx - Get current content info
 * - POST /api/v1/speaker-portal/content/draft - Save draft (auto-save)
 * - POST /api/v1/speaker-portal/content/submit - Submit content for review
 * - POST /api/v1/speaker-portal/materials/presigned-url - Get presigned URL for file upload
 * - POST /api/v1/speaker-portal/materials/confirm - Confirm file upload
 *
 * Security:
 * - Token validated on each request
 * - Failed attempts logged with IP for audit
 * - Token never logged (security requirement)
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalContentController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalContentController.class);

    private final ContentSubmissionService contentSubmissionService;
    private final SpeakerPortalMaterialsService materialsService;
    private final MagicLinkService magicLinkService;
    private final SpeakerPoolRepository speakerPoolRepository;

    public SpeakerPortalContentController(
            ContentSubmissionService contentSubmissionService,
            SpeakerPortalMaterialsService materialsService,
            MagicLinkService magicLinkService,
            SpeakerPoolRepository speakerPoolRepository) {
        this.contentSubmissionService = contentSubmissionService;
        this.materialsService = materialsService;
        this.magicLinkService = magicLinkService;
        this.speakerPoolRepository = speakerPoolRepository;
    }

    /**
     * Get speaker content information.
     * AC1: Session assignment check
     * AC4: Draft restoration on page reload
     * AC8: Revision feedback display
     *
     * @param token magic link token (query parameter)
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with content info if successful, error status otherwise
     */
    @GetMapping("/content")
    public ResponseEntity<SpeakerContentInfo> getContentInfo(
            @RequestParam(required = false) String token,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (token == null || token.isBlank()) {
            LOG.warn("Content info request failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Content info request received from IP: {}", clientIp);

        try {
            SpeakerContentInfo contentInfo = contentSubmissionService.getContentInfo(token);

            LOG.info("Content info retrieved for speaker: {} from IP: {}",
                    contentInfo.speakerName(), clientIp);

            return ResponseEntity.ok(contentInfo);

        } catch (IllegalArgumentException e) {
            LOG.warn("Content info failed - {}: from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Save content draft (auto-save or manual save).
     * AC4: Draft auto-save every 30 seconds
     *
     * @param request the draft request with token and content
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with saved draft response if successful
     */
    @PostMapping("/content/draft")
    public ResponseEntity<ContentDraftResponse> saveDraft(
            @Valid @RequestBody ContentDraftRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (request.token() == null || request.token().isBlank()) {
            LOG.warn("Draft save failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.debug("Draft save request received from IP: {}", clientIp);

        try {
            ContentDraftResponse response = contentSubmissionService.saveDraft(request);

            LOG.info("Draft saved - draftId: {} from IP: {}", response.draftId(), clientIp);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            LOG.warn("Draft save failed - {}: from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Submit content for organizer review.
     * AC5: Content submission with validation
     * AC6: Triggers SpeakerContentSubmittedEvent
     *
     * @param request the submit request with token, title, and abstract
     * @param httpRequest the HTTP request (for IP logging)
     * @return 201 Created with submission response if successful
     */
    @PostMapping("/content/submit")
    public ResponseEntity<ContentSubmitResponse> submitContent(
            @Valid @RequestBody ContentSubmitRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (request.token() == null || request.token().isBlank()) {
            LOG.warn("Content submission failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Content submission request received from IP: {}", clientIp);

        try {
            // Story 11.C.2 — the controller now owns token validation + principal construction
            // so the consolidated ContentSubmissionService.submit() can be invoked with a
            // principal-agnostic payload (matches the organizer endpoint shape).
            //
            // NB: NO @PreAuthorize annotation here in 11.C.2 — Phase E (Story 11.E.3) replaces
            // the magic-link token check with Cognito Bearer + hasRole('SPEAKER').
            TokenValidationResult validation = validateToken(request.token());

            SpeakerPool speaker = speakerPoolRepository.findById(validation.speakerPoolId())
                    .orElseThrow(() -> new ValidationException("Speaker not found"));

            // Build a SPEAKER principal. Username is taken from the pool entry (populated at
            // CONTACTED → READY per Story 11.B.2). For pre-11.B.2 legacy magic-link sessions
            // where speaker.username may be null, fall back to the speaker_name from the
            // pool — matches the pattern Story 11.B.2 established in SpeakerResponseService.
            String actorUsername = speaker.getUsername() != null && !speaker.getUsername().isBlank()
                    ? speaker.getUsername()
                    : speaker.getSpeakerName();
            SecurityPrincipal actor = new SecurityPrincipal(actorUsername, List.of("SPEAKER"));

            ContentSubmissionPayload payload = new ContentSubmissionPayload(
                    request.title(),
                    request.contentAbstract(),
                    request.bio(),
                    request.profilePictureUrl(),
                    request.presentationUploadId()
            );

            ContentSubmitResponse response = contentSubmissionService.submit(
                    speaker.getId(),
                    validation.eventCode(),
                    payload,
                    actor
            );

            // Mark token used after a successful submit (existing magic-link path).
            magicLinkService.markTokenAsUsed(request.token());

            LOG.info("Content submitted - submissionId: {}, version: {} from IP: {}",
                    response.submissionId(), response.version(), clientIp);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            LOG.warn("Content submission failed - validation error: {} from IP: {}",
                    e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());

        } catch (IllegalStateException e) {
            LOG.warn("Content submission failed - state error: {} from IP: {}",
                    e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Validate a magic-link token and surface a friendly error message per the existing
     * Story 6.3 mapping. Story 11.C.2 lifted this from
     * {@code ContentSubmissionService.validateToken(...)} so the controller can build the
     * principal before delegating to the consolidated {@code submit(...)} method.
     */
    private TokenValidationResult validateToken(String token) {
        TokenValidationResult result = magicLinkService.validateToken(token);
        if (!result.valid()) {
            String message = switch (result.error()) {
                case "NOT_FOUND" -> "Invalid token";
                case "EXPIRED" -> "Token has expired";
                case "ALREADY_USED" -> "Token has already been used";
                default -> "Token validation failed";
            };
            throw new ValidationException(message);
        }
        return result;
    }

    /**
     * Generate presigned URL for material upload.
     * AC7: Presentation file uploads (PPTX, PPT, KEY, PDF, max 50MB)
     *
     * @param request the upload request with token and file info
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with presigned URL if successful
     */
    @PostMapping("/materials/presigned-url")
    public ResponseEntity<SpeakerMaterialUploadResponse> generatePresignedUrl(
            @Valid @RequestBody SpeakerMaterialUploadRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (request.token() == null || request.token().isBlank()) {
            LOG.warn("Material upload request failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Material presigned URL request received from IP: {}", clientIp);

        try {
            SpeakerMaterialUploadResponse response = materialsService.generatePresignedUrl(request);

            LOG.info("Presigned URL generated - uploadId: {} from IP: {}",
                    response.uploadId(), clientIp);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            LOG.warn("Material upload failed - {}: from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());

        } catch (FileSizeExceededException | InvalidFileTypeException e) {
            LOG.warn("Material upload failed - {}: from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Confirm material upload and associate with session.
     * AC7: Links uploaded file to speaker's session
     *
     * @param request the confirm request with token and upload details
     * @param httpRequest the HTTP request (for IP logging)
     * @return 201 Created with material info if successful
     */
    @PostMapping("/materials/confirm")
    public ResponseEntity<SpeakerMaterialConfirmResponse> confirmUpload(
            @Valid @RequestBody SpeakerMaterialConfirmRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (request.token() == null || request.token().isBlank()) {
            LOG.warn("Material confirm failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Material confirm request received - uploadId: {} from IP: {}",
                request.uploadId(), clientIp);

        try {
            SpeakerMaterialConfirmResponse response = materialsService.confirmUpload(request);

            LOG.info("Material confirmed - materialId: {} from IP: {}",
                    response.materialId(), clientIp);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            LOG.warn("Material confirm failed - {}: from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());

        } catch (IllegalStateException e) {
            LOG.warn("Material confirm failed - state error: {} from IP: {}", e.getMessage(), clientIp);
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for requests through load balancers/proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
