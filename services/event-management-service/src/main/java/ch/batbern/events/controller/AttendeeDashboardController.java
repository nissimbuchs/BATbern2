package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.AttendeePortalApi;
import ch.batbern.events.core.dto.generated.AttendeeDashboardResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.AttendeeDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendee dashboard endpoint (Story 7.6).
 *
 * <p>Mirrors {@code SpeakerPortalDashboardController}, but authorized with {@code isAuthenticated()}
 * — any logged-in user can view their own attended-event history (a speaker viewing their attendee
 * history is fine; the speaker dashboard remains their primary surface). The username is taken from
 * the JWT, never a path/header param.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link AttendeePortalApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AttendeeDashboardController implements AttendeePortalApi {

    private final AttendeeDashboardService dashboardService;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendeeDashboardResponse> getAttendeeDashboard() {
        String username = securityContextHelper.getCurrentUsername();
        return ResponseEntity.ok(dashboardService.getDashboard(username));
    }
}
