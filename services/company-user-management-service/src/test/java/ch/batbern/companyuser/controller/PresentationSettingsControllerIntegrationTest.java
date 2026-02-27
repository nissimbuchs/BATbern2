package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TDD integration tests for PresentationSettingsController.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Tests (written FIRST per TDD / story requirement):
 * - GET /api/v1/public/settings/presentation returns 200 + default values when no row exists
 * - PUT + GET round-trip persists updated values
 * - GET is public (no auth required)
 * - PUT requires ORGANIZER role (rejects unauthenticated and non-organizer callers)
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("PresentationSettings Controller Integration Tests")
class PresentationSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String GET_URL = "/api/v1/public/settings/presentation";
    private static final String PUT_URL = "/api/v1/settings/presentation";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Each test runs in a transaction that is rolled back — no cleanup needed.
        // The service will either return defaults (no row) or the row inserted by the test.
    }

    // ─── AC: GET returns 200 + default values when no row exists ───────────────

    @Test
    @DisplayName("GET /api/v1/public/settings/presentation - returns 200 with default values when no row exists")
    void should_returnDefaults_when_noSettingsRowExists() throws Exception {
        mockMvc.perform(get(GET_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").isString())
                .andExpect(jsonPath("$.partnerCount").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/public/settings/presentation - returns non-empty aboutText by default")
    void should_returnNonEmptyAboutText_byDefault() throws Exception {
        mockMvc.perform(get(GET_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").isNotEmpty());
    }

    // ─── AC: PUT saves new values; GET returns updated values ─────────────────

    @Test
    @DisplayName("PUT /api/v1/settings/presentation - saves values; GET returns updated values")
    @WithMockUser(roles = "ORGANIZER")
    void should_saveAndReturnUpdatedSettings_when_putCalledByOrganizer() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "aboutText", "BATbern verbindet Architekten und Ingenieure in Bern.",
                "partnerCount", 12
        ));

        mockMvc.perform(put(PUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").value("BATbern verbindet Architekten und Ingenieure in Bern."))
                .andExpect(jsonPath("$.partnerCount").value(12));

        mockMvc.perform(get(GET_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").value("BATbern verbindet Architekten und Ingenieure in Bern."))
                .andExpect(jsonPath("$.partnerCount").value(12));
    }

    // ─── AC: GET is public (no auth required) ─────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/public/settings/presentation - succeeds without authentication")
    void should_allowUnauthenticated_get() throws Exception {
        // No @WithMockUser — unauthenticated request
        mockMvc.perform(get(GET_URL))
                .andExpect(status().isOk());
    }

    // ─── AC: PUT requires ORGANIZER role ──────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/settings/presentation - returns 401 when unauthenticated")
    void should_reject401_when_putWithoutAuth() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "aboutText", "Test",
                "partnerCount", 5
        ));

        mockMvc.perform(put(PUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/settings/presentation - returns 403 when non-organizer")
    @WithMockUser(roles = "PARTNER")
    void should_reject403_when_putByNonOrganizer() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "aboutText", "Test",
                "partnerCount", 5
        ));

        mockMvc.perform(put(PUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/settings/presentation - returns 400 for blank aboutText")
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_aboutTextIsBlank() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "aboutText", "",
                "partnerCount", 5
        ));

        mockMvc.perform(put(PUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/settings/presentation - returns 400 for negative partnerCount")
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_partnerCountIsNegative() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "aboutText", "Valid text",
                "partnerCount", -1
        ));

        mockMvc.perform(put(PUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
