package ch.batbern.events.controller;

import ch.batbern.events.dto.SpeakerDashboardDto;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal dashboard.
 *
 * <p>Story 6.4: Speaker Dashboard (View-Only).
 *
 * <p>Story 11.E.3 (ADR-009 §Decision 3): Cognito Bearer + {@code @PreAuthorize("hasRole('SPEAKER')")}
 * replace the magic-link path. The dashboard aggregates across all events the authenticated
 * speaker has pool rows in — it's the one speaker-portal endpoint that doesn't take an
 * {@code eventCode}.
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalDashboardController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalDashboardController.class);

    private final SpeakerDashboardService dashboardService;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerPortalDashboardController(
            SpeakerDashboardService dashboardService,
            SecurityContextHelper securityContextHelper) {
        this.dashboardService = dashboardService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Get speaker dashboard summary across every event the authenticated speaker is in.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SpeakerDashboardDto> getDashboard(HttpServletRequest httpRequest) {

        String username = securityContextHelper.getCurrentUsername();
        LOG.info("Dashboard request: username={} ip={}", username, getClientIp(httpRequest));

        SpeakerDashboardDto dashboard = dashboardService.getDashboard(username);

        LOG.info("Dashboard retrieved for speaker: {} ({} upcoming, {} past)",
                username,
                dashboard.upcomingEvents() == null ? 0 : dashboard.upcomingEvents().size(),
                dashboard.pastEvents() == null ? 0 : dashboard.pastEvents().size());

        return ResponseEntity.ok(dashboard);
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
