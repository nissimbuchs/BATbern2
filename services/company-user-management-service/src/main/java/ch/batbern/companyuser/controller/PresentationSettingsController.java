package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.PresentationSettingsApi;
import ch.batbern.companyuser.dto.generated.PresentationSettingsRequest;
import ch.batbern.companyuser.dto.generated.PresentationSettingsResponse;
import ch.batbern.companyuser.service.PresentationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional.
 * Implements the OpenAPI-generated {@link PresentationSettingsApi} interface (ADR-006
 * contract-first); the interface carries the verb/path/validation mappings.
 *
 * GET /api/v1/public/settings/presentation  — public (no auth)
 * PUT /api/v1/settings/presentation         — ORGANIZER role required
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PresentationSettingsController implements PresentationSettingsApi {

    private final PresentationSettingsService service;

    /**
     * Returns the current presentation settings.
     * Public endpoint — no authentication required (AC #8).
     */
    @Override
    public ResponseEntity<PresentationSettingsResponse> getPresentationSettings() {
        log.debug("Fetching presentation settings");
        return ResponseEntity.ok(service.getSettings());
    }

    /**
     * Updates the presentation settings.
     * Requires ORGANIZER role (AC #8).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PresentationSettingsResponse> updatePresentationSettings(
            PresentationSettingsRequest presentationSettingsRequest) {
        log.info("Updating presentation settings");
        return ResponseEntity.ok(service.updateSettings(presentationSettingsRequest));
    }
}
