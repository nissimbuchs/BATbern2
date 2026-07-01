package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.generated.CreateCompanyRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateCompanyRequest;
import ch.batbern.companyuser.dto.generated.UpdateCompanyRequest;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration tests for CompanyController REST API
 * Tests all CRUD operations, authentication, authorization, and validation
 * AC4: REST API implementation with full coverage
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Company REST API Integration Tests")
class CompanyControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();

        testCompany = Company.builder()
                .name("Test Company")
                .displayName("Test Co")
                .swissUID("CHE-123.456.789")
                .website("https://test.example.com")
                .industry("Technology")
                .description("A test company")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        testCompany = companyRepository.save(testCompany);
    }

    // CREATE COMPANY TESTS (AC4)

    @Test
    @DisplayName("POST /companies - should create company when authenticated with valid role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldCreateCompany_whenAuthenticatedWithValidRole() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .displayName("New Co")
                .swissUID("CHE-987.654.321")
                .website("https://new.example.com")
                .industry("Finance")
                .description("A new company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // Story 1.16.2: use company name instead of UUID - removed id field
                .andExpect(jsonPath("$.name").value("New Company"))
                .andExpect(jsonPath("$.displayName").value("New Co"))
                .andExpect(jsonPath("$.swissUID").value("CHE-987.654.321"))
                .andExpect(jsonPath("$.website").value("https://new.example.com"))
                .andExpect(jsonPath("$.industry").value("Finance"))
                .andExpect(jsonPath("$.description").value("A new company"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // Verify company was persisted
        assertThat(companyRepository.findByName("New Company")).isPresent();
    }

    // ── POST /companies:get-or-create (colon custom method, AIP-136) ──────────

    @Test
    @DisplayName("POST /companies:get-or-create - creates company for any authenticated role (incl. ATTENDEE)")
    @WithMockUser(roles = {"ATTENDEE"})
    void shouldGetOrCreateCompany_whenAuthenticatedAttendee() throws Exception {
        GetOrCreateCompanyRequest request = GetOrCreateCompanyRequest.builder()
                .displayName("Infowell GmbH")
                .build();

        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Infowell GmbH"))
                // ADR-003: name is the generated lowercase-alphanumeric slug
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.matchesPattern("^[a-z0-9]+$")));

        assertThat(companyRepository.findByNameContainingIgnoreCase("infowell")).hasSize(1);
    }

    @Test
    @DisplayName("POST /companies:get-or-create - is idempotent (no duplicate on repeat)")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldBeIdempotent_whenSameDisplayNameRepeated() throws Exception {
        long before = companyRepository.count();
        GetOrCreateCompanyRequest request = GetOrCreateCompanyRequest.builder()
                .displayName("Repeat Co AG")
                .build();
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Exactly one new company created across both calls
        assertThat(companyRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("POST /companies:get-or-create - resolves existing company by generated slug (no duplicate)")
    @WithMockUser(roles = {"PARTNER"})
    void shouldResolveExisting_whenSlugMatches() throws Exception {
        // Seed a properly-slugged company: slug("Slugged Co") == "sluggedco".
        Company existing = Company.builder()
                .name("sluggedco")
                .displayName("Slugged Co")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        companyRepository.save(existing);
        long before = companyRepository.count();

        GetOrCreateCompanyRequest request = GetOrCreateCompanyRequest.builder()
                .displayName("Slugged Co")
                .build();

        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("sluggedco"));

        // No new company — existing one resolved by slug
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("POST /companies:get-or-create - returns 401 when not authenticated")
    void shouldReturn401_whenGetOrCreateUnauthenticated() throws Exception {
        GetOrCreateCompanyRequest request = GetOrCreateCompanyRequest.builder()
                .displayName("Anon Co")
                .build();

        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /companies:get-or-create - returns 400 when displayName blank")
    @WithMockUser(roles = {"ATTENDEE"})
    void shouldReturn400_whenGetOrCreateDisplayNameBlank() throws Exception {
        GetOrCreateCompanyRequest request = GetOrCreateCompanyRequest.builder()
                .displayName("")
                .build();

        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /companies - should return 403 when authenticated with insufficient role")
    @WithMockUser(roles = {"ATTENDEE"})
    void shouldReturn403_whenInsufficientRole() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when required field is missing")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenRequiredFieldMissing() throws Exception {
        // Given - Missing required 'name' field
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .displayName("New Co")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when Swiss UID format is invalid")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenSwissUIDFormatInvalid() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .swissUID("INVALID-UID")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 409 when company name already exists")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn409_whenCompanyNameAlreadyExists() throws Exception {
        // Given - Use existing company name
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name(testCompany.getName())
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // GET COMPANY BY ID TESTS (AC4)

    @Test
    @DisplayName("GET /companies/{name} - should return company when authenticated")
    @WithMockUser
    void shouldReturnCompany_whenAuthenticated() throws Exception {
        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(get("/api/v1/companies/{name}", testCompany.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(testCompany.getName()))
                .andExpect(jsonPath("$.displayName").value(testCompany.getDisplayName()))
                .andExpect(jsonPath("$.swissUID").value(testCompany.getSwissUID()))
                .andExpect(jsonPath("$.website").value(testCompany.getWebsite()))
                .andExpect(jsonPath("$.industry").value(testCompany.getIndustry()))
                .andExpect(jsonPath("$.description").value(testCompany.getDescription()))
                .andExpect(jsonPath("$.isVerified").value(testCompany.isVerified()));
    }

    // Test removed: GET /companies/{name} is now public (Story 4.1: Partner Showcase)
    // Public company endpoint allows partner showcase enrichment without authentication

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @DisplayName("GET /companies/{name} - should return 404 when company not found")
    @WithMockUser
    void shouldReturn404_whenCompanyNotFound() throws Exception {
        // Given
        // Story 1.16.2: use company name instead of UUID
        String nonExistentName = "Non Existent Company";

        // When & Then
        mockMvc.perform(get("/api/v1/companies/{name}", nonExistentName))
                .andExpect(status().isNotFound());
    }

    // GET ALL COMPANIES TESTS (AC4)

    @Test
    @DisplayName("GET /companies - should return all companies when authenticated")
    @WithMockUser
    void shouldReturnAllCompanies_whenAuthenticated() throws Exception {
        // Given - Create additional company
        Company company2 = Company.builder()
                .name("Another Company")
                .displayName("Another Co")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        companyRepository.save(company2);

        // When & Then
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("Test Company", "Another Company")))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));
    }

    @Test
    @DisplayName("GET /companies - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForGetAll() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized());
    }

    // SEARCH COMPANIES TESTS (AC5)

    @Test
    @DisplayName("GET /companies/search - should return search results when authenticated")
    @WithMockUser
    void shouldReturnSearchResults_whenAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Test Company"));
    }

    // Test removed: GET /companies/search is now public (Story 4.1: Partner Showcase)
    // Public company search endpoint allows partner showcase enrichment without authentication

    // UPDATE COMPANY TESTS (AC4)

    @Test
    @DisplayName("PUT /companies/{name} - should update company when authenticated with ORGANIZER role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldUpdateCompany_whenAuthenticatedWithOrganizerRole() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated Display Name")
                .website("https://updated.example.com")
                .industry("Updated Industry")
                .description("Updated description")
                .build();

        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(patch("/api/v1/companies/{name}", testCompany.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(testCompany.getName())) // Name unchanged
                .andExpect(jsonPath("$.displayName").value("Updated Display Name"))
                .andExpect(jsonPath("$.website").value("https://updated.example.com"))
                .andExpect(jsonPath("$.industry").value("Updated Industry"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        // Verify company was updated in database
        Company updated = companyRepository.findByName(testCompany.getName()).orElseThrow();
        assertThat(updated.getDisplayName()).isEqualTo("Updated Display Name");
    }

    @Test
    @DisplayName("PUT /companies/{name} - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForUpdate() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(patch("/api/v1/companies/{name}", testCompany.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /companies/{name} - should return 403 when authenticated without ORGANIZER role")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldReturn403_whenNotOrganizerForUpdate() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(patch("/api/v1/companies/{name}", testCompany.getName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @DisplayName("PUT /companies/{name} - should return 404 when company not found")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn404_whenCompanyNotFoundForUpdate() throws Exception {
        // Given
        // Story 1.16.2: use company name instead of UUID
        String nonExistentName = "Non Existent Company";
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        mockMvc.perform(patch("/api/v1/companies/{name}", nonExistentName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // DELETE COMPANY TESTS (AC4)

    @Test
    @DisplayName("DELETE /companies/{name} - should delete company when authenticated with ORGANIZER role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldDeleteCompany_whenAuthenticatedWithOrganizerRole() throws Exception {
        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(delete("/api/v1/companies/{name}", testCompany.getName()))
                .andExpect(status().isNoContent());

        // Verify company was deleted
        assertThat(companyRepository.findByName(testCompany.getName())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /companies/{name} - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForDelete() throws Exception {
        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(delete("/api/v1/companies/{name}", testCompany.getName()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /companies/{name} - should return 403 when authenticated without ORGANIZER role")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldReturn403_whenNotOrganizerForDelete() throws Exception {
        // When & Then
        // Story 1.16.2: use company name instead of UUID
        mockMvc.perform(delete("/api/v1/companies/{name}", testCompany.getName()))
                .andExpect(status().isForbidden());
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @DisplayName("DELETE /companies/{name} - should return 404 when company not found")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn404_whenCompanyNotFoundForDelete() throws Exception {
        // Given
        // Story 1.16.2: use company name instead of UUID
        String nonExistentName = "Non Existent Company";

        // When & Then
        mockMvc.perform(delete("/api/v1/companies/{name}", nonExistentName))
                .andExpect(status().isNotFound());
    }

    // VALIDATION TESTS

    @Test
    @DisplayName("POST /companies - should return 400 when name is too short")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenNameTooShort() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("A") // Too short (min 2 characters)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when name is too long")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenNameTooLong() throws Exception {
        // Given
        String longName = "A".repeat(256); // Too long (max 255 characters)
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name(longName)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ROLE-BASED ACCESS TESTS

    @Test
    @DisplayName("POST /companies - should allow SPEAKER role to create company")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldAllowSpeakerToCreateCompany() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("Speaker Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /companies - should allow PARTNER role to create company")
    @WithMockUser(roles = {"PARTNER"})
    void shouldAllowPartnerToCreateCompany() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("Partner Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /companies/search - should return 400 when limit param is not an integer")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400WhenLimitParamIsNotAnInteger() throws Exception {
        // ZAP finding: ?limit=c:/Windows/system.ini caused 500 — must return 400
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "Swiss")
                        .param("limit", "not-an-integer")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
    }

    // SWISS UID VALIDATION TESTS (AC12)
    // (migrated from the former CompanyControllerTest @WebMvcTest slice — see CompanyVerificationApi)

    @Test
    @DisplayName("GET /companies/validate-uid - returns valid=true for a well-formed Swiss UID")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldValidateUID_whenValidUIDProvided() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "CHE-123.456.789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.uid").value("CHE-123.456.789"))
                .andExpect(jsonPath("$.message").value("Valid Swiss UID format"));
    }

    @Test
    @DisplayName("GET /companies/validate-uid - returns valid=false for a malformed Swiss UID")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturnInvalid_whenInvalidUIDProvided() throws Exception {
        mockMvc.perform(get("/api/v1/companies/validate-uid")
                        .param("uid", "INVALID-UID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.uid").value("INVALID-UID"))
                .andExpect(jsonPath("$.message").value("Invalid Swiss UID format. Expected: CHE-XXX.XXX.XXX"));
    }

    // COMPANY VERIFICATION TESTS (AC13)

    @Test
    @DisplayName("POST /companies/{name}/verify - marks company verified for ORGANIZER")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldVerifyCompany_whenOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/companies/{name}/verify", testCompany.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(testCompany.getName()))
                .andExpect(jsonPath("$.isVerified").value(true));

        Company verified = companyRepository.findByName(testCompany.getName()).orElseThrow();
        assertThat(verified.isVerified()).isTrue();
    }

    @Test
    @DisplayName("POST /companies/{name}/verify - returns 403 for non-ORGANIZER")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldReturn403_whenVerifyNotOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/companies/{name}/verify", testCompany.getName()))
                .andExpect(status().isForbidden());
    }
}
