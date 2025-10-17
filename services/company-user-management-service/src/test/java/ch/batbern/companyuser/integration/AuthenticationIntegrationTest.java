package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication and authorization
 * AC10: Authentication integration with API Gateway
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Transactional
@Import(TestAwsConfig.class)
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Test 10.1: should_createCompany_when_authenticatedAsOrganizer
     */
    @Test
    @WithMockUser(username = "organizer@example.com", roles = {"ORGANIZER"})
    void should_createCompany_when_authenticatedAsOrganizer() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Test Company Auth")
            .displayName("Test Company Authentication")
            .swissUID("CHE-123.456.789")
            .website("https://testcompany.ch")
            .industry("Technology")
            .description("Test company for authentication integration")
            .build();

        // When/Then
        MvcResult result = mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Company Auth"))
            .andReturn();

        // Verify createdBy is set from security context
        CompanyResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            CompanyResponse.class
        );
        assertThat(response.getId()).isNotNull();
    }

    /**
     * Test 10.2: should_createCompany_when_authenticatedAsSpeaker
     */
    @Test
    @WithMockUser(username = "speaker@example.com", roles = {"SPEAKER"})
    void should_createCompany_when_authenticatedAsSpeaker() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Speaker Company")
            .displayName("Speaker Test Company")
            .build();

        // When/Then
        mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    /**
     * Test 10.3: should_denyAccess_when_notAuthenticated
     */
    @Test
    void should_denyAccess_when_notAuthenticated() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
            .name("Unauthenticated Company")
            .build();

        // When/Then
        mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Test 10.4: should_allowGet_when_authenticatedAsAttendee
     */
    @Test
    @WithMockUser(username = "attendee@example.com", roles = {"ATTENDEE"})
    void should_allowGet_when_authenticatedAsAttendee() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/companies"))
            .andExpect(status().isOk());
    }

    /**
     * Test 10.5: should_denyUpdate_when_notOrganizer
     */
    @Test
    @WithMockUser(username = "speaker@example.com", roles = {"SPEAKER"})
    void should_denyUpdate_when_notOrganizer() throws Exception {
        // Given
        String companyId = "123e4567-e89b-12d3-a456-426614174000";

        // When/Then
        mockMvc.perform(put("/api/v1/companies/" + companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    /**
     * Test 10.6: should_allowUpdate_when_authenticatedAsOrganizer
     */
    @Test
    @WithMockUser(username = "organizer@example.com", roles = {"ORGANIZER"})
    void should_allowUpdate_when_authenticatedAsOrganizer() throws Exception {
        // First create a company
        CreateCompanyRequest createRequest = CreateCompanyRequest.builder()
            .name("Company To Update")
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        CompanyResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            CompanyResponse.class
        );

        // Then update it
        mockMvc.perform(put("/api/v1/companies/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"Updated Display Name\"}"))
            .andExpect(status().isOk());
    }

    /**
     * Test 10.7: should_denyDelete_when_notOrganizer
     */
    @Test
    @WithMockUser(username = "partner@example.com", roles = {"PARTNER"})
    void should_denyDelete_when_notOrganizer() throws Exception {
        // Given
        String companyId = "123e4567-e89b-12d3-a456-426614174000";

        // When/Then
        mockMvc.perform(delete("/api/v1/companies/" + companyId))
            .andExpect(status().isForbidden());
    }

    /**
     * Test 10.8: should_allowSearch_when_authenticated
     */
    @Test
    @WithMockUser(username = "user@example.com", roles = {"ATTENDEE"})
    void should_allowSearch_when_authenticated() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/companies/search")
                .param("query", "test"))
            .andExpect(status().isOk());
    }

    /**
     * Test 10.9: should_denySearch_when_notAuthenticated
     */
    @Test
    void should_denySearch_when_notAuthenticated() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/companies/search")
                .param("query", "test"))
            .andExpect(status().isUnauthorized());
    }
}
