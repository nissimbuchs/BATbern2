package ch.batbern.events.controller;

import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.ValidateTokenRequest;
import ch.batbern.events.service.MagicLinkService;
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
 * REST Controller for speaker portal token validation.
 * Story 6.1a: Magic Link Infrastructure (AC5)
 *
 * Provides the token validation endpoint for the speaker portal.
 * This is a PUBLIC endpoint - no authentication required.
 * The magic link token IS the authentication mechanism.
 *
 * Security:
 * - Rate limited: 5 requests/minute per IP (AC5)
 * - Failed attempts logged with IP for audit (AC6)
 * - Token never logged (AC6)
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalTokenController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalTokenController.class);

    private final MagicLinkService magicLinkService;

    public SpeakerPortalTokenController(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    /**
     * Validate a magic link token.
     * AC5: POST /api/v1/speaker-portal/validate-token
     *
     * This endpoint validates the token without consuming it.
     * For RESPOND tokens that should be consumed (accept/decline),
     * use the specific accept/decline endpoints in Story 6.2.
     *
     * @param request the token validation request
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with speaker context if valid, 401 with error code if invalid
     */
    @PostMapping("/validate-token")
    public ResponseEntity<TokenValidationResult> validateToken(
            @Valid @RequestBody ValidateTokenRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // AC6: Never log the token itself
        LOG.info("Token validation request from IP: {}", clientIp);

        TokenValidationResult result = magicLinkService.validateToken(request.token());

        if (result.valid()) {
            LOG.info("Token validation successful for speaker pool: {} from IP: {}",
                    result.speakerPoolId(), clientIp);
            return ResponseEntity.ok(result);
        } else {
            // AC6: Log failed attempts with IP for audit
            LOG.warn("Token validation failed with error: {} from IP: {}",
                    result.error(), clientIp);
            return ResponseEntity.status(401).body(result);
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
