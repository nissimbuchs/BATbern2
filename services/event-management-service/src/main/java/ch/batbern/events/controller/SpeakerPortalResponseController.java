package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.service.SpeakerPortalAuthorizationService;
import ch.batbern.events.service.SpeakerResponseService;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal response submission.
 *
 * <p>Story 6.2a: Invitation Response Portal (AC3-AC7).
 *
 * <p>Story 11.E.3 (ADR-009 §Decision 3): Cognito Bearer + {@code @PreAuthorize("hasRole('SPEAKER')")}
 * replace the previous magic-link token path. The {@code eventCode} arrives as a path parameter
 * and the actor is read from Spring's {@code SecurityContext}; the
 * {@link SpeakerPortalAuthorizationService} gates access by pool ownership.
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalResponseController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalResponseController.class);

    private final SpeakerResponseService speakerResponseService;
    private final SpeakerPortalAuthorizationService authorizationService;

    public SpeakerPortalResponseController(
            SpeakerResponseService speakerResponseService,
            SpeakerPortalAuthorizationService authorizationService) {
        this.speakerResponseService = speakerResponseService;
        this.authorizationService = authorizationService;
    }

    /**
     * Submit a Cognito-authenticated speaker's response to an invitation.
     * Story 11.E.3: {@code POST /api/v1/speaker-portal/events/{eventCode}/respond}.
     *
     * @param eventCode    the event the speaker is responding to (path)
     * @param request      response type + optional reason + preferences
     * @param httpRequest  used for IP logging on failure paths
     * @param authentication the Cognito-derived authentication injected by Spring Security
     */
    @PostMapping("/events/{eventCode}/respond")
    public ResponseEntity<SpeakerResponseResult> respond(
            @PathVariable String eventCode,
            @Valid @RequestBody SpeakerResponseRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        SecurityPrincipal actor = SecurityPrincipal.fromAuthentication(authentication);
        String clientIp = getClientIp(httpRequest);

        LOG.info("Speaker response request received: type={} eventCode={} username={} ip={}",
                request.getResponse(), eventCode, actor.username(), clientIp);

        SpeakerPool speaker = authorizationService.resolveSpeakerPool(actor.username(), eventCode);

        try {
            SpeakerResponseResult result =
                    speakerResponseService.processResponse(actor, speaker, request);

            LOG.info("Speaker response processed successfully: type={} eventCode={} username={}",
                    request.getResponse(), eventCode, actor.username());

            return ResponseEntity.ok(result);

        } catch (ValidationException e) {
            LOG.warn("Speaker response failed - validation error: {} ip={}",
                    e.getMessage(), clientIp);
            throw e;
        } catch (AlreadyRespondedException e) {
            LOG.warn("Speaker response failed - already responded: previous={} ip={}",
                    e.getPreviousResponse(), clientIp);
            throw e;
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
