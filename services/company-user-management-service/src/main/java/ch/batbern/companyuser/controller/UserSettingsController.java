package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.UserSettingsApi;
import ch.batbern.companyuser.dto.generated.UserSettings;
import ch.batbern.companyuser.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for User Settings Management
 * Story 2.6: User Account Management Frontend
 *
 * Implements OpenAPI-generated UserSettingsApi interface
 * Provides endpoints for managing user settings (privacy, security, account preferences)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserSettingsController implements UserSettingsApi {

    private final UserService userService;

    /**
     * GET /api/v1/users/me/settings
     * Retrieve account settings for the currently authenticated user
     *
     * Story 2.6 AC34: Privacy settings
     *
     * @return User settings (privacy controls, account preferences, security settings)
     */
    @Override
    @Timed(value = "users.getUserSettings",
            description = "Time to get current user settings",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserSettings> getUserSettings() {
        log.debug("Getting settings for current authenticated user");

        UserSettings settings = userService.getCurrentUserSettings();

        return ResponseEntity.ok(settings);
    }

    /**
     * PUT /api/v1/users/me/settings
     * Update account settings for the currently authenticated user
     *
     * Story 2.6 AC34: Persist privacy settings
     *
     * @param userSettings Updated settings
     * @return Updated user settings
     */
    @Override
    @Timed(value = "users.updateUserSettings",
            description = "Time to update current user settings",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserSettings> updateUserSettings(
            @Valid @RequestBody UserSettings userSettings) {
        log.info("Updating settings for current authenticated user");

        UserSettings updated = userService.updateCurrentUserSettings(userSettings);

        return ResponseEntity.ok(updated);
    }
}
