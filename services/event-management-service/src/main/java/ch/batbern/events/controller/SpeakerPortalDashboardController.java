package ch.batbern.events.controller;

import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerDashboardService;
import ch.batbern.events.speakers.api.generated.SpeakerPortalDashboardApi;
import ch.batbern.events.speakers.dto.generated.SpeakerDashboardDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *
 * <p>API-consolidation Phase 7: implements the generated {@link SpeakerPortalDashboardApi}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('SPEAKER')")
public class SpeakerPortalDashboardController implements SpeakerPortalDashboardApi {

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
    @Override
    public ResponseEntity<SpeakerDashboardDto> getDashboard() {

        String username = securityContextHelper.getCurrentUsername();
        LOG.info("Dashboard request: username={} ip={}", username, SpeakerPortalHttp.clientIp());

        SpeakerDashboardDto dashboard = dashboardService.getDashboard(username);

        LOG.info("Dashboard retrieved for speaker: {} ({} upcoming, {} past)",
                username,
                dashboard.getUpcomingEvents() == null ? 0 : dashboard.getUpcomingEvents().size(),
                dashboard.getPastEvents() == null ? 0 : dashboard.getPastEvents().size());

        return ResponseEntity.ok(dashboard);
    }
}
