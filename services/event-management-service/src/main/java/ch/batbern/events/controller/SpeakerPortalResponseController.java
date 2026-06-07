package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerPortalAuthorizationService;
import ch.batbern.events.service.SpeakerResponseService;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * and the actor's username is read from {@link SecurityContextHelper}; the
 * {@link SpeakerPortalAuthorizationService} gates access by pool ownership.
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalResponseController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalResponseController.class);

    private final SpeakerResponseService speakerResponseService;
    private final SpeakerPortalAuthorizationService authorizationService;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerPortalResponseController(
            SpeakerResponseService speakerResponseService,
            SpeakerPortalAuthorizationService authorizationService,
            SecurityContextHelper securityContextHelper) {
        this.speakerResponseService = speakerResponseService;
        this.authorizationService = authorizationService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Submit a Cognito-authenticated speaker's response to an invitation.
     * Story 11.E.3: {@code POST /api/v1/speaker-portal/events/{eventCode}/respond}.
     *
     * @param eventCode    the event the speaker is responding to (path)
     * @param request      response type + optional reason + preferences
     * @param httpRequest  used for IP logging on failure paths
     */
    // Story 11.E.8 follow-up — accept/decline flips session_users.is_confirmed +
    // speaker_pool.status (and clears session on post-INVITED DECLINE). All of these are
    // surfaced in the GET event-with-includes payload (speakers[].isConfirmed, status,
    // sessions[]). Evict the cache so organizers see the response immediately rather than
    // waiting up to 15 min for the Caffeine TTL.
    @PostMapping("/events/{eventCode}/respond")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SpeakerResponseResult> respond(
            @PathVariable String eventCode,
            @Valid @RequestBody SpeakerResponseRequest request,
            HttpServletRequest httpRequest) {

        String username = securityContextHelper.getCurrentUsername();
        String clientIp = getClientIp(httpRequest);

        LOG.info("Speaker response request received: type={} eventCode={} username={} ip={}",
                request.getResponse(), eventCode, username, clientIp);

        SpeakerPool speaker = authorizationService.resolveSpeakerPool(username, eventCode);

        try {
            SpeakerResponseResult result =
                    speakerResponseService.processResponse(username, speaker, request);

            LOG.info("Speaker response processed successfully: type={} eventCode={} username={}",
                    request.getResponse(), eventCode, username);

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
