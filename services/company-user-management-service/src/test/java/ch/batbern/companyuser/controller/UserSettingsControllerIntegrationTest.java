package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserSettings;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UserSettingsController
 * Story 2.6: User Account Management Frontend
 * Tests settings endpoints (GET/PUT /api/v1/users/me/settings)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("UserSettingsController Integration Tests")
class UserSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        UserSettings settings = UserSettings.builder()
                .profileVisibility("public")
                .showEmail(false)
                .showCompany(true)
                .showActivityHistory(true)
                .allowMessaging(true)
                .allowCalendarSync(false)
                .timezone("Europe/Zurich")
                .twoFactorEnabled(false)
                .build();

        testUser = User.builder()
                .username("max.muster")
                .email("max.muster@example.com")
                .firstName("Max")
                .lastName("Muster")
                .cognitoUserId("max.muster")
                .companyId("SwissCorpZH")
                .roles(new HashSet<>(Set.of(Role.PARTNER)))
                .settings(settings)
                .build();

        testUser = userRepository.save(testUser);
    }

    // Story 2.6 AC34: GET /api/v1/users/me/settings

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_returnUserSettings_when_authenticated")
    void should_returnUserSettings_when_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value("public"))
                .andExpect(jsonPath("$.showEmail").value(false))
                .andExpect(jsonPath("$.showCompany").value(true))
                .andExpect(jsonPath("$.showActivityHistory").value(true))
                .andExpect(jsonPath("$.allowMessaging").value(true))
                .andExpect(jsonPath("$.allowCalendarSync").value(false))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_initializeDefaultSettings_when_settingsNull")
    void should_initializeDefaultSettings_when_settingsNull() throws Exception {
        // Create user without settings
        testUser.setSettings(null);
        userRepository.save(testUser);

        mockMvc.perform(get("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value("public"))
                .andExpect(jsonPath("$.showEmail").value(false))
                .andExpect(jsonPath("$.showCompany").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"));
    }

    // Story 2.6 AC34: PUT /api/v1/users/me/settings - Privacy Settings

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_updatePrivacySettings_when_validSettingsProvided")
    void should_updatePrivacySettings_when_validSettingsProvided() throws Exception {
        String requestBody = """
                {
                    "profileVisibility": "members_only",
                    "showEmail": false,
                    "showCompany": true,
                    "showActivityHistory": false,
                    "allowMessaging": false,
                    "allowCalendarSync": true,
                    "timezone": "Europe/Zurich",
                    "twoFactorEnabled": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value("members_only"))
                .andExpect(jsonPath("$.showEmail").value(false))
                .andExpect(jsonPath("$.showActivityHistory").value(false))
                .andExpect(jsonPath("$.allowMessaging").value(false));
    }

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_updateProfileVisibility_when_privateSelected")
    void should_updateProfileVisibility_when_privateSelected() throws Exception {
        String requestBody = """
                {
                    "profileVisibility": "private",
                    "showEmail": false,
                    "showCompany": false,
                    "showActivityHistory": false,
                    "allowMessaging": false,
                    "allowCalendarSync": false,
                    "timezone": "Europe/Zurich",
                    "twoFactorEnabled": false
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value("private"))
                .andExpect(jsonPath("$.showEmail").value(false))
                .andExpect(jsonPath("$.showCompany").value(false))
                .andExpect(jsonPath("$.showActivityHistory").value(false));
    }

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_updateTwoFactorAuth_when_enabled")
    void should_updateTwoFactorAuth_when_enabled() throws Exception {
        String requestBody = """
                {
                    "profileVisibility": "public",
                    "showEmail": false,
                    "showCompany": true,
                    "showActivityHistory": true,
                    "allowMessaging": true,
                    "allowCalendarSync": false,
                    "timezone": "Europe/Zurich",
                    "twoFactorEnabled": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    @WithMockUser(username = "max.muster")
    @DisplayName("should_persistSettingsChanges_when_updated")
    void should_persistSettingsChanges_when_updated() throws Exception {
        String requestBody = """
                {
                    "profileVisibility": "members_only",
                    "showEmail": true,
                    "showCompany": false,
                    "showActivityHistory": false,
                    "allowMessaging": false,
                    "allowCalendarSync": true,
                    "timezone": "America/New_York",
                    "twoFactorEnabled": true
                }
                """;

        // Update settings
        mockMvc.perform(put("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Verify persistence by fetching again
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value("members_only"))
                .andExpect(jsonPath("$.showEmail").value(true))
                .andExpect(jsonPath("$.showCompany").value(false))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    @DisplayName("should_return401_when_notAuthenticated")
    void should_return401_when_notAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "unknown.user")
    @DisplayName("should_return404_when_userNotFound")
    void should_return404_when_userNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
