package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.UserPreferencesApi;
import ch.batbern.companyuser.dto.generated.UserPreferences;
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
 * REST Controller for User Preferences Management
 * Story 2.6: User Account Management Frontend
 *
 * Implements OpenAPI-generated UserPreferencesApi interface
 * Provides endpoints for managing user preferences (theme, notifications, timezone)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesController implements UserPreferencesApi {

    private final UserService userService;

    /**
     * GET /api/v1/users/me/preferences
     * Retrieve preferences for the currently authenticated user
     *
     * Story 2.6 AC21, AC24: Theme and timezone preferences
     * Story 2.6 AC28: Notification preferences
     *
     * @return User preferences (theme, language, notifications, quiet hours)
     */
    @Override
    @Timed(value = "users.getUserPreferences",
            description = "Time to get current user preferences",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserPreferences> getUserPreferences() {
        log.debug("Getting preferences for current authenticated user");

        UserPreferences preferences = userService.getCurrentUserPreferences();

        return ResponseEntity.ok(preferences);
    }

    /**
     * PUT /api/v1/users/me/preferences
     * Update preferences for the currently authenticated user
     *
     * Story 2.6 AC21: Persist theme changes
     * Story 2.6 AC24: Persist timezone changes
     * Story 2.6 AC28: Persist notification preferences
     *
     * @param userPreferences Updated preferences
     * @return Updated user preferences
     */
    @Override
    @Timed(value = "users.updateUserPreferences",
            description = "Time to update current user preferences",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserPreferences> updateUserPreferences(
            @Valid @RequestBody UserPreferences userPreferences) {
        log.info("Updating preferences for current authenticated user");

        UserPreferences updated = userService.updateCurrentUserPreferences(userPreferences);

        return ResponseEntity.ok(updated);
    }
}
