package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.service.SpeakerInvitationEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Epic9MigrationController (Story 9.4, AC8, AC10).
 *
 * Tests POST /api/v1/admin/migrations/epic9:
 * - Returns 200 with MigrationReport for ORGANIZER role
 * - Returns 403 when called without authentication
 * - dryRun parameter is forwarded correctly
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Import(TestUserApiClientConfig.class)
@DisplayName("Epic9MigrationController - admin endpoint")
class Epic9MigrationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String MIGRATION_URL = "/api/v1/admin/migrations/epic9";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeakerInvitationEmailService speakerInvitationEmailService;

    @BeforeEach
    void setUp() {
        doNothing().when(speakerInvitationEmailService)
                .sendInvitationEmailSync(any(), any(), any(), any(), any());
    }

    // ─── AC8: ORGANIZER-authenticated endpoint ────────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should return 200 with empty MigrationReport when ORGANIZER calls with no speakers")
    void should_return200_withEmptyReport_when_organizerCallsWithNoAcceptedSpeakers() throws Exception {
        mockMvc.perform(post(MIGRATION_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.provisionedNew").value(0))
                .andExpect(jsonPath("$.extended").value(0))
                .andExpect(jsonPath("$.emailsSent").value(0))
                .andExpect(jsonPath("$.errors").value(0))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should return 200 in dry-run mode when dryRun=true")
    void should_return200_when_dryRunTrue() throws Exception {
        mockMvc.perform(post(MIGRATION_URL).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.results").isArray());
    }

    // ─── AC8: Auth required — no token returns 403 ───────────────────────────

    @Test
    @DisplayName("should return 403 when called without authentication (TestSecurityConfig: permitAll at HTTP, @PreAuthorize at method)")
    void should_return403_when_calledWithoutAuthentication() throws Exception {
        // TestSecurityConfig uses permitAll() at HTTP level; unauthenticated requests
        // reach @PreAuthorize("hasRole('ORGANIZER')") and are denied with 403
        mockMvc.perform(post(MIGRATION_URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("should return 403 when called with SPEAKER role (not ORGANIZER)")
    void should_return403_when_calledWithSpeakerRole() throws Exception {
        mockMvc.perform(post(MIGRATION_URL))
                .andExpect(status().isForbidden());
    }
}
