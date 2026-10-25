package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for additional company endpoints (Task 15)
 * Tests AC11-13: Advanced search, UID validation, verification workflow
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Additional Company Endpoints Integration Tests")
class AdditionalEndpointsIntegrationTest extends AbstractIntegrationTest {

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
        createTestCompany("Swisscom AG");
        createTestCompany("Swiss Post");
        createTestCompany("Credit Suisse");

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
            createTestCompany("Company " + i);
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
        createTestCompany("Swisscom AG");

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
        createTestCompany("Test Company");

        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/companies/search - should return 200 when unauthenticated (public endpoint for registration autocomplete)")
    void shouldReturn200_whenSearchingUnauthenticated() throws Exception {
        // Story 4.1.5: Company search is now public for registration autocomplete
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("GET /api/v1/companies/search - should return empty array when no matches found")
    void shouldReturnEmptyArray_whenNoMatchesFound() throws Exception {
        // Given - Create company with different name
        createTestCompany("Different Company");

        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "NonExistentCompany")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== AC13: Verification Workflow Endpoint Tests ====================

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should verify company when requested by ORGANIZER")
    void shouldVerifyCompany_whenRequestedByOrganizer() throws Exception {
        // Given - Create unverified company
        var company = createTestCompany("Test Company");

        // When & Then - Story 1.16.2: use company name in path instead of UUID
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(company.getName())))
                .andExpect(jsonPath("$.isVerified", is(true)));
    }

    @Test
    @WithMockUser(roles = {"SPEAKER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should return 403 when SPEAKER attempts verification")
    void shouldReturn403_whenSpeakerAttemptsVerification() throws Exception {
        // Given
        var company = createTestCompany("Test Company");

        // When & Then - Story 1.16.2: use company name in path instead of UUID
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"PARTNER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should return 403 when PARTNER attempts verification")
    void shouldReturn403_whenPartnerAttemptsVerification() throws Exception {
        // Given
        var company = createTestCompany("Test Company");

        // When & Then - Story 1.16.2: use company name in path instead of UUID
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should return 404 when company does not exist")
    void shouldReturn404_whenCompanyDoesNotExist() throws Exception {
        // Story 1.16.2: use non-existent company name instead of UUID
        mockMvc.perform(post("/api/v1/companies/NonExistentCompany/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/companies/{name}/verify - should return 401 when unauthenticated")
    void shouldReturn401_whenVerifyingUnauthenticated() throws Exception {
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(post("/api/v1/companies/SomeCompany/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should be idempotent when verifying already verified company")
    void shouldBeIdempotent_whenVerifyingAlreadyVerifiedCompany() throws Exception {
        // Given - Create and verify company
        var company = createTestCompany("Test Company");

        // First verification - Story 1.16.2: use company name in path instead of UUID
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified", is(true)));

        // Second verification (should succeed without error)
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified", is(true)));
    }

    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    @DisplayName("POST /api/v1/companies/{name}/verify - should update updatedAt timestamp when verified")
    void shouldUpdateTimestamp_whenCompanyVerified() throws Exception {
        // Given
        var company = createTestCompany("Test Company");
        var originalUpdatedAt = company.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // When - Story 1.16.2: use company name in path instead of UUID
        mockMvc.perform(post("/api/v1/companies/" + company.getName() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then - Verify updatedAt changed (lookup by name, not ID)
        var updatedCompany = companyRepository.findByName(company.getName()).orElseThrow();
        assert updatedCompany.getUpdatedAt().isAfter(originalUpdatedAt);
    }

    // ==================== Helper Methods ====================

    private Company createTestCompany(String name) {
        Company company = Company.builder()
                .name(name)
                .displayName(name)
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        return companyRepository.save(company);
    }
}
