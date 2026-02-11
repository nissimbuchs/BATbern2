package ch.batbern.events.controller;

import ch.batbern.events.dto.SpeakerDashboardDto;
import ch.batbern.events.service.SpeakerDashboardService;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal dashboard.
 * Story 6.4: Speaker Dashboard (View-Only)
 *
 * Provides a read-only dashboard endpoint for speakers to view
 * their upcoming and past events, content status, and deadlines.
 *
 * This is a PUBLIC endpoint - no authentication required.
 * The magic link token IS the authentication mechanism.
 *
 * Endpoint:
 * - GET /api/v1/speaker-portal/dashboard?token=xxx - Get dashboard summary
 *
 * Security:
 * - Token validated on each request via MagicLinkService
 * - Failed attempts logged with IP for audit
 * - Token never logged (security requirement)
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalDashboardController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalDashboardController.class);

    private final SpeakerDashboardService dashboardService;

    public SpeakerPortalDashboardController(SpeakerDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get speaker dashboard summary.
     * AC1: Token validation
     * AC2: Upcoming events
     * AC3: Past events
     * AC4: Material status
     * AC5: Organizer contact
     *
     * @param token magic link token (query parameter)
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with dashboard summary if successful, error status otherwise
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SpeakerDashboardDto> getDashboard(
            @RequestParam(required = false) String token,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (token == null || token.isBlank()) {
            LOG.warn("Dashboard request failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Dashboard request received from IP: {}", clientIp);

        try {
            SpeakerDashboardDto dashboard = dashboardService.getDashboard(token);

            LOG.info("Dashboard retrieved for speaker: {} from IP: {}",
                    dashboard.speakerName(), clientIp);

            return ResponseEntity.ok(dashboard);

        } catch (IllegalArgumentException e) {
            LOG.warn("Dashboard request failed - {}: from IP: {}", e.getMessage(), clientIp);
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
