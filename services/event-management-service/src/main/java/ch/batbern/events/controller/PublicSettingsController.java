package ch.batbern.events.controller;

import ch.batbern.events.appsettings.api.generated.AppSettingsApi;
import ch.batbern.events.appsettings.dto.generated.FeatureFlagsResponse;
import ch.batbern.events.appsettings.dto.generated.PresentationSettingsResponse;
import ch.batbern.events.config.AiConfig;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.PresentationSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public application settings, read anonymously by the frontend: runtime feature flags and the
 * moderator presentation settings.
 *
 * <p>Feature flags were relocated from {@code AiAssistController} in API consolidation Phase 8.
 * The presentation settings moved here from CUMS in Story 4.9; they are stored by
 * {@link AdminSettingsService} (written with {@code PUT /admin/settings/presentation}). Both
 * paths are public (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicSettingsController implements AppSettingsApi {

    private final AiConfig aiConfig;
    private final AdminSettingsService adminSettingsService;

    /** Public: no auth required — used by the frontend feature-flag check. */
    @Override
    public ResponseEntity<FeatureFlagsResponse> getFeatureFlags() {
        return ResponseEntity.ok(new FeatureFlagsResponse().aiContentEnabled(aiConfig.isAiEnabled()));
    }

    /** Public: no auth required — the moderator presentation page runs on a projector. */
    @Override
    public ResponseEntity<PresentationSettingsResponse> getPresentationSettings() {
        PresentationSettings settings = adminSettingsService.getPresentationSettings();
        return ResponseEntity.ok(new PresentationSettingsResponse()
                .aboutText(settings.aboutText())
                .partnerCount(settings.partnerCount()));
    }
}
