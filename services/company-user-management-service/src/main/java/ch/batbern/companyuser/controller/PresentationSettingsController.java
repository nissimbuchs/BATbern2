package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.PresentationSettingsRequest;
import ch.batbern.companyuser.dto.PresentationSettingsResponse;
import ch.batbern.companyuser.service.PresentationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * GET /api/v1/public/settings/presentation  — public (no auth)
 * PUT /api/v1/settings/presentation         — ORGANIZER role required
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PresentationSettingsController {

    private final PresentationSettingsService service;

    /**
     * Returns the current presentation settings.
     * Public endpoint — no authentication required (AC #8).
     */
    @GetMapping("/api/v1/public/settings/presentation")
    public ResponseEntity<PresentationSettingsResponse> getPresentationSettings() {
        log.debug("Fetching presentation settings");
        return ResponseEntity.ok(service.getSettings());
    }

    /**
     * Updates the presentation settings.
     * Requires ORGANIZER role (AC #8).
     */
    @PutMapping("/api/v1/settings/presentation")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PresentationSettingsResponse> updatePresentationSettings(
            @Valid @RequestBody PresentationSettingsRequest request) {
        log.info("Updating presentation settings");
        return ResponseEntity.ok(service.updateSettings(request));
    }
}
