package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for additional company endpoints (Task 15)
 * Tests AC11-13: Advanced search, UID validation, verification workflow
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@Transactional
@DisplayName("Additional Company Endpoints Integration Tests")
class AdditionalEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
    }

    // ==================== AC11: Advanced Search Endpoint Tests ====================

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/search - should return search results when query provided")
    void shouldReturnSearchResults_whenQueryProvided() throws Exception {
        // Given - Create test companies
        createTestCompany("Swisscom AG", "CHE-123.456.789");
        createTestCompany("Swiss Post", "CHE-987.654.321");
        createTestCompany("Credit Suisse", "CHE-111.222.333");

        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Swiss")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].name", hasItem(containsString("Swiss"))));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/search - should limit results when limit parameter provided")
    void shouldLimitResults_whenLimitParameterProvided() throws Exception {
        // Given - Create 10 test companies
        for (int i = 0; i < 10; i++) {
            createTestCompany("Company " + i, "CHE-10" + i + ".000.00" + i);
        }

        // When & Then - Request with limit of 5
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Company")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/search - should return cached results on second request")
    void shouldReturnCachedResults_whenSearchExecutedTwice() throws Exception {
        // Given - Create test company
        createTestCompany("Swisscom AG", "CHE-123.456.789");

        // When - First request (cache miss)
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Swisscom")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Delete the company from database
        companyRepository.deleteAll();

        // Then - Second request should still return cached result
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Swisscom")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Swisscom AG")));
    }

    @Test
    @WithMockUser(roles = {"SPEAKER"})
    @DisplayName("GET /api/v1/companies/search - should allow SPEAKER role")
    void shouldAllowSpeakerRole_whenSearching() throws Exception {
        // Given
        createTestCompany("Test Company", "CHE-123.456.789");

        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/companies/search - should return 401 when unauthenticated")
    void shouldReturn401_whenSearchingUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/search - should return empty array when no matches found")
    void shouldReturnEmptyArray_whenNoMatchesFound() throws Exception {
        // Given - Create company with different name
        createTestCompany("Different Company", "CHE-123.456.789");

        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "NonExistentCompany")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== AC12: Swiss UID Validation Endpoint Tests ====================

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should return valid for correct UID format")
    void shouldReturnValid_whenValidSwissUIDProvided() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "CHE-123.456.789")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.uid", is("CHE-123.456.789")));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should return invalid for incorrect UID format")
    void shouldReturnInvalid_whenInvalidUIDFormat() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "INVALID-UID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.uid", is("INVALID-UID")));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should return invalid for missing UID parameter")
    void shouldReturn400_whenUIDParameterMissing() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"SPEAKER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should allow SPEAKER role")
    void shouldAllowSpeakerRole_whenValidatingUID() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "CHE-123.456.789")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/companies/validate-uid - should return 401 when unauthenticated")
    void shouldReturn401_whenValidatingUIDUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "CHE-123.456.789")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should return invalid for all-zero UID")
    void shouldReturnInvalid_whenAllZeroUID() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "CHE-000.000.000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/validate-uid - should trim whitespace from UID")
    void shouldTrimWhitespace_whenValidatingUID() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "  CHE-123.456.789  ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    // ==================== AC13: Verification Workflow Endpoint Tests ====================

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should verify company when requested by ORGANIZER")
    void shouldVerifyCompany_whenRequestedByOrganizer() throws Exception {
        // Given - Create unverified company
        var company = createTestCompany("Test Company", "CHE-123.456.789");

        // When & Then
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(company.getId().toString())))
                .andExpect(jsonPath("$.isVerified", is(true)));
    }

    @Test
    @WithMockUser(roles = {"SPEAKER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should return 403 when SPEAKER attempts verification")
    void shouldReturn403_whenSpeakerAttemptsVerification() throws Exception {
        // Given
        var company = createTestCompany("Test Company", "CHE-123.456.789");

        // When & Then
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"PARTNER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should return 403 when PARTNER attempts verification")
    void shouldReturn403_whenPartnerAttemptsVerification() throws Exception {
        // Given
        var company = createTestCompany("Test Company", "CHE-123.456.789");

        // When & Then
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should return 404 when company does not exist")
    void shouldReturn404_whenCompanyDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/companies/00000000-0000-0000-0000-000000000000/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/companies/{id}/verify - should return 401 when unauthenticated")
    void shouldReturn401_whenVerifyingUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/companies/00000000-0000-0000-0000-000000000000/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should be idempotent when verifying already verified company")
    void shouldBeIdempotent_whenVerifyingAlreadyVerifiedCompany() throws Exception {
        // Given - Create and verify company
        var company = createTestCompany("Test Company", "CHE-123.456.789");

        // First verification
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified", is(true)));

        // Second verification (should succeed without error)
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified", is(true)));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{id}/verify - should update updatedAt timestamp when verified")
    void shouldUpdateTimestamp_whenCompanyVerified() throws Exception {
        // Given
        var company = createTestCompany("Test Company", "CHE-123.456.789");
        var originalUpdatedAt = company.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // When
        mockMvc.perform(post("/api/v1/companies/" + company.getId() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then - Verify updatedAt changed
        var updatedCompany = companyRepository.findById(company.getId()).orElseThrow();
        assert updatedCompany.getUpdatedAt().isAfter(originalUpdatedAt);
    }

    // ==================== Helper Methods ====================

    private Company createTestCompany(String name, String swissUID) {
        Company company = Company.builder()
                .name(name)
                .displayName(name)
                .swissUID(swissUID)
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        return companyRepository.save(company);
    }
}
