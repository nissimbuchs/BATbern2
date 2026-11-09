package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.UserSettingsApi;
import ch.batbern.companyuser.dto.generated.UpdateUserSettingsRequest;
import ch.batbern.companyuser.dto.generated.UserSettings;
import ch.batbern.companyuser.service.UserSettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's settings, one resource (Story 4.9): display, notifications and privacy.
 *
 * <p>{@code GET /api/v1/users/me/settings} and {@code PATCH /api/v1/users/me/settings} (partial,
 * ADR-013). The former {@code /users/me/preferences} and the full-replace PUT are gone.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserSettingsController implements UserSettingsApi {

    private final UserSettingsService userSettingsService;

    @Override
    @Timed(value = "users.getUserSettings",
            description = "Time to get current user settings",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSettings> getUserSettings() {
        return ResponseEntity.ok(userSettingsService.getCurrentUserSettings());
    }

    @Override
    @Timed(value = "users.updateUserSettings",
            description = "Time to update current user settings",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSettings> updateUserSettings(UpdateUserSettingsRequest request) {
        return ResponseEntity.ok(userSettingsService.updateCurrentUserSettings(request));
    }
}
