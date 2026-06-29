package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/v1/sessions (cross-event company session search),
 * wired contract-first to the generated {@code CompanySessionsApi} (GlobalSessionController).
 *
 * Validates the wiring contract — path/verb binding, ORGANIZER authorization, and the
 * {@code {data, pagination}} response shape. The populated result path is exercised by the
 * live gateway smoke against real company-session data (heavy session+speaker+company seeding
 * is out of scope for the wiring assertion).
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class GlobalSessionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("should return empty data + pagination when companyName is blank")
    void should_returnEmptyPage_when_companyNameBlank() throws Exception {
        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("should return empty data when no session matches the company")
    void should_returnEmptyData_when_noSessionForCompany() throws Exception {
        mockMvc.perform(get("/api/v1/sessions").param("companyName", "NoSuchCompanyXYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.page").value(1));
    }

    @Test
    @DisplayName("should return 403 when unauthenticated")
    void should_return403_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/sessions").param("companyName", "GoogleZH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ATTENDEE"})
    @DisplayName("should return 403 when caller is not an organizer")
    void should_return403_when_attendeeRole() throws Exception {
        mockMvc.perform(get("/api/v1/sessions").param("companyName", "GoogleZH"))
                .andExpect(status().isForbidden());
    }
}
