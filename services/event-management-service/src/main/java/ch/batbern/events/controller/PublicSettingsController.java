package ch.batbern.events.controller;

import ch.batbern.events.appsettings.api.generated.AppSettingsApi;
import ch.batbern.events.appsettings.dto.generated.FeatureFlagsResponse;
import ch.batbern.events.config.AiConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public application settings — runtime feature flags consumed by the frontend.
 *
 * <p>Relocated from {@code AiAssistController} in API consolidation Phase 8: feature flags are an
 * app-settings concern, not an AI one. The path ({@code /public/settings/features}) is preserved
 * verbatim and stays public (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicSettingsController implements AppSettingsApi {

    private final AiConfig aiConfig;

    /** Public: no auth required — used by the frontend feature-flag check. */
    @Override
    public ResponseEntity<FeatureFlagsResponse> getFeatureFlags() {
        return ResponseEntity.ok(new FeatureFlagsResponse().aiContentEnabled(aiConfig.isAiEnabled()));
    }
}
