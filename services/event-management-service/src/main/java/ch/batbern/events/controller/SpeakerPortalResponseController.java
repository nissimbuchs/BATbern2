package ch.batbern.events.controller;

import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.service.SpeakerResponseService;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal response submission.
 * Story 6.2a: Invitation Response Portal (AC3-AC7)
 *
 * Provides the response endpoint for the speaker portal.
 * This is a PUBLIC endpoint - no authentication required.
 * The magic link token IS the authentication mechanism.
 *
 * Security:
 * - Token validated on each request
 * - Failed attempts logged with IP for audit
 * - Token never logged
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalResponseController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalResponseController.class);

    private final SpeakerResponseService speakerResponseService;

    public SpeakerPortalResponseController(SpeakerResponseService speakerResponseService) {
        this.speakerResponseService = speakerResponseService;
    }

    /**
     * Submit a response to a speaker invitation.
     * AC3-AC7: POST /api/v1/speaker-portal/respond
     *
     * @param request the response request containing token, response type, and optional preferences
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with result if successful, error status otherwise
     */
    @PostMapping("/respond")
    public ResponseEntity<SpeakerResponseResult> respond(
            @Valid @RequestBody SpeakerResponseRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Never log the token itself
        LOG.info("Speaker response request received: type={} from IP: {}",
                request.getResponse(), clientIp);

        try {
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            LOG.info("Speaker response processed successfully: type={} from IP: {}",
                    request.getResponse(), clientIp);

            return ResponseEntity.ok(result);

        } catch (InvalidTokenException e) {
            LOG.warn("Speaker response failed - invalid token: {} from IP: {}",
                    e.getErrorCode(), clientIp);
            throw e; // Let exception handler return 401

        } catch (ValidationException e) {
            LOG.warn("Speaker response failed - validation error: {} from IP: {}",
                    e.getMessage(), clientIp);
            throw e; // Let exception handler return 400

        } catch (AlreadyRespondedException e) {
            LOG.warn("Speaker response failed - already responded: {} from IP: {}",
                    e.getPreviousResponse(), clientIp);
            throw e; // Let exception handler return 409
        }
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for requests through load balancers/proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // First IP in the chain is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
